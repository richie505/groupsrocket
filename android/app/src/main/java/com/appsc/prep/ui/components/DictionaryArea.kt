package com.appsc.prep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.appsc.prep.data.Dictionary
import com.appsc.prep.data.Repository
import com.appsc.prep.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The selection toolbar's state: where the selection is and how to copy it. */
private class SelectionMenu : TextToolbar {
    var rect by mutableStateOf<Rect?>(null)
    var copy: (() -> Unit)? = null
    var selectAll: (() -> Unit)? = null

    override val status get() = if (rect != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        copy = onCopyRequested
        selectAll = onSelectAllRequested
        this.rect = rect
    }

    override fun hide() {
        rect = null
    }
}

/**
 * Makes the text inside selectable, with a "Meaning" button next to Copy: it shows the dictionary meaning of
 * the selected word or phrase, the full form of a short form, and the notes pages whose heading mentions it.
 */
@Composable
fun DictionaryArea(onOpenNotes: (book: Int, row: Int, sec: Int) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val menu = remember { SelectionMenu() }
    val clipboard = LocalClipboardManager.current
    var word by remember { mutableStateOf<String?>(null) }

    Box(modifier) {
        CompositionLocalProvider(LocalTextToolbar provides menu) {
            SelectionContainer { content() }
        }
        menu.rect?.let { rect ->
            Popup(popupPositionProvider = Above(rect)) {
                Row(
                    Modifier.shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(C.Navy),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuItem("Meaning", bold = true) {
                        word = selectedText(clipboard, menu)
                        menu.hide()
                    }
                    MenuItem("Copy") {
                        menu.copy?.invoke()
                        menu.hide()
                    }
                    if (menu.selectAll != null) MenuItem("Select all") { menu.selectAll?.invoke() }
                }
            }
        }
        word?.let { MeaningCard(it, onOpenNotes = { b, r, s -> word = null; onOpenNotes(b, r, s) }) { word = null } }
    }
}

/** Copies the selection to read it, then puts the user's clipboard back. */
private fun selectedText(clipboard: ClipboardManager, menu: SelectionMenu): String? {
    val before = runCatching { clipboard.getText() }.getOrNull()
    menu.copy?.invoke() ?: return null
    val text = runCatching { clipboard.getText()?.text }.getOrNull()
    runCatching { clipboard.setText(before ?: AnnotatedString("")) }
    return text?.takeIf { it.isNotBlank() }
}

@Composable
private fun Heading(label: String, color: Color) {
    Spacer(Modifier.height(14.dp))
    Text(label, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.8.sp))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun MenuItem(label: String, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = Color.White),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Puts the menu just above the selection (below it when there is no room). */
private class Above(private val rect: Rect) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (rect.center.x - popupContentSize.width / 2).toInt().coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        val gap = 24
        val above = rect.top.toInt() - popupContentSize.height - gap
        val y = if (above > 8) above else (rect.bottom.toInt() + gap).coerceAtMost(windowSize.height - popupContentSize.height - 8)
        return IntOffset(x, y)
    }
}

/** The Meaning card for [text] (a key term tapped on a notes page, or a selection). */
@Composable
fun MeaningSheet(text: String, onOpenNotes: (book: Int, row: Int, sec: Int) -> Unit, onClose: () -> Unit) =
    MeaningCard(text, onOpenNotes, onClose)

@Composable
private fun MeaningCard(text: String, onOpenNotes: (Int, Int, Int) -> Unit, onClose: () -> Unit) {
    val repo = LocalApp.current.repo
    var entry by remember(text) { mutableStateOf<Dictionary.Entry?>(null) }
    var looked by remember(text) { mutableStateOf(false) }
    var hits by remember(text) { mutableStateOf<List<Repository.NoteHit>?>(null) }
    // what the notes say about it (s.144, 84th Amendment, any word or phrase)
    var about by remember(text) { mutableStateOf<Pair<String, List<Dictionary.NoteDefinition>>?>(null) }
    var searched by remember(text) { mutableStateOf(false) }
    var google by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        entry = withContext(Dispatchers.IO) { repo.dictionary.lookup(text) }
        looked = true
        about = repo.notesAbout(text)
        searched = true
        hits = repo.findInNotes(text.trim())
    }

    // above everything on screen (buttons, player bar), without darkening the notes behind it;
    // a tap outside or back closes it. Hidden while its Google page is open (closing Google brings it back).
    if (!google) Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true),
    ) {
    Box {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .shadow(16.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .border(1.dp, C.Line, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MEANING", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Accent, letterSpacing = 0.8.sp), modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = C.Muted) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(end = 12.dp)) {
                val e = entry
                val general = e?.senses?.isNotEmpty() == true || e?.india != null
                Text(
                    if (general) e!!.word.replaceFirstChar { it.uppercase() } else about?.first ?: text.trim(),
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Navy),
                )
                if (e?.shortForm != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Short for", style = TextStyle(fontSize = 12.sp, color = C.Muted))
                    Text(e.shortForm, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                }
                // the notes' definitions, then what the notes say about it
                val seen = HashSet<String>()
                val fromNotes = (e?.notes.orEmpty() + about?.second.orEmpty()).filter { seen.add(it.text.lowercase()) }.take(5)
                when {
                    !looked -> Text("Looking up…", style = TextStyle(fontSize = 15.sp, color = C.Muted), modifier = Modifier.padding(top = 8.dp))
                    e == null && searched && fromNotes.isEmpty() -> Text(
                        "Not found in the dictionary or in your notes.",
                        style = TextStyle(fontSize = 15.sp, color = C.Muted), modifier = Modifier.padding(top = 8.dp),
                    )
                    else -> {
                        e?.india?.let {
                            Heading("IN INDIAN CONTEXT", C.ExamInk)
                            Text(it, style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = C.Body))
                        }
                        if (fromNotes.isNotEmpty() || !searched) {
                            Heading("FROM YOUR NOTES", C.Green)
                            if (!searched) Text("Searching your notes…", style = TextStyle(fontSize = 14.sp, color = C.Muted))
                            fromNotes.forEach { d ->
                                Text(d.text.replaceFirstChar { it.uppercase() }, style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = C.Body), modifier = Modifier.padding(top = 2.dp))
                                Text(d.where, style = TextStyle(fontSize = 12.sp, color = C.Muted), maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                        if (e != null && e.senses.isNotEmpty()) {
                            // with an Indian meaning above, two general senses are enough
                            val shown = if (e.india != null || fromNotes.isNotEmpty()) e.senses.take(2) else e.senses
                            Heading("DICTIONARY", C.Accent)
                            shown.forEachIndexed { i, s ->
                                if (i > 0) Spacer(Modifier.height(8.dp))
                                Row {
                                    Text("${i + 1}.", style = TextStyle(fontSize = 15.sp, color = C.Muted), modifier = Modifier.width(22.dp))
                                    Column {
                                        Text(s.pos, style = TextStyle(fontSize = 12.sp, fontStyle = FontStyle.Italic, color = C.Accent))
                                        Text(s.definition.replaceFirstChar { it.uppercase() }, style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = C.Body))
                                        if (s.example.isNotBlank()) {
                                            Text("“${s.example}”", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, color = C.Muted), modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Search on Google",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Accent),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp))
                        .clickable { google = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
                val h = hits
                if (!h.isNullOrEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = C.Line)
                    Spacer(Modifier.height(10.dp))
                    Text("IN YOUR NOTES", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.ExamInk, letterSpacing = 0.8.sp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        h.forEach { hit ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenNotes(hit.book, hit.row, hit.sec) }.padding(vertical = 8.dp),
                            ) {
                                Text(hit.title, style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = C.Accent, fontWeight = FontWeight.Medium), maxLines = 2)
                                Text(hit.where, style = TextStyle(fontSize = 12.sp, color = C.Muted), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
    if (google) GooglePage(about?.first ?: text.trim()) { google = false }
}

/** Google search with India settings (gl=in) in English. */
fun googleUrl(query: String) = "https://www.google.com/search?hl=en&gl=in&q=" + java.net.URLEncoder.encode(query, "UTF-8")

/** Google's AI Mode answering [prompt] (free, no sign-in): "Explain simply" on a notes page. */
fun googleAiUrl(prompt: String) = "https://www.google.com/search?udm=50&hl=en&gl=in&q=" + java.net.URLEncoder.encode(prompt, "UTF-8")

/**
 * Google results for [query] inside the app (India settings), full screen; back goes back a page, then closes.
 * With [onAdd]: a button that takes the text copied from the results (select it, Copy) to the notes.
 */
@Composable
fun GooglePage(
    query: String,
    onAdd: ((String) -> Unit)? = null,
    title: String = "Google · $query",
    url: String = googleUrl(query),
    gemini: String = query,
    onClose: () -> Unit,
) {
    val platform = LocalApp.current.platform
    val back = remember { mutableStateOf<(() -> Boolean)?>(null) }
    var told by remember { mutableStateOf<String?>(null) } // a message after "Ask Gemini"
    Popup(
        onDismissRequest = { if (back.value?.invoke() != true) onClose() },
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().background(Color.White)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = C.Ink) }
                Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Ink), maxLines = 1, modifier = Modifier.weight(1f))
                // the same question to Gemini, or this page in Chrome (signed in to Google)
                IconButton(onClick = { told = platform.askGemini(gemini) }) {
                    Icon(Icons.Outlined.AutoAwesome, "Ask Gemini", tint = C.Accent)
                }
                IconButton(onClick = { platform.openInBrowser(url) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in Chrome", tint = C.Ink)
                }
            }
            told?.let {
                Text(it, style = TextStyle(fontSize = 13.sp, color = C.Green), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Text(
                "Signed-in Google works best in Chrome: tap ↗. Ask Gemini: ✦.",
                style = TextStyle(fontSize = 12.sp, color = C.Muted),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            HorizontalDivider(color = C.Line)
            val selected = remember { mutableStateOf<(((WebText) -> Unit) -> Unit)?>(null) }
            platform.WebPage(url, Modifier.fillMaxWidth().weight(1f), back, if (onAdd != null) selected else null)
            if (onAdd != null) {
                @Suppress("DEPRECATION")
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                var empty by remember { mutableStateOf(false) }
                var picking by remember { mutableStateOf<Pair<List<String>, Boolean>?>(null) } // paragraphs, first one selected
                // what is selected (or copied) first, then the page's paragraphs to tick
                fun pick(page: WebText) {
                    val chosen = page.selection.ifBlank { clipboard.getText()?.text.orEmpty() }.trim()
                    val all = (listOfNotNull(chosen.takeIf { it.isNotBlank() }) + page.paragraphs).distinct()
                    if (all.isEmpty()) empty = true else { empty = false; picking = all to chosen.isNotBlank() }
                }
                picking?.let { (all, first) ->
                    PickText(all, preselect = if (first) 0 else -1, onAdd = { picking = null; onAdd(it) }) { picking = null }
                }
                HorizontalDivider(color = C.Line)
                Text(
                    if (empty) "Nothing to add yet - wait for the answer to load, then tap again."
                    else "When the answer has loaded, tap below and tick the useful parts:",
                    style = TextStyle(fontSize = 13.sp, color = if (empty) C.High else C.Muted),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                androidx.compose.material3.Button(
                    onClick = { selected.value?.invoke { pick(it) } ?: pick(WebText("", emptyList())) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = C.Green),
                ) { Text("Add text from this page to my notes", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
            }
        }
    }
}

/** The page's paragraphs with a tick box each; [preselect] (the selected or copied text) starts ticked. */
@Composable
fun PickText(paragraphs: List<String>, preselect: Int, onAdd: (String) -> Unit, onClose: () -> Unit) {
    val ticked = remember(paragraphs) { androidx.compose.runtime.mutableStateListOf<Int>().apply { if (preselect >= 0) add(preselect) } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    "Tick what to add to your notes",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(paragraphs.size) { i ->
                        val on = i in ticked
                        Row(
                            Modifier.fillMaxWidth().clickable { if (on) ticked.remove(i) else ticked.add(i) }.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { if (on) ticked.remove(i) else ticked.add(i) })
                            Text(paragraphs[i], style = TextStyle(fontSize = 14.sp, color = C.Body), modifier = Modifier.padding(top = 12.dp, end = 8.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = onClose) { Text("Cancel") }
                    androidx.compose.material3.TextButton(
                        enabled = ticked.isNotEmpty(),
                        onClick = { onAdd(ticked.sorted().joinToString("\n") { paragraphs[it] }) },
                    ) { Text(if (ticked.isEmpty()) "Add" else "Add (${ticked.size})") }
                }
            }
        }
    }
}
