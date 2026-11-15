const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validDebtPayload(uid, overrides = {}) {
  return {
    userId: uid,
    personName: "أحمد محمد",
    personPhone: "0501234567",
    direction: "receivable",
    currency: "SAR",
    originalAmount: 500.0,
    remainingAmount: 500.0,
    status: "active",
    dueDateMillis: 1795000000000,
    reminderEnabled: true,
    notes: "قرض شخصي",
    verificationCode: "DY-2026-A1B2",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function validTransactionPayload(uid, debtId, overrides = {}) {
  return {
    userId: uid,
    debtId,
    type: "initial",
    amount: 500.0,
    balanceAfter: 500.0,
    note: "إنشاء سجل الدين",
    referenceCode: "TX-2026-0001",
    performedAtMillis: Date.now(),
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot list or write debts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/debts`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/debts/debt_1`).set(validDebtPayload(ALICE_UID))
  );
});

test("Authenticated owner can create debt and initial transaction atomically in batch", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  const debtRef = aliceDb.doc(`users/${ALICE_UID}/debts/debt_1`);
  const txRef = aliceDb.doc(`users/${ALICE_UID}/debts/debt_1/transactions/tx_1`);
  batch.set(debtRef, validDebtPayload(ALICE_UID));
  batch.set(txRef, validTransactionPayload(ALICE_UID, "debt_1"));
  await assertSucceeds(batch.commit());
});

test("Cross-user access: Bob cannot read, create, update, or delete Alice's debts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/debts/debt_1`).set(validDebtPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/debts`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/debts/debt_1`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/debts/debt_2`).set(validDebtPayload(ALICE_UID))
  );
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/debts/debt_1`).update({
      remainingAmount: 0,
      status: "settled",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/debts/debt_1`).delete());
});

test("Shadow update with ghost field or invalid direction/amount fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/debts/debt_ghost`).set({
      ...validDebtPayload(ALICE_UID),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/debts/debt_bad_dir`).set(
      validDebtPayload(ALICE_UID, { direction: "other" })
    )
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/debts/debt_neg`).set(
      validDebtPayload(ALICE_UID, { originalAmount: -50, remainingAmount: -50 })
    )
  );
});

test("Terminal state locking prevents modifying settled debt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const debtRef = aliceDb.doc(`users/${ALICE_UID}/debts/debt_1`);
  await assertSucceeds(debtRef.set(validDebtPayload(ALICE_UID)));

  // Settle the debt
  await assertSucceeds(
    debtRef.update({
      remainingAmount: 0.0,
      status: "settled",
      updatedAt: serverTimestamp(),
    })
  );

  // Subsequent modification on settled debt must be rejected
  await assertFails(
    debtRef.update({
      remainingAmount: 100.0,
      status: "active",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Orphan transaction creation without parent debt fails; updating transaction fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .doc(`users/${ALICE_UID}/debts/missing_debt/transactions/tx_1`)
      .set(validTransactionPayload(ALICE_UID, "missing_debt"))
  );

  const debtRef = aliceDb.doc(`users/${ALICE_UID}/debts/debt_1`);
  const txRef = aliceDb.doc(`users/${ALICE_UID}/debts/debt_1/transactions/tx_1`);
  await assertSucceeds(debtRef.set(validDebtPayload(ALICE_UID)));
  await assertSucceeds(txRef.set(validTransactionPayload(ALICE_UID, "debt_1")));

  // Transaction audit log is immutable on update
  await assertFails(txRef.update({ amount: 1.0 }));
});
