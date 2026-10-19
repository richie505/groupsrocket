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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.prep.data.BookInfo
import com.appsc.prep.ui.components.Card
import com.appsc.prep.ui.components.PageList
import com.appsc.prep.ui.components.PracticeCard
import com.appsc.prep.ui.components.columnsFor
import com.appsc.prep.ui.components.gridItems
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.components.ProgressLine
import com.appsc.prep.ui.components.SectionHeader
import com.appsc.prep.ui.components.SectionItem
import com.appsc.prep.ui.components.StatBox
import com.appsc.prep.ui.components.studyTime
import com.appsc.prep.data.DayLog
import androidx.compose.material.icons.outlined.Timer
import com.appsc.prep.ui.components.Tag
import com.appsc.prep.ui.components.TopBar
import com.appsc.prep.ui.theme.C

private val bookColors = listOf(
    Color(0xFF6D4C41), Color(0xFF3949AB), Color(0xFF00897B),
    Color(0xFF2E7D32), Color(0xFF8E24AA), Color(0xFFD84315),
)

/** "1 MCQ", "5 MCQs". */
fun mcqCount(n: Int): String = if (n == 1) "1 MCQ" else "$n MCQs"

@Composable
private fun bookDone(b: BookInfo): Int {
    val store = LocalApp.current.store
    return b.rows.sumOf { store.doneCount(b.id, it.index, it.subsectionCount) }
}

@Composable
fun BooksScreen(nav: Nav) {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Notes")
        PageList(Modifier.fillMaxSize(), max = 1180.dp) { width ->
            item { SectionHeader("ROCKET Sheets · 14 subjects · 721 sheets") }
            gridItems(app.repo.index, columnsFor(width, 460.dp, 2), spacing = 0.dp) { b ->
                val done = bookDone(b)
                Card(onClick = { nav.book(b.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(width = 46.dp, height = 58.dp).clip(RoundedCornerShape(8.dp)).background(bookColors[(b.id - 1) % 6]),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${b.id}", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                            Text(
                                "${b.units.size} topics · ${b.rows.size} sections · ${b.pages} pages",
                                style = TextStyle(fontSize = 12.sp, color = C.Muted),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressLine(done / b.subsectionTotal.coerceAtLeast(1).toFloat(), Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text("${done * 100 / b.subsectionTotal.coerceAtLeast(1)}%", style = TextStyle(fontSize = 11.sp, color = C.Muted))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** A book: Topics (units), each expandable to its Sections (rows). */
@Composable
fun BookScreen(id: Int, nav: Nav) {
    val app = LocalApp.current
    val b = app.repo.index[id - 1]
    Column(Modifier.fillMaxSize()) {
        TopBar(b.short, onBack = nav::back)
        PageList(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(20.dp)) {
                    Text(b.title, style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                    Spacer(Modifier.height(10.dp))
                    val done = bookDone(b)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(done / b.subsectionTotal.coerceAtLeast(1).toFloat(), Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text("$done / ${b.subsectionTotal}", style = TextStyle(fontSize = 12.sp, color = C.Muted))
                    }
                }
                HorizontalDivider(color = C.Line)
            }
            b.units.forEachIndexed { ui, unit ->
                item(key = "u$ui") {
                    var open by rememberSaveable { mutableStateOf(b.units.size == 1) }
                    val rows = b.rows.filter { it.unitIndex == ui }
                    val total = rows.sumOf { it.subsectionCount }
                    val done = rows.sumOf { app.store.doneCount(b.id, it.index, it.subsectionCount) }
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("TOPIC ${ui + 1}", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Accent, letterSpacing = 0.6.sp))
                                    if (unit.code.isNotBlank()) Tag(unit.code)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(unit.title, style = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                                Text(
                                    "${rows.size} sections · $done/$total ${LocalApp.current.doneWord}",
                                    style = TextStyle(fontSize = 12.sp, color = C.Muted),
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = C.Muted)
                        }
                        if (open) {
                            rows.forEachIndexed { i, r ->
                                HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 16.dp))
                                SectionItem(
                                    number = "${i + 1}",
                                    title = r.title,
                                    meta = listOfNotNull(
                                        "pp ${r.p1}-${r.p2}",
                                        r.codes.firstOrNull(),
                                        r.questionCount.takeIf { it > 0 }?.let { mcqCount(it) },
                                        "${r.subsectionCount} subsections",
                                    ).joinToString(" · "),
                                    priority = app.repo.planRowByRef[b.id to r.index]?.priority ?: "",
                                    done = app.store.doneCount(b.id, r.index, r.subsectionCount),
                                    total = r.subsectionCount,
                                    onClick = { nav.row(b.id, r.index) },
                                    onPractice = if (r.questionCount > 0) ({ nav.quiz("row", b.id, r.index) }) else null,
                                )
                            }
                            val general = b.unitQuestions[ui] ?: 0
                            if (general > 0) {
                                HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 16.dp))
                                Row(
                                    Modifier.fillMaxWidth().clickable { nav.quiz("unit", b.id, ui) }.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Quiz, null, tint = C.ExamInk, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Topic MCQs (not tied to one section) · $general",
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.ExamInk),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
                                }
                            }
                        }
                        HorizontalDivider(color = C.Line)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ProgressScreen(nav: Nav) {
    val app = LocalApp.current
    val store = app.store
    val books = app.repo.index
    val total = books.sumOf { it.subsectionTotal }
    val done = books.sumOf { bookDone(it) }
    val daysDone = app.repo.plan.days.count { d ->
        val (dd, tt) = dayProgress(d)
        tt > 0 && dd >= tt
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Progress")
        PageList(Modifier.fillMaxSize()) { width ->
            item {
                Column(Modifier.padding(20.dp)) {
                    Text("Overall", style = TextStyle(fontSize = 13.sp, color = C.Muted))
                    Text(
                        "${if (total == 0) 0 else done * 100 / total}% of notes ${app.doneWord}",
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink),
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(if (total == 0) 0f else done / total.toFloat())
                    Spacer(Modifier.height(18.dp))
                    // Answers saved before 2.8 are for PYQs (now in the MCQ app); notes-MCQ ids start with "n".
                    val mine = store.answers.filterKeys { it.startsWith("n") }
                    val answered = mine.size
                    val right = mine.count { it.value }
                    val boxes: List<@Composable (Modifier) -> Unit> = listOf(
                        { m -> StatBox("$done", "subsections ${app.doneWord}", Icons.Outlined.TaskAlt, m) },
                        { m -> StatBox("$daysDone / 90", "days completed", Icons.Outlined.CalendarMonth, m) },
                        { m -> StatBox("${store.streak()}", "day streak", Icons.Outlined.LocalFireDepartment, m) },
                        { m -> StatBox("${store.saved.size}", "bookmarks", Icons.Outlined.AutoStories, m) },
                        { m -> StatBox("$answered", "MCQs answered", Icons.Outlined.Quiz, m) },
                        { m -> StatBox(if (answered == 0) "–" else "${right * 100 / answered}%", "MCQ accuracy", Icons.Outlined.TaskAlt, m) },
                    )
                    // 2 a row on a phone, 3 on a wide window
                    boxes.chunked(columnsFor(width, 300.dp, 3).coerceAtLeast(2)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { it(Modifier.weight(1f)) } }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            item {
                HorizontalDivider(color = C.Line)
                McqResults(nav)
                HorizontalDivider(color = C.Line)
                StudyActivity(width)
                HorizontalDivider(color = C.Line)
                SectionHeader("By subject")
            }
            gridItems(books, columnsFor(width, 420.dp, 2), spacing = 0.dp) { b ->
                val d = bookDone(b)
                Column(
                    Modifier.fillMaxWidth().clickable { nav.book(b.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row {
                        Text(b.short, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink), modifier = Modifier.weight(1f))
                        Text("$d / ${b.subsectionTotal}", style = TextStyle(fontSize = 13.sp, color = C.Muted))
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(d / b.subsectionTotal.coerceAtLeast(1).toFloat(), color = bookColors[(b.id - 1) % 6])
                }
            }
            item { HorizontalDivider(color = C.Line); BackupCard() }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * The notes MCQs split by how they were solved: right at the first try, right on a retry (after a wrong answer or
 * after seeing the answer), still wrong, and answer seen but not solved on its own yet. The last two need practice.
 */
@Composable
private fun McqResults(nav: Nav) {
    val store = LocalApp.current.store
    val ids = (store.answers.keys + store.seen).filter { it.startsWith("n") }
    val first = ids.count { store.answers[it] == true && it !in store.missed && it !in store.seen }
    val retry = ids.count { store.answers[it] == true && (it in store.missed || it in store.seen) }
    val wrong = ids.count { store.answers[it] == false }
    val peeked = ids.count { it in store.seen && it !in store.answers }
    val total = first + retry + wrong + peeked
    SectionHeader("MCQ results")
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
        val parts = listOf(
            Triple("Right first time", "Solved on your own at the first try", first) to C.Green,
            Triple("Right on a retry", "Wrong earlier or answer seen, then solved", retry) to C.Accent,
            Triple("Still wrong", "Wrong on the latest try", wrong) to C.High,
            Triple("Answer seen", "Answer shown before you picked, not solved on your own yet", peeked) to C.Med,
        )
        if (total > 0) {
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                parts.filter { it.first.third > 0 }.forEach { (p, color) ->
                    Box(Modifier.weight(p.third.toFloat()).fillMaxSize().background(color))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        parts.forEach { (p, color) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.first, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink))
                    Text(p.second, style = TextStyle(fontSize = 12.sp, color = C.Muted))
                }
                Text("${p.third}", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink))
            }
        }
        val need = wrong + peeked
        if (need > 0) {
            Spacer(Modifier.height(8.dp))
            PracticeCard(
                "MCQs to practise",
                "$need questions · still wrong or answer seen",
                attempted = null,
                onStart = { nav.quiz("need", 0, 0, "practice") },
            )
        }
    }
}

/** What was done each day: MCQs practised and time on notes and on MCQs. A record only, with no targets. */
@Composable
private fun StudyActivity(width: androidx.compose.ui.unit.Dp) {
    val store = LocalApp.current.store
    val log = store.activity
    val today = store.dayLog()
    SectionHeader("Study activity")
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Today", style = TextStyle(fontSize = 13.sp, color = C.Muted))
        Spacer(Modifier.height(6.dp))
        val boxes: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> StatBox("${today.mcqs}", "MCQs practised", Icons.Outlined.Quiz, m) },
            { m -> StatBox(studyTime(today.notesSec), "on notes", Icons.Outlined.AutoStories, m) },
            { m -> StatBox(studyTime(today.mcqSec), "on MCQs", Icons.Outlined.Timer, m) },
        )
        boxes.chunked(columnsFor(width, 300.dp, 3).coerceAtLeast(3)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { it(Modifier.weight(1f)) } }
        }
        SpentOn(java.time.LocalDate.now(), "Where today's time went")
        val days = log.filterValues { !it.empty }.toSortedMap(reverseOrder())
        if (days.isNotEmpty()) {
            val all = days.values.fold(DayLog()) { a, b -> a + b }
            Spacer(Modifier.height(10.dp))
            Text(
                "All days: ${all.mcqs} MCQs · ${studyTime(all.totalSec)} in the app (notes ${studyTime(all.notesSec)}, MCQs ${studyTime(all.mcqSec)})",
                style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = C.Body),
            )
            Spacer(Modifier.height(12.dp))
            ActivityRow("Day", "MCQs", "Notes", "MCQ time", "Total", header = true)
            var open by rememberSaveable { mutableStateOf<String?>(null) }
            days.entries.take(30).forEach { (d, l) ->
                val day = runCatching { java.time.LocalDate.parse(d) }.getOrNull()
                val date = day?.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")) ?: d
                // tap a day: what its time went on
                ActivityRow(date, "${l.mcqs}", studyTime(l.notesSec), studyTime(l.mcqSec), studyTime(l.totalSec), open = open == d) {
                    open = if (open == d) null else d
                }
                if (open == d && day != null) SpentOn(day, null)
            }
            if (days.size > 30) {
                Text("Showing the last 30 days.", style = TextStyle(fontSize = 12.sp, color = C.Muted), modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text(
            "Time counts while you read notes (or listen to them) and practise MCQs; it pauses after 5 minutes without use.",
            style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = C.Muted),
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )
    }
}

/** What a day's study time went on: each notes page and each MCQ practice, longest first. */
@Composable
private fun SpentOn(day: java.time.LocalDate, title: String?) {
    val app = LocalApp.current
    val spent = app.store.spentOn(day).filterValues { it.sec >= 60 || it.mcqs > 0 }
    if (spent.isEmpty()) return
    // page titles need the books they are in
    val books = spent.keys.mapNotNull { it.split(':').getOrNull(if (it.startsWith("n:")) 1 else 2)?.toIntOrNull() }.toSet()
    val loaded by androidx.compose.runtime.produceState(emptyMap<Int, com.appsc.prep.data.Book>(), books) {
        value = books.filter { it in 1..6 }.associateWith { runCatching { app.repo.book(it) }.getOrNull() }.filterValues { it != null }.mapValues { it.value!! }
    }
    fun rowTitle(b: Int, r: Int) = app.repo.rowInfo(b, r)?.title ?: loaded[b]?.rows?.getOrNull(r)?.title ?: "Section"
    fun secTitle(b: Int, r: Int, s: Int) = loaded[b]?.rows?.getOrNull(r)?.secs?.getOrNull(s)?.title
    fun label(key: String): Pair<String, String?> {
        val p = key.split(':')
        val n = p.drop(1).map { it.toIntOrNull() }
        return if (p[0] == "n") {
            val (b, r, s) = Triple(n.getOrNull(0) ?: 0, n.getOrNull(1) ?: 0, n.getOrNull(2) ?: 0)
            (secTitle(b, r, s) ?: "Page ${s + 1}") to rowTitle(b, r)
        } else {
            val kind = p.getOrNull(1)
            val b = p.getOrNull(2)?.toIntOrNull() ?: 0
            val i = p.getOrNull(3)?.toIntOrNull() ?: 0
            val s = p.getOrNull(4)?.toIntOrNull() ?: -1
            when (kind) {
                "sub" -> (if (s < 0) "Section-wide MCQs" else secTitle(b, i, s) ?: "Subsection ${s + 1}") to rowTitle(b, i)
                "row" -> rowTitle(b, i) to "All MCQs of this section"
                "unit" -> (app.repo.index.getOrNull(b - 1)?.units?.getOrNull(i)?.title ?: "Unit") to "Unit MCQs"
                "day" -> "Day $i MCQs" to "Study plan"
                "need" -> "MCQs to practise" to "All subjects"
                else -> "MCQ practice" to null
            }
        }
    }
    val notes = spent.filterKeys { it.startsWith("n:") }.entries.sortedByDescending { it.value.sec }
    val mcqs = spent.filterKeys { it.startsWith("q:") }.entries.sortedByDescending { it.value.sec }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        if (title != null) Text(title, style = TextStyle(fontSize = 13.sp, color = C.Muted), modifier = Modifier.padding(bottom = 4.dp))
        @Composable
        fun group(name: String, items: List<Map.Entry<String, com.appsc.prep.data.Spent>>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
            if (items.isEmpty()) return
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                Icon(icon, null, tint = C.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(name, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent))
            }
            items.forEach { (k, v) ->
                val (main, under) = label(k)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(main, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = C.Ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (under != null) Text(under, style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        studyTime(v.sec) + if (v.mcqs > 0) " · ${mcqCount(v.mcqs)}" else "",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                    )
                }
            }
        }
        group("Notes", notes, Icons.Outlined.AutoStories)
        group("MCQs", mcqs, Icons.Outlined.Quiz)
    }
}

@Composable
private fun ActivityRow(
    day: String, mcqs: String, notes: String, mcqTime: String, total: String, header: Boolean = false,
    open: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val style = TextStyle(fontSize = 13.sp, fontWeight = if (header || open) FontWeight.SemiBold else FontWeight.Normal, color = if (header) C.Muted else C.Ink)
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
            Text(day, style = style)
            if (onClick != null) Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Hide" else "Show what the time went on", tint = C.Muted, modifier = Modifier.size(18.dp))
        }
        for (v in listOf(mcqs, notes, mcqTime, total)) {
            Text(v, style = style, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
    if (header) HorizontalDivider(color = C.Line)
}

@Composable
fun SavedScreen(nav: Nav) {
    val app = LocalApp.current
    val saved = app.store.saved
    Column(Modifier.fillMaxSize()) {
        TopBar("Saved")
        if (saved.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Bookmark, null, tint = C.Line, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("No bookmarks yet", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                Text(
                    "Use the bookmark icon while reading to save a subsection here.",
                    style = TextStyle(fontSize = 14.sp, color = C.Muted),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            return@Column
        }
        PageList(Modifier.fillMaxSize()) {
            items(saved, key = { it.id }) { s ->
                val p = s.id.split(':').map { it.toInt() }
                Row(
                    Modifier.fillMaxWidth().clickable { nav.read(p[0], p[1], p[2]) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = C.Ink))
                        Text(
                            "${app.repo.index[p[0] - 1].short} · ${s.rowTitle}",
                            style = TextStyle(fontSize = 12.sp, color = C.Muted),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
                }
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

/** Save everything kept on this device to a file, and bring it back on a new phone or after reinstalling. */
@Composable
private fun BackupCard() {
    val app = LocalApp.current
    val store = app.store
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, is a problem
    val save = app.platform.rememberSaveFile { ok ->
        if (ok) store.backedUp()
        message = if (ok) "Backup saved. Keep the file somewhere safe (Drive, WhatsApp to yourself, email)." to false
        else "Backup not saved." to true
    }
    val open = app.platform.rememberOpenFile { text ->
        message = when (text) {
            null -> "No file opened." to true
            else -> runCatching { store.restore(text, app.repo.idMoves, app.repo.noteMoves) }.fold(
                { r -> "Restored: ${r.read} pages ${app.doneWord}, ${r.saved} bookmarks, ${r.answers} MCQ answers, ${r.notes} own notes." to false },
                { (it.message ?: "Could not read this file.") to true },
            )
        }
    }
    if (save == null || open == null) return
    Column(Modifier.padding(20.dp)) {
        Text("Backup", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
        Spacer(Modifier.height(6.dp))
        Text(
            "Your read marks, bookmarks, MCQ answers and own notes are kept only on this device. Save a backup file " +
                "so you never lose them; restore it after reinstalling or on a new phone. Restoring adds to what is here - nothing is deleted.",
            style = TextStyle(fontSize = 14.sp, color = C.Body),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Last backup: " + (store.lastBackup?.let { java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")) } ?: "never"),
            style = TextStyle(fontSize = 13.sp, color = if (store.lastBackup == null) C.High else C.Muted),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(
                onClick = { save("${app.repo.appName.replace(' ', '-')}-backup-${java.time.LocalDate.now()}.json", store.backup()) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = C.Green),
                modifier = Modifier.weight(1f),
            ) { Text("Back up now") }
            androidx.compose.material3.OutlinedButton(onClick = { open() }, modifier = Modifier.weight(1f)) { Text("Restore") }
        }
        message?.let { (text, bad) ->
            Spacer(Modifier.height(10.dp))
            Text(text, style = TextStyle(fontSize = 14.sp, color = if (bad) C.High else C.Green))
        }
    }
}
