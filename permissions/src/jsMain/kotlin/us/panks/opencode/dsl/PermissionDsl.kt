package us.panks.opencode.dsl

import opencode.plugin.promise.PermissionEvaluation

public fun OpenCodePluginBuilder.permissions(block: PermissionDsl.() -> Unit) {
    PermissionDsl(this).apply(block)
}

@OpenCodePluginDsl
public class PermissionDsl internal constructor(
    private val plugin: OpenCodePluginBuilder,
) {
    public fun evaluate(handler: suspend (PermissionEvaluation) -> Unit) {
        plugin.setup { context ->
            context.permission.hook("evaluate") { input ->
                openCodePromise {
                    handler(input.unsafeCast<PermissionEvaluation>())
                }
            }
        }
    }
}
