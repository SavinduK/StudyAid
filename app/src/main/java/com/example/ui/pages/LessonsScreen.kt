package com.example.ui.pages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileAttachment
import com.example.data.model.Lesson
import com.example.service.PdfService
import com.example.ui.components.LessonCard
import com.example.ui.components.LoadingProgressDialog
import com.example.ui.viewmodel.StudyViewModel
import kotlinx.coroutines.launch

@Composable
fun LessonsScreen(
    viewModel: StudyViewModel,
    onNavigateToQuiz: (Long) -> Unit,
    onNavigateToPdf: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val isIngesting by viewModel.isIngesting.collectAsStateWithLifecycle()
    val progressMessage by viewModel.ingestionProgress.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var lessonToDelete by remember { mutableStateOf<Lesson?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Lesson") },
                modifier = Modifier.testTag("add_lesson_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (lessons.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .testTag("lessons_empty_state"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No Lessons Added Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Upload lecture slides or paste course notes to generate high-yield summary PDFs and AI practice questions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("empty_state_add_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Lesson")
                        }

                        OutlinedButton(
                            onClick = { viewModel.loadSampleLesson() },
                            modifier = Modifier.testTag("empty_state_sample_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sample Lecture")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("lessons_list"),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = "Your Study Lessons",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    items(lessons, key = { it.id }) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            questionCount = 0,
                            onTakeQuiz = { onNavigateToQuiz(lesson.id) },
                            onOpenPdf = { onNavigateToPdf(lesson.id) },
                            onDelete = { lessonToDelete = lesson }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (lessonToDelete != null) {
        AlertDialog(
            onDismissRequest = { lessonToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Lesson?") },
            text = {
                Text("Are you sure you want to delete \"${lessonToDelete?.title}\"? All generated questions, notes, and quiz history will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        lessonToDelete?.id?.let { viewModel.deleteLesson(it) }
                        lessonToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { lessonToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Lesson Ingestion Dialog
    if (showAddDialog) {
        AddLessonDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { title, fileName, lectureText, pastPaperText, lectureAttachment, pastPaperAttachment ->
                viewModel.ingestLesson(
                    title = title,
                    sourceFileName = fileName,
                    lectureText = lectureText,
                    pastPaperText = pastPaperText,
                    lectureAttachment = lectureAttachment,
                    pastPaperAttachment = pastPaperAttachment,
                    onComplete = { newLessonId ->
                        showAddDialog = false
                    }
                )
            }
        )
    }

    // Loading Progress Dialog
    if (isIngesting) {
        LoadingProgressDialog(message = progressMessage)
    }
}

@Composable
fun AddLessonDialog(
    onDismiss: () -> Unit,
    onSubmit: (
        title: String,
        fileName: String,
        lectureText: String,
        pastPaperText: String,
        lectureAttachment: FileAttachment?,
        pastPaperAttachment: FileAttachment?
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfService = remember { PdfService(context) }

    var title by remember { mutableStateOf("") }
    var lectureText by remember { mutableStateOf("") }
    var pastPaperText by remember { mutableStateOf("") }
    var lectureAttachment by remember { mutableStateOf<FileAttachment?>(null) }
    var pastPaperAttachment by remember { mutableStateOf<FileAttachment?>(null) }
    var isReadingFile by remember { mutableStateOf(false) }

    // File pickers targeting PDF and common document types
    val lectureFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isReadingFile = true
                val attachment = pdfService.readFileAttachmentFromUri(uri)
                if (attachment != null) {
                    lectureAttachment = attachment
                    if (title.isBlank()) {
                        val suggested = attachment.fileName
                            .substringBeforeLast('.')
                            .replace(Regex("[_\\-]+"), " ")
                            .trim()
                            .split(" ")
                            .joinToString(" ") { word ->
                                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            }
                        title = suggested
                    }
                }
                isReadingFile = false
            }
        }
    }

    val pastPaperFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val attachment = pdfService.readFileAttachmentFromUri(uri)
                if (attachment != null) {
                    pastPaperAttachment = attachment
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("add_lesson_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Text(
                    text = "Add New Lecture Topic",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Upload your lecture PDF directly to Gemini without text conversion, or paste notes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Lesson Title (Optional)") },
                    placeholder = { Text("e.g. Cardiovascular Pharmacology") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_lesson_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Lecture Material Section
                Text(
                    text = "Lecture Material",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (lectureAttachment != null) {
                    // Attached File Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lecture_file_attachment_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = lectureAttachment!!.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = pdfService.formatFileSize(lectureAttachment!!.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "Direct Upload to Model",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { lectureAttachment = null },
                                modifier = Modifier.testTag("remove_lecture_attachment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = lectureText,
                        onValueChange = { lectureText = it },
                        label = { Text("Additional Focus Notes (Optional)") },
                        placeholder = { Text("Add specific chapters, instructions, or topics to prioritize...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 70.dp, max = 110.dp)
                            .testTag("add_lecture_text_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    // Upload button and fallback manual input
                    OutlinedButton(
                        onClick = { lectureFileLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_lecture_file_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Upload PDF / Slide Deck",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Or paste lecture notes manually:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = lectureText,
                        onValueChange = { lectureText = it },
                        placeholder = { Text("Paste slide content, textbook excerpts, or study notes...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 130.dp)
                            .testTag("add_lecture_text_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional Past Paper Content
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Past-Paper Exam Style",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Optional exam questions or difficulty guide",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (pastPaperAttachment == null) {
                        OutlinedButton(
                            onClick = { pastPaperFileLauncher.launch("*/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("upload_past_paper_file_button")
                        ) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Attach File", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (pastPaperAttachment != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("past_paper_file_attachment_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = pastPaperAttachment!!.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${pdfService.formatFileSize(pastPaperAttachment!!.sizeBytes)} • Direct Model Upload",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { pastPaperAttachment = null },
                                modifier = Modifier.testTag("remove_past_paper_attachment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedTextField(
                    value = pastPaperText,
                    onValueChange = { pastPaperText = it },
                    placeholder = { Text("Paste past exam questions here to guide difficulty and question style...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp, max = 110.dp)
                        .testTag("add_past_paper_text_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val canSubmit = (lectureAttachment != null || lectureText.isNotBlank()) && !isReadingFile

                    Button(
                        onClick = {
                            val effectiveName = lectureAttachment?.fileName ?: "Manual Notes Entry"
                            onSubmit(title, effectiveName, lectureText, pastPaperText, lectureAttachment, pastPaperAttachment)
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("submit_lesson_ingestion_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Aid")
                    }
                }
            }
        }
    }
}
