package us.panks.opencode.dsl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import opencode.plugin.promise.ToolEditor
import us.panks.opencode.dsl.internal.jsObject

public fun OpenCodePluginBuilder.tools(block: ToolsDsl.() -> Unit) {
    ToolsDsl(this).apply(block)
}

@OpenCodePluginDsl
public class ToolsDsl internal constructor(
    private val plugin: OpenCodePluginBuilder,
) {
    public fun transform(block: ToolEditorScope.() -> Unit) {
        plugin.setup { context ->
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

    public fun <Input> tool(
        name: String,
        serializer: KSerializer<Input>,
        block: ToolBuilder<Input, ToolResult>.() -> Unit,
    ) {
        editor.add(ToolBuilder<Input, ToolResult>(name, serializer).apply(block).build())
    }

    public inline fun <reified Input> tool(
        name: String,
        noinline block: ToolBuilder<Input, ToolResult>.() -> Unit,
    ) {
        tool(name, serializer<Input>(), block)
    }
}
