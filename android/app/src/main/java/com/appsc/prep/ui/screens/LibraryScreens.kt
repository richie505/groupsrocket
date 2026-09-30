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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.components.ProgressLine
import com.appsc.prep.ui.components.SectionHeader
import com.appsc.prep.ui.components.SectionItem
import com.appsc.prep.ui.components.StatBox
import com.appsc.prep.ui.components.Tag
import com.appsc.prep.ui.components.TopBar
import com.appsc.prep.ui.theme.C

private val bookColors = listOf(
    Color(0xFF6D4C41), Color(0xFF3949AB), Color(0xFF00897B),
    Color(0xFF2E7D32), Color(0xFF8E24AA), Color(0xFFD84315),
)

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
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionHeader("ROCKET Sheets · 14 subjects · 721 sheets") }
            items(app.repo.index) { b ->
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
        LazyColumn(Modifier.fillMaxSize()) {
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
                                    "${rows.size} sections · $done/$total read",
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
                                        r.questionCount.takeIf { it > 0 }?.let { "$it MCQs" },
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
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(20.dp)) {
                    Text("Overall", style = TextStyle(fontSize = 13.sp, color = C.Muted))
                    Text(
                        "${if (total == 0) 0 else done * 100 / total}% of notes read",
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink),
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(if (total == 0) 0f else done / total.toFloat())
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("$done", "subsections read", Icons.Outlined.TaskAlt, Modifier.weight(1f))
                        StatBox("$daysDone / 90", "days completed", Icons.Outlined.CalendarMonth, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("${store.streak()}", "day streak", Icons.Outlined.LocalFireDepartment, Modifier.weight(1f))
                        StatBox("${store.saved.size}", "bookmarks", Icons.Outlined.AutoStories, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    // Answers saved before 2.8 are for PYQs (now in the MCQ app); notes-MCQ ids start with "n".
                    val mine = store.answers.filterKeys { it.startsWith("n") }
                    val answered = mine.size
                    val right = mine.count { it.value }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("$answered", "MCQs answered", Icons.Outlined.Quiz, Modifier.weight(1f))
                        StatBox(
                            if (answered == 0) "–" else "${right * 100 / answered}%",
                            "MCQ accuracy", Icons.Outlined.TaskAlt, Modifier.weight(1f),
                        )
                    }
                }
                HorizontalDivider(color = C.Line)
                SectionHeader("By subject")
            }
            items(books) { b ->
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
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
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
        LazyColumn(Modifier.fillMaxSize()) {
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
