package com.appsc.prep

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android's regex engine (ICU) refuses a look-behind without a maximum length - "(?<=[a-z]{2,} )" or
 * "(?<!\w+)" - and throws when the pattern is built, while desktop Java accepts it. v2.22 crashed on opening a
 * page because of one, so every look-behind in the app's code is checked here.
 */
class AndroidRegexTest {
    @Test
    fun lookBehindsHaveAMaximumLength() {
        val bad = mutableListOf<String>()
        File("src/main/java").walk().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                var at = line.indexOf("(?<")
                while (at >= 0) {
                    if (line.startsWith("(?<=", at) || line.startsWith("(?<!", at)) {
                        val body = group(line, at)
                        if (unbounded(body)) bad += "${file.name}:${i + 1}: $body"
                    }
                    at = line.indexOf("(?<", at + 3)
                }
            }
        }
        assertTrue("look-behind without a maximum length (crashes on Android):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** The look-behind group starting at [from], up to its closing bracket. */
    private fun group(s: String, from: Int): String {
        var depth = 0
        var inClass = false
        var i = from
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> i++
                inClass -> if (c == ']') inClass = false
                c == '[' -> inClass = true
                c == '(' -> depth++
                c == ')' -> if (--depth == 0) return s.substring(from, i + 1)
            }
            i++
        }
        return s.substring(from)
    }

    /** A * or + outside [...] (escapes dropped first), or {n,} with no upper bound. */
    private fun unbounded(body: String): Boolean {
        val plain = body.replace(Regex("""\\."""), "").replace(Regex("""\[[^\]]*]"""), "")
        return '*' in plain || '+' in plain || Regex("""\{\d+,}""").containsMatchIn(plain)
    }
}
