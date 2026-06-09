package `fun`.nightshift.launcher.client.i18n

/**
 * Tiny flat-JSON parser used only by [LocalizationManager] to read
 * `{"key": "value"}` overrides. Purposefully tolerant: missing trailing
 * brace, wonky whitespace, line comments — none of these crash the launcher.
 *
 * Returns an empty map on any parse error (the caller already logs).
 */
internal object FlatJson {
    fun parse(input: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var i = 0
        val n = input.length
        // Find first '{'
        while (i < n && input[i] != '{') i++
        if (i >= n) return emptyMap()
        i++
        while (i < n) {
            i = skipWs(input, i)
            if (i >= n || input[i] == '}') break
            if (input[i] != '"') return map
            val keyEnd = findStringEnd(input, i + 1) ?: return map
            val key = unescape(input.substring(i + 1, keyEnd))
            i = keyEnd + 1
            i = skipWs(input, i)
            if (i >= n || input[i] != ':') return map
            i++
            i = skipWs(input, i)
            if (i >= n || input[i] != '"') return map
            val valEnd = findStringEnd(input, i + 1) ?: return map
            val value = unescape(input.substring(i + 1, valEnd))
            map[key] = value
            i = valEnd + 1
            i = skipWs(input, i)
            if (i < n && input[i] == ',') i++
        }
        return map
    }

    private fun skipWs(s: String, from: Int): Int {
        var i = from
        while (i < s.length && (s[i].isWhitespace() || s[i] == '\n')) i++
        return i
    }

    private fun findStringEnd(s: String, from: Int): Int? {
        var i = from
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                '"' -> return i
                else -> i++
            }
        }
        return null
    }

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    'r' -> out.append('\r')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
