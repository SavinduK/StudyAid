package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Lesson
import com.example.data.model.Question
import com.example.data.model.QuizAttempt

@Database(
    entities = [Lesson::class, Question::class, QuizAttempt::class],
    version = 1,
    exportSchema = false
)
abstract class StudyAidDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun questionDao(): QuestionDao
    abstract fun quizAttemptDao(): QuizAttemptDao

    companion object {
        @Volatile
        private var INSTANCE: StudyAidDatabase? = null

        fun getDatabase(context: Context): StudyAidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyAidDatabase::class.java,
                    "studyaid_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
