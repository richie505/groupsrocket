package com.groupsrocket.mcq

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val correctGreen = Color(0xFF16A34A)
private val wrongRed = Color(0xFFDC2626)

@Composable
fun QuizScreen(app: AppState, quiz: Screen.Quiz, onExit: () -> Unit, onRetry: (Screen.Quiz) -> Unit) {
    val questions = quiz.questions
    if (questions.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No MCQs here yet.", style = MaterialTheme.typography.titleMedium)
            Text("MCQs for these sheets haven't been generated. Run scripts/generate_mcqs.py and rebuild the app.")
            Button(onClick = onExit) { Text("Back") }
        }
        return
    }

    var pos by remember(quiz) { mutableIntStateOf(0) }
    var chosen by remember(quiz) { mutableStateOf<Int?>(null) }
    val wrong = remember(quiz) { mutableStateListOf<Mcq>() }
    var correct by remember(quiz) { mutableIntStateOf(0) }
    var finished by remember(quiz) { mutableStateOf(false) }

    if (finished) {
        val pct = correct * 100 / questions.size
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$pct%", style = MaterialTheme.typography.displayLarge, color = scoreColor(pct), fontWeight = FontWeight.Bold)
            Text("$correct of ${questions.size} correct", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wrong.isNotEmpty()) {
                    Button(onClick = { onRetry(Screen.Quiz("${quiz.title} · Retry", wrong.toList())) }) { Text("Retry wrong (${wrong.size})") }
                }
                OutlinedButton(onClick = onExit) { Text("Done") }
            }
            if (wrong.isNotEmpty()) {
                HorizontalDivider()
                Text("Review your mistakes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                wrong.forEach { q ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q.question, style = MaterialTheme.typography.bodyMedium)
                            Text("✓ ${q.options[q.answer]}", color = correctGreen, fontWeight = FontWeight.SemiBold)
                            Text(q.explanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        return
    }

    val q = questions[pos]
    val sheet = app.index.sheet(q.sheet)
    @Suppress("UNUSED_VARIABLE") val v = app.progress.version

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (pos + 1f) / questions.size },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Question ${pos + 1} of ${questions.size}  ·  ✓ $correct",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { app.progress.toggleBookmark(q) }) {
                    val marked = app.progress.isBookmarked(q.id)
                    Text(
                        if (marked) "★" else "☆",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (marked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(q.format)
                if (q.keyword.isNotBlank()) Tag(q.keyword, MaterialTheme.colorScheme.tertiaryContainer)
            }
            if (sheet != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "Practising: ${sheet.subjectName} · ROCKET SHEET #${sheet.id} — ${sheet.title}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Text(q.question, style = MaterialTheme.typography.titleMedium)

            q.options.forEachIndexed { i, opt ->
                val answered = chosen != null
                val border = when {
                    answered && i == q.answer -> BorderStroke(2.dp, correctGreen)
                    answered && i == chosen -> BorderStroke(2.dp, wrongRed)
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }
                val bg = when {
                    answered && i == q.answer -> correctGreen.copy(alpha = 0.12f)
                    answered && i == chosen -> wrongRed.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surface
                }
                Surface(
                    onClick = {
                        if (!answered) {
                            chosen = i
                            val ok = i == q.answer
                            if (ok) correct++ else wrong.add(q)
                            app.progress.record(q, ok)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = border,
                    color = bg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${'A' + i})", fontWeight = FontWeight.Bold)
                        Text(opt, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (chosen != null) {
                val ok = chosen == q.answer
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (ok) "Correct!" else "Wrong — answer is ${'A' + q.answer}",
                            color = if (ok) correctGreen else wrongRed,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Button(
            onClick = {
                if (pos == questions.lastIndex) {
                    finished = true
                    quiz.day?.let { app.progress.markDayDone(it) }
                    // Score every sheet whose full question set was part of this quiz.
                    val wrongIds = wrong.map { it.id }.toSet()
                    questions.groupBy { it.sheet }.forEach { (ref, qs) ->
                        if (qs.size == app.index.sheet(ref)?.count) {
                            app.progress.recordSheetScore(ref, (qs.size - qs.count { it.id in wrongIds }) * 100 / qs.size)
                        }
                    }
                } else {
                    pos++
                    chosen = null
                }
            },
            enabled = chosen != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(if (pos == questions.lastIndex) "Finish" else "Next") }
    }
}
