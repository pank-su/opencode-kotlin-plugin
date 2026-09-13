package us.panks.opencode.tui

import js.promise.Promise
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

@OptIn(DelicateCoroutinesApi::class)
public fun <T> tuiPromise(block: suspend () -> T): Promise<T> =
    GlobalScope.promise { block() }.unsafeCast<Promise<T>>()
