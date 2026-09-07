package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceFileName: String = "Lecture Note",
    val lectureText: String,
    val pastPaperText: String = "",
    val summaryMarkdown: String = "",
    val pdfFilePath: String = "",
    val keyPointsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = Lesson::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lessonId")]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val questionType: String, // "MCQ" or "TRUE_FALSE"
    val questionText: String,
    val optionsJson: String = "[]", // JSON array of options
    val correctAnswer: String,
    val explanation: String
)

@Entity(tableName = "quiz_attempts")
data class QuizAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val lessonTitle: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class FlashcardItem(
    val lessonId: Long,
    val lessonTitle: String,
    val point: String,
    val category: String = "High-Yield Fact"
)

data class AppSettings(
    val apiKey: String = "",
    val selectedModel: String = "gemini-3.5-flash",
    val questionType: String = "MIXED", // "MCQ", "TRUE_FALSE", "MIXED"
    val questionCount: Int = 5 // 5 or 10
)

data class FileAttachment(
    val fileName: String,
    val mimeType: String,
    val base64Data: String,
    val sizeBytes: Long = 0L
)
