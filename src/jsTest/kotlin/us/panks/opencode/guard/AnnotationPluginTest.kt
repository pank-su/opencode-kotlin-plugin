package us.panks.opencode.guard

import kotlinx.serialization.Serializable
import opencode.plugin.promise.PermissionEvaluation
import opencode.plugin.promise.ToolContext
import us.panks.opencode.annotations.OpenCodePermission
import us.panks.opencode.annotations.OpenCodePlugin
import us.panks.opencode.annotations.OpenCodeTool
import us.panks.opencode.dsl.ToolResult
import us.panks.opencode.dsl.toolResult
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
internal data class AnnotatedInspectInput(val path: String)

@OpenCodePlugin(
    id = "test.annotated-plugin",
    entryPoint = "createAnnotatedFixture",
)
internal class AnnotatedFixture {
    var inspectedPath: String? = null

    @OpenCodePermission
    suspend fun guard(event: PermissionEvaluation) {
        event.effect = "deny"
        event.message = "annotation"
    }

    @OpenCodeTool(
        name = "inspect_path",
        description = "Inspect \${notCode}\b",
        namespace = "guard",
        namespaceDescription = "Guard tools",
    )
    suspend fun inspect(input: AnnotatedInspectInput, context: ToolContext): ToolResult {
        inspectedPath = input.path
        return toolResult(content = input.path)
    }
}

@OpenCodePlugin(
    id = "test.nullable-input",
    entryPoint = "createNullableInputFixture",
)
internal class NullableInputFixture {
    @OpenCodeTool(name = "nullable", description = "Accept nullable input")
    suspend fun nullable(input: AnnotatedInspectInput?): ToolResult =
        toolResult(content = input?.path ?: "NULL")
}

class AnnotationPluginTest {
    @Test
    fun generatedEntrypointRegistersAnnotatedHandlers(): Promise<Unit> {
        var permissionCallback: dynamic = null
        var transformCallback: dynamic = null
        val permission: dynamic = js("({})")
        permission.hook = { _: String, callback: dynamic ->
            permissionCallback = callback
            Promise.resolve<dynamic>(js("({ dispose: () => Promise.resolve() })"))
        }
        val tool: dynamic = js("({})")
        tool.transform = { callback: dynamic ->
            transformCallback = callback
            Promise.resolve<dynamic>(js("({ dispose: () => Promise.resolve() })"))
        }
        val context: dynamic = js("({})")
        context.permission = permission
        context.tool = tool

        val plugin: dynamic = createAnnotatedFixture()
        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<dynamic> {
                val event: dynamic = js("({ effect: 'allow', message: null })")
                permissionCallback(event)
                    .unsafeCast<Promise<dynamic>>()
                    .then<dynamic> {
                        assertEquals("deny", event.effect as String)

                        var definition: dynamic = null
                        val editor: dynamic = js("({})")
                        editor.namespace = { _: dynamic -> Unit }
                        editor.add = { value: dynamic -> definition = value }
                        transformCallback(editor)
                        assertEquals("Inspect \${notCode}\b", definition.description as String)
                        assertEquals("string", definition.input.properties.path.type as String)
                        definition.execute(js("({ path: '/tmp/from-annotation' })"), js("({})"))
                    }
            }
            .then<Unit> { result ->
                assertEquals("/tmp/from-annotation", result.content as String)
            }
    }

    @Test
    fun generatedNullableToolPreservesNullability(): Promise<Unit> {
        var transformCallback: dynamic = null
        val tool: dynamic = js("({})")
        tool.transform = { callback: dynamic ->
            transformCallback = callback
            Promise.resolve<dynamic>(js("({ dispose: () => Promise.resolve() })"))
        }
        val context: dynamic = js("({})")
        context.tool = tool

        val plugin: dynamic = createNullableInputFixture()
        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<dynamic> {
                var definition: dynamic = null
                val editor: dynamic = js("({})")
                editor.add = { value: dynamic -> definition = value }
                transformCallback(editor)
                val alternatives = definition.input.anyOf.unsafeCast<Array<dynamic>>()
                assertEquals("object", alternatives[0].type as String)
                assertEquals("null", alternatives[1].type as String)
                definition.execute(null, js("({})"))
            }
            .then<Unit> { result ->
                assertEquals("NULL", result.content as String)
            }
    }
}
