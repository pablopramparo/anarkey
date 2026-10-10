package org.anarkey.core.exchange

/**
 * A small JSON reader and writer so song files need no extra dependency and can be tested on the JVM.
 * Values are `null`, `Boolean`, `Long`, `Double`, `String`, `List<Any?>` and `Map<String, Any?>`.
 */
object Json {
    class ParseException(message: String) : Exception(message)

    private const val MAX_DEPTH = 64

    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.value(0)
        reader.skipWhitespace()
        if (!reader.atEnd) throw ParseException("trailing_content")
        return value
    }

    fun write(value: Any?, pretty: Boolean = false): String = StringBuilder().also { write(it, value, pretty, 0) }.toString()

    private fun write(out: StringBuilder, value: Any?, pretty: Boolean, depth: Int) {
        fun newline(level: Int) { if (pretty) { out.append('\n'); repeat(level) { out.append("  ") } } }
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value.toString())
            is Double -> out.append(if (value.isFinite()) value.toString() else "null")
            is String -> quote(out, value)
            is Map<*, *> -> {
                if (value.isEmpty()) { out.append("{}"); return }
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    newline(depth + 1)
                    quote(out, key.toString())
                    out.append(if (pretty) ": " else ":")
                    write(out, item, pretty, depth + 1)
                }
                newline(depth)
                out.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) { out.append("[]"); return }
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    newline(depth + 1)
                    write(out, item, pretty, depth + 1)
                }
                newline(depth)
                out.append(']')
            }
            else -> throw IllegalArgumentException("unsupported_json_value")
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    private class Reader(private val text: String) {
        private var index = 0
        val atEnd: Boolean get() = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) index++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseException("too_deep")
            if (atEnd) throw ParseException("unexpected_end")
            return when (val char = text[index]) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char.isDigit()) number() else throw ParseException("unexpected_character")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, index)) throw ParseException("invalid_literal")
            index += word.length
            return result
        }

        private fun number(): Any {
            val start = index
            if (text[index] == '-') index++
            while (index < text.length && (text[index].isDigit() || text[index] in ".eE+-")) index++
            val token = text.substring(start, index)
            return token.toLongOrNull() ?: token.toDoubleOrNull() ?: throw ParseException("invalid_number")
        }

        private fun string(): String {
            index++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd) throw ParseException("unterminated_string")
                val char = text[index++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd) throw ParseException("unterminated_string")
                        when (val escape = text[index++]) {
                            '"', '\\', '/' -> out.append(escape)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (index + 4 > text.length) throw ParseException("invalid_escape")
                                out.append(text.substring(index, index + 4).toIntOrNull(16)?.toChar() ?: throw ParseException("invalid_escape"))
                                index += 4
                            }
                            else -> throw ParseException("invalid_escape")
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            index++
            val items = mutableListOf<Any?>()
            skipWhitespace()
            if (!atEnd && text[index] == ']') { index++; return items }
            while (true) {
                skipWhitespace()
                items += value(depth + 1)
                skipWhitespace()
                if (atEnd) throw ParseException("unexpected_end")
                when (text[index++]) {
                    ',' -> Unit
                    ']' -> return items
                    else -> throw ParseException("expected_comma_or_bracket")
                }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            index++
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd && text[index] == '}') { index++; return map }
            while (true) {
                skipWhitespace()
                if (atEnd || text[index] != '"') throw ParseException("expected_key")
                val key = string()
                skipWhitespace()
                if (atEnd || text[index++] != ':') throw ParseException("expected_colon")
                skipWhitespace()
                map[key] = value(depth + 1)
                skipWhitespace()
                if (atEnd) throw ParseException("unexpected_end")
                when (text[index++]) {
                    ',' -> Unit
                    '}' -> return map
                    else -> throw ParseException("expected_comma_or_brace")
                }
            }
        }
    }
}
