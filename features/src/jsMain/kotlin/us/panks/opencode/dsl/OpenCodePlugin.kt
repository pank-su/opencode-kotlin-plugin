package us.panks.opencode.dsl

import opencode.plugin.promise.Context
import opencode.plugin.promise.Plugin
import us.panks.opencode.dsl.internal.SetupAction
import us.panks.opencode.dsl.internal.executeSetupPlan
import us.panks.opencode.dsl.internal.jsObject

public fun opencodePlugin(
    id: String,
    block: OpenCodePluginBuilder.() -> Unit,
): Plugin {
    require(id.isNotBlank()) { "Plugin id must not be blank" }
    val builder = OpenCodePluginBuilder().apply(block)
    return jsObject(
        "id" to id,
        "setup" to { context: Context -> executeSetupPlan(context, builder.actions) },
    )
}

@OpenCodePluginDsl
public class OpenCodePluginBuilder internal constructor() {
    internal val actions: MutableList<SetupAction> = mutableListOf()

    public fun permissions(block: PermissionDsl.() -> Unit) {
        PermissionDsl(actions).apply(block)
    }

    public fun tools(block: ToolsDsl.() -> Unit) {
        ToolsDsl(actions).apply(block)
    }
}
