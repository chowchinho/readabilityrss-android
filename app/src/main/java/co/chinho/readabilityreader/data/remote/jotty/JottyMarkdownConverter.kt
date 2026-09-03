package co.chinho.readabilityreader.data.remote.jotty

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JottyMarkdownConverter @Inject constructor() {

    fun convert(html: String): String {
        if (html.isBlank()) return ""
        val body = Jsoup.parse(html).body()
        val builder = StringBuilder()
        body.childNodes().forEach { renderBlock(it, builder, listDepth = 0, listMarker = null, listIndex = 0) }
        return builder.toString()
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun renderBlock(
        node: Node,
        out: StringBuilder,
        listDepth: Int,
        listMarker: ListMarker?,
        listIndex: Int,
    ) {
        when {
            node is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    out.append(escape(text))
                }
            }
            node is Element -> renderElement(node, out, listDepth, listMarker, listIndex)
        }
    }

    private fun renderElement(
        el: Element,
        out: StringBuilder,
        listDepth: Int,
        listMarker: ListMarker?,
        listIndex: Int,
    ) {
        when (el.tagName().lowercase()) {
            "h1" -> heading(el, out, "# ")
            "h2" -> heading(el, out, "## ")
            "h3" -> heading(el, out, "### ")
            "h4" -> heading(el, out, "#### ")
            "h5" -> heading(el, out, "##### ")
            "h6" -> heading(el, out, "###### ")
            "p" -> {
                ensureBlankLine(out)
                el.childNodes().forEach { renderInline(it, out) }
                out.append("\n\n")
            }
            "br" -> out.append("  \n")
            "hr" -> {
                ensureBlankLine(out)
                out.append("---\n\n")
            }
            "blockquote" -> {
                ensureBlankLine(out)
                val inner = StringBuilder()
                el.childNodes().forEach { renderBlock(it, inner, listDepth, listMarker = null, listIndex = 0) }
                inner.toString().trim().lines().forEach { line ->
                    out.append("> ").append(line).append("\n")
                }
                out.append("\n")
            }
            "ul" -> renderList(el, out, listDepth, ListMarker.UL)
            "ol" -> renderList(el, out, listDepth, ListMarker.OL)
            "li" -> {
                // Should be handled by parent ul/ol; if orphan, render inline.
                el.childNodes().forEach { renderInline(it, out) }
                out.append("\n")
            }
            "pre" -> {
                ensureBlankLine(out)
                val codeText = el.text()
                out.append("```\n").append(codeText).append("\n```\n\n")
            }
            "figure" -> el.childNodes().forEach { renderBlock(it, out, listDepth, listMarker, listIndex) }
            "figcaption" -> {
                ensureBlankLine(out)
                out.append("_")
                el.childNodes().forEach { renderInline(it, out) }
                out.append("_\n\n")
            }
            "img" -> {
                ensureBlankLine(out)
                out.append(imageMarkdown(el))
                out.append("\n\n")
            }
            "div", "section", "article", "main", "header", "footer", "aside" -> {
                el.childNodes().forEach { renderBlock(it, out, listDepth, listMarker, listIndex) }
            }
            else -> {
                // Inline-ish or unknown: try as inline within current line.
                el.childNodes().forEach { renderInline(it, out) }
            }
        }
    }

    private fun heading(el: Element, out: StringBuilder, prefix: String) {
        ensureBlankLine(out)
        out.append(prefix)
        el.childNodes().forEach { renderInline(it, out) }
        out.append("\n\n")
    }

    private fun renderList(el: Element, out: StringBuilder, listDepth: Int, marker: ListMarker) {
        ensureBlankLine(out)
        var idx = 1
        el.children().forEach { child ->
            if (!child.tagName().equals("li", ignoreCase = true)) return@forEach
            val indent = "  ".repeat(listDepth)
            val bullet = when (marker) {
                ListMarker.UL -> "- "
                ListMarker.OL -> "${idx}. "
            }
            out.append(indent).append(bullet)
            // Render li content: inline children inline, nested ul/ol recurse with deeper depth.
            child.childNodes().forEach { node ->
                if (node is Element && (node.tagName().equals("ul", true) || node.tagName().equals("ol", true))) {
                    out.append("\n")
                    renderList(
                        node,
                        out,
                        listDepth + 1,
                        if (node.tagName().equals("ul", true)) ListMarker.UL else ListMarker.OL,
                    )
                } else {
                    renderInline(node, out)
                }
            }
            if (!out.endsWith("\n")) out.append("\n")
            idx += 1
        }
        out.append("\n")
    }

    private fun renderInline(node: Node, out: StringBuilder) {
        when {
            node is TextNode -> out.append(escape(node.text()))
            node is Element -> when (node.tagName().lowercase()) {
                "a" -> {
                    val href = node.attr("href").trim()
                    val label = StringBuilder().also { sb -> node.childNodes().forEach { renderInline(it, sb) } }.toString()
                    if (href.isBlank()) {
                        out.append(label)
                    } else {
                        out.append('[').append(label.ifBlank { href }).append("](").append(href).append(')')
                    }
                }
                "img" -> out.append(imageMarkdown(node))
                "strong", "b" -> {
                    out.append("**")
                    node.childNodes().forEach { renderInline(it, out) }
                    out.append("**")
                }
                "em", "i" -> {
                    out.append("_")
                    node.childNodes().forEach { renderInline(it, out) }
                    out.append("_")
                }
                "code" -> {
                    out.append('`').append(node.text()).append('`')
                }
                "del", "s", "strike" -> {
                    out.append("~~")
                    node.childNodes().forEach { renderInline(it, out) }
                    out.append("~~")
                }
                "br" -> out.append("  \n")
                "span", "u", "small", "font" -> node.childNodes().forEach { renderInline(it, out) }
                else -> {
                    // Block-ish nested in inline context: flush the existing text then recurse as block.
                    if (out.isNotEmpty() && !out.endsWith("\n")) out.append("\n")
                    renderElement(node, out, listDepth = 0, listMarker = null, listIndex = 0)
                }
            }
        }
    }

    private fun imageMarkdown(el: Element): String {
        val src = el.attr("src").ifBlank { el.attr("data-src") }.trim()
        val alt = el.attr("alt").trim()
        if (src.isBlank()) return ""
        return "![${escape(alt)}](${src})"
    }

    private fun ensureBlankLine(out: StringBuilder) {
        if (out.isEmpty()) return
        if (out.endsWith("\n\n")) return
        if (out.endsWith("\n")) {
            out.append("\n")
        } else {
            out.append("\n\n")
        }
    }

    private fun escape(text: String): String {
        // Minimal escaping to avoid breaking markdown. We don't try to escape every special character;
        // article bodies are mostly prose and over-escaping makes output ugly.
        return text
            .replace(" ", " ")
    }

    private enum class ListMarker { UL, OL }
}
