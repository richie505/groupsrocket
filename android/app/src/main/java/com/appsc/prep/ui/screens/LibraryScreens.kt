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
import com.appsc.prep.ui.components.columnsFor
import com.appsc.prep.ui.components.gridItems
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
            else -> runCatching { store.restore(text, app.repo.idMoves) }.fold(
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
