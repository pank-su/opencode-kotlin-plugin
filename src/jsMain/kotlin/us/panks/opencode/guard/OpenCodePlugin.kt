package us.panks.opencode.guard

import js.promise.Promise
import opencode.plugin.promise.Context
import opencode.plugin.promise.Info
import opencode.plugin.promise.PermissionEvaluation
import opencode.plugin.promise.Plugin
import opencode.plugin.promise.ToolContext
import opencode.plugin.promise.ToolEditor
import kotlin.js.json

private external interface PathInspectionInput {
    val path: String
}

private external interface PathInspectionResult {
    val content: String
}

@JsModule("node:fs/promises")
private external object FileSystemPromises {
    fun realpath(path: String): Promise<String>
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun createPluginDefinition(): Plugin = json(
    "id" to "panks.kotlin-secret-guard",
    "setup" to { context: Context -> setupPlugin(context) },
).unsafeCast<Plugin>()

private fun setupPlugin(context: Context): Promise<Unit> {
    val permissionRegistration = context.permission.hook("evaluate") { input ->
        evaluatePermission(input.unsafeCast<PermissionEvaluation>())
    }
    val toolRegistration = context.tool.transform { editor ->
        registerTools(editor)
    }

    return permissionRegistration
        .flatThen { toolRegistration }
        .then { Unit }
}

private fun registerTools(editor: ToolEditor) {
    val namespace = json(
        "name" to "kotlin_guard",
        "description" to "Tools implemented with Kotlin/JS for inspecting potentially sensitive paths",
    )
    editor.namespace(namespace)

    val pathProperty = json(
        "type" to "string",
        "description" to "File path to inspect without reading the file",
    )
    val inputSchema = json(
        "type" to "object",
        "properties" to json("path" to pathProperty),
        "required" to arrayOf("path"),
        "additionalProperties" to false,
    )
    val options = json(
        "namespace" to "kotlin_guard",
        "codemode" to false,
    )
    val execute: (Any?, ToolContext) -> Promise<Any?> = { input, _ ->
        val path = input.unsafeCast<PathInspectionInput>().path
        val status = if (SensitivePathPolicy.isSensitive(path)) "BLOCKED" else "ALLOWED"
        val result = json("content" to "$status: $path").unsafeCast<PathInspectionResult>()
        Promise.resolve<Any?>(result)
    }
    val tool = json(
        "name" to "inspect_path",
        "description" to "Check whether a path looks sensitive without reading its contents",
        "input" to inputSchema,
        "options" to options,
        "execute" to execute,
    ).unsafeCast<Info<Any?, Any?>>()

    editor.add(tool)
}

private fun evaluatePermission(event: PermissionEvaluation): Promise<Unit> {
    if (event.action != "read") return Promise.resolve(Unit)

    val resources = event.resources.unsafeCast<Array<String>>()
    return evaluateResource(event, resources, index = 0)
}

private fun evaluateResource(
    event: PermissionEvaluation,
    resources: Array<String>,
    index: Int,
): Promise<Unit> {
    if (index >= resources.size) return Promise.resolve(Unit)

    val requestedPath = resources[index]
    if (SensitivePathPolicy.isSensitive(requestedPath)) {
        deny(event, requestedPath)
        return Promise.resolve(Unit)
    }

    return canonicalPathOrSelf(requestedPath).flatThen { canonicalPath ->
        if (SensitivePathPolicy.isSensitive(canonicalPath)) {
            deny(event, requestedPath, canonicalPath)
            Promise.resolve(Unit)
        } else {
            evaluateResource(event, resources, index + 1)
        }
    }
}

private fun canonicalPathOrSelf(path: String): Promise<String> = FileSystemPromises.realpath(path).then(
    onFulfilled = { it },
    onRejected = { path },
)

private fun deny(
    event: PermissionEvaluation,
    requestedPath: String,
    canonicalPath: String = requestedPath,
) {
    event.effect = "deny"
    event.message = if (canonicalPath == requestedPath) {
        "Kotlin Secret Guard blocked reading a sensitive path: $requestedPath"
    } else {
        "Kotlin Secret Guard blocked reading a sensitive path: $requestedPath -> $canonicalPath"
    }
}
