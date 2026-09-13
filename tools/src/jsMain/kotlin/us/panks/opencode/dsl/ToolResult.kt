package us.panks.opencode.dsl

import us.panks.opencode.dsl.internal.jsObject

public external interface ToolResult {
    val content: Any?
    val output: Any?
    val metadata: Any?
}

public fun toolResult(
    content: String? = null,
    output: Any? = null,
    metadata: Any? = null,
): ToolResult {
    val values = mutableListOf<Pair<String, Any?>>()
    content?.let { values += "content" to it }
    output?.let { values += "output" to it }
    metadata?.let { values += "metadata" to it }
    return jsObject(*values.toTypedArray())
}
