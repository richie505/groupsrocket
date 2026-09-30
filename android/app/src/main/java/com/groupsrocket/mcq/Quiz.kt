package com.groupsrocket.mcq

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
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
    val answers = remember(quiz) { mutableStateMapOf<Int, Int>() } // question index -> chosen option
    val skipped = remember(quiz) { mutableStateListOf<Int>() }
    val hints = remember(quiz) { mutableStateMapOf<Int, List<Int>>() } // question index -> eliminated options
    var finished by remember(quiz) { mutableStateOf(false) }
    val correct = answers.count { (i, a) -> questions[i].answer == a }
    val wrong = answers.filter { (i, a) -> questions[i].answer != a }.keys.sorted().map { questions[it] }
    val skippedQs = skipped.filter { it !in answers }.sorted().map { questions[it] }

    fun finish() {
        finished = true
        quiz.day?.let { app.progress.markDayDone(it) }
        // Score every sheet whose full question set was answered in this quiz.
        questions.indices.groupBy { questions[it].sheet }.forEach { (ref, idx) ->
            if (idx.size == app.index.sheet(ref)?.count && idx.all { it in answers }) {
                app.progress.recordSheetScore(ref, idx.count { questions[it].answer == answers[it] } * 100 / idx.size)
            }
        }
    }

    if (finished) {
        val answered = answers.size
        val pct = if (answered == 0) 0 else correct * 100 / answered
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$pct%", style = MaterialTheme.typography.displayLarge, color = scoreColor(pct), fontWeight = FontWeight.Bold)
            Text("$correct of $answered answered correctly", style = MaterialTheme.typography.titleMedium)
            if (skippedQs.isNotEmpty()) Text("${skippedQs.size} skipped", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wrong.isNotEmpty()) {
                    Button(onClick = { onRetry(Screen.Quiz("${quiz.title} · Retry", wrong)) }) { Text("Retry wrong (${wrong.size})") }
                }
                if (skippedQs.isNotEmpty()) {
                    OutlinedButton(onClick = { onRetry(Screen.Quiz("${quiz.title} · Skipped", skippedQs)) }) { Text("Practise skipped (${skippedQs.size})") }
                }
            }
            OutlinedButton(onClick = onExit) { Text("Done") }
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
    val chosen = answers[pos]
    val eliminated = hints[pos].orEmpty()
    @Suppress("UNUSED_VARIABLE") val v = app.progress.version

    fun next() {
        if (pos == questions.lastIndex) finish() else pos++
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Question ${pos + 1} of ${questions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("Score $correct", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { (pos + 1f) / questions.size },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                drawStopIndicator = {},
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(q.format)
                    if (sheet != null) Tag(sheet.subjectName, MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                IconButton(onClick = { app.progress.toggleBookmark(q) }) {
                    val marked = app.progress.isBookmarked(q.id)
                    Text(
                        if (marked) "★" else "☆",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (marked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            Text(q.question, style = MaterialTheme.typography.titleLarge)

            q.options.forEachIndexed { i, opt ->
                val answered = chosen != null
                val out = i in eliminated && !answered
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
                        if (!answered && !out) {
                            answers[pos] = i
                            skipped.remove(pos)
                            app.progress.record(q, i == q.answer)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = border,
                    color = bg,
                    modifier = Modifier.fillMaxWidth().alpha(if (out) 0.4f else 1f),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(
                            opt,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (out) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }

            if (chosen == null) {
                if (eliminated.isEmpty()) {
                    OutlinedButton(
                        onClick = {
                            // Cross out two wrong options (a fixed pick per question).
                            val wrongOpts = q.options.indices.filter { it != q.answer }
                            hints[pos] = wrongOpts.shuffled(java.util.Random(q.id.hashCode().toLong())).take(2)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Stuck? Show a hint", modifier = Modifier.padding(vertical = 6.dp)) }
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Text(
                            "Hint: two wrong options are crossed out." + if (q.keyword.isNotBlank()) " This question tests the \"${q.keyword}\" angle." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            } else {
                val ok = chosen == q.answer
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (ok) "Correct!" else "Wrong — correct answer is option ${q.answer + 1}",
                            color = if (ok) correctGreen else wrongRed,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = { pos-- }, enabled = pos > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
            OutlinedButton(
                onClick = { if (pos !in skipped) skipped.add(pos); next() },
                enabled = chosen == null,
                modifier = Modifier.weight(1f),
            ) { Text("Skip") }
            Button(onClick = ::next, enabled = chosen != null, modifier = Modifier.weight(1.3f)) {
                Text(if (pos == questions.lastIndex) "Finish" else "Next")
            }
        }
    }
}
