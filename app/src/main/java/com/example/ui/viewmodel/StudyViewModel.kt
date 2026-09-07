package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.local.StudyAidDatabase
import com.example.data.model.AppSettings
import com.example.data.model.FileAttachment
import com.example.data.model.FlashcardItem
import com.example.data.model.Lesson
import com.example.data.model.Question
import com.example.data.model.QuizAttempt
import com.example.data.repository.StudyRepository
import com.example.service.GeminiService
import com.example.service.PdfService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.random.Random

data class LessonStat(
    val lessonId: Long,
    val lessonTitle: String,
    val attemptsCount: Int,
    val totalQuestionsAnswered: Int,
    val totalCorrect: Int,
    val accuracyPercent: Int,
    val needsReview: Boolean
)

data class OverallStats(
    val totalQuestionsAnswered: Int = 0,
    val totalCorrect: Int = 0,
    val overallAccuracy: Int = 0,
    val totalAttempts: Int = 0,
    val lessonStats: List<LessonStat> = emptyList(),
    val flaggedLessons: List<LessonStat> = emptyList()
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StudyAidDatabase.getDatabase(application)
    private val settingsDataStore = SettingsDataStore(application)
    private val repository = StudyRepository(
        database.lessonDao(),
        database.questionDao(),
        database.quizAttemptDao(),
        settingsDataStore
    )
    private val geminiService = GeminiService()
    private val pdfService = PdfService(application)

    // Settings state
    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    // Lessons list
    val lessons: StateFlow<List<Lesson>> = repository.allLessons.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Flashcards
    val allFlashcards: StateFlow<List<FlashcardItem>> = repository.allFlashcards.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentFlashcard = MutableStateFlow<FlashcardItem?>(null)
    val currentFlashcard: StateFlow<FlashcardItem?> = _currentFlashcard.asStateFlow()

    private val _isCardFlipped = MutableStateFlow(false)
    val isCardFlipped: StateFlow<Boolean> = _isCardFlipped.asStateFlow()

    // Loading & Operation states
    private val _isIngesting = MutableStateFlow(false)
    val isIngesting: StateFlow<Boolean> = _isIngesting.asStateFlow()

    private val _ingestionProgress = MutableStateFlow("")
    val ingestionProgress: StateFlow<String> = _ingestionProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Connection testing
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testConnectionStatus = MutableStateFlow<String?>(null)
    val testConnectionStatus: StateFlow<String?> = _testConnectionStatus.asStateFlow()

    // Active Quiz Runner State
    private val _activeLesson = MutableStateFlow<Lesson?>(null)
    val activeLesson: StateFlow<Lesson?> = _activeLesson.asStateFlow()

    private val _activeQuestions = MutableStateFlow<List<Question>>(emptyList())
    val activeQuestions: StateFlow<List<Question>> = _activeQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _selectedAnswer = MutableStateFlow<String?>(null)
    val selectedAnswer: StateFlow<String?> = _selectedAnswer.asStateFlow()

    private val _isAnswerSubmitted = MutableStateFlow(false)
    val isAnswerSubmitted: StateFlow<Boolean> = _isAnswerSubmitted.asStateFlow()

    private val _isAnswerCorrect = MutableStateFlow<Boolean?>(null)
    val isAnswerCorrect: StateFlow<Boolean?> = _isAnswerCorrect.asStateFlow()

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore.asStateFlow()

    private val _isQuizFinished = MutableStateFlow(false)
    val isQuizFinished: StateFlow<Boolean> = _isQuizFinished.asStateFlow()

    private val _isGeneratingQuestions = MutableStateFlow(false)
    val isGeneratingQuestions: StateFlow<Boolean> = _isGeneratingQuestions.asStateFlow()

    // Statistics computation
    val statistics: StateFlow<OverallStats> = combine(
        repository.allLessons,
        repository.allAttempts
    ) { allLessonsList, attemptsList ->
        var totalAnswered = 0
        var totalCorrect = 0

        val lessonMap = allLessonsList.associateBy { it.id }
        val attemptsByLesson = attemptsList.groupBy { it.lessonId }

        val lessonStatsList = mutableListOf<LessonStat>()

        for ((lessonId, attempts) in attemptsByLesson) {
            val title = lessonMap[lessonId]?.title ?: attempts.firstOrNull()?.lessonTitle ?: "Lesson #$lessonId"
            val lessonTotalQ = attempts.sumOf { it.totalQuestions }
            val lessonCorrect = attempts.sumOf { it.correctAnswers }
            totalAnswered += lessonTotalQ
            totalCorrect += lessonCorrect

            val acc = if (lessonTotalQ > 0) ((lessonCorrect * 100) / lessonTotalQ) else 0
            lessonStatsList.add(
                LessonStat(
                    lessonId = lessonId,
                    lessonTitle = title,
                    attemptsCount = attempts.size,
                    totalQuestionsAnswered = lessonTotalQ,
                    totalCorrect = lessonCorrect,
                    accuracyPercent = acc,
                    needsReview = acc < 70
                )
            )
        }

        // Include lessons with 0 attempts as well
        for (lesson in allLessonsList) {
            if (!attemptsByLesson.containsKey(lesson.id)) {
                lessonStatsList.add(
                    LessonStat(
                        lessonId = lesson.id,
                        lessonTitle = lesson.title,
                        attemptsCount = 0,
                        totalQuestionsAnswered = 0,
                        totalCorrect = 0,
                        accuracyPercent = 0,
                        needsReview = true
                    )
                )
            }
        }

        val overallAcc = if (totalAnswered > 0) ((totalCorrect * 100) / totalAnswered) else 0
        val sortedStats = lessonStatsList.sortedBy { it.accuracyPercent }
        val flagged = sortedStats.filter { it.needsReview }

        OverallStats(
            totalQuestionsAnswered = totalAnswered,
            totalCorrect = totalCorrect,
            overallAccuracy = overallAcc,
            totalAttempts = attemptsList.size,
            lessonStats = lessonStatsList.sortedByDescending { it.attemptsCount },
            flaggedLessons = flagged
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverallStats()
    )

    init {
        // Initialize default flashcard if lessons exist
        viewModelScope.launch {
            allFlashcards.collect { cards ->
                if (_currentFlashcard.value == null && cards.isNotEmpty()) {
                    _currentFlashcard.value = cards.random()
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _successMessage.value = null
    }

    fun flipFlashcard() {
        _isCardFlipped.value = !_isCardFlipped.value
    }

    fun nextFlashcard() {
        _isCardFlipped.value = false
        val cards = allFlashcards.value
        if (cards.isNotEmpty()) {
            val nextCard = if (cards.size > 1) {
                val pool = cards.filter { it != _currentFlashcard.value }
                pool.random()
            } else {
                cards.first()
            }
            _currentFlashcard.value = nextCard
        }
    }

    // --- Ingestion Pipeline ---
    fun ingestLesson(
        title: String,
        sourceFileName: String,
        lectureText: String,
        pastPaperText: String,
        lectureAttachment: FileAttachment? = null,
        pastPaperAttachment: FileAttachment? = null,
        onComplete: (Long) -> Unit
    ) {
        if (lectureAttachment == null && lectureText.isBlank()) {
            _errorMessage.value = "Please upload a lecture file (PDF) or enter study notes."
            return
        }

        viewModelScope.launch {
            _isIngesting.value = true
            val effectiveSourceFileName = when {
                lectureAttachment != null -> lectureAttachment.fileName
                sourceFileName.isNotBlank() -> sourceFileName
                else -> "Lecture Document"
            }

            if (lectureAttachment != null) {
                _ingestionProgress.value = "Uploading ${lectureAttachment.fileName} to Gemini (${settings.value.selectedModel})..."
            } else {
                _ingestionProgress.value = "Connecting to Gemini (${settings.value.selectedModel})..."
            }

            val currentKey = settings.value.apiKey
            val currentModel = settings.value.selectedModel

            // Step 1: Generate summary and key facts
            _ingestionProgress.value = "Direct model analysis: extracting high-yield concepts & exam facts..."
            val summaryResult = geminiService.generateSummary(
                apiKey = currentKey,
                model = currentModel,
                lectureText = lectureText,
                pastPaperText = pastPaperText.ifBlank { null },
                lectureAttachment = lectureAttachment,
                pastPaperAttachment = pastPaperAttachment
            )

            summaryResult.onFailure { error ->
                _isIngesting.value = false
                _errorMessage.value = "Summary Generation Failed: ${error.localizedMessage}"
                return@launch
            }

            val summaryData = summaryResult.getOrThrow()
            val effectiveTitle = if (title.isNotBlank()) {
                title.trim()
            } else if (summaryData.title.isNotBlank() && summaryData.title != "Lecture Summary") {
                summaryData.title
            } else if (lectureAttachment != null) {
                lectureAttachment.fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
            } else {
                summaryData.title
            }

            val effectiveLectureText = if (lectureText.isNotBlank()) {
                lectureText
            } else if (lectureAttachment != null) {
                "[Direct Model Upload: ${lectureAttachment.fileName}]"
            } else {
                ""
            }

            val effectivePastPaperText = if (pastPaperText.isNotBlank()) {
                pastPaperText
            } else if (pastPaperAttachment != null) {
                "[Direct Model Upload: ${pastPaperAttachment.fileName}]"
            } else {
                ""
            }

            // Save lesson preliminary row to Room
            val initialLesson = Lesson(
                title = effectiveTitle,
                sourceFileName = effectiveSourceFileName,
                lectureText = effectiveLectureText,
                pastPaperText = effectivePastPaperText,
                summaryMarkdown = summaryData.summaryMarkdown,
                keyPointsJson = JSONArray(summaryData.keyPoints).toString()
            )

            _ingestionProgress.value = "Generating High-Yield Summary PDF..."
            val lessonId = repository.insertLesson(initialLesson)

            // Save uploaded attachment locally if present
            if (lectureAttachment != null) {
                pdfService.saveAttachmentLocally(lectureAttachment, lessonId)
            }
            if (pastPaperAttachment != null) {
                pdfService.saveAttachmentLocally(pastPaperAttachment, lessonId)
            }

            // Step 2: Render & save PDF file
            val pdfFile = try {
                pdfService.generateSummaryPdf(
                    lessonId = lessonId,
                    title = effectiveTitle,
                    summaryMarkdown = summaryData.summaryMarkdown,
                    keyPoints = summaryData.keyPoints
                )
            } catch (e: Exception) {
                null
            }

            val updatedLesson = initialLesson.copy(
                id = lessonId,
                pdfFilePath = pdfFile?.absolutePath ?: ""
            )
            repository.updateLesson(updatedLesson)

            // Step 3: Automatically generate initial quiz questions per settings
            _ingestionProgress.value = "Generating exam-calibrated questions..."
            val questionsResult = geminiService.generateQuestions(
                apiKey = currentKey,
                model = currentModel,
                lectureNotes = "${summaryData.summaryMarkdown}\n\n${summaryData.keyPoints.joinToString("\n")}",
                pastPaperText = pastPaperText.ifBlank { null },
                questionType = settings.value.questionType,
                questionCount = settings.value.questionCount,
                lectureAttachment = lectureAttachment,
                pastPaperAttachment = pastPaperAttachment
            )

            questionsResult.onSuccess { generatedList ->
                val entities = generatedList.map { item ->
                    Question(
                        lessonId = lessonId,
                        questionType = item.questionType,
                        questionText = item.questionText,
                        optionsJson = JSONArray(item.options).toString(),
                        correctAnswer = item.correctAnswer,
                        explanation = item.explanation
                    )
                }
                repository.insertQuestions(entities)
                // Generate a text file for questions only (do not show it in UI)
                try {
                    pdfService.generateQuestionsTextFile(lessonId, effectiveTitle, entities)
                } catch (_: Exception) {}
            }

            _isIngesting.value = false
            _successMessage.value = "Lesson \"$effectiveTitle\" processed successfully!"
            onComplete(lessonId)
        }
    }

    // --- Quiz Runner Pipeline ---
    fun loadLessonForQuiz(lessonId: Long) {
        viewModelScope.launch {
            val lesson = repository.getLessonById(lessonId)
            _activeLesson.value = lesson
            val questions = repository.getQuestionsForLessonSync(lessonId)
            _activeQuestions.value = questions
            resetQuizState()

            if (questions.isEmpty() && lesson != null) {
                // Generate questions now if none exist
                regenerateQuestions(lessonId)
            }
        }
    }

    private fun resetQuizState() {
        _currentQuestionIndex.value = 0
        _selectedAnswer.value = null
        _isAnswerSubmitted.value = false
        _isAnswerCorrect.value = null
        _quizScore.value = 0
        _isQuizFinished.value = false
    }

    fun submitAnswer(answer: String) {
        if (_isAnswerSubmitted.value) return

        val questions = _activeQuestions.value
        val currentIndex = _currentQuestionIndex.value
        if (currentIndex !in questions.indices) return

        val currentQ = questions[currentIndex]
        _selectedAnswer.value = answer
        _isAnswerSubmitted.value = true

        // Compare answer: either prefix matches (e.g. "A" vs "A. ...") or full string matches
        val correct = isMatch(answer, currentQ.correctAnswer)
        _isAnswerCorrect.value = correct

        if (correct) {
            _quizScore.value += 1
        }
    }

    private fun isMatch(userAnswer: String, correctAnswer: String): Boolean {
        val u = userAnswer.trim().lowercase()
        val c = correctAnswer.trim().lowercase()
        if (u == c) return true
        if (u.startsWith(c) || c.startsWith(u)) return true
        // For MCQ like "A. xyz" and answer "A"
        val cleanUser = u.substringBefore('.').trim()
        val cleanCorrect = c.substringBefore('.').trim()
        return cleanUser == cleanCorrect
    }

    fun nextQuestion() {
        val questions = _activeQuestions.value
        val nextIndex = _currentQuestionIndex.value + 1

        if (nextIndex < questions.size) {
            _currentQuestionIndex.value = nextIndex
            _selectedAnswer.value = null
            _isAnswerSubmitted.value = false
            _isAnswerCorrect.value = null
        } else {
            // Quiz finished, record attempt in statistics
            _isQuizFinished.value = true
            val lesson = _activeLesson.value
            if (lesson != null && questions.isNotEmpty()) {
                viewModelScope.launch {
                    repository.recordAttempt(
                        QuizAttempt(
                            lessonId = lesson.id,
                            lessonTitle = lesson.title,
                            totalQuestions = questions.size,
                            correctAnswers = _quizScore.value
                        )
                    )
                }
            }
        }
    }

    fun retryQuiz() {
        resetQuizState()
    }

    fun regenerateQuestions(lessonId: Long) {
        viewModelScope.launch {
            val lesson = repository.getLessonById(lessonId) ?: return@launch
            _isGeneratingQuestions.value = true

            val currentKey = settings.value.apiKey
            val currentModel = settings.value.selectedModel

            val promptContext = if (lesson.summaryMarkdown.isNotBlank()) {
                lesson.summaryMarkdown
            } else {
                lesson.lectureText
            }

            val result = geminiService.generateQuestions(
                apiKey = currentKey,
                model = currentModel,
                lectureNotes = promptContext,
                pastPaperText = lesson.pastPaperText.ifBlank { null },
                questionType = settings.value.questionType,
                questionCount = settings.value.questionCount
            )

            result.onSuccess { generatedList ->
                val entities = generatedList.map { item ->
                    Question(
                        lessonId = lessonId,
                        questionType = item.questionType,
                        questionText = item.questionText,
                        optionsJson = JSONArray(item.options).toString(),
                        correctAnswer = item.correctAnswer,
                        explanation = item.explanation
                    )
                }
                repository.insertQuestions(entities)
                _activeQuestions.value = entities
                try {
                    pdfService.generateQuestionsTextFile(lessonId, lesson.title, entities)
                } catch (_: Exception) {}
                resetQuizState()
                _successMessage.value = "Generated ${entities.size} new questions for ${lesson.title}!"
            }.onFailure { e ->
                _errorMessage.value = "Failed to generate questions: ${e.localizedMessage}"
            }

            _isGeneratingQuestions.value = false
        }
    }

    suspend fun getOrGenerateSummaryPdf(lesson: Lesson): File? = withContext(Dispatchers.IO) {
        val existing = if (lesson.pdfFilePath.isNotBlank()) File(lesson.pdfFilePath) else null
        if (existing != null && existing.exists()) {
            return@withContext existing
        }

        val keyPointsList = try {
            val arr = JSONArray(lesson.keyPointsJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            list
        } catch (_: Exception) {
            emptyList()
        }

        try {
            val file = pdfService.generateSummaryPdf(
                lessonId = lesson.id,
                title = lesson.title,
                summaryMarkdown = lesson.summaryMarkdown,
                keyPoints = keyPointsList
            )
            val updated = lesson.copy(pdfFilePath = file.absolutePath)
            repository.updateLesson(updated)
            file
        } catch (_: Exception) {
            null
        }
    }

    suspend fun ensureQuestionsTextFile(lesson: Lesson) = withContext(Dispatchers.IO) {
        try {
            val questions = repository.getQuestionsForLessonSync(lesson.id)
            if (questions.isNotEmpty()) {
                pdfService.generateQuestionsTextFile(lesson.id, lesson.title, questions)
            }
        } catch (_: Exception) {}
    }

    // --- Lesson Actions ---
    fun deleteLesson(lessonId: Long) {
        viewModelScope.launch {
            repository.deleteLesson(lessonId)
            _successMessage.value = "Lesson removed."
        }
    }

    // --- Settings Actions ---
    fun updateApiKey(key: String) {
        viewModelScope.launch {
            repository.updateApiKey(key)
        }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch {
            repository.updateSelectedModel(model)
        }
    }

    fun updateQuestionType(type: String) {
        viewModelScope.launch {
            repository.updateQuestionType(type)
        }
    }

    fun updateQuestionCount(count: Int) {
        viewModelScope.launch {
            repository.updateQuestionCount(count)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionStatus.value = null
            val key = settings.value.apiKey
            val model = settings.value.selectedModel
            val result = geminiService.testConnection(key, model)
            result.onSuccess { msg ->
                _testConnectionStatus.value = msg
            }.onFailure { err ->
                _testConnectionStatus.value = "Error: ${err.localizedMessage}"
            }
            _isTestingConnection.value = false
        }
    }

    // --- Demo Lesson Seeder ---
    fun loadSampleLesson() {
        val sampleTitle = "Autonomic Pharmacology & Beta Blockers"
        val sampleLecture = """
        Autonomic Nervous System: Adrenergic Receptor Pharmacology
        
        1. Overview of Adrenergic Receptors:
        - Alpha-1 (Gq): Vasoconstriction, increased peripheral resistance, mydriasis, urinary sphincter contraction.
        - Alpha-2 (Gi): Presynaptic inhibition of norepinephrine release, decreased sympathetic outflow from CNS.
        - Beta-1 (Gs): Heart - increased heart rate (chronotropic), contractility (inotropic), and conduction velocity (dromotropic); stimulates renin release from juxtaglomerular cells.
        - Beta-2 (Gs): Bronchodilation, vasodilation in skeletal muscle vascular beds, decreased uterine tone, stimulates gluconeogenesis and glycogenolysis, drives potassium into cells.
        - Beta-3 (Gs): Lipolysis, detrusor muscle relaxation in bladder.
        
        2. Beta Blocker Classification:
        - Non-selective (Beta-1 and Beta-2): Propranolol, Timolol, Nadolol.
          * High risk of bronchospasm in asthmatics / COPD patients due to Beta-2 blockade.
          * May mask hypoglycemia symptoms (sweating, tachycardia) in diabetic patients.
        - Cardioselective (Beta-1 selective): Atenolol, Metoprolol, Bisoprolol, Esmolol.
          * Safer in respiratory disease, though selectivity is dose-dependent.
        - Combined Alpha and Beta Blockers: Labetalol, Carvedilol.
          * Useful in hypertensive emergencies, pheochromocytoma, and chronic heart failure.
        - Partial Agonists / Intrinsic Sympathomimetic Activity (ISA): Pindolol, Acebutolol.
          * Avoid in patients with angina or post-myocardial infarction.
        """.trimIndent()

        val samplePastPaper = """
        Exam Questions (2024 Past Paper):
        Q1. A 48-year-old male with moderate asthma and essential hypertension requires anti-hypertensive therapy. Why are non-selective beta blockers contraindicated, and which receptor subtype mediates this risk?
        Q2. Contrast the hemodynamic effects of Carvedilol vs Metoprolol.
        """.trimIndent()

        ingestLesson(
            title = sampleTitle,
            sourceFileName = "Cardiovascular_Pharmacology_Lecture.pdf",
            lectureText = sampleLecture,
            pastPaperText = samplePastPaper,
            onComplete = {}
        )
    }
}

class StudyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
