package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfService(private val context: Context) {

    /**
     * Generates a multi-page high-yield summary PDF from the generated lesson notes.
     */
    suspend fun generateSummaryPdf(
        lessonId: Long,
        title: String,
        summaryMarkdown: String,
        keyPoints: List<String>
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // Standard A4 width in points at 72 dpi
        val pageHeight = 842 // Standard A4 height in points at 72 dpi

        val marginHorizontal = 40f
        val marginVertical = 45f
        val contentWidth = pageWidth - (marginHorizontal * 2)

        // Paints
        val titlePaint = Paint().apply {
            color = Color.parseColor("#1E3A8A") // Deep navy
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#64748B") // Slate gray
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val sectionHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E40AF")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val calloutTitlePaint = Paint().apply {
            color = Color.parseColor("#92400E") // Dark amber
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val calloutBodyPaint = Paint().apply {
            color = Color.parseColor("#78350F")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val boxPaint = Paint().apply {
            color = Color.parseColor("#FEF3C7") // Warm amber container
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val boxBorderPaint = Paint().apply {
            color = Color.parseColor("#F59E0B")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }

        val headerBannerPaint = Paint().apply {
            color = Color.parseColor("#DBEAFE")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        var currentY = marginVertical

        // Draw top brand banner
        val bannerRect = RectF(marginHorizontal, currentY, marginHorizontal + contentWidth, currentY + 60f)
        canvas.drawRoundRect(bannerRect, 8f, 8f, headerBannerPaint)

        canvas.drawText("StudyAid • High-Yield Exam Summary", marginHorizontal + 16f, currentY + 24f, sectionHeaderPaint)
        val displayTitle = if (title.length > 45) title.take(42) + "..." else title
        canvas.drawText(displayTitle, marginHorizontal + 16f, currentY + 46f, titlePaint)
        currentY += 75f

        // Draw Key Facts Callout Box
        if (keyPoints.isNotEmpty()) {
            val calloutHeight = minOf(140f, 30f + (keyPoints.take(4).size * 22f))
            val calloutRect = RectF(marginHorizontal, currentY, marginHorizontal + contentWidth, currentY + calloutHeight)
            canvas.drawRoundRect(calloutRect, 8f, 8f, boxPaint)
            canvas.drawRoundRect(calloutRect, 8f, 8f, boxBorderPaint)

            canvas.drawText("⚠️ High-Stakes Key Takeaways", marginHorizontal + 14f, currentY + 20f, calloutTitlePaint)

            var pointY = currentY + 38f
            for (pt in keyPoints.take(4)) {
                val cleanPoint = pt.replace(Regex("^[-*•0-9.]+\\s*"), "").trim()
                val wrapped = wrapText(cleanPoint, calloutBodyPaint, contentWidth - 40f)
                canvas.drawText("•  ${wrapped.firstOrNull() ?: ""}", marginHorizontal + 16f, pointY, calloutBodyPaint)
                pointY += 18f
            }
            currentY += calloutHeight + 18f
        }

        // Parse markdown lines and draw
        val lines = summaryMarkdown.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) {
                currentY += 8f
                continue
            }

            // Check if page limit reached
            if (currentY > pageHeight - marginVertical - 30f) {
                // Footer
                canvas.drawText("Page $pageNumber", pageWidth - marginHorizontal - 40f, pageHeight - 20f, subtitlePaint)
                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = marginVertical

                // Draw miniature header on subsequent pages
                canvas.drawText("StudyAid — $displayTitle", marginHorizontal, currentY, subtitlePaint)
                currentY += 24f
            }

            when {
                line.startsWith("# ") -> {
                    currentY += 12f
                    val heading = line.removePrefix("# ").trim()
                    canvas.drawText(heading, marginHorizontal, currentY, titlePaint)
                    currentY += 22f
                }
                line.startsWith("## ") -> {
                    currentY += 14f
                    val heading = line.removePrefix("## ").trim()
                    canvas.drawText(heading, marginHorizontal, currentY, sectionHeaderPaint)
                    currentY += 18f
                }
                line.startsWith("### ") -> {
                    currentY += 10f
                    val heading = line.removePrefix("### ").trim()
                    canvas.drawText(heading, marginHorizontal, currentY, sectionHeaderPaint)
                    currentY += 16f
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val bulletText = line.substring(2).trim()
                    val wrappedLines = wrapText("•  $bulletText", bodyPaint, contentWidth - 10f)
                    for (wLine in wrappedLines) {
                        canvas.drawText(wLine, marginHorizontal + 10f, currentY, bodyPaint)
                        currentY += 14f
                    }
                }
                line.startsWith("|") -> {
                    // Simple table line rendering
                    val cells = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    val cellWidth = contentWidth / maxOf(1, cells.size)
                    cells.forEachIndexed { index, cell ->
                        val cellText = if (cell.length > 25) cell.take(22) + ".." else cell
                        canvas.drawText(cellText, marginHorizontal + (index * cellWidth), currentY, bodyPaint)
                    }
                    currentY += 15f
                }
                else -> {
                    val wrappedLines = wrapText(line, bodyPaint, contentWidth)
                    for (wLine in wrappedLines) {
                        canvas.drawText(wLine, marginHorizontal, currentY, bodyPaint)
                        currentY += 14f
                    }
                }
            }
        }

        // Draw last page footer
        canvas.drawText("Page $pageNumber • Generated by StudyAid AI", marginHorizontal, pageHeight - 20f, subtitlePaint)
        pdfDocument.finishPage(page)

        // Save PDF to internal files directory
        val summariesDir = File(context.filesDir, "summaries")
        if (!summariesDir.exists()) summariesDir.mkdirs()

        val pdfFile = File(summariesDir, "lesson_${lessonId}_summary.pdf")
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        pdfFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = StringBuilder(candidate)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * Reads text content from a picked file URI (txt, markdown, or plain stream).
     */
    suspend fun readTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            } ?: ""
        } catch (e: Exception) {
            "Error reading file: ${e.localizedMessage}"
        }
    }
}
