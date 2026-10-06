package com.appsc.prep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.prep.data.PlanDay
import com.appsc.prep.data.PlanRow
import com.appsc.prep.ui.components.Card
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.components.PracticeCard
import com.appsc.prep.ui.components.ProgressLine
import com.appsc.prep.ui.components.SectionHeader
import com.appsc.prep.ui.components.SectionItem
import com.appsc.prep.ui.components.Tag
import com.appsc.prep.ui.components.TopBar
import com.appsc.prep.ui.theme.C
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

interface Nav {
    fun quiz(kind: String, book: Int, index: Int, mode: String = "new", sub: Int = -1)
    fun day(n: Int)
    fun row(book: Int, row: Int)
    fun read(book: Int, row: Int, sec: Int)
    fun book(id: Int)
    fun back()
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/** (done, total) subsections across a day's rows. */
@Composable
fun dayProgress(day: PlanDay): Pair<Int, Int> {
    val app = LocalApp.current
    var done = 0
    var total = 0
    for (r in day.rows) {
        val n = app.repo.rowInfo(r.book, r.row)?.subsectionCount ?: 0
        total += n
        done += app.store.doneCount(r.book, r.row, n)
    }
    return done to total
}

@Composable
fun TodayScreen(nav: Nav) {
    val app = LocalApp.current
    val today = LocalDate.now()
    val plan = app.repo.plan
    val day = app.repo.dayFor(today)
    val afterPlan = today.isAfter(plan.days.last().date)
    val beforePlan = today.isBefore(plan.days.first().date)
    val daysToExam = ChronoUnit.DAYS.between(today, plan.exam).coerceAtLeast(0)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp)) {
                Text(today.format(dateFmt), style = TextStyle(fontSize = 13.sp, color = C.Muted))
                Text(
                    LocalApp.current.repo.appName,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink),
                )
            }
        }
        item { HeroCard(day, daysToExam, plan.examLabel, beforePlan, afterPlan, nav) }
        item { ContinueCard(nav) }
        if (afterPlan) {
            item { SectionHeader("Final buffer (R3)") }
            items(plan.buffer) { b ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(b.dates, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Accent))
                        Spacer(Modifier.height(2.dp))
                        Text(b.work, style = TextStyle(fontSize = 15.sp, color = C.Body))
                    }
                }
            }
        }
        dayBody(day, nav, showBrief = true)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeroCard(
    day: PlanDay,
    daysToExam: Long,
    examLabel: String,
    beforePlan: Boolean,
    afterPlan: Boolean,
    nav: Nav,
) {
    val (done, total) = dayProgress(day)
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3949AB))))
            .clickable { nav.day(day.n) }
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        beforePlan -> "Starts ${day.date.format(DateTimeFormatter.ofPattern("d MMM"))}"
                        afterPlan -> "Plan complete"
                        else -> "Today's target"
                    },
                    style = TextStyle(fontSize = 13.sp, color = Color(0xFFC7D2FE), fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$daysToExam days to exam",
                    style = TextStyle(fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Day ${day.n} of 90",
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            Text(
                listOf(prettyPhase(day), day.focus.titleCase()).filter { it.isNotBlank() }.joinToString(" · "),
                style = TextStyle(fontSize = 14.sp, color = Color(0xFFE0E7FF)),
            )
            if (total > 0) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(done / total.toFloat(), Modifier.weight(1f), color = Color(0xFFFBBF24))
                    Spacer(Modifier.width(10.dp))
                    Text("$done / $total", style = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold))
                }
                Text(
                    "subsections ${LocalApp.current.doneWord} · exam $examLabel",
                    style = TextStyle(fontSize = 12.sp, color = Color(0xFFC7D2FE)),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(nav: Nav) {
    val app = LocalApp.current
    val last = app.store.lastRead ?: return
    val parts = last.split(':').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return
    val info = app.repo.rowInfo(parts[0], parts[1]) ?: return
    Card(onClick = { nav.read(parts[0], parts[1], parts[2]) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.AccentSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = C.Accent) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Continue reading", style = TextStyle(fontSize = 12.sp, color = C.Muted))
                Text(
                    info.title,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
        }
    }
}

/** Topics -> Sections list plus the schedule for a day. */
fun LazyListScope.dayBody(day: PlanDay, nav: Nav, showBrief: Boolean) {
    if (showBrief && day.brief.isNotEmpty()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                day.brief.forEach {
                    Text(
                        it,
                        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Muted),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
    // Weekly test, revision, mock and repair days: the test comes first, then the sheets it draws on.
    val testDay = day.quiz > 0 || day.repair
    if (testDay) {
        item(key = "mcq-${day.n}") { DayPracticeCard(day, nav) }
    }
    if (day.rows.isNotEmpty()) {
        item(key = "topics-${day.n}") {
            val app = LocalApp.current
            // group consecutive rows by topic (book + unit)
            val groups = mutableListOf<Pair<Pair<Int, Int>, MutableList<PlanRow>>>()
            for (r in day.rows) {
                val unit = app.repo.rowInfo(r.book, r.row)?.unitIndex ?: -1
                val key = r.book to unit
                if (groups.lastOrNull()?.first == key) groups.last().second += r else groups += key to mutableListOf(r)
            }
            Column {
                SectionHeader(if (day.type == "revision") "Revise today" else "Today's topics")
                groups.forEachIndexed { gi, (key, rows) ->
                    TopicCard(gi + 1, key.first, key.second, rows, nav)
                }
            }
        }
    }
    if (day.rows.isNotEmpty() && !testDay) {
        item(key = "mcq-${day.n}") { DayPracticeCard(day, nav) }
    }
    if (day.tasks.isNotEmpty()) {
        item(key = "sched-${day.n}") { ScheduleCard(day) }
    }
}

@Composable
private fun TopicCard(number: Int, book: Int, unit: Int, rows: List<PlanRow>, nav: Nav) {
    val app = LocalApp.current
    val bookInfo = app.repo.index.getOrNull(book - 1)
    val unitInfo = bookInfo?.units?.getOrNull(unit)
    Card {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "TOPIC $number · ${bookInfo?.short ?: ""}".uppercase(),
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Accent, letterSpacing = 0.6.sp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        unitInfo?.title ?: "",
                        style = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = C.Ink),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!unitInfo?.code.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Tag(unitInfo!!.code)
                }
            }
            rows.forEachIndexed { i, r ->
                val info = app.repo.rowInfo(r.book, r.row)
                val total = info?.subsectionCount ?: 0
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 16.dp))
                SectionItem(
                    number = "${i + 1}",
                    title = info?.title ?: r.topic,
                    meta = listOfNotNull(
                        r.pages.takeIf { it.isNotBlank() }?.let { "pp $it" },
                        info?.questionCount?.takeIf { it > 0 }?.let { "$it MCQs" },
                        "$total subsections",
                    ).joinToString(" · "),
                    priority = r.priority,
                    done = app.store.doneCount(r.book, r.row, total),
                    total = total,
                    onClick = { nav.row(r.book, r.row) },
                    onPractice = if ((info?.questionCount ?: 0) > 0) ({ nav.quiz("row", r.book, r.row) }) else null,
                )
            }
        }
    }
}

@Composable
private fun DayPracticeCard(day: PlanDay, nav: Nav) {
    val app = LocalApp.current
    val onSections = day.rows.sumOf { app.repo.rowInfo(it.book, it.row)?.questionCount ?: 0 }
    val wrong = if (day.repair) app.store.answers.count { !it.value } else 0
    val total = when {
        day.repair -> wrong
        day.quiz > 0 -> day.quiz
        else -> onSections
    }
    if (total == 0 && !day.repair) return
    SectionHeader("MCQ practice")
    PracticeCard(
        title = when {
            day.repair -> "Repair: your wrong answers"
            day.type == "mock" -> "Mock test"
            day.quiz > 0 -> "Today's test"
            else -> "Today's MCQs"
        },
        subtitle = when {
            day.repair -> if (wrong == 0) "No wrong answers yet" else "$wrong questions you last answered wrong"
            day.quiz > 0 && day.rows.isEmpty() -> "$total random questions from all 721 ROCKET sheets"
            day.quiz > 0 -> "$total random questions from these ${day.rows.size} sections"
            else -> "$total questions on today's sections"
        },
        attempted = null,
        onStart = { nav.quiz("day", 0, day.n) },
    )
}

@Composable
private fun ScheduleCard(day: PlanDay) {
    var open by rememberSaveable(day.n) { mutableStateOf(false) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Daily schedule", Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = C.Muted, modifier = Modifier.padding(top = 12.dp))
        }
        if (open) {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
                    day.tasks.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 16.dp))
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Icon(Icons.Outlined.Schedule, null, tint = C.Accent, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    listOf(t.time, t.block).filter { it.isNotBlank() }.joinToString("  ·  "),
                                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                                )
                                Text(t.task, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayScreen(n: Int, nav: Nav) {
    val app = LocalApp.current
    val days = app.repo.plan.days
    var current by rememberSaveable { mutableStateOf(n) }
    val day = days.first { it.n == current }
    Column(Modifier.fillMaxSize()) {
        TopBar("Day ${day.n}", onBack = nav::back) {
            IconButton(onClick = { current-- }, enabled = current > 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
            }
            IconButton(onClick = { current++ }, enabled = current < days.size) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "head-${day.n}") {
                val (done, total) = dayProgress(day)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "${day.date.format(dateFmt)} · ${day.left}",
                        style = TextStyle(fontSize = 13.sp, color = C.Muted),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        day.focus.titleCase().ifBlank { prettyPhase(day) },
                        style = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                    )
                    Spacer(Modifier.height(6.dp))
                    Tag(prettyPhase(day), C.AccentSoft, C.Accent)
                    if (total > 0) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressLine(done / total.toFloat(), Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            Text("$done / $total ${LocalApp.current.doneWord}", style = TextStyle(fontSize = 12.sp, color = C.Muted))
                        }
                    }
                }
                HorizontalDivider(color = C.Line)
            }
            dayBody(day, nav, showBrief = true)
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PlanScreen(nav: Nav) {
    val app = LocalApp.current
    val plan = app.repo.plan
    val todayN = app.repo.dayFor(LocalDate.now()).n
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem((todayN - 3).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        TopBar("90-Day Plan")
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            var lastPhase = ""
            plan.days.forEach { d ->
                val phase = phaseGroup(d)
                if (phase != lastPhase) {
                    lastPhase = phase
                    item(key = "ph-$phase") { SectionHeader(phase) }
                }
                item(key = "d-${d.n}") { DayItem(d, d.n == todayN) { nav.day(d.n) } }
            }
            item(key = "buffer") {
                Column {
                    SectionHeader("Final buffer (R3) · 20 Dec – 2 Jan")
                    plan.buffer.forEach { b ->
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            Text(b.dates, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Accent), modifier = Modifier.width(92.dp))
                            Text(b.work, style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = C.Body))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DayItem(d: PlanDay, isToday: Boolean, onClick: () -> Unit) {
    val (done, total) = dayProgress(d)
    val complete = total > 0 && done >= total
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isToday) C.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        complete -> C.Green
                        isToday -> C.Accent
                        else -> C.Chip
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (complete) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    "${d.n}",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isToday) Color.White else C.Ink),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${d.dow}, ${d.date.format(DateTimeFormatter.ofPattern("d MMM"))}",
                    style = TextStyle(fontSize = 12.sp, color = C.Muted),
                )
                if (isToday) Tag("TODAY", C.Accent, Color.White)
                if (d.type == "sunday") Tag("Review", C.MedSoft, C.Med)
                if (d.type == "mock") Tag("Mock", C.HighSoft, C.High)
            }
            Text(
                dayTitle(d),
                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = C.Ink),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (total > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(done / total.toFloat(), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("${d.rows.size} sections", style = TextStyle(fontSize = 11.sp, color = C.Muted))
                }
            }
        }
    }
}

private fun dayTitle(d: PlanDay): String = when (d.type) {
    "mock" -> d.brief.firstOrNull() ?: "Mock test"
    "sunday" -> "Weekly review + test"
    else -> d.focus.titleCase().ifBlank { d.brief.firstOrNull() ?: "" }
}

private fun phaseGroup(d: PlanDay): String = when {
    d.phase.contains("PHASE 1") || d.type == "sunday" && d.n <= 70 -> "Phase 1 · First pass"
    d.phase.contains("PHASE 2") -> "Phase 2 · R2 revision"
    d.phase.contains("PHASE 3") -> "Phase 3 · Mocks"
    else -> d.phase.titleCase()
}

fun prettyPhase(d: PlanDay): String = when (d.type) {
    "sunday" -> "Weekly review"
    "revision" -> "R2 revision"
    "mock" -> "Mock test"
    else -> "First pass"
}

fun String.titleCase(): String =
    lowercase().split(" ").joinToString(" ") { w ->
        when {
            w == "&" || w == "+" -> w
            w in setOf("ir", "ca", "r2", "r3") -> w.uppercase()
            w.startsWith("(") -> w
            else -> w.replaceFirstChar { it.uppercase() }
        }
    }.replace("Ir ", "IR ")
