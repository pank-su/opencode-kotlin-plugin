package us.panks.opencode.tui

import opencode.plugin.tui.Cleanup as UpstreamCleanup
import opencode.plugin.tui.Context
import opencode.plugin.tui.Definition
import opencode.plugin.tui.define

public typealias TuiContext = Context
public typealias TuiDefinition = Definition
public typealias TuiCleanup = suspend () -> Unit

public fun tuiPlugin(
    id: String,
    setup: suspend (TuiContext) -> Unit,
): TuiDefinition = tuiDefinition(id) { context ->
    tuiPromise { setup(context) }
}

public fun tuiPluginWithCleanup(
    id: String,
    setup: suspend (TuiContext) -> TuiCleanup?,
): TuiDefinition = tuiDefinition(id) { context ->
    tuiPromise<UpstreamCleanup?> {
        val cleanup = setup(context) ?: return@tuiPromise null
        val adapted: UpstreamCleanup = {
            tuiPromise { cleanup() }
        }
        adapted
    }
}

private fun tuiDefinition(
    id: String,
    setup: (TuiContext) -> Any?,
): TuiDefinition {
    require(id.isNotBlank()) { "TUI plugin id must not be blank" }
    val definition: dynamic = js("({})")
    definition.id = id
    definition.setup = setup
    return define(definition.unsafeCast<TuiDefinition>())
}
