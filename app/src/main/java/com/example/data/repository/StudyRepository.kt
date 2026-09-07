package com.example.data.repository

import com.example.data.local.LessonDao
import com.example.data.local.QuestionDao
import com.example.data.local.QuizAttemptDao
import com.example.data.local.SettingsDataStore
import com.example.data.model.AppSettings
import com.example.data.model.FlashcardItem
import com.example.data.model.Lesson
import com.example.data.model.Question
import com.example.data.model.QuizAttempt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

class StudyRepository(
    private val lessonDao: LessonDao,
    private val questionDao: QuestionDao,
    private val quizAttemptDao: QuizAttemptDao,
    private val settingsDataStore: SettingsDataStore
) {
    val allLessons: Flow<List<Lesson>> = lessonDao.getAllLessons()
    val allAttempts: Flow<List<QuizAttempt>> = quizAttemptDao.getAllAttempts()
    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow

    fun getQuestionsForLesson(lessonId: Long): Flow<List<Question>> =
        questionDao.getQuestionsForLesson(lessonId)

    suspend fun getQuestionsForLessonSync(lessonId: Long): List<Question> =
        questionDao.getQuestionsForLessonSync(lessonId)

    suspend fun getLessonById(id: Long): Lesson? =
        lessonDao.getLessonById(id)

    suspend fun insertLesson(lesson: Lesson): Long =
        lessonDao.insertLesson(lesson)

    suspend fun updateLesson(lesson: Lesson) =
        lessonDao.updateLesson(lesson)

    suspend fun deleteLesson(lessonId: Long) {
        questionDao.deleteQuestionsForLesson(lessonId)
        quizAttemptDao.deleteAttemptsForLesson(lessonId)
        lessonDao.deleteLessonById(lessonId)
    }

    suspend fun insertQuestions(questions: List<Question>) =
        questionDao.insertQuestions(questions)

    suspend fun recordAttempt(attempt: QuizAttempt): Long =
        quizAttemptDao.insertAttempt(attempt)

    suspend fun updateApiKey(apiKey: String) =
        settingsDataStore.updateApiKey(apiKey)

    suspend fun updateSelectedModel(model: String) =
        settingsDataStore.updateSelectedModel(model)

    suspend fun updateQuestionType(type: String) =
        settingsDataStore.updateQuestionType(type)

    suspend fun updateQuestionCount(count: Int) =
        settingsDataStore.updateQuestionCount(count)

    /**
     * Aggregates key points across all stored lessons into flashcard items.
     */
    val allFlashcards: Flow<List<FlashcardItem>> = allLessons.map { lessons ->
        val items = mutableListOf<FlashcardItem>()
        for (lesson in lessons) {
            try {
                val array = JSONArray(lesson.keyPointsJson)
                for (i in 0 until array.length()) {
                    val pt = array.optString(i)
                    if (pt.isNotBlank()) {
                        items.add(
                            FlashcardItem(
                                lessonId = lesson.id,
                                lessonTitle = lesson.title,
                                point = pt
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Ignore parse errors
            }
        }
        items
    }
}
