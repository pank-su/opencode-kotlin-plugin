package us.panks.opencode.guard

import js.promise.await
import kotlinx.serialization.Serializable
import opencode.plugin.promise.PermissionEvaluation
import us.panks.opencode.annotations.OpenCodePermission
import us.panks.opencode.annotations.OpenCodePlugin
import us.panks.opencode.annotations.OpenCodeTool
import us.panks.opencode.dsl.ToolResult
import us.panks.opencode.dsl.deny
import us.panks.opencode.dsl.resourcePaths
import us.panks.opencode.dsl.toolResult

@Serializable
data class PathInspectionInput(val path: String)

@OpenCodePlugin("panks.kotlin-secret-guard")
class SecretGuardPlugin {
    @OpenCodePermission
    suspend fun evaluatePermission(event: PermissionEvaluation) {
        if (event.action != "read") return
        for (resource in event.resourcePaths) {
            val inspection = SensitivePathInspector.inspect(resource).await()
            if (inspection.blocked) {
                event.deny(inspection.message())
                return
            }
        }
    }

    @OpenCodeTool(
        name = "inspect_path",
        description = "Check whether a path looks sensitive without reading its contents",
        namespace = "kotlin_guard",
        namespaceDescription = "Tools implemented with Kotlin/JS for inspecting potentially sensitive paths",
    )
    suspend fun inspectPath(input: PathInspectionInput): ToolResult {
        val inspection = SensitivePathInspector.inspect(input.path).await()
        return toolResult(content = inspection.summary())
    }
}
