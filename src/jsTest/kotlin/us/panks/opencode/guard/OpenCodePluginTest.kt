package us.panks.opencode.guard

import opencode.plugin.promise.Plugin
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@JsModule("node:fs")
private external object TestFileSystem {
    fun mkdtempSync(prefix: String): String
    fun writeFileSync(path: String, content: String)
    fun symlinkSync(target: String, path: String)
    fun rmSync(path: String, options: dynamic)
}

class OpenCodePluginTest {
    @Test
    fun exportsGeneratedPluginType() {
        val plugin: Plugin = createPluginDefinition()

        assertEquals("panks.kotlin-secret-guard", plugin.id)
    }

    @Test
    fun exportsStablePluginId() {
        val plugin: dynamic = createPluginDefinition()

        assertEquals("panks.kotlin-secret-guard", plugin.id as String)
    }

    @Test
    fun exportsSetupFunction() {
        val plugin: dynamic = createPluginDefinition()

        assertNotNull(plugin.setup)
        assertEquals("function", jsTypeOf(plugin.setup))
    }

    @Test
    fun registersPermissionEvaluationHook(): Promise<Unit> {
        var registeredHook: String? = null
        val context = fakeContext(
            onPermissionHook = { name, _ -> registeredHook = name },
        )

        val plugin: dynamic = createPluginDefinition()
        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<Unit> {
                assertEquals("evaluate", registeredHook)
            }
    }

    @Test
    fun deniesReadPermissionForSensitivePath() {
        var evaluate: dynamic = null
        val context = fakeContext(
            onPermissionHook = { _, callback -> evaluate = callback },
        )
        val plugin: dynamic = createPluginDefinition()
        plugin.setup(context)

        val event: dynamic = js("({})")
        event.action = "read"
        event.resources = arrayOf("/project/.env")
        event.effect = "allow"
        evaluate(event)

        assertEquals("deny", event.effect as String)
    }

    @Test
    fun deniesReadPermissionThroughSymlink(): Promise<Unit> {
        var evaluate: dynamic = null
        val context = fakeContext(
            onPermissionHook = { _, callback -> evaluate = callback },
        )
        val plugin: dynamic = createPluginDefinition()
        plugin.setup(context)

        val directory = TestFileSystem.mkdtempSync("/tmp/opencode-kotlin-guard-")
        val sensitivePath = "$directory/.env"
        val aliasPath = "$directory/safe.txt"
        TestFileSystem.writeFileSync(sensitivePath, "not-a-real-secret")
        TestFileSystem.symlinkSync(sensitivePath, aliasPath)

        val event: dynamic = js("({})")
        event.action = "read"
        event.resources = arrayOf(aliasPath)
        event.effect = "allow"

        return evaluate(event)
            .unsafeCast<Promise<dynamic>>()
            .then<Unit> {
                try {
                    assertEquals("deny", event.effect as String)
                } finally {
                    val options: dynamic = js("({ recursive: true, force: true })")
                    TestFileSystem.rmSync(directory, options)
                }
            }
    }

    @Test
    fun registersToolTransform(): Promise<Unit> {
        var transform: dynamic = null
        val context = fakeContext(
            onToolTransform = { callback -> transform = callback },
        )

        val plugin: dynamic = createPluginDefinition()
        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<Unit> {
                assertNotNull(transform)
                assertEquals("function", jsTypeOf(transform))
            }
    }

    @Test
    fun addsNamespacedPathInspectionTool() {
        var transform: dynamic = null
        val context = fakeContext(
            onToolTransform = { callback -> transform = callback },
        )
        val plugin: dynamic = createPluginDefinition()
        plugin.setup(context)

        var namespace: dynamic = null
        var toolDefinition: dynamic = null
        val editor: dynamic = js("({})")
        editor.namespace = { value: dynamic -> namespace = value }
        editor.add = { value: dynamic -> toolDefinition = value }
        transform(editor)

        assertEquals("kotlin_guard", namespace.name as String)
        assertEquals("inspect_path", toolDefinition.name as String)
        assertEquals("kotlin_guard", toolDefinition.options.namespace as String)
    }

    @Test
    fun inspectionToolReportsBlockedPath(): Promise<Unit> {
        var transform: dynamic = null
        val context = fakeContext(
            onToolTransform = { callback -> transform = callback },
        )
        val plugin: dynamic = createPluginDefinition()
        plugin.setup(context)

        var toolDefinition: dynamic = null
        val editor: dynamic = js("({})")
        editor.namespace = { _: dynamic -> }
        editor.add = { value: dynamic -> toolDefinition = value }
        transform(editor)

        val input: dynamic = js("({})")
        input.path = "/project/.env"
        val toolContext: dynamic = js("({})")
        return toolDefinition.execute(input, toolContext)
            .unsafeCast<Promise<dynamic>>()
            .then<Unit> { result ->
                val content = result.content as String
                assertTrue(content.contains("BLOCKED"))
                assertTrue(content.contains("/project/.env"))
            }
    }

    private fun fakeContext(
        onPermissionHook: (String, dynamic) -> Unit = { _, _ -> },
        onToolTransform: (dynamic) -> Unit = {},
    ): dynamic {
        val permission: dynamic = js("({})")
        permission.hook = { name: String, callback: dynamic ->
            onPermissionHook(name, callback)
            Promise.resolve(Unit)
        }

        val tool: dynamic = js("({})")
        tool.transform = { callback: dynamic ->
            onToolTransform(callback)
            Promise.resolve(Unit)
        }

        val context: dynamic = js("({})")
        context.permission = permission
        context.tool = tool
        return context
    }
}
