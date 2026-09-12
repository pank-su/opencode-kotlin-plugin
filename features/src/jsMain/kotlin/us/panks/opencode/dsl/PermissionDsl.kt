package us.panks.opencode.dsl

import js.promise.Promise
import opencode.plugin.promise.Context
import opencode.plugin.promise.PermissionEvaluation
import us.panks.opencode.dsl.internal.SetupAction

@OpenCodePluginDsl
public class PermissionDsl internal constructor(
    private val actions: MutableList<SetupAction>,
) {
    public fun evaluate(
        handler: (PermissionEvaluation) -> Unit,
    ) {
        register { event ->
            handler(event)
            Unit
        }
    }

    public fun evaluateAsync(
        handler: (PermissionEvaluation) -> Promise<Unit>,
    ) {
        register(handler)
    }

    private fun register(handler: (PermissionEvaluation) -> Any?) {
        actions += SetupAction { context: Context ->
            context.permission.hook("evaluate") { input ->
                handler(input.unsafeCast<PermissionEvaluation>())
            }
        }
    }
}
