package com.appsc.prep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import com.appsc.prep.ui.theme.C

/** What differs between the Android and Windows apps. */
interface Platform {
    /** True on Windows: wider layout and keyboard shortcuts. */
    val desktop: Boolean
    fun share(title: String, text: String)
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)

    /** Keyboard keys while this screen shows (Windows only): "1".."9", "Left", "Right". True if handled. */
    @Composable
    fun Shortcuts(onKey: (String) -> Boolean)

    /** Read-aloud engine, or null where there is none (the reader then hides the Listen button). */
    val speech: Speech? get() = null
}

/** One subsection to read aloud: its id ("book:row:sec"), title and the parts (paragraphs) to speak. */
data class SpeechPage(val id: String, val title: String, val parts: List<String>)

/** Where read-aloud is: [active] while a session runs (playing or paused), the page and part being read. */
data class Playback(val active: Boolean = false, val playing: Boolean = false, val pageId: String = "", val part: Int = 0)

/**
 * Read-aloud for the notes. One session for the whole app: it keeps reading into the next pages (from [play]'s
 * `next`) with the screen locked or the app in the background, until [stop] or the app is closed.
 */
interface Speech {
    val playback: State<Playback>

    /** Reads [page] from part [from], then each page `next()` gives until it returns null. */
    fun play(page: SpeechPage, from: Int, rate: Float, next: () -> SpeechPage?)
    fun pause()
    fun resume()

    /** Jumps to a part of the current page. */
    fun seek(part: Int)
    fun setRate(rate: Float)
    fun stop()
}

class AppState(val repo: Repository, val store: ProgressStore, val platform: Platform)

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Ink)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = MaterialThemeTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            actions()
        }
        HorizontalDivider(color = C.Line)
    }
}

private val MaterialThemeTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = C.Ink)

@Composable
fun Tag(text: String, bg: Color = C.Chip, fg: Color = C.Muted, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

@Composable
fun PriorityTag(priority: String) {
    when (priority.uppercase()) {
        "HIGH" -> Tag("HIGH", C.HighSoft, C.High)
        "MED" -> Tag("MED", C.MedSoft, C.Med)
        "LIGHT" -> Tag("LIGHT", C.LightSoft, C.Light)
        "" -> {}
        else -> Tag(priority)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Accent) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = if (fraction >= 1f) C.Green else color,
        trackColor = C.Chip,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun DoneIcon(done: Boolean, size: Int = 22) {
    if (done) {
        Icon(Icons.Filled.CheckCircle, "Done", tint = C.Green, modifier = Modifier.size(size.dp))
    } else {
        Icon(Icons.Outlined.Circle, "Not done", tint = C.Line, modifier = Modifier.size(size.dp))
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Muted, letterSpacing = 1.sp),
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .background(Color.White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** One syllabus row ("Section") in a list, with its progress. */
@Composable
fun SectionItem(
    number: String?,
    title: String,
    meta: String,
    priority: String,
    done: Int,
    total: Int,
    onClick: () -> Unit,
    onPractice: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (total > 0 && done >= total) C.GreenSoft else C.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (total > 0 && done >= total) {
                Icon(Icons.Filled.CheckCircle, null, tint = C.Green, modifier = Modifier.size(20.dp))
            } else {
                Text(number ?: "", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Accent))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = C.Ink))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriorityTag(priority)
                Text(meta, style = TextStyle(fontSize = 12.sp, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (total > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(done / total.toFloat(), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("$done/$total", style = TextStyle(fontSize = 11.sp, color = C.Muted))
                }
            }
        }
        if (onPractice != null) {
            IconButton(onClick = onPractice) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.ExamBg),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Quiz, "Practice MCQs", tint = C.ExamInk, modifier = Modifier.size(20.dp)) }
            }
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = C.Accent)
    }
}

@Composable
fun StatBox(value: String, label: String, icon: ImageVector?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.Surface)
            .padding(14.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = C.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(value, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Ink))
        Text(label, style = TextStyle(fontSize = 12.sp, color = C.Muted))
    }
}

/** Entry point to an MCQ practice set. [attempted] = (attempted, correct, total) when known. */
@Composable
fun PracticeCard(
    title: String,
    subtitle: String,
    attempted: Triple<Int, Int, Int>?,
    onStart: () -> Unit,
    onWrong: (() -> Unit)? = null,
) {
    Card(onClick = onStart) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.ExamBg),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Quiz, null, tint = C.ExamInk) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                    Text(subtitle, style = TextStyle(fontSize = 13.sp, color = C.Muted))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
            }
            if (attempted != null && attempted.third > 0) {
                val (a, c, t) = attempted
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(a / t.toFloat(), Modifier.weight(1f), color = C.ExamInk)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "$a/$t done" + if (a > 0) " · ${c * 100 / a}%" else "",
                        style = TextStyle(fontSize = 12.sp, color = C.Muted),
                    )
                }
                if (onWrong != null && a - c > 0) {
                    Text(
                        "Retry ${a - c} wrong answers",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.High),
                        modifier = Modifier.padding(top = 10.dp).clickable(onClick = onWrong),
                    )
                }
            }
        }
    }
}
