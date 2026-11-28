package com.example.mybeaver.sql

data class ScriptChunk(val sql: String, val multi: Boolean = false)

object SqlScriptSplitter {

    private data class Piece(val start: Int, val end: Int, val delimiter: String)

    fun split(script: String): List<ScriptChunk> {
        val pieces = scan(script)
        val chunks = mutableListOf<ScriptChunk>()
        var k = 0
        while (k < pieces.size) {
            val p = pieces[k]
            val text = script.substring(p.start, p.end).trim()
            if (p.delimiter == ";" && isCompoundCreate(text)) {
                var j = k
                while (j + 1 < pieces.size && pieces[j + 1].delimiter == ";") j++
                chunks.add(ScriptChunk(script.substring(p.start, pieces[j].end).trim(), multi = true))
                k = j + 1
            } else {
                chunks.add(ScriptChunk(text))
                k++
            }
        }
        return chunks
    }

    private fun scan(s: String): List<Piece> {
        val pieces = mutableListOf<Piece>()
        var delimiter = ";"
        var start = 0
        var hasCode = false
        var lineStart = true
        var i = 0
        val n = s.length

        fun flush(end: Int) {
            if (hasCode) pieces.add(Piece(start, end, delimiter))
            hasCode = false
        }

        while (i < n) {
            val c = s[i]

            if (lineStart && !hasCode && s.regionMatches(i, "DELIMITER", 0, 9, ignoreCase = true) &&
                i + 9 < n && (s[i + 9] == ' ' || s[i + 9] == '\t')
            ) {
                val eol = s.indexOf('\n', i).let { if (it == -1) n else it }
                val newDelimiter = s.substring(i + 9, eol).trim().split(Regex("\\s+"))[0]
                if (newDelimiter.isNotEmpty()) delimiter = newDelimiter
                i = eol
                start = eol
                continue
            }

            when {
                c == '\'' || c == '"' || c == '`' -> {
                    i = skipQuoted(s, i, c)
                    hasCode = true; lineStart = false
                    continue
                }
                c == '#' || (c == '-' && i + 1 < n && s[i + 1] == '-' && (i + 2 >= n || s[i + 2].isWhitespace())) -> {
                    i = s.indexOf('\n', i).let { if (it == -1) n else it }
                    continue
                }
                c == '/' && i + 1 < n && s[i + 1] == '*' -> {
                    i = s.indexOf("*/", i + 2).let { if (it == -1) n else it + 2 }
                    lineStart = false
                    continue
                }
                s.startsWith(delimiter, i) -> {
                    flush(i)
                    i += delimiter.length
                    start = i
                    lineStart = false
                    continue
                }
            }

            if (c == '\n') lineStart = true
            else if (!c.isWhitespace()) { lineStart = false; hasCode = true }
            i++
        }
        flush(n)
        return pieces
    }

    private fun skipQuoted(s: String, open: Int, quote: Char): Int {
        var i = open + 1
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && quote != '`') { i += 2; continue }
            if (c == quote) {
                if (i + 1 < s.length && s[i + 1] == quote) { i += 2; continue }
                return i + 1
            }
            i++
        }
        return s.length
    }

    private val COMPOUND = Regex(
        "^CREATE\\s+(OR\\s+REPLACE\\s+)?(DEFINER\\s*=\\s*\\S+\\s+)?(AGGREGATE\\s+)?" +
            "(PROCEDURE|FUNCTION|TRIGGER|EVENT)\\b.*\\bBEGIN\\b",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun isCompoundCreate(text: String): Boolean = COMPOUND.containsMatchIn(stripLeadingComments(text))

    private fun stripLeadingComments(text: String): String {
        var t = text.trimStart()
        while (true) {
            t = when {
                t.startsWith("--") || t.startsWith("#") -> t.substringAfter('\n', "").trimStart()
                t.startsWith("/*") -> t.substringAfter("*/", "").trimStart()
                else -> return t
            }
        }
    }
}
