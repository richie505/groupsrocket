package com.appsc.prep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.appsc.prep.data.Block
import com.appsc.prep.data.Run
import com.appsc.prep.data.TableBlock
import com.appsc.prep.data.TextBlock
import com.appsc.prep.ui.theme.C

fun annotated(runs: List<Run>, base: Color = C.Body, strong: Color = Color.Black): AnnotatedString =
    buildAnnotatedString {
        for (r in runs) {
            val style = SpanStyle(
                fontWeight = if (r.bold) FontWeight.SemiBold else null,
                fontStyle = if (r.italic) FontStyle.Italic else null,
                color = when {
                    r.muted -> C.Faint
                    r.bold -> strong
                    else -> base
                },
                fontSize = if (r.muted && r.text.trim().startsWith("[")) 0.78.em else androidx.compose.ui.unit.TextUnit.Unspecified,
            )
            withStyle(style) { append(r.text) }
        }
    }

/** Renders one content block of a subsection, scaled by [scale]. */
@Composable
fun BlockView(block: Block, scale: Float) {
    val body = TextStyle(fontSize = (16 * scale).sp, lineHeight = (25 * scale).sp, color = C.Body)
    when (block) {
        is TableBlock -> TableView(block, scale)
        is TextBlock -> when (block.kind) {
            'b', 's' -> {
                val sub = block.kind == 's'
                Row(Modifier.padding(start = if (sub) 22.dp else 4.dp, top = 3.dp, bottom = 3.dp)) {
                    Text(
                        if (sub) "–" else "•",
                        style = body.copy(color = if (sub) C.Muted else C.Ink, fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    val text = remember(block) { annotated(block.runs) }
                    Text(text, style = if (sub) body.copy(fontSize = (15 * scale).sp) else body)
                }
            }
            'x' -> Callout(block.runs, C.ExamBg, C.ExamInk, "Exam angle", scale)
            'a' -> Callout(block.runs, C.SeeBg, C.SeeInk, null, scale)
            'n' -> {
                val text = remember(block) { annotated(block.runs, base = C.Muted, strong = C.Muted) }
                Text(
                    text,
                    style = body.copy(fontSize = (14 * scale).sp, fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            else -> {
                val text = remember(block) { annotated(block.runs) }
                Text(text, style = body, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun Callout(runs: List<Run>, bg: Color, ink: Color, label: String?, scale: Float) {
    // Drop the leading "Exam angle:" from the text since we show it as a label.
    val shown = remember(runs) {
        if (label == null) runs else {
            val first = runs.firstOrNull()
            if (first != null && first.text.startsWith("$label:")) {
                listOf(first.copy(text = first.text.removePrefix("$label:").trimStart())) + runs.drop(1)
            } else runs
        }
    }
    Row(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(ink))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (label != null) {
                Text(
                    label.uppercase(),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink, letterSpacing = 0.8.sp),
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                remember(shown) { annotated(shown.map { it.copy(flags = it.flags and 2.inv()) }, base = ink, strong = ink) },
                style = TextStyle(fontSize = (15 * scale).sp, lineHeight = (22 * scale).sp),
            )
        }
    }
}

@Composable
private fun TableView(t: TableBlock, scale: Float) {
    val cols = maxOf(t.head.size, t.rows.maxOfOrNull { it.size } ?: 0)
    if (cols == 0) return
    val cellStyle = TextStyle(fontSize = (14 * scale).sp, lineHeight = (20 * scale).sp, color = C.Body)
    val hasHead = t.head.any { cell -> cell.any { it.text.isNotBlank() } }
    val all = (if (hasHead) listOf(t.head) else emptyList()) + t.rows
    // per column: longest word (never broken) and total text (for proportional share)
    val stats = remember(t) {
        (0 until cols).map { c ->
            val texts = all.map { row -> row.getOrNull(c)?.joinToString("") { it.text } ?: "" }
            val longest = texts.flatMap { it.split(' ') }.maxOfOrNull { it.length } ?: 1
            longest to texts.sumOf { it.length }.coerceAtLeast(1)
        }
    }
    BoxWithConstraints(Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        val avail = maxWidth - 2.dp
        val mins = stats.map { (longest, _) -> (longest * 8.2f * scale + 22).dp }
        val weights = stats.map { (_, total) -> kotlin.math.sqrt(total.toFloat()) }
        val wsum = weights.sum()
        var widths = weights.map { avail * (it / wsum) }.mapIndexed { i, w -> maxOf(w, mins[i]) }
        val used = widths.fold(0.dp) { a, b -> a + b }
        if (used < avail) {
            // hand back the space, keeping proportions
            widths = widths.map { it * (avail / used) }
        } else if (cols >= 2) {
            widths = widths.map { minOf(it, 260.dp) }
        }
        val total = widths.fold(0.dp) { a, b -> a + b }
        val scroll = rememberScrollState()
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, C.Line, RoundedCornerShape(10.dp))
                .then(if (total > avail + 1.dp) Modifier.horizontalScroll(scroll) else Modifier),
        ) {
            Column(Modifier.width(total)) {
                all.forEachIndexed { ri, row ->
                    val isHead = hasHead && ri == 0
                    Row(
                        Modifier
                            .width(total)
                            .height(IntrinsicSize.Min)
                            .background(
                                when {
                                    isHead -> C.AccentSoft
                                    ri % 2 == 0 -> C.Surface
                                    else -> Color.White
                                },
                            ),
                    ) {
                        for (c in 0 until cols) {
                            val cell = row.getOrNull(c) ?: emptyList()
                            val text = remember(cell, isHead) {
                                if (isHead) annotated(cell.map { it.copy(flags = 1) }, strong = C.Navy) else annotated(cell)
                            }
                            Text(
                                text,
                                style = cellStyle,
                                modifier = Modifier
                                    .width(widths[c])
                                    .fillMaxHeight()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                            if (c < cols - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
                        }
                    }
                    if (ri < all.size - 1) Box(Modifier.width(total).height(1.dp).background(C.Line))
                }
            }
        }
    }
}
