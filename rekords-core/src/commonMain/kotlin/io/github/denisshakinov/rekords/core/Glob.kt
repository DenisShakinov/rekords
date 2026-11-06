package io.github.denisshakinov.rekords.core

/**
 * Whether this text matches [pattern] as SQLite's `GLOB` tells - see [Filter.Glob] - so that an
 * editor filtering rekords in memory selects the ones a SQL editor does.
 *
 * Both are read a code point at a time, the way SQLite reads UTF-8: `?` matches a character
 * outside the Basic Multilingual Plane whole, not half of its surrogate pair.
 */
internal fun String.matchesGlob(pattern: String): Boolean {
    val tokens: List<GlobToken> = globTokens(pattern) ?: return false
    val text: IntArray = globCodePoints()
    var t = 0
    var p = 0
    // Where the last `*` was met, and the text it was last tried to stop at: a token that fails
    // after it lets the `*` take one more character instead.
    var starP = -1
    var starT = 0
    while (t < text.size) {
        val token = tokens.getOrNull(p)
        when {
            token == GlobToken.AnyRun -> {
                starP = p
                starT = t
                p++
            }
            token != null && token.matches(text[t]) -> {
                p++
                t++
            }
            starP >= 0 -> {
                p = starP + 1
                t = ++starT
            }
            else -> return false
        }
    }
    while (tokens.getOrNull(p) == GlobToken.AnyRun) p++
    return p == tokens.size
}

/** What a glob pattern is made of: a `*`, or what matches exactly one character. */
private sealed interface GlobToken {

    fun matches(codePoint: Int): Boolean

    data object AnyRun : GlobToken {
        override fun matches(codePoint: Int): Boolean = false
    }

    data object AnyOne : GlobToken {
        override fun matches(codePoint: Int): Boolean = true
    }

    class Literal(private val codePoint: Int) : GlobToken {
        override fun matches(codePoint: Int): Boolean = codePoint == this.codePoint
    }

    class Set(private val ranges: List<IntRange>, private val inverted: Boolean) : GlobToken {
        override fun matches(codePoint: Int): Boolean = ranges.any { codePoint in it } != inverted
    }
}

/**
 * The tokens of [pattern], read the way SQLite's `patternCompare` reads them; null for a set left
 * open, which SQLite matches nothing with.
 */
private fun globTokens(pattern: String): List<GlobToken>? {
    val chars: IntArray = pattern.globCodePoints()
    val tokens = mutableListOf<GlobToken>()
    var i = 0
    while (i < chars.size) {
        tokens += when (val c = chars[i++]) {
            '*'.code -> GlobToken.AnyRun
            '?'.code -> GlobToken.AnyOne
            '['.code -> {
                var inverted = false
                if (chars.getOrNull(i) == '^'.code) {
                    inverted = true
                    i++
                }
                val ranges = mutableListOf<IntRange>()
                if (chars.getOrNull(i) == ']'.code) {
                    ranges += ']'.code..']'.code
                    i++
                }
                // The character a `-` begins a range from; none first, after a `]` leading the set,
                // and after a range, where a `-` is a character of its own.
                var prior = 0
                while (i < chars.size && chars[i] != ']'.code) {
                    val member = chars[i++]
                    val next = chars.getOrNull(i)
                    if (member == '-'.code && prior > 0 && next != null && next != ']'.code) {
                        ranges += prior..next
                        i++
                        prior = 0
                    } else {
                        ranges += member..member
                        prior = member
                    }
                }
                if (i == chars.size) return null
                i++
                GlobToken.Set(ranges, inverted)
            }
            else -> GlobToken.Literal(c)
        }
    }
    return tokens
}

/** The code points of this text, a surrogate pair being read as the one character it encodes. */
private fun String.globCodePoints(): IntArray {
    val codePoints = IntArray(length)
    var count = 0
    var i = 0
    while (i < length) {
        val high = this[i++]
        val low = getOrNull(i)
        codePoints[count++] = if (high.isHighSurrogate() && low != null && low.isLowSurrogate()) {
            i++
            0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        } else {
            high.code
        }
    }
    return codePoints.copyOf(count)
}
