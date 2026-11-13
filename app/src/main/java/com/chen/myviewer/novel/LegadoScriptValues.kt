package com.chen.myviewer.novel

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.mozilla.javascript.*
import java.util.IdentityHashMap

/** Exposes only the DOM operations needed by source scripts. */
internal class LegadoScriptValues(private val cx: Context, private val scope: Scriptable, private val checkActive: () -> Unit) {
    private val elements = IdentityHashMap<Scriptable, Element>()

    fun function(action: (Array<out Any>) -> Any?): BaseFunction = object : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any? {
            checkActive()
            return try { action(args) }
            catch (error: Exception) { throw Context.throwAsScriptRuntimeEx(error) }
        }
    }

    fun toJs(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is Element -> element(value)
        is JSONObject, is JSONArray -> cx.evaluateString(scope, "JSON.parse(${JSONObject.quote(value.toString())})", "novel-data", 1, null)
        is List<*> -> cx.newArray(scope, value.map(::toJs).toTypedArray()).apply {
            ScriptableObject.putProperty(this, "get", function { get(Context.toNumber(it.firstOrNull()).toInt(), this) })
            ScriptableObject.putProperty(this, "size", function { value.size })
            ScriptableObject.putProperty(this, "first", function { if (value.isEmpty()) null else get(0, this) })
            ScriptableObject.putProperty(this, "text", function { value.joinToString(" ") { if (it is Element) it.text() else it.toString() } })
            ScriptableObject.putProperty(this, "html", function { value.joinToString("\n") { if (it is Element) it.outerHtml() else it.toString() } })
        }
        else -> Context.javaToJS(value, scope)
    }

    fun fromJs(value: Any?): Any? = when (value) {
        null, Undefined.instance, Scriptable.NOT_FOUND -> null
        is Number -> Context.toString(value)
        is CharSequence -> value.toString()
        is NativeArray -> (0 until value.length.toInt()).mapNotNull { fromJs(value.get(it, value)) }
        is Scriptable -> elements[value] ?: run {
            ScriptableObject.putProperty(scope, "__output", value)
            val json = Context.toString(cx.evaluateString(scope, "JSON.stringify(__output)", "novel-result", 1, null))
            if (json.startsWith('{')) JSONObject(json) else json
        }
        else -> value
    }

    private fun element(value: Element): Scriptable = cx.newObject(scope).apply {
        elements[this] = value
        ScriptableObject.putProperty(this, "textContent", value.text())
        ScriptableObject.putProperty(this, "text", function { value.text() })
        ScriptableObject.putProperty(this, "html", function { value.html() })
        ScriptableObject.putProperty(this, "outerHtml", function { value.outerHtml() })
        ScriptableObject.putProperty(this, "toString", function { value.outerHtml() })
        ScriptableObject.putProperty(this, "attr", function { value.attr(Context.toString(it.firstOrNull() ?: "")) })
        ScriptableObject.putProperty(this, "select", function { toJs(value.select(Context.toString(it.firstOrNull() ?: "")).toList()) })
        ScriptableObject.putProperty(this, "selectFirst", function { toJs(value.selectFirst(Context.toString(it.firstOrNull() ?: ""))) })
        ScriptableObject.putProperty(this, "children", function { toJs(value.children().toList()) })
        ScriptableObject.putProperty(this, "parent", function { toJs(value.parent()) })
    }
}
