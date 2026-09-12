package us.panks.opencode.dsl

import opencode.plugin.promise.Context
import opencode.plugin.promise.ToolEditor
import us.panks.opencode.dsl.internal.SetupAction
import us.panks.opencode.dsl.internal.jsObject

@OpenCodePluginDsl
public class ToolsDsl internal constructor(
    private val actions: MutableList<SetupAction>,
) {
    public fun transform(
        block: ToolEditorScope.() -> Unit,
    ) {
        actions += SetupAction { context: Context ->
            context.tool.transform { editor ->
                ToolEditorScope(editor).block()
            }
        }
    }
}

@OpenCodePluginDsl
public class ToolEditorScope internal constructor(
    private val editor: ToolEditor,
) {
    public fun namespace(
        name: String,
        description: String = "Tools in namespace $name",
    ) {
        require(name.isNotBlank()) { "Tool namespace name must not be blank" }
        require(description.isNotBlank()) { "Tool namespace description must not be blank" }
        editor.namespace(
            jsObject<Any?>(
                "name" to name,
                "description" to description,
            ),
        )
    }

    public fun <Input, Output> tool(
        name: String,
        block: ToolBuilder<Input, Output>.() -> Unit,
    ) {
        editor.add(ToolBuilder<Input, Output>(name).apply(block).build())
    }
}
