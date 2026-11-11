package com.chen.myviewer.source

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/** Jsoup implements the DOM and selector operations used by Aidoku's SwiftSoup host. */
internal class AidokuHtml(private val host: AidokuHost) {
    fun call(name: String, args: LongArray): Long {
        fun text(index: Int) = host.string(args, index)
        fun store(value: Any?) = host.store(value).toLong()
        val value = host.values[args.getOrElse(0) { -1 }.toInt()]
        val node = value as? Node
        val element = value as? Element
        val elements = when (value) { is Elements -> value; is Element -> Elements(value); else -> null }
        return when (name) {
            "parse" -> store(Jsoup.parse(text(0), text(2)))
            "parse_fragment" -> store(Jsoup.parseBodyFragment(text(0), text(2)))
            "escape" -> store(Entities.escape(text(0)))
            "unescape" -> store(Entities.unescape(text(0)))
            "kind" -> when (value) { is Document -> 7; is Elements -> 6; is Element -> 5; is Comment -> 4; is DataNode -> 3; is TextNode -> 2; is Node -> 1; else -> 0 }
            "child_nodes" -> store(node?.childNodes())
            "children" -> store(element?.children())
            "size" -> (value as? List<*>)?.size?.toLong() ?: -1
            "get" -> store((value as? List<*>)?.getOrNull(args[1].toInt()))
            "first" -> store((value as? List<*>)?.firstOrNull())
            "last" -> store((value as? List<*>)?.lastOrNull())
            "parent" -> store(node?.parent())
            "siblings" -> store(element?.siblingElements())
            "next" -> store(node?.nextSibling())
            "previous" -> store(node?.previousSibling())
            "base_uri" -> store(node?.baseUri())
            "own_text" -> store(element?.ownText())
            "data" -> store(element?.data())
            "id" -> store(element?.id())
            "tag_name" -> store(element?.tagName())
            "class_name" -> store(element?.className())
            "has_class" -> if (element?.hasClass(text(1)) == true) 1 else 0
            "has_attr" -> if (node?.hasAttr(text(1)) == true) 1 else 0
            "attr" -> store(node?.attr(text(1)))
            "outer_html" -> store(elements?.outerHtml() ?: node?.outerHtml())
            "html" -> store(elements?.html())
            "text" -> store(elements?.text() ?: (node as? TextNode)?.text())
            "untrimmed_text" -> store(elements?.joinToString(" ") { it.wholeText() } ?: (node as? TextNode)?.wholeText)
            "select" -> store(elements?.select(text(1)))
            "select_first" -> store(elements?.select(text(1))?.firstOrNull())
            "set_attr" -> { node?.attr(text(1), text(3)); 0 }
            "remove_attr" -> { node?.removeAttr(text(1)); 0 }
            "set_text" -> { element?.text(text(1)); 0 }
            "set_html" -> { element?.html(text(1)); 0 }
            "prepend" -> { element?.prepend(text(1)); 0 }
            "append" -> { element?.append(text(1)); 0 }
            "add_class" -> { element?.addClass(text(1)); 0 }
            "remove_class" -> { element?.removeClass(text(1)); 0 }
            "remove" -> { elements?.remove() ?: node?.remove(); 0 }
            else -> error("暂不支持漫画源接口 html.$name。")
        }
    }
}
