package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.Debt
import com.example.data.model.DebtTransaction
import java.io.File
import java.io.FileOutputStream

object ReceiptPdfExporter {

  fun shareStatementPdf(
    context: Context,
    debt: Debt,
    transactions: List<DebtTransaction>,
    ownerName: String,
  ): Result<Unit> {
    return runCatching {
      val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
      val safeCode = debt.verificationCode.replace(Regex("[^A-Za-z0-9_-]"), "_")
      val pdfFile = File(receiptsDir, "duyun_statement_$safeCode.pdf")

      val document = PdfDocument()
      try {
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val cairoTypeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val tajawalTypeface = runCatching { ResourcesCompat.getFont(context, R.font.tajawal) }.getOrNull() ?: Typeface.DEFAULT

        val headerBgPaint = Paint().apply { color = Color.parseColor("#0B5CAD") }
        val accentGoldPaint = Paint().apply { color = Color.parseColor("#0A8762") }
        val cardBgPaint = Paint().apply { color = Color.parseColor("#EEF6FB") }
        val dividerPaint = Paint().apply {
          color = Color.parseColor("#C4D7E5")
          strokeWidth = 1f
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = 20f
          typeface = cairoTypeface
          textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#D6EAFF")
          textSize = 12f
          typeface = tajawalTypeface
          textAlign = Paint.Align.RIGHT
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#0B5CAD")
          textSize = 14f
          typeface = cairoTypeface
          textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#131C18")
          textSize = 12f
          typeface = tajawalTypeface
          textAlign = Paint.Align.RIGHT
        }
        val staticLayoutPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#131C18")
          textSize = 12f
          typeface = tajawalTypeface
          textAlign = Paint.Align.LEFT
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#54635C")
          textSize = 10.5f
          typeface = tajawalTypeface
          textAlign = Paint.Align.RIGHT
        }

        // Header Banner
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 110f, headerBgPaint)
        canvas.drawRect(0f, 106f, pageWidth.toFloat(), 110f, accentGoldPaint)
        canvas.drawText("كشف حساب وتوثيق مالي — تطبيق ديون", pageWidth - 36f, 46f, titlePaint)
        canvas.drawText(
          "مرجع التوثيق: ${debt.verificationCode}  |  تاريخ الإصدار: ${formatDateTimeAr(System.currentTimeMillis())}",
          pageWidth - 36f,
          74f,
          subtitlePaint,
        )
        if (ownerName.isNotBlank()) {
          canvas.drawText("صاحب الحساب: $ownerName", pageWidth - 36f, 94f, subtitlePaint)
        }

        var y = 136f
        // Summary Box
        canvas.drawRoundRect(32f, y, pageWidth - 32f, y + 132f, 12f, 12f, cardBgPaint)
        canvas.drawText("ملخص السجل المالي", pageWidth - 48f, y + 26f, sectionPaint)
        canvas.drawText("الطرف الآخر: ${debt.personName} (${debt.directionEnum.labelAr})", pageWidth - 48f, y + 50f, bodyPaint)
        canvas.drawText(
          "المبلغ الأصلي: ${formatAmount(debt.originalAmount, debt.currency)}   |   المدفوع: ${formatAmount(debt.paidAmount, debt.currency)}",
          pageWidth - 48f,
          y + 72f,
          bodyPaint,
        )
        canvas.drawText(
          "الرصيد المتبقي: ${formatAmount(debt.remainingAmount, debt.currency)}   |   الحالة: ${debt.statusEnum.labelAr}",
          pageWidth - 48f,
          y + 94f,
          bodyPaint,
        )
        val dueText = if (debt.dueDateMillis > 0L) formatDateAr(debt.dueDateMillis) else "غير محدد"
        canvas.drawText("موعد السداد المستهدف: $dueText", pageWidth - 48f, y + 116f, mutedPaint)

        y += 156f
        if (debt.notes.isNotBlank()) {
          val noteText = "ملاحظات الاتفاق: ${debt.notes}"
          val noteLayout = StaticLayout.Builder.obtain(
            noteText,
            0,
            noteText.length,
            staticLayoutPaint,
            pageWidth - 72,
          )
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .build()
          canvas.save()
          canvas.translate(36f, y)
          noteLayout.draw(canvas)
          canvas.restore()
          y += noteLayout.height + 16f
        }

        canvas.drawText("سجل العمليات الموثقة (${transactions.size} عملية)", pageWidth - 36f, y, sectionPaint)
        y += 14f
        canvas.drawLine(36f, y, pageWidth - 36f, y, dividerPaint)
        y += 20f

        val orderedTx = transactions.sortedBy { it.performedAtMillis }
        for ((index, tx) in orderedTx.withIndex()) {
          if (y > pageHeight - 80f) {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 48f
          }
          canvas.drawText(
            "${index + 1}. ${tx.typeEnum.labelAr} — ${formatAmount(tx.amount, debt.currency)} (الرصيد بعدها: ${formatAmount(tx.balanceAfter, debt.currency)})",
            pageWidth - 36f,
            y,
            bodyPaint,
          )
          y += 18f
          canvas.drawText(
            "التاريخ: ${formatDateTimeAr(tx.performedAtMillis)}  |  رقم العملية: ${tx.referenceCode}",
            pageWidth - 48f,
            y,
            mutedPaint,
          )
          y += 16f
          if (tx.note.isNotBlank()) {
            canvas.drawText("البيان: ${tx.note}", pageWidth - 48f, y, mutedPaint)
            y += 16f
          }
          canvas.drawLine(48f, y, pageWidth - 48f, y, dividerPaint)
          y += 18f
        }

        document.finishPage(page)
        FileOutputStream(pdfFile).use { out -> document.writeTo(out) }
      } finally {
        document.close()
      }

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "كشف حساب موثق - ${debt.personName} (${debt.verificationCode})")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(shareIntent, "مشاركة كشف الحساب الموثق (PDF)")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    }
  }
}
