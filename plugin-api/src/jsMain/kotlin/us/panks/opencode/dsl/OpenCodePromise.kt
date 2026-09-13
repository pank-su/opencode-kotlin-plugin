package us.panks.opencode.dsl

import js.promise.Promise
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

@OptIn(DelicateCoroutinesApi::class)
public fun <T> openCodePromise(block: suspend () -> T): Promise<T> =
    GlobalScope.promise { block() }.unsafeCast<Promise<T>>()
