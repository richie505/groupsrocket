package com.appsc.prep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.prep.data.SpeechText
import com.appsc.prep.data.Book
import com.appsc.prep.data.Saved
import com.appsc.prep.data.TableBlock
import com.appsc.prep.data.TextBlock
import com.appsc.prep.data.subsectionId
import com.appsc.prep.ui.components.BlockView
import com.appsc.prep.ui.components.Loading
import com.appsc.prep.ui.components.Playback
import com.appsc.prep.ui.components.SpeechPage
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.components.TopBar
import com.appsc.prep.ui.theme.C
import kotlinx.coroutines.launch

/** Position of a subsection within a book: (row, sec). */
private data class Pos(val row: Int, val sec: Int)

private fun Book.next(p: Pos): Pos? = when {
    p.sec + 1 < rows[p.row].secs.size -> Pos(p.row, p.sec + 1)
    else -> (p.row + 1 until rows.size).firstOrNull { rows[it].secs.isNotEmpty() }?.let { Pos(it, 0) }
}

private fun Book.prev(p: Pos): Pos? = when {
    p.sec > 0 -> Pos(p.row, p.sec - 1)
    else -> (p.row - 1 downTo 0).firstOrNull { rows[it].secs.isNotEmpty() }?.let { Pos(it, rows[it].secs.size - 1) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(bookId: Int, rowIndex: Int, secIndex: Int, nav: Nav) {
    val app = LocalApp.current
    val store = app.store
    val book = rememberBook(bookId)
    val mcq by androidx.compose.runtime.produceState(app.repo.cachedMcq(bookId), bookId) { value = app.repo.mcq(bookId) }
    var rowI by rememberSaveable { mutableStateOf(rowIndex) }
    var secI by rememberSaveable { mutableStateOf(secIndex) }
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    if (book == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("", onBack = nav::back)
            Loading()
        }
        return
    }
    val row = book.rows[rowI]
    val sec = row.secs[secI.coerceIn(0, row.secs.size - 1)]
    val id = subsectionId(bookId, rowI, secI)
    val pos = Pos(rowI, secI)
    val next = book.next(pos)
    val prev = book.prev(pos)
    val scale = store.textScale

    LaunchedEffect(id) {
        store.rememberPosition(id)
        listState.scrollToItem(0)
    }
    app.platform.BackHandler(enabled = tocOpen) { tocOpen = false }

    fun go(p: Pos) {
        rowI = p.row
        secI = p.sec
    }

    // ---- read aloud (one session for the app: it carries on with the screen locked) ----
    val speech = app.platform.speech
    val pb = speech?.playback?.value ?: Playback()
    val listening = pb.active
    val here = pb.pageId == id
    val part = if (here) pb.part else 0
    val parts = remember(id) {
        app.repo.abbreviations // short forms the notes define
        SpeechText.parts(sec.title, sec.blocks, bookId)
    }

    fun page(p: Pos): SpeechPage {
        val s = book.rows[p.row].secs[p.sec]
        return SpeechPage(subsectionId(bookId, p.row, p.sec), s.title, SpeechText.parts(s.title, s.blocks, bookId).map { it.second })
    }

    fun play(from: Int) {
        val s = speech ?: return
        var cursor = pos
        s.play(SpeechPage(id, sec.title, parts.map { it.second }), from, store.speechRate) {
            // then on through the book, page by page
            book.next(cursor)?.let { cursor = it; page(it) }
        }
    }

    fun stopAndBack() {
        speech?.stop()
        nav.back()
    }

    // leaving the reader ends read-aloud (locking the screen or switching apps does not)
    app.platform.BackHandler(enabled = listening && !tocOpen) { stopAndBack() }
    LaunchedEffect(id) {
        // opened another page by hand while reading: read that one
        val now = speech?.playback?.value ?: return@LaunchedEffect
        if (now.playing && now.pageId != id) play(0)
    }
    // reading moved on from the page on screen (end of page, or several pages while the phone was locked):
    // follow it. A page opened by hand is not followed away from - it is read instead (above).
    var lastSpoken by remember { mutableStateOf(pb.pageId) }
    LaunchedEffect(pb.pageId) {
        val was = lastSpoken
        lastSpoken = pb.pageId
        if (!pb.active || was != id || pb.pageId == id) return@LaunchedEffect
        val (b, r, s) = pb.pageId.split(':').map { it.toIntOrNull() ?: return@LaunchedEffect }
        if (b == bookId) go(Pos(r, s))
    }
    LaunchedEffect(part, here) {
        if (!here) return@LaunchedEffect
        // keep the paragraph being read on screen (item 0 is the heading)
        val item = (parts.getOrNull(part)?.first ?: -1) + 1
        val info = listState.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.index == item }
        if (seen == null || seen.offset < 0 || seen.offset + seen.size > info.viewportEndOffset - 220) {
            listState.animateScrollToItem(item)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(sec.title, onBack = ::stopAndBack) {
                val saved = store.isSaved(id)
                IconButton(onClick = { store.toggleSaved(Saved(id, sec.title, row.title)) }) {
                    Icon(
                        if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        "Bookmark", tint = if (saved) C.Accent else C.Ink,
                    )
                }
                IconButton(onClick = { store.toggleDone(id) }) {
                    val done = store.isDone(id)
                    Icon(
                        if (done) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        "Mark as read", tint = if (done) C.Green else C.Ink,
                    )
                }
                if (speech != null) {
                    IconButton(onClick = {
                        when {
                            !here -> play(0)
                            !pb.playing -> speech.resume()
                        }
                    }) { Icon(Icons.Outlined.Headphones, "Listen", tint = if (listening) C.Accent else C.Ink) }
                }
                Box {
                    IconButton(onClick = { sizeMenu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = C.Ink) }
                    DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Larger text") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                            onClick = { store.changeTextScale(0.1f) },
                        )
                        DropdownMenuItem(
                            text = { Text("Smaller text") },
                            leadingIcon = { Icon(Icons.Filled.Remove, null) },
                            onClick = { store.changeTextScale(-0.1f) },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = {
                                sizeMenu = false
                                app.platform.share(sec.title, plainText(sec.title, sec.blocks))
                            },
                        )
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item(key = "head-$id") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                        Text(
                            row.title,
                            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = C.Accent, fontWeight = FontWeight.SemiBold),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            sec.title,
                            style = TextStyle(fontSize = (24 * scale).sp, lineHeight = (31 * scale).sp, fontWeight = FontWeight.Bold, color = Color.Black),
                        )
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = C.Line)
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(
                                "${book.short} · Page ${sec.page}",
                                style = TextStyle(fontSize = 14.sp, color = C.Faint),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(sec.wordCount / 180).coerceAtLeast(1)} min read",
                                style = TextStyle(fontSize = 14.sp, color = C.Faint),
                            )
                        }
                        HorizontalDivider(color = C.Line)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                itemsIndexed(sec.blocks, key = { i, _ -> "$id-$i" }) { i, b ->
                    val reading = here && parts.getOrNull(part)?.first == i
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (reading) C.AccentSoft else Color.Transparent)
                            .then(
                                if (listening) {
                                    Modifier.clickable {
                                        val at = parts.indexOfFirst { it.first == i }
                                        if (at < 0) return@clickable
                                        if (here) {
                                            speech?.seek(at)
                                            speech?.resume()
                                        } else play(at)
                                    }
                                } else Modifier,
                            )
                            .padding(horizontal = 8.dp),
                    ) { BlockView(b, scale) }
                }
                item(key = "foot-$id") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        if (sec.badges.isNotEmpty() || row.codes.isNotEmpty()) {
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Tags:", style = TextStyle(fontSize = 15.sp, color = C.Navy), modifier = Modifier.padding(top = 3.dp))
                                sec.badges.forEach { BadgeTag(it) }
                                row.codes.forEach { com.appsc.prep.ui.components.Tag(it) }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        val done = store.isDone(id)
                        Button(
                            onClick = {
                                store.setDone(id, !done)
                                if (!done && next != null) go(next)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (done) C.GreenSoft else C.Accent, contentColor = if (done) C.Green else Color.White),
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (done) "Read ✓  (tap to undo)" else if (next != null) "Mark as read & next" else "Mark as read",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                        val subQ = mcq?.subCount(rowI, secI) ?: 0
                        if (subQ > 0) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { nav.quiz("sub", bookId, rowI, sub = secI) },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    "Practice $subQ MCQs on this subsection",
                                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.ExamInk),
                                )
                            }
                        }
                        val qCount = app.repo.rowInfo(bookId, rowI)?.questionCount ?: 0
                        if (secI == row.secs.size - 1 && qCount > 0) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { nav.quiz("row", bookId, rowI) },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    "Practice all $qCount MCQs of this section",
                                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.ExamInk),
                                )
                            }
                        }
                        if (row.sources.isNotEmpty() && secI == row.secs.size - 1) {
                            Spacer(Modifier.height(16.dp))
                            SourcesBox(row.sources)
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable(enabled = prev != null) { prev?.let { go(it) } }
                                    .padding(vertical = 6.dp),
                            ) {
                                if (prev != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = C.Navy)
                                        Text("Previous", style = TextStyle(fontSize = 16.sp, color = C.Navy))
                                    }
                                    Text(
                                        book.rows[prev.row].secs[prev.sec].title,
                                        style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = C.Faint),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 24.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable(enabled = next != null) { next?.let { go(it) } }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                if (next != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Next", style = TextStyle(fontSize = 16.sp, color = C.Navy))
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Navy)
                                    }
                                    Text(
                                        book.rows[next.row].secs[next.sec].title,
                                        style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = C.Faint),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 24.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(90.dp))
                    }
                }
            }
        }

        // floating table-of-contents button
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = if (listening) 96.dp else 22.dp)
                .size(58.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(C.FabSoft)
                .clickable { tocOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuOpen, "Table of content", tint = Color.White, modifier = Modifier.size(30.dp))
        }

        if (listening && speech != null) {
            PlayerBar(
                playing = pb.playing && here,
                position = "${part + 1} / ${parts.size}",
                rate = store.speechRate,
                onPlayPause = {
                    when {
                        !here -> play(0)
                        pb.playing -> speech.pause()
                        else -> speech.resume()
                    }
                },
                onPrev = { if (here) speech.seek(part - 1) else play(0) },
                onNext = { if (here && part < parts.lastIndex) speech.seek(part + 1) else next?.let { go(it) } },
                onRate = {
                    val rates = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                    val r = rates[(rates.indexOf(store.speechRate) + 1) % rates.size]
                    store.changeSpeechRate(r)
                    speech.setRate(r)
                },
                onClose = { speech.stop() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // table-of-contents drawer (subsections of this section)
        AnimatedVisibility(tocOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tocOpen = false },
            )
        }
        AnimatedVisibility(
            tocOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.78f)
                    .background(Color.White)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Table of content", style = TextStyle(fontSize = 20.sp, color = C.Ink), modifier = Modifier.weight(1f))
                    IconButton(onClick = { tocOpen = false }) { Icon(Icons.Filled.Close, "Close", tint = C.Ink) }
                }
                Text(
                    row.title,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = C.Muted),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider(color = C.Ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
                val tocState = rememberLazyListState(initialFirstVisibleItemIndex = (secI - 2).coerceAtLeast(0))
                LazyColumn(state = tocState) {
                    itemsIndexed(row.secs) { i, s ->
                        val current = i == secI
                        val read = store.isDone(subsectionId(bookId, rowI, i))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    go(Pos(rowI, i))
                                    tocOpen = false
                                    scope.launch { listState.scrollToItem(0) }
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Text(
                                "${i + 1}.  ${s.title}",
                                style = TextStyle(
                                    fontSize = 16.sp, lineHeight = 23.sp,
                                    color = if (current) C.Blue else C.Ink,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (read) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.CheckCircle, null, tint = C.Green, modifier = Modifier.size(18.dp).padding(top = 3.dp))
                            }
                        }
                        if (i < row.secs.size - 1) HorizontalDivider(color = C.Ink.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesBox(sources: List<String>) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, C.Line, RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sources", style = TextStyle(fontSize = 17.sp, color = C.Navy), modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.Remove else Icons.Filled.Add, null, tint = C.Navy, modifier = Modifier.size(26.dp))
        }
        if (open) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                sources.forEach {
                    Text(it, style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = C.Muted), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

private fun plainText(title: String, blocks: List<com.appsc.prep.data.Block>): String = buildString {
    appendLine(title)
    appendLine()
    for (b in blocks) {
        when (b) {
            is TextBlock -> {
                val t = b.runs.joinToString("") { it.text }
                when (b.kind) {
                    'b' -> appendLine("• $t")
                    's' -> appendLine("   – $t")
                    else -> appendLine(t)
                }
            }
            is TableBlock -> {
                (listOf(b.head) + b.rows).forEach { r ->
                    val line = r.joinToString(" | ") { c -> c.joinToString("") { it.text } }
                    if (line.isNotBlank()) appendLine(line)
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(
    playing: Boolean,
    position: String,
    rate: Float,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "Previous paragraph", tint = C.Ink) }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Accent).clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next paragraph", tint = C.Ink) }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(C.AccentSoft).clickable(onClick = onRate).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("${if (rate % 1f == 0f) rate.toInt() else rate}×", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Accent))
        }
        Text(
            position,
            style = TextStyle(fontSize = 13.sp, color = C.Muted),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Stop listening", tint = C.Muted) }
    }
}
