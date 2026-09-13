package us.panks.opencode.dsl.internal

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
internal fun <T> jsObject(vararg entries: Pair<String, Any?>): T {
    val result = js("({})")
    for ((key, value) in entries) result[key] = value
    return result.unsafeCast<T>()
}
