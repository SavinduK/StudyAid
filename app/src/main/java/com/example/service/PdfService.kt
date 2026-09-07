package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.model.FileAttachment
import com.example.data.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        // Draw Key Facts Section - Render EVERY Important Point
        if (keyPoints.isNotEmpty()) {
            // Check page boundary
            if (currentY > pageHeight - marginVertical - 60f) {
                canvas.drawText("Page $pageNumber", pageWidth - marginHorizontal - 40f, pageHeight - 20f, subtitlePaint)
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = marginVertical
                canvas.drawText("StudyAid — $displayTitle", marginHorizontal, currentY, subtitlePaint)
                currentY += 24f
            }

            canvas.drawText("⚠️ HIGH-YIELD EXAM TAKEAWAYS & KEY POINTS", marginHorizontal, currentY + 12f, sectionHeaderPaint)
            currentY += 22f

            for (pt in keyPoints) {
                val cleanPoint = pt.replace(Regex("^[-*•0-9.]+\\s*"), "").trim()
                val wrapped = wrapText("•  $cleanPoint", calloutBodyPaint, contentWidth - 28f)
                val blockHeight = (wrapped.size * 14f) + 10f

                if (currentY + blockHeight > pageHeight - marginVertical - 30f) {
                    canvas.drawText("Page $pageNumber", pageWidth - marginHorizontal - 40f, pageHeight - 20f, subtitlePaint)
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = marginVertical
                    canvas.drawText("StudyAid — $displayTitle", marginHorizontal, currentY, subtitlePaint)
                    currentY += 24f
                }

                val itemRect = RectF(marginHorizontal, currentY, marginHorizontal + contentWidth, currentY + blockHeight)
                canvas.drawRoundRect(itemRect, 6f, 6f, boxPaint)
                canvas.drawRoundRect(itemRect, 6f, 6f, boxBorderPaint)

                var textY = currentY + 12f
                for (wLine in wrapped) {
                    canvas.drawText(wLine, marginHorizontal + 12f, textY, calloutBodyPaint)
                    textY += 14f
                }
                currentY += blockHeight + 6f
            }
            currentY += 10f
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

    /**
     * Generates a structured plain text file for questions and explanations.
     * Saved locally in internal storage; not shown in the UI.
     */
    suspend fun generateQuestionsTextFile(
        lessonId: Long,
        lessonTitle: String,
        questions: List<Question>
    ): File = withContext(Dispatchers.IO) {
        val questionsDir = File(context.filesDir, "questions")
        if (!questionsDir.exists()) questionsDir.mkdirs()

        val textFile = File(questionsDir, "questions_lesson_${lessonId}.txt")
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateStr = dateFormatter.format(Date())

        val content = buildString {
            appendLine("================================================================================")
            appendLine("STUDYAID • PRACTICE QUESTIONS & EXAM RATIONALE")
            appendLine("Lesson: $lessonTitle")
            appendLine("Generated: $dateStr")
            appendLine("Total Questions: ${questions.size}")
            appendLine("================================================================================")
            appendLine()

            questions.forEachIndexed { index, q ->
                appendLine("--------------------------------------------------------------------------------")
                appendLine("QUESTION ${index + 1} [Type: ${q.questionType}]")
                appendLine(q.questionText)
                appendLine()

                try {
                    val options = JSONArray(q.optionsJson)
                    if (options.length() > 0) {
                        appendLine("OPTIONS:")
                        for (i in 0 until options.length()) {
                            appendLine("  ${options.getString(i)}")
                        }
                        appendLine()
                    }
                } catch (_: Exception) {}

                appendLine("CORRECT ANSWER: ${q.correctAnswer}")
                appendLine()
                appendLine("EXPLANATION / RATIONALE:")
                appendLine(q.explanation.ifBlank { "Core medical syllabus rationale." })
                appendLine()
            }
            appendLine("================================================================================")
            appendLine("END OF QUESTIONS")
            appendLine("================================================================================")
        }

        textFile.writeText(content)
        textFile
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
     * Reads a file attachment directly from a picked URI into base64 bytes for model upload.
     */
     suspend fun readFileAttachmentFromUri(uri: Uri): FileAttachment? = withContext(Dispatchers.IO) {
        try {
            var fileName = "lecture_document.pdf"
            var fileSize = 0L

            // Extract display name and size from content provider
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Determine accurate MIME type
            var mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.isBlank() || mimeType == "application/octet-stream") {
                val lower = fileName.lowercase()
                mimeType = when {
                    lower.endsWith(".pdf") -> "application/pdf"
                    lower.endsWith(".png") -> "image/png"
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                    lower.endsWith(".webp") -> "image/webp"
                    lower.endsWith(".txt") -> "text/plain"
                    lower.endsWith(".md") -> "text/markdown"
                    else -> "application/pdf"
                }
            }

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            if (fileSize <= 0) {
                fileSize = bytes.size.toLong()
            }

            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)

            FileAttachment(
                fileName = fileName,
                mimeType = mimeType,
                base64Data = base64Data,
                sizeBytes = fileSize
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveAttachmentLocally(attachment: FileAttachment, lessonId: Long): File? = withContext(Dispatchers.IO) {
        try {
            val uploadsDir = File(context.filesDir, "uploads")
            if (!uploadsDir.exists()) uploadsDir.mkdirs()
            val cleanName = attachment.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destFile = File(uploadsDir, "lesson_${lessonId}_$cleanName")
            val bytes = Base64.decode(attachment.base64Data, Base64.NO_WRAP)
            destFile.writeBytes(bytes)
            destFile
        } catch (e: Exception) {
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
            bytes > 0 -> "$bytes B"
            else -> ""
        }
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
