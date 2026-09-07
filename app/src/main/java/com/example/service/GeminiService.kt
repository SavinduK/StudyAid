package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeneratedSummaryResult(
    val title: String,
    val summaryMarkdown: String,
    val keyPoints: List<String>
)

data class GeneratedQuestionItem(
    val questionType: String,
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String
)

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        val AVAILABLE_MODELS = listOf(
            "gemini-3.5-flash" to "Gemini 3.5 Flash (Recommended)",
            "gemini-3.1-pro-preview" to "Gemini 3.1 Pro (Advanced Reasoning)",
            "gemini-flash-latest" to "Gemini Flash Latest",
            "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash Lite"
        )

        // Centralized prompts for easy tuning
        fun buildSummaryPrompt(lectureContent: String, pastPaperText: String? = null): String {
            val pastPaperSection = if (!pastPaperText.isNullOrBlank()) {
                """
                
                --- PAST-PAPER EXAM CONTEXT & EMPHASIS ---
                $pastPaperText
                
                Note: Tailor the summary to emphasize high-probability topics and distinctions tested in the past paper questions above.
                """.trimIndent()
            } else ""

            return """
            You are StudyAid, an expert academic tutor and exam specialist.
            Given the following lecture content, generate an exam-focused, high-yield study summary.

            --- LECTURE CONTENT ---
            $lectureContent
            $pastPaperSection

            Please format your response strictly as valid JSON with the following schema:
            {
              "title": "A concise, descriptive title for this lecture topic",
              "keyPoints": [
                "Short, high-yield fact #1 (suitable for a flashcard)",
                "Short, high-yield fact #2 (suitable for a flashcard)",
                "Short, high-yield fact #3",
                "Short, high-yield fact #4",
                "Short, high-yield fact #5"
              ],
              "summaryMarkdown": "Formatted markdown text containing: \n# [Topic Title]\n\n## 📌 Executive Core Concepts\n(Concise breakdown)\n\n## ⚖️ Comparison & Contrast\n(Markdown table or comparison bullet points distinguishing confusing concepts)\n\n## ⚠️ High-Yield Exam Pitfalls & High-Stakes Facts\n(Key facts to remember for exams)\n\n## 📝 Clinical / Practical Takeaways\n(Application notes)"
            }

            OUTPUT ONLY THE RAW JSON OBJECT. No backticks, no markdown fence.
            """.trimIndent()
        }

        fun buildQuestionPrompt(
            lectureNotes: String,
            pastPaperText: String? = null,
            questionType: String,
            questionCount: Int
        ): String {
            val pastPaperSection = if (!pastPaperText.isNullOrBlank()) {
                """
                
                --- PAST-PAPER STYLE REFERENCE ---
                $pastPaperText
                
                Instruction: Emulate the difficulty, question phrasing, distractor complexity, and topic focus demonstrated in the past paper.
                """.trimIndent()
            } else ""

            val typeInstruction = when (questionType.uppercase()) {
                "MCQ" -> "All questions must be Multiple Choice Questions (MCQ) with 4 realistic options (A, B, C, D)."
                "TRUE_FALSE" -> "All questions must be True/False statement-based questions testing precise understanding or common misconceptions. The options array must be [\"True\", \"False\"]."
                else -> "Include a balanced mix of Multiple Choice Questions (MCQ with 4 options) and True/False statement questions."
            }

            return """
            You are StudyAid's quiz generator. Create exactly $questionCount high-quality exam questions based on the following lecture reference notes.

            --- REFERENCE NOTES ---
            $lectureNotes
            $pastPaperSection

            Requirements:
            1. $typeInstruction
            2. Each question MUST test core high-yield knowledge or critical thinking, avoiding trivial recall.
            3. Provide a clear, educational explanation for why the correct answer is right and why common distractors are incorrect.

            Return your response strictly as a valid JSON array of question objects:
            [
              {
                "questionType": "MCQ",
                "questionText": "A 54-year-old patient presents with... Which of the following is the primary mechanism?",
                "options": ["A. Competitive inhibition of ...", "B. Non-selective activation of ...", "C. Degradation of ...", "D. Allosteric modulation of ..."],
                "correctAnswer": "A",
                "explanation": "Option A is correct because..."
              },
              {
                "questionType": "TRUE_FALSE",
                "questionText": "Statement: Non-selective beta blockers are contraindicated in patients with severe asthma.",
                "options": ["True", "False"],
                "correctAnswer": "True",
                "explanation": "True. Non-selective beta blockers block Beta-2 receptors in bronchial smooth muscle, causing bronchoconstriction."
              }
            ]

            OUTPUT ONLY THE RAW JSON ARRAY. No backticks, no markdown fence.
            """.trimIndent()
        }
    }

    suspend fun generateSummary(
        apiKey: String,
        model: String,
        lectureText: String,
        pastPaperText: String? = null
    ): Result<GeneratedSummaryResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Gemini API key is not configured. Please enter your API key in Settings.")
            )
        }

        try {
            val prompt = buildSummaryPrompt(lectureText, pastPaperText)
            val jsonResponse = callGeminiApi(apiKey, model, prompt)
            val cleanJson = extractJsonPayload(jsonResponse)

            val root = JSONObject(cleanJson)
            val title = root.optString("title", "Lecture Summary").trim()
            val summaryMarkdown = root.optString("summaryMarkdown", "").trim()
            val keyPointsArray = root.optJSONArray("keyPoints")
            val keyPoints = mutableListOf<String>()
            if (keyPointsArray != null) {
                for (i in 0 until keyPointsArray.length()) {
                    val p = keyPointsArray.optString(i)
                    if (p.isNotBlank()) keyPoints.add(p)
                }
            }
            if (keyPoints.isEmpty()) {
                keyPoints.add("Key concepts extracted from lecture.")
            }

            Result.success(GeneratedSummaryResult(title, summaryMarkdown, keyPoints))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateQuestions(
        apiKey: String,
        model: String,
        lectureNotes: String,
        pastPaperText: String? = null,
        questionType: String,
        questionCount: Int
    ): Result<List<GeneratedQuestionItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Gemini API key is not configured. Please enter your API key in Settings.")
            )
        }

        try {
            val prompt = buildQuestionPrompt(lectureNotes, pastPaperText, questionType, questionCount)
            val rawResponse = callGeminiApi(apiKey, model, prompt)
            val cleanJson = extractJsonPayload(rawResponse)

            val questions = mutableListOf<GeneratedQuestionItem>()
            val jsonArray = JSONArray(cleanJson)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = obj.optString("questionType", "MCQ")
                val text = obj.optString("questionText", "")
                val optionsArray = obj.optJSONArray("options")
                val optionsList = mutableListOf<String>()
                if (optionsArray != null) {
                    for (j in 0 until optionsArray.length()) {
                        optionsList.add(optionsArray.optString(j))
                    }
                }
                if (optionsList.isEmpty()) {
                    if (type == "TRUE_FALSE") {
                        optionsList.addAll(listOf("True", "False"))
                    } else {
                        optionsList.addAll(listOf("A", "B", "C", "D"))
                    }
                }
                val answer = obj.optString("correctAnswer", "")
                val explanation = obj.optString("explanation", "")

                if (text.isNotBlank()) {
                    questions.add(
                        GeneratedQuestionItem(
                            questionType = type,
                            questionText = text,
                            options = optionsList,
                            correctAnswer = answer,
                            explanation = explanation
                        )
                    )
                }
            }

            if (questions.isEmpty()) {
                throw IllegalStateException("The model did not return any valid questions. Please try again.")
            }

            Result.success(questions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter an API key."))
        }
        try {
            val response = callGeminiApi(apiKey, model, "Respond with only the word 'OK' if you receive this.")
            if (response.isNotBlank()) {
                Result.success("Connection successful! Gemini ($model) is ready.")
            } else {
                Result.failure(IOException("Empty response from Gemini API."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGeminiApi(apiKey: String, model: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.4)
            }
            put("generationConfig", generationConfig)
        }

        val requestBody = requestBodyJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    val errorObj = errJson.optJSONObject("error")
                    errorObj?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                throw IOException(errorMsg)
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val promptFeedback = json.optJSONObject("promptFeedback")
                val blockReason = promptFeedback?.optString("blockReason")
                throw IOException("No response generated. ${if (!blockReason.isNullOrBlank()) "Block reason: $blockReason" else ""}")
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)
            val text = firstPart?.optString("text")

            if (text.isNullOrBlank()) {
                throw IOException("Received empty content text from model.")
            }

            return text
        }
    }

    private fun extractJsonPayload(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }
        val firstBrace = text.indexOf('{')
        val firstBracket = text.indexOf('[')

        val start = when {
            firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
            firstBrace != -1 -> firstBrace
            firstBracket != -1 -> firstBracket
            else -> 0
        }

        val lastBrace = text.lastIndexOf('}')
        val lastBracket = text.lastIndexOf(']')
        val end = when {
            lastBrace != -1 && lastBracket != -1 -> maxOf(lastBrace, lastBracket) + 1
            lastBrace != -1 -> lastBrace + 1
            lastBracket != -1 -> lastBracket + 1
            else -> text.length
        }

        return if (start < end) text.substring(start, end) else text
    }
}
