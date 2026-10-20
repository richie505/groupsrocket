package com.appsc.prep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.prep.data.MapNode
import com.appsc.prep.ui.theme.C

/** One colour per branch, as in a drawn mind map. */
private val branchColors = listOf(
    Color(0xFF3949AB), Color(0xFF00897B), Color(0xFFD84315), Color(0xFF8E24AA), Color(0xFF2E7D32), Color(0xFF6D4C41),
)

/**
 * The read-aloud line of every node (tools/build_mindmap.py map_blocks writes the page text in this order): a
 * branch is a line; a node is a line, its leaves joined into it when all its children are leaves; exam angles
 * are lines of their own.
 */
fun mapLines(branches: List<MapNode>): java.util.IdentityHashMap<MapNode, Int> {
    val out = java.util.IdentityHashMap<MapNode, Int>()
    var i = 0
    fun walk(nodes: List<MapNode>) {
        for (n in nodes) {
            out[n] = i++
            if (n.kind == 'x') continue
            val merged = n.children.isNotEmpty() && n.children.all { it.children.isEmpty() }
            if (merged) n.children.forEach { out[it] = out[n]!! } else walk(n.children)
        }
    }
    for (b in branches) {
        out[b] = i++
        walk(b.children)
    }
    return out
}

/**
 * A page's mind map drawn as a tree that fits the screen's width (no sideways panning): the page title at the top,
 * each branch in its own colour with its facts hanging from it on connector lines. Tap a branch to fold it.
 * While the page is read aloud, [reading] is the line being read: its node is highlighted (its branch opens),
 * [onLine] reports where each line sits (px from the top of the map) and a tap on a node reads from there.
 */
@Composable
fun MindMap(
    title: String,
    branches: List<MapNode>,
    scale: Float,
    reading: Int = -1,
    onLine: (Int, Float) -> Unit = { _, _ -> },
    onTap: ((Int) -> Unit)? = null,
) {
    val lines = remember(branches) { mapLines(branches) }
    val top = remember { FloatArray(1) } // the map's own position: not state, it changes with every scroll
    val ctx = MapCtx(lines, reading, { n, y -> lines[n]?.let { onLine(it, y - top[0]) } }, onTap)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).onGloballyPositioned { top[0] = it.positionInRoot().y }) {
        Text(
            title,
            style = TextStyle(fontSize = (15 * scale).sp, lineHeight = (21 * scale).sp, fontWeight = FontWeight.Bold, color = Color.White),
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(C.Navy).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        branches.forEachIndexed { i, b ->
            Connected(C.Navy, last = i == branches.lastIndex, indent = 18.dp, top = 22.dp) {
                Branch(b, branchColors[i % branchColors.size], scale, ctx)
            }
        }
    }
}

/** What every node needs while reading aloud: its line, the line being read, where to report, what a tap does. */
private class MapCtx(
    val lines: java.util.IdentityHashMap<MapNode, Int>,
    val reading: Int,
    val place: (MapNode, Float) -> Unit,
    val onTap: ((Int) -> Unit)?,
) {
    fun reads(n: MapNode) = reading >= 0 && lines[n] == reading
    fun inside(n: MapNode): Boolean = reads(n) || n.children.any { inside(it) }
    fun mod(n: MapNode): Modifier = Modifier
        .onGloballyPositioned { place(n, it.positionInRoot().y) }
        .then(if (onTap != null) Modifier.clickable { lines[n]?.let(onTap) } else Modifier)
}

@Composable
private fun Branch(node: MapNode, color: Color, scale: Float, ctx: MapCtx) {
    var open by rememberSaveable(node.runs.joinToString("") { it.text }) { mutableStateOf(true) }
    val shown = open || ctx.inside(node) // the part being read is never folded away
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            Modifier
                .onGloballyPositioned { ctx.place(node, it.positionInRoot().y) }
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .then(if (ctx.reads(node)) Modifier.border(3.dp, C.Navy, RoundedCornerShape(12.dp)) else Modifier)
                .clickable { open = !shown }
                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                annotated(node.runs, base = Color.White, strong = Color.White),
                style = TextStyle(fontSize = (14.5f * scale).sp, lineHeight = (20 * scale).sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(if (shown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.85f))
        }
        if (shown) Children(node.children, color, scale, depth = 1, ctx)
    }
}

@Composable
private fun Children(nodes: List<MapNode>, color: Color, scale: Float, depth: Int, ctx: MapCtx) {
    nodes.forEachIndexed { i, n ->
        Connected(color.copy(alpha = 0.55f), last = i == nodes.lastIndex, indent = 16.dp, top = if (depth == 1) 19.dp else 15.dp) {
            Column(Modifier.padding(top = 6.dp)) {
                Node(n, color, scale, depth, ctx)
                if (n.children.isNotEmpty()) Children(n.children, color, scale, depth + 1, ctx)
            }
        }
    }
}

@Composable
private fun Node(n: MapNode, color: Color, scale: Float, depth: Int, ctx: MapCtx) {
    val text = remember(n) { annotated(n.runs, strong = Color.Black) }
    // the node being read aloud: a highlight behind it
    val hl = if (ctx.reads(n)) Modifier.clip(RoundedCornerShape(10.dp)).background(C.AccentSoft) else Modifier
    val base = ctx.mod(n).then(hl)
    when {
        n.kind == 'x' -> Text(
            text,
            style = TextStyle(fontSize = (13.5f * scale).sp, lineHeight = (19 * scale).sp, color = C.ExamInk),
            modifier = base.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(if (ctx.reads(n)) C.AccentSoft else C.ExamBg).padding(horizontal = 10.dp, vertical = 7.dp),
        )
        // a table's columns ("Act → Key provisions"): a label over its rows
        n.kind == 'g' -> Text(
            text,
            style = TextStyle(fontSize = (12.5f * scale).sp, lineHeight = (17 * scale).sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
            modifier = base.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        depth == 1 || n.kind == 't' -> Text(
            text,
            style = TextStyle(fontSize = (14 * scale).sp, lineHeight = (20 * scale).sp, color = C.Ink, fontWeight = if (n.children.isNotEmpty()) FontWeight.SemiBold else null),
            modifier = base
                .clip(RoundedCornerShape(10.dp))
                .border(if (ctx.reads(n)) 2.5.dp else 1.5.dp, if (ctx.reads(n)) C.Navy else color.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .background(if (ctx.reads(n)) C.AccentSoft else color.copy(alpha = 0.07f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        else -> Text(
            text,
            style = TextStyle(fontSize = (13.5f * scale).sp, lineHeight = (19 * scale).sp, color = C.Body),
            modifier = base.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/** A child hanging from its parent: an elbow line on the left (the line runs on for all but the last child). */
@Composable
private fun Connected(line: Color, last: Boolean, indent: Dp, top: Dp, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(indent)
                .fillMaxHeight()
                .drawBehind {
                    val x = 6.dp.toPx()
                    val y = top.toPx()
                    val w = 2.dp.toPx()
                    drawLine(line, Offset(x, 0f), Offset(x, if (last) y else size.height), strokeWidth = w)
                    drawLine(line, Offset(x, y), Offset(size.width, y), strokeWidth = w)
                },
        )
        Box(Modifier.weight(1f)) { content() }
    }
    Spacer(Modifier.height(0.dp))
}
