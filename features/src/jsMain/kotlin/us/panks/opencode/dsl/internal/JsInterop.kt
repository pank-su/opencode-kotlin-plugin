package us.panks.opencode.dsl.internal

import kotlin.js.json

internal fun <T> jsObject(
    vararg properties: Pair<String, Any?>,
): T = json(*properties).unsafeCast<T>()
