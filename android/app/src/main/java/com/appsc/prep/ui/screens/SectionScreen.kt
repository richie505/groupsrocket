package com.appsc.prep.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.prep.data.Book
import com.appsc.prep.data.subsectionId
import com.appsc.prep.ui.components.DoneIcon
import com.appsc.prep.ui.components.PageList
import com.appsc.prep.ui.components.Loading
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.components.PracticeCard
import com.appsc.prep.ui.components.practiceSets
import com.appsc.prep.ui.components.PriorityTag
import com.appsc.prep.ui.components.ProgressLine
import com.appsc.prep.ui.components.SectionHeader
import com.appsc.prep.ui.components.Tag
import com.appsc.prep.ui.components.TopBar
import com.appsc.prep.ui.components.annotated
import com.appsc.prep.ui.theme.C

@Composable
fun rememberBook(id: Int): Book? {
    val app = LocalApp.current
    val book by produceState(app.repo.cachedBook(id), id) { value = app.repo.book(id) }
    return book
}

/** A syllabus row: its subsections (■ headings) with read state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionScreen(bookId: Int, rowIndex: Int, nav: Nav) {
    val app = LocalApp.current
    val info = app.repo.rowInfo(bookId, rowIndex)
    val book = rememberBook(bookId)
    val mcq by produceState(app.repo.cachedMcq(bookId), bookId) { value = app.repo.mcq(bookId) }
    Column(Modifier.fillMaxSize()) {
        TopBar(app.repo.index.getOrNull(bookId - 1)?.short ?: "Section", onBack = nav::back)
        if (book == null) {
            Loading()
            return@Column
        }
        val row = book.rows[rowIndex]
        val unit = book.units.getOrNull(row.unitIndex)
        val planRow = app.repo.planRowByRef[bookId to rowIndex]
        val done = app.store.doneCount(bookId, rowIndex, row.secs.size)
        val firstUnread = row.secs.indices.firstOrNull { !app.store.isDone(subsectionId(bookId, rowIndex, it)) } ?: 0
        PageList(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    if (unit != null) {
                        Text(
                            listOf(unit.code, unit.title).filter { it.isNotBlank() }.joinToString(" · "),
                            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = C.Accent, fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        row.title,
                        style = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PriorityTag(planRow?.priority ?: "")
                        row.codes.forEach { Tag(it) }
                        if (row.tag.isNotBlank()) Tag(row.tag, C.AccentSoft, C.Accent)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        listOfNotNull(
                            "ROCKET Sheets PDF · pp ${row.p1}-${row.p2}",
                            row.pyq.takeIf { it.isNotBlank() }?.let { "Asked in past papers: $it" },
                        ).joinToString("   ·   "),
                        style = TextStyle(fontSize = 13.sp, color = C.Muted),
                    )
                    row.sub.forEach {
                        Text(it, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = C.Muted), modifier = Modifier.padding(top = 4.dp))
                    }
                    row.see.forEach {
                        Text(
                            annotated(it, base = C.SeeInk, strong = C.SeeInk),
                            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(if (row.secs.isEmpty()) 0f else done / row.secs.size.toFloat(), Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text("$done / ${row.secs.size} ${LocalApp.current.doneWord}", style = TextStyle(fontSize = 12.sp, color = C.Muted))
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { nav.read(bookId, rowIndex, firstUnread) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Accent),
                    ) {
                        Text(
                            when {
                                done == 0 -> "Start reading"
                                done >= row.secs.size -> "Read again"
                                else -> "Continue reading"
                            },
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                    val qCount = info?.questionCount ?: 0
                    if (qCount > 0) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { nav.quiz("row", bookId, rowIndex) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Quiz, null, tint = C.ExamInk, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Practice section MCQs ($qCount)",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.ExamInk),
                            )
                        }
                    }
                }
                HorizontalDivider(color = C.Line)
                SectionHeader("Subsections (${row.secs.size})")
            }
            itemsIndexed(row.secs) { i, s ->
                val id = subsectionId(bookId, rowIndex, i)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.read(bookId, rowIndex, i) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}.",
                        style = TextStyle(fontSize = 15.sp, color = C.Muted),
                        modifier = Modifier.width(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title,
                            style = TextStyle(
                                fontSize = 15.sp, lineHeight = 21.sp, color = C.Ink,
                                fontWeight = if (s.universal) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                        val subQ = mcq?.subCount(rowIndex, i) ?: 0
                        if (s.badges.isNotEmpty() || subQ > 0) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                s.badges.forEach { BadgeTag(it) }
                                if (subQ > 0) {
                                    Tag(
                                        "Practice $subQ MCQs",
                                        C.ExamBg, C.ExamInk,
                                        modifier = Modifier.clickable { nav.quiz("sub", bookId, rowIndex, sub = i) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    DoneIcon(app.store.isDone(id))
                }
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 50.dp))
            }
            val otherQ = mcq?.subCount(rowIndex, -1) ?: 0
            if (otherQ > 0) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.quiz("sub", bookId, rowIndex, sub = -1) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Quiz, null, tint = C.ExamInk, modifier = Modifier.width(30.dp).size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Other MCQs of this section",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink),
                            )
                            Text(
                                "Filed under this section but not matched to one subsection",
                                style = TextStyle(fontSize = 12.sp, color = C.Muted),
                            )
                        }
                        Tag("Practice $otherQ MCQs", C.ExamBg, C.ExamInk)
                    }
                    HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 50.dp))
                }
            }
            if ((info?.questionCount ?: 0) > 0) {
                item {
                    SectionHeader("Practice after reading")
                    val ids = mcq?.rows?.get(rowIndex)?.map { it.id }
                    val stats = ids?.let { app.store.quizStats(it) }
                    PracticeCard(
                        title = "All section MCQs",
                        subtitle = "${info!!.questionCount} questions · all in one go",
                        attempted = stats?.let { Triple(it.first, it.second, ids.size) },
                        onStart = { nav.quiz("row", bookId, rowIndex) },
                        onWrong = { nav.quiz("row", bookId, rowIndex, "wrong") },
                        sets = ids?.let { practiceSets(it, app.store.answers, app.store.seen) },
                        onSet = { mode -> nav.quiz("row", bookId, rowIndex, mode) },
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun BadgeTag(badge: String) = when (badge) {
    "APPSC" -> Tag("APPSC", C.ExamBg, C.ExamInk)
    "GROUP-II" -> Tag("Group-II", C.GreenSoft, C.Green)
    else -> Tag(badge.replaceFirstChar { it.uppercase() })
}
