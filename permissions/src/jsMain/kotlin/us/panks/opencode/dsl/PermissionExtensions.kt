package us.panks.opencode.dsl

import opencode.plugin.promise.PermissionEvaluation

public val PermissionEvaluation.resourcePaths: List<String>
    get() = resources.unsafeCast<Array<String>>().asList()

public fun PermissionEvaluation.deny(message: String) {
    effect = "deny"
    this.message = message
}
