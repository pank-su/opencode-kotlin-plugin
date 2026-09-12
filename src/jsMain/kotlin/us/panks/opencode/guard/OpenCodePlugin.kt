package us.panks.opencode.guard

import js.promise.Promise
import opencode.plugin.promise.PermissionEvaluation
import opencode.plugin.promise.Plugin
import us.panks.opencode.dsl.deny
import us.panks.opencode.dsl.objectSchema
import us.panks.opencode.dsl.opencodePlugin
import us.panks.opencode.dsl.resourcePaths
import us.panks.opencode.dsl.toolResult

private external interface PathInspectionInput {
    val path: String
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun createPluginDefinition(): Plugin = opencodePlugin("panks.kotlin-secret-guard") {
    permissions {
        evaluateAsync(::evaluatePermission)
    }
    tools {
        transform {
            namespace(
                name = "kotlin_guard",
                description = "Tools implemented with Kotlin/JS for inspecting potentially sensitive paths",
            )
            tool<PathInspectionInput, us.panks.opencode.dsl.ToolResult>("inspect_path") {
                description = "Check whether a path looks sensitive without reading its contents"
                input(objectSchema {
                    string(
                        name = "path",
                        description = "File path to inspect without reading the file",
                        required = true,
                    )
                    additionalProperties = false
                })
                options {
                    namespace = "kotlin_guard"
                    codeMode = false
                }
                executeAsync { input, _ ->
                    SensitivePathInspector.inspect(input.path).then { inspection ->
                        toolResult(content = inspection.summary())
                    }
                }
            }
        }
    }
}

private fun evaluatePermission(event: PermissionEvaluation): Promise<Unit> {
    if (event.action != "read") return Promise.resolve(Unit)
    return evaluateResource(event, event.resourcePaths, index = 0)
}

private fun evaluateResource(
    event: PermissionEvaluation,
    resources: List<String>,
    index: Int,
): Promise<Unit> {
    if (index >= resources.size) return Promise.resolve(Unit)

    return SensitivePathInspector.inspect(resources[index]).flatThen { inspection ->
        if (inspection.blocked) {
            event.deny(inspection.message())
            Promise.resolve(Unit)
        } else {
            evaluateResource(event, resources, index + 1)
        }
    }
}
