package us.panks.opencode.guard

import js.promise.Promise

@JsModule("node:fs/promises")
private external object FileSystemPromises {
    fun realpath(path: String): Promise<String>
}

internal data class PathInspection(
    val requestedPath: String,
    val canonicalPath: String?,
    val blocked: Boolean,
    val canonicalizationFailed: Boolean = false,
) {
    fun message(): String = when {
        canonicalizationFailed ->
            "Kotlin Secret Guard blocked reading a path that could not be canonicalized safely: $requestedPath"

        canonicalPath != null && canonicalPath != requestedPath ->
            "Kotlin Secret Guard blocked reading a sensitive path: $requestedPath -> $canonicalPath"

        else ->
            "Kotlin Secret Guard blocked reading a sensitive path: $requestedPath"
    }

    fun summary(): String = when {
        canonicalizationFailed -> "BLOCKED: $requestedPath (canonicalization failed)"
        blocked && canonicalPath != null && canonicalPath != requestedPath ->
            "BLOCKED: $requestedPath -> $canonicalPath"
        blocked -> "BLOCKED: $requestedPath"
        else -> "ALLOWED: $requestedPath"
    }
}

internal object SensitivePathInspector {
    fun inspect(path: String): Promise<PathInspection> {
        if (SensitivePathPolicy.isSensitive(path)) {
            return Promise.resolve(
                PathInspection(
                    requestedPath = path,
                    canonicalPath = path,
                    blocked = true,
                ),
            )
        }

        return FileSystemPromises.realpath(path).then(
            onFulfilled = { canonicalPath ->
                PathInspection(
                    requestedPath = path,
                    canonicalPath = canonicalPath,
                    blocked = SensitivePathPolicy.isSensitive(canonicalPath),
                )
            },
            onRejected = {
                PathInspection(
                    requestedPath = path,
                    canonicalPath = null,
                    blocked = true,
                    canonicalizationFailed = true,
                )
            },
        )
    }
}
