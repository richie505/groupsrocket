package com.groupsrocket.mcq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = AppState(Repository(applicationContext), Progress(applicationContext))
        setContent { RocketTheme { RocketApp(app) } }
    }
}

class AppState(val repo: Repository, val progress: Progress) {
    val index get() = repo.index

    fun planStart(): LocalDate = progress.planStart() ?: LocalDate.parse(index.planStart)

    /** 1-based plan day for today (may be <1 before the start or >90 after the end). */
    fun todayDay(): Int = ChronoUnit.DAYS.between(planStart(), LocalDate.now()).toInt() + 1

    fun dayDate(day: Int): LocalDate = planStart().plusDays(day - 1L)

    /** Today's plan day, kept within 1..90. */
    fun currentDay(): Int = todayDay().coerceIn(1, index.plan.size.coerceAtLeast(1))

    fun isDayDone(day: Int): Boolean {
        val d = index.plan.getOrNull(day - 1) ?: return false
        return progress.isDayDone(day) || (d.isStudy && d.sheets.isNotEmpty() && d.sheets.all { progress.bestScore(it) != null })
    }

    /** Questions whose latest attempt was wrong. */
    fun wrongQuestions(): List<Mcq> {
        val ids = progress.wrongIds()
        return repo.mcqs(ids.values.toSet()).filter { it.id in ids }
    }
}

sealed interface Screen {
    data object Today : Screen
    data object Plan : Screen
    data object Subjects : Screen
    data object TrackerTab : Screen
    data object Review : Screen
    data class Day(val day: Int) : Screen
    data class SubjectDetail(val slug: String) : Screen
    data class SheetDetail(val ref: SheetRef) : Screen
    data class UnitDetail(val id: String) : Screen
    data class Notes(val ref: SheetRef) : Screen
    data class Facts(val ref: SheetRef) : Screen
    data class Quiz(val title: String, val questions: List<Mcq>, val day: Int? = null) : Screen
}

private val tabs = listOf(Screen.Today, Screen.Plan, Screen.Subjects, Screen.Review, Screen.TrackerTab)

@Composable
fun RocketTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFF93B4FF), secondary = Color(0xFFFDBA74), tertiary = Color(0xFF86EFAC))
    } else {
        lightColorScheme(primary = Color(0xFF1E3A8A), secondary = Color(0xFFC2410C), tertiary = Color(0xFF15803D))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RocketApp(app: AppState) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Today) }
    val current = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    // Re-read progress whenever it changes.
    @Suppress("UNUSED_VARIABLE") val v = app.progress.version

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val t = if (current == Screen.Today) "Day ${app.currentDay()} · Today" else titleOf(app, current)
                    Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (stack.size > 1) IconButton(onClick = ::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val day = when (current) {
                        Screen.Today -> app.currentDay()
                        is Screen.Day -> current.day
                        else -> null
                    }
                    if (day != null) {
                        // Browse days: from Today push a Day screen, otherwise swap the current one.
                        fun show(d: Int) = if (current is Screen.Today) go(Screen.Day(d)) else stack[stack.lastIndex] = Screen.Day(d)
                        IconButton(onClick = { show(day - 1) }, enabled = day > 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                        }
                        IconButton(onClick = { show(day + 1) }, enabled = day < app.index.plan.size) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current !is Screen.Quiz) NavigationBar {
                val root = stack.first()
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = root == tab,
                        onClick = { stack.clear(); stack.add(tab) },
                        icon = {
                            Icon(
                                when (tab) {
                                    Screen.Today -> Icons.Filled.Home
                                    Screen.Plan -> Icons.Filled.DateRange
                                    Screen.Subjects -> Icons.AutoMirrored.Filled.List
                                    Screen.Review -> Icons.Filled.Star
                                    else -> Icons.Filled.CheckCircle
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(titleOf(app, tab)) },
                    )
                }
            }
        },
    ) { pad ->
        Surface(Modifier.fillMaxSize().padding(pad)) {
            when (val s = current) {
                Screen.Today -> DayScreen(app, app.currentDay(), ::go)
                Screen.Plan -> PlanScreen(app, ::go)
                Screen.Subjects -> SubjectsScreen(app, ::go)
                Screen.TrackerTab -> TrackerScreen(app, ::go)
                Screen.Review -> ReviewScreen(app, ::go)
                is Screen.Day -> DayScreen(app, s.day, ::go)
                is Screen.SubjectDetail -> SubjectScreen(app, s.slug, ::go)
                is Screen.SheetDetail -> SheetScreen(app, s.ref, ::go)
                is Screen.UnitDetail -> UnitScreen(app, s.id, ::go)
                is Screen.Notes -> NotesScreen(app, s.ref)
                is Screen.Facts -> FactsScreen(app, s.ref, ::go)
                is Screen.Quiz -> QuizScreen(app, s, onExit = ::back, onRetry = { stack[stack.lastIndex] = it })
            }
        }
    }
}

private fun titleOf(app: AppState, s: Screen): String = when (s) {
    Screen.Today -> "Today"
    Screen.Plan -> "Plan"
    Screen.Subjects -> "Subjects"
    Screen.TrackerTab -> "Progress"
    Screen.Review -> "Review"
    is Screen.Day -> "Day ${s.day}" + if (s.day == app.todayDay()) " · Today" else ""
    is Screen.SubjectDetail -> app.index.subjects.firstOrNull { it.slug == s.slug }?.name ?: "Subject"
    is Screen.SheetDetail -> app.index.sheet(s.ref)?.let { "${it.subjectName} #${it.id}" } ?: "Sheet"
    is Screen.UnitDetail -> s.id
    is Screen.Notes -> app.index.sheet(s.ref)?.let { "Notes · ${it.subjectName} #${it.id}" } ?: "Notes"
    is Screen.Facts -> app.index.sheet(s.ref)?.let { "Facts · ${it.subjectName} #${it.id}" } ?: "Facts"
    is Screen.Quiz -> s.title
}

// ---------------------------------------------------------------- shared bits

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun ProgressLine(done: Int, total: Int) {
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else done.toFloat() / total },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        drawStopIndicator = {},
    )
}

@Composable
fun ClickCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() } }
}

@Composable
fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

@Composable
fun SheetRow(app: AppState, sheet: SheetInfo, go: (Screen) -> Unit, showSubject: Boolean = true) {
    val tally = app.progress.tallyBySheet()[sheet.ref]
    val best = app.progress.bestScore(sheet.ref)
    ClickCard(onClick = { go(Screen.SheetDetail(sheet.ref)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (showSubject) "${sheet.subjectName} · Sheet #${sheet.id}" else "Sheet #${sheet.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(sheet.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (best != null) {
                Text("$best%", style = MaterialTheme.typography.titleMedium, color = scoreColor(best), fontWeight = FontWeight.Bold)
            }
        }
        Text(
            if (sheet.count == 0) "MCQs not generated yet"
            else "${sheet.count} MCQs" + (tally?.let { " · ${it.attempted} done" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun scoreColor(percent: Int): Color = when {
    percent >= 80 -> MaterialTheme.colorScheme.tertiary
    percent >= 50 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

fun quizOf(app: AppState, title: String, refs: List<SheetRef>, limit: Int? = null, day: Int? = null): Screen.Quiz {
    var qs = app.repo.mcqs(refs)
    if (limit != null) qs = qs.shuffled().take(limit)
    return Screen.Quiz(title, qs, day)
}

@Composable
fun PracticeButtons(app: AppState, title: String, refs: List<SheetRef>, go: (Screen) -> Unit, day: Int? = null) {
    val total = refs.sumOf { app.index.sheet(it)?.count ?: 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { go(quizOf(app, title, refs, day = day)) }, enabled = total > 0) { Text("Practice all ($total)") }
        if (total > 20) OutlinedButton(onClick = { go(quizOf(app, "$title · Mixed 20", refs, limit = 20)) }) { Text("Random 20") }
    }
}

/** The start button(s) for a plan day, depending on its type. */
@Composable
fun DayButtons(app: AppState, day: Int, go: (Screen) -> Unit) {
    val d = app.index.plan.getOrNull(day - 1) ?: return
    when (d.type) {
        "study" -> PracticeButtons(app, "Day $day", d.sheets, go, day)
        "repair" -> {
            val wrong = app.wrongQuestions()
            Button(
                onClick = { go(Screen.Quiz("Day $day · Repair", wrong.shuffled(), day)) },
                enabled = wrong.isNotEmpty(),
            ) { Text("Practise wrong answers (${wrong.size})") }
        }
        else -> Button(onClick = { go(quizOf(app, "Day $day · ${d.title}", d.sheets, limit = d.questions, day = day)) }) {
            Text("Start test (${d.questions} random Qs)")
        }
    }
}

// ---------------------------------------------------------------- 90-day plan

@Composable
fun PlanScreen(app: AppState, go: (Screen) -> Unit) {
    val plan = app.index.plan
    val today = app.todayDay()
    var showReset by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            InfoCard {
                when {
                    today < 1 -> Text("Plan starts ${app.planStart().format(dateFmt)}", style = MaterialTheme.typography.titleLarge)
                    today > plan.size -> Text("90-day plan complete 🎉", style = MaterialTheme.typography.titleLarge)
                    else -> {
                        Text("Day $today of ${plan.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(LocalDate.now().format(dateFmt), style = MaterialTheme.typography.bodyMedium)
                        Text(plan[today - 1].title, style = MaterialTheme.typography.titleMedium)
                        DayButtons(app, today, go)
                    }
                }
                val daysDone = (1..plan.size).count { app.isDayDone(it) }
                Text("$daysDone / ${plan.size} days completed", style = MaterialTheme.typography.bodySmall)
                ProgressLine(daysDone, plan.size)
            }
        }
        if (today in 1..plan.size && plan[today - 1].isStudy) {
            item { SectionHeader("Today's sheets") }
            items(plan[today - 1].sheets) { ref -> app.index.sheet(ref)?.let { SheetSection(app, it, go) } }
        }
        item { SectionHeader("All 90 days · started ${app.planStart().format(dateFmt)}") }
        items(plan.indices.toList()) { d ->
            val day = d + 1
            val p = plan[d]
            ClickCard(onClick = { go(Screen.Day(day)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Day $day · ${app.dayDate(day).format(dateFmt)}" + if (day == today) " · Today" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (app.isDayDone(day)) {
                        Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                    } else if (p.isStudy) {
                        Text("${p.sheets.count { app.progress.bestScore(it) != null }}/${p.sheets.size}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item {
            TextButton(onClick = { showReset = true }, modifier = Modifier.padding(16.dp)) { Text("Restart plan from today") }
        }
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Restart the 90-day plan?") },
            text = { Text("Day 1 will be today. Your scores are kept.") },
            confirmButton = { TextButton(onClick = { app.progress.setPlanStart(LocalDate.now()); showReset = false }) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
    }
}

private fun dayTypeLabel(type: String) = when (type) {
    "study" -> "Study day"
    "review" -> "Weekly review test"
    "revision" -> "Revision day"
    "mock" -> "Mock test"
    else -> "Repair day"
}

@Composable
fun DayScreen(app: AppState, day: Int, go: (Screen) -> Unit) {
    val d = app.index.plan.getOrNull(day - 1) ?: return
    val sheets = d.sheets.mapNotNull { app.index.sheet(it) }
    val total = sheets.sumOf { it.count }
    val facts = sheets.sumOf { it.facts }
    val tally = app.progress.tally(d.sheets)
    val isToday = day == app.todayDay()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            InfoCard {
                Text(
                    "${app.dayDate(day).format(dateFmt)} · ${dayTypeLabel(d.type)}" + if (app.isDayDone(day)) " · ✓ done" else "",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(d.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                when {
                    d.isStudy -> {
                        Text("${sheets.size} ROCKET sheets · $total MCQs · $facts facts", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { ProgressLine(tally.attempted, total) }
                            Text("  ${tally.attempted}/$total", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    d.type == "repair" -> Text("Re-practise every question you last answered wrong.", style = MaterialTheme.typography.bodyMedium)
                    else -> Text(
                        "${d.questions} random MCQs from ${if (sheets.size == app.index.subjects.sumOf { it.sheets.size }) "all subjects" else "the ${sheets.size} sheets below"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                DayButtons(app, day, go)
            }
        }
        val showList = d.isStudy || d.type == "review" || (d.type == "revision" && sheets.size < 200)
        if (showList && sheets.isNotEmpty()) {
            item {
                val label = when {
                    !d.isStudy -> "SHEETS IN THIS TEST"
                    isToday -> "TODAY'S SHEETS"
                    else -> "DAY $day SHEETS"
                }
                Text(
                    "$label (${sheets.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(sheets) { SheetSection(app, it, go, showSubject = !d.isStudy) }
        }
    }
}

/** LIGHT / MED / HEAVY workload chip for a sheet, by MCQ count. */
@Composable
fun LoadChip(count: Int) {
    val (label, bg, fg) = when {
        count <= 15 -> Triple("LIGHT", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        count <= 35 -> Triple("MED", Color(0xFFFFEFD5), Color(0xFFB45309))
        else -> Triple("HEAVY", Color(0xFFFFE4E1), Color(0xFFB91C1C))
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** One sheet in a day's list: number badge, clear title, workload, counts and progress. */
@Composable
fun SheetSection(app: AppState, sheet: SheetInfo, go: (Screen) -> Unit, showSubject: Boolean = false) {
    val tally = app.progress.tallyBySheet()[sheet.ref]
    val done = tally?.attempted ?: 0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { go(Screen.SheetDetail(sheet.ref)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("#${sheet.id}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showSubject) {
                Text(sheet.subjectName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(sheet.title, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadChip(sheet.count)
                Text(
                    "ROCKET SHEET #${sheet.id} · ${sheet.count} MCQs" + if (sheet.facts > 0) " · ${sheet.facts} facts" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ProgressLine(done, sheet.count) }
                Text("  $done/${sheet.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- subjects

@Composable
fun SubjectsScreen(app: AppState, go: (Screen) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(app.index.subjects) { s ->
            val tally = app.progress.tally(s.sheets.map { it.ref })
            ClickCard(onClick = { go(Screen.SubjectDetail(s.slug)) }) {
                Text(s.name, style = MaterialTheme.typography.titleMedium)
                val facts = s.sheets.sumOf { it.facts }
                val covered = s.sheets.sumOf { it.covered }
                Text(
                    "${s.sheets.size} ROCKET sheets · ${s.total} MCQs · ${tally.attempted} done" +
                        if (facts > 0) "\n$covered / $facts facts covered" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProgressLine(tally.attempted, s.total)
            }
        }
    }
}

@Composable
fun SubjectScreen(app: AppState, slug: String, go: (Screen) -> Unit) {
    val s = app.index.subjects.first { it.slug == slug }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { InfoCard { PracticeButtons(app, s.name, s.sheets.map { it.ref }, go) } }
        items(s.sheets) { SheetRow(app, it, go, showSubject = false) }
    }
}

@Composable
fun SheetScreen(app: AppState, ref: SheetRef, go: (Screen) -> Unit) {
    val sheet = app.index.sheet(ref) ?: return
    val tally = app.progress.tallyBySheet()[ref]
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            InfoCard {
                Text(sheet.title, style = MaterialTheme.typography.titleLarge)
                Text("${sheet.subjectName} – ROCKET SHEET #${sheet.id}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${sheet.count} MCQs" +
                        (tally?.let { " · ${it.attempted} attempted · ${it.correct} correct" } ?: "") +
                        (app.progress.bestScore(ref)?.let { " · best $it%" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (sheet.facts > 0) {
                    Text(
                        "Facts covered: ${sheet.covered} / ${sheet.facts}" + if (sheet.covered == sheet.facts) " ✓ every fact tested" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { go(quizOf(app, "${sheet.subjectName} · Sheet #${sheet.id}", listOf(ref))) },
                        enabled = sheet.count > 0,
                    ) { Text("Practice") }
                    OutlinedButton(onClick = { go(Screen.Notes(ref)) }) { Text("Notes") }
                    if (sheet.facts > 0) OutlinedButton(onClick = { go(Screen.Facts(ref)) }) { Text("Facts") }
                }
            }
        }
        item { SectionHeader("Syllabus tracker") }
        if (sheet.tracker.all.isEmpty()) {
            item {
                Text(
                    "Mapped when this sheet's MCQs are generated.",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(sheet.tracker.all) { id ->
            val u = app.index.unitsById[id] ?: return@items
            ClickCard(onClick = { go(Screen.UnitDetail(id)) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tag(u.exam); Tag(u.id, MaterialTheme.colorScheme.tertiaryContainer) }
                Text(u.section, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(u.title, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun NotesScreen(app: AppState, ref: SheetRef) {
    val text = remember(ref) { app.repo.notes(ref) }
    SelectionContainer {
        Text(
            text.ifBlank { "Notes not found." },
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---------------------------------------------------------------- syllabus tracker

@Composable
fun TrackerScreen(app: AppState, go: (Screen) -> Unit) {
    var exam by remember { mutableIntStateOf(0) }
    val exams = listOf("G1", "G2")
    Column {
        TabRow(selectedTabIndex = exam) {
            Tab(selected = exam == 0, onClick = { exam = 0 }, text = { Text("Group 1 Prelims") })
            Tab(selected = exam == 1, onClick = { exam = 1 }, text = { Text("Group 2") })
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                val overall = app.progress.overall()
                val plan = app.index.plan
                InfoCard {
                    Text("Your progress", style = MaterialTheme.typography.titleMedium)
                    Text("${(1..plan.size).count { app.isDayDone(it) }} / ${plan.size} plan days done")
                    Text("${overall.attempted} / ${app.index.totalMcqs} MCQs attempted" + if (overall.attempted > 0) " · accuracy ${overall.correct * 100 / overall.attempted}%" else "")
                    ProgressLine(overall.attempted, app.index.totalMcqs)
                }
            }
            item { SectionHeader("Syllabus tracker · ${if (exam == 0) "Group 1 Prelims" else "Group 2"}") }
            app.index.units.filter { it.exam == exams[exam] }.groupBy { it.section }.forEach { (section, units) ->
                item { SectionHeader(section) }
                unitCards(app, units, go)
            }
        }
    }
}

private fun LazyListScope.unitCards(app: AppState, units: List<SyllabusUnit>, go: (Screen) -> Unit) {
    items(units) { u ->
        val total = u.sheets.sumOf { app.index.sheet(it)?.count ?: 0 }
        val tally = app.progress.tally(u.sheets)
        ClickCard(onClick = { go(Screen.UnitDetail(u.id)) }) {
            Tag(u.id, MaterialTheme.colorScheme.tertiaryContainer)
            Text(u.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (u.sheets.isEmpty()) "No ROCKET sheets mapped yet"
                else "${u.sheets.size} sheets · $total MCQs · ${tally.attempted} done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (total > 0) ProgressLine(tally.attempted, total)
        }
    }
}

@Composable
fun UnitScreen(app: AppState, id: String, go: (Screen) -> Unit) {
    val u = app.index.unitsById[id] ?: return
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            InfoCard {
                Text(u.section, style = MaterialTheme.typography.labelLarge)
                Text(u.title, style = MaterialTheme.typography.bodyLarge)
                PracticeButtons(app, u.id, u.sheets, go)
            }
        }
        items(u.sheets) { ref -> app.index.sheet(ref)?.let { SheetRow(app, it, go) } }
    }
}

// ---------------------------------------------------------------- review

@Composable
fun ReviewScreen(app: AppState, go: (Screen) -> Unit) {
    val wrong = app.progress.wrongIds()
    val marked = app.progress.bookmarkIds()
    val overall = app.progress.overall()
    var confirmReset by remember { mutableStateOf(false) }

    fun load(ids: Map<String, SheetRef>): List<Mcq> =
        app.repo.mcqs(ids.values.toSet()).filter { it.id in ids }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        InfoCard {
            Text("Overall", style = MaterialTheme.typography.titleMedium)
            Text("${overall.attempted} of ${app.index.totalMcqs} MCQs attempted")
            if (overall.attempted > 0) Text("Accuracy ${overall.correct * 100 / overall.attempted}%")
            ProgressLine(overall.attempted, app.index.totalMcqs)
        }
        ClickCard(onClick = { if (wrong.isNotEmpty()) go(Screen.Quiz("Wrong answers", app.wrongQuestions().shuffled())) }) {
            Text("Wrong answers", style = MaterialTheme.typography.titleMedium)
            Text("${wrong.size} questions whose last attempt was wrong", style = MaterialTheme.typography.bodySmall)
        }
        ClickCard(onClick = { if (marked.isNotEmpty()) go(Screen.Quiz("Bookmarked", load(marked))) }) {
            Text("★ Bookmarked", style = MaterialTheme.typography.titleMedium)
            Text("${marked.size} saved questions", style = MaterialTheme.typography.bodySmall)
        }
        ClickCard(onClick = { go(quizOf(app, "Random 25", app.index.subjects.flatMap { s -> s.sheets.map { it.ref } }, limit = 25)) }) {
            Text("Random mixed test", style = MaterialTheme.typography.titleMedium)
            Text("25 questions from all subjects", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Reset all progress") }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("Scores, wrong answers and bookmarks will be cleared.") },
            confirmButton = { TextButton(onClick = { app.progress.resetAll(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- facts checklist

@Composable
fun FactsScreen(app: AppState, ref: SheetRef, go: (Screen) -> Unit) {
    val facts = remember(ref) { app.repo.facts(ref) }
    val mcqs = remember(ref) { app.repo.mcqs(ref) }
    val byFact = remember(ref) {
        val m = HashMap<String, MutableList<Mcq>>()
        mcqs.forEach { q -> q.facts.forEach { m.getOrPut(it) { mutableListOf() }.add(q) } }
        m
    }
    val covered = facts.count { byFact.containsKey(it.id) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            InfoCard {
                Text("$covered of ${facts.size} facts are tested by an MCQ", style = MaterialTheme.typography.titleMedium)
                Text("Tap a fact to practise the questions that test it.", style = MaterialTheme.typography.bodySmall)
                ProgressLine(covered, facts.size)
            }
        }
        items(facts) { f ->
            val qs = byFact[f.id].orEmpty()
            ClickCard(onClick = { if (qs.isNotEmpty()) go(Screen.Quiz("Fact ${f.id}", qs)) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (qs.isEmpty()) "✗" else "✓",
                        color = if (qs.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(f.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${f.id} · " + if (qs.isEmpty()) "not tested" else "${qs.size} MCQ" + if (qs.size > 1) "s" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
