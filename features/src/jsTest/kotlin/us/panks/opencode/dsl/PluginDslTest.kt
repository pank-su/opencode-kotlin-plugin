package us.panks.opencode.dsl

import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private external interface PathInput {
    val path: String
}

private external interface PathResult {
    val content: String
}

class PluginDslTest {
    @Test
    fun buildsPluginAndRegistersConfiguredDomains(): Promise<Unit> {
        var permissionHookName: String? = null
        var permissionCallback: dynamic = null
        var toolTransform: dynamic = null

        val plugin = opencodePlugin("example.typed-plugin") {
            permissions {
                evaluate { event ->
                    event.effect = "deny"
                    event.message = "blocked"
                }
            }
            tools {
                transform {
                    namespace("example", "Example tools")
                }
            }
        }

        val permission: dynamic = js("({})")
        permission.hook = { name: String, callback: dynamic ->
            permissionHookName = name
            permissionCallback = callback
            Promise.resolve(Unit)
        }
        val tool: dynamic = js("({})")
        tool.transform = { callback: dynamic ->
            toolTransform = callback
            Promise.resolve(Unit)
        }
        val context: dynamic = js("({})")
        context.permission = permission
        context.tool = tool

        assertEquals("example.typed-plugin", plugin.id)
        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<Unit> {
                assertEquals("evaluate", permissionHookName)
                assertNotNull(permissionCallback)
                assertNotNull(toolTransform)
            }
    }

    @Test
    fun cleanupDisposesRegistrationsInReverseOrder(): Promise<Unit> {
        val disposed = mutableListOf<String>()
        val plugin = opencodePlugin("example.cleanup") {
            permissions { evaluate { } }
            tools { transform { } }
        }

        fun registration(name: String): dynamic {
            val value: dynamic = js("({})")
            value.dispose = {
                disposed += name
                Promise.resolve(Unit)
            }
            return value
        }

        val permission: dynamic = js("({})")
        permission.hook = { _: String, _: dynamic -> Promise.resolve<dynamic>(registration("permission")) }
        val tool: dynamic = js("({})")
        tool.transform = { _: dynamic -> Promise.resolve<dynamic>(registration("tool")) }
        val context: dynamic = js("({})")
        context.permission = permission
        context.tool = tool

        var cleanupFunction: dynamic = null
        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<dynamic> { cleanup ->
                cleanupFunction = cleanup
                cleanup()
            }
            .then<dynamic> { cleanupFunction() }
            .then<Unit> {
                assertEquals(listOf("tool", "permission"), disposed)
            }
    }

    @Test
    fun failedSetupRollsBackPreviousRegistrations(): Promise<Unit> {
        val disposed = mutableListOf<String>()
        val plugin = opencodePlugin("example.rollback") {
            permissions { evaluate { } }
            tools { transform { } }
        }

        val permissionRegistration: dynamic = js("({})")
        permissionRegistration.dispose = {
            disposed += "permission"
            Promise.resolve(Unit)
        }
        val permission: dynamic = js("({})")
        permission.hook = { _: String, _: dynamic -> Promise.resolve<dynamic>(permissionRegistration) }
        val tool: dynamic = js("({})")
        tool.transform = { _: dynamic ->
            Promise<dynamic> { _, reject -> reject(Throwable("tool registration failed")) }
        }
        val context: dynamic = js("({})")
        context.permission = permission
        context.tool = tool

        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<Unit>(
                onFulfilled = { error("setup should reject") },
                onRejected = {
                    assertEquals(listOf("permission"), disposed)
                },
            )
    }

    @Test
    fun cleanupContinuesAfterDisposalFailure(): Promise<Unit> {
        val disposed = mutableListOf<String>()
        val plugin = opencodePlugin("example.cleanup-failure") {
            permissions { evaluate { } }
            tools { transform { } }
        }

        val permissionRegistration: dynamic = js("({})")
        permissionRegistration.dispose = {
            disposed += "permission"
            Promise.resolve(Unit)
        }
        val toolRegistration: dynamic = js("({})")
        toolRegistration.dispose = {
            disposed += "tool"
            Promise<dynamic> { _, reject -> reject(Throwable("tool dispose failed")) }
        }
        val permission: dynamic = js("({})")
        permission.hook = { _: String, _: dynamic -> Promise.resolve<dynamic>(permissionRegistration) }
        val tool: dynamic = js("({})")
        tool.transform = { _: dynamic -> Promise.resolve<dynamic>(toolRegistration) }
        val context: dynamic = js("({})")
        context.permission = permission
        context.tool = tool

        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<dynamic> { cleanup -> cleanup() }
            .then<Unit>(
                onFulfilled = { error("cleanup should reject") },
                onRejected = {
                    assertEquals(listOf("tool", "permission"), disposed)
                },
            )
    }

    @Test
    fun cleanupRejectsWhenDisposalRejectsWithUndefined(): Promise<Unit> {
        val disposed = mutableListOf<String>()
        val plugin = opencodePlugin("example.undefined-cleanup-failure") {
            permissions { evaluate { } }
            tools { transform { } }
        }

        val permissionRegistration: dynamic = js("({})")
        permissionRegistration.dispose = {
            disposed += "permission"
            Promise.resolve(Unit)
        }
        val toolRegistration: dynamic = js("({})")
        toolRegistration.dispose = {
            disposed += "tool"
            js("Promise.reject(undefined)")
        }
        val permission: dynamic = js("({})")
        permission.hook = { _: String, _: dynamic -> Promise.resolve<dynamic>(permissionRegistration) }
        val tool: dynamic = js("({})")
        tool.transform = { _: dynamic -> Promise.resolve<dynamic>(toolRegistration) }
        val context: dynamic = js("({})")
        context.permission = permission
        context.tool = tool

        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<dynamic> { cleanup -> cleanup() }
            .then<Unit>(
                onFulfilled = { error("cleanup should reject even when the reason is undefined") },
                onRejected = {
                    assertEquals(listOf("tool", "permission"), disposed)
                },
            )
    }

    @Test
    fun synchronousToolFailureBecomesRejectedPromise(): Promise<Unit> {
        var transformCallback: dynamic = null
        var executionReturned = false
        val plugin = opencodePlugin("example.tool-failure") {
            tools {
                transform {
                    tool<PathInput, PathResult>("failing_tool") {
                        description = "Always fails"
                        input(objectSchema {
                            string("path", required = true)
                        })
                        execute { _, _ -> error("handler failed") }
                    }
                }
            }
        }

        val toolDomain: dynamic = js("({})")
        toolDomain.transform = { callback: dynamic ->
            transformCallback = callback
            Promise.resolve(Unit)
        }
        val context: dynamic = js("({})")
        context.tool = toolDomain

        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<dynamic> {
                var definition: dynamic = null
                val editor: dynamic = js("({})")
                editor.add = { value: dynamic -> definition = value }
                transformCallback(editor)

                val input: dynamic = js("({})")
                input.path = "/tmp/demo"
                val execution = definition.execute(input, js("({})"))
                executionReturned = true
                execution
            }
            .then<Unit>(
                onFulfilled = { error("tool execution should reject") },
                onRejected = {
                    assertTrue(executionReturned, "execute must return a Promise before rejecting")
                },
            )
    }

    @Test
    fun buildsTypedToolWithObjectSchema(): Promise<Unit> {
        var transformCallback: dynamic = null
        val plugin = opencodePlugin("example.tool-plugin") {
            tools {
                transform {
                    namespace("example", "Example tools")
                    tool<PathInput, PathResult>("inspect_path") {
                        description = "Inspect a path"
                        input(objectSchema {
                            string("path", description = "Path to inspect", required = true)
                            additionalProperties = false
                        })
                        options {
                            namespace = "example"
                            codeMode = false
                        }
                        execute { input, _ ->
                            val result: dynamic = js("({})")
                            result.content = "PATH: ${input.path}"
                            result.unsafeCast<PathResult>()
                        }
                    }
                }
            }
        }

        val toolDomain: dynamic = js("({})")
        toolDomain.transform = { callback: dynamic ->
            transformCallback = callback
            Promise.resolve(Unit)
        }
        val context: dynamic = js("({})")
        context.tool = toolDomain

        return plugin.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<dynamic> {
                var namespace: dynamic = null
                var definition: dynamic = null
                val editor: dynamic = js("({})")
                editor.namespace = { value: dynamic -> namespace = value }
                editor.add = { value: dynamic -> definition = value }
                transformCallback(editor)

                assertEquals("example", namespace.name as String)
                assertEquals("inspect_path", definition.name as String)
                assertEquals("object", definition.input.type as String)
                assertEquals("string", definition.input.properties.path.type as String)
                assertTrue(definition.input.required.unsafeCast<Array<String>>().contains("path"))

                val input: dynamic = js("({})")
                input.path = "/tmp/demo"
                definition.execute(input, js("({})"))
            }
            .then<Unit> { result ->
                assertEquals("PATH: /tmp/demo", result.content as String)
            }
    }
}
