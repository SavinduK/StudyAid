package com.example

import com.example.data.model.AppSettings
import com.example.service.GeminiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAidUnitTest {

    @Test
    fun testAppSettingsDefaults() {
        val settings = AppSettings()
        assertEquals("gemini-3.5-flash", settings.selectedModel)
        assertEquals("MIXED", settings.questionType)
        assertEquals(5, settings.questionCount)
    }

    @Test
    fun testBuildSummaryPromptContainsContext() {
        val lecture = "Renin-angiotensin-aldosterone system causes vasoconstriction."
        val pastPaper = "What is the primary action of ACE inhibitors?"
        val prompt = GeminiService.buildSummaryPrompt(lecture, pastPaper)

        assertTrue(prompt.contains(lecture))
        assertTrue(prompt.contains(pastPaper))
        assertTrue(prompt.contains("High-Yield Exam Summary"))
    }

    @Test
    fun testBuildQuestionPromptFormat() {
        val lecture = "Autonomic pharmacology"
        val mcqPrompt = GeminiService.buildQuestionPrompt(lecture, null, "MCQ", 10)
        assertTrue(mcqPrompt.contains("Multiple Choice Questions"))
        assertTrue(mcqPrompt.contains("10 high-quality exam questions"))

        val tfPrompt = GeminiService.buildQuestionPrompt(lecture, null, "TRUE_FALSE", 5)
        assertTrue(tfPrompt.contains("True/False statement-based questions"))
        assertTrue(tfPrompt.contains("5 high-quality exam questions"))
    }

    @Test
    fun testBuildSummaryPromptWithAttachment() {
        val prompt = GeminiService.buildSummaryPrompt(
            lectureContent = "Focus on RAAS inhibitors",
            pastPaperText = null,
            hasLectureAttachment = true,
            hasPastPaperAttachment = false
        )
        assertTrue(prompt.contains("lecture document/PDF has been uploaded as an attachment"))
        assertTrue(prompt.contains("Focus on RAAS inhibitors"))
        assertTrue(prompt.contains("High-Yield Exam Summary"))
    }

    @Test
    fun testAvailableModelsNotEmpty() {
        assertTrue(GeminiService.AVAILABLE_MODELS.isNotEmpty())
        assertTrue(GeminiService.AVAILABLE_MODELS.any { it.first == "gemini-3.5-flash" })
    }
}
