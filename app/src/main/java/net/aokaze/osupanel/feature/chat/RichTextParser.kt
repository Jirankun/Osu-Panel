/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Lightweight HTML → AnnotatedString parser for Chatango bios.
 *
 * Chatango's <body> is HTML (e.g. `<b>bold</b>`, `<font color="#fff">`,
 * `<a href="...">`, `<br>`), with HTML entities. It is NOT a full HTML
 * engine — only the tags below are understood; everything else is shown as
 * plain text. Links are styled (pink + underline) and — via [parseRich] —
 * their text ranges + URLs are returned so the UI can make them clickable.
 */
object RichTextParser {

    /** Parsed bio: styled text + the clickable link ranges (offset, url). */
    data class ParsedRichText(
        val annotated: AnnotatedString,
        /** (text range in [annotated], url) — one entry per `<a href>` tag. */
        val links: List<Pair<IntRange, String>>,
    )

    private val TAG_REGEX = Regex("<(/)?([a-zA-Z0-9]+)([^>]*)>")
    private val COLOR_REGEX = Regex("""color\s*=\s*["']?#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})""")
    private val HREF_REGEX = Regex("""href\s*=\s*["']([^"']+)["']""")

    /** Parse + return clickable links (see [parseRich]). */
    fun parse(html: String, linkColor: Color = Color(0xFFFF66AB)): AnnotatedString =
        parseRich(html, linkColor).annotated

    fun parseRich(html: String, linkColor: Color = Color(0xFFFF66AB)): ParsedRichText {
        if (html.isBlank()) return ParsedRichText(AnnotatedString(""), emptyList())
        val builder = AnnotatedString.Builder()
        val styles = ArrayDeque<SpanStyle>()
        // Active <a> elements: (href, text start offset) — for link ranges.
        val linkStack = ArrayDeque<Pair<String, Int>>()
        val links = mutableListOf<Pair<IntRange, String>>()

        fun currentStyle(): SpanStyle = styles.lastOrNull() ?: SpanStyle()

        var lastIndex = 0
        for (match in TAG_REGEX.findAll(html)) {
            builder.append(decodeEntities(html.substring(lastIndex, match.range.first)))
            lastIndex = match.range.last + 1

            val closing = match.groupValues[1] == "/"
            val tag = match.groupValues[2].lowercase()
            val attrs = match.groupValues[3]

            when {
                closing -> {
                    if (tag == "a") {
                        linkStack.removeLastOrNull()?.let { (href, start) ->
                            if (builder.length > start) links.add(start until builder.length to href)
                        }
                    }
                    if (styles.isNotEmpty()) styles.removeLast()
                }
                tag == "br" || tag == "p" || tag == "div" -> {
                    builder.append("\n")
                    continue
                }
                tag == "b" || tag == "strong" -> {
                    styles.addLast(currentStyle().plus(SpanStyle(fontWeight = FontWeight.Bold)))
                }
                tag == "i" || tag == "em" -> {
                    styles.addLast(currentStyle().plus(SpanStyle(fontStyle = FontStyle.Italic)))
                }
                tag == "u" -> {
                    styles.addLast(currentStyle().plus(SpanStyle(textDecoration = TextDecoration.Underline)))
                }
                tag == "s" || tag == "strike" || tag == "del" -> {
                    styles.addLast(currentStyle().plus(SpanStyle(textDecoration = TextDecoration.LineThrough)))
                }
                tag == "font" -> {
                    val color = COLOR_REGEX.find(attrs)?.groupValues?.get(1)
                    if (color != null) {
                        styles.addLast(currentStyle().plus(SpanStyle(color = parseColor(color))))
                    } else {
                        styles.addLast(currentStyle())
                    }
                }
                tag == "a" -> {
                    val href = decodeEntities(HREF_REGEX.find(attrs)?.groupValues?.get(1).orEmpty())
                    linkStack.addLast(href to builder.length)
                    val linkStyle = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                    styles.addLast(currentStyle().plus(linkStyle))
                }
                else -> Unit // unknown tag → ignore
            }
            builder.pushStyle(currentStyle())
        }
        builder.append(decodeEntities(html.substring(lastIndex)))
        return ParsedRichText(builder.toAnnotatedString(), links)
    }

    /** Parse "#rgb" / "#rrggbb" hex colors, defaulting to a neutral gray. */
    private fun parseColor(hex: String): Color {
        return runCatching {
            when (hex.length) {
                3 -> Color(
                    red = hex[0].digitToInt(16) * 17 / 255f,
                    green = hex[1].digitToInt(16) * 17 / 255f,
                    blue = hex[2].digitToInt(16) * 17 / 255f,
                )
                6 -> Color(
                    red = hex.substring(0, 2).toInt(16) / 255f,
                    green = hex.substring(2, 4).toInt(16) / 255f,
                    blue = hex.substring(4, 6).toInt(16) / 255f,
                )
                else -> error("bad hex")
            }
        }.getOrDefault(Color.Gray)
    }

    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val amp = text.indexOf('&', i)
            if (amp < 0) {
                sb.append(text, i, text.length)
                break
            }
            sb.append(text, i, amp)
            val semi = text.indexOf(';', amp)
            if (semi < 0) {
                sb.append(text, amp, text.length)
                break
            }
            val entity = text.substring(amp + 1, semi)
            when {
                entity == "amp" -> sb.append('&')
                entity == "lt" -> sb.append('<')
                entity == "gt" -> sb.append('>')
                entity == "quot" -> sb.append('"')
                entity == "apos" || entity == "#39" -> sb.append('\'')
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    sb.append(entity.substring(2).toIntOrNull(16)?.toChar() ?: text.substring(amp, semi + 1))
                entity.startsWith("#") ->
                    sb.append(entity.substring(1).toIntOrNull()?.toChar() ?: text.substring(amp, semi + 1))
                entity.startsWith("nbsp") -> sb.append(' ')
                else -> sb.append(text, amp, semi + 1)
            }
            i = semi + 1
        }
        return sb.toString()
    }
}
