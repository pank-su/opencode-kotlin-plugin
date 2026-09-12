import assert from "node:assert/strict"
import fs from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const sourcePath = path.join(root, "src", "jsMain", "kotlin", "us", "panks", "opencode", "guard", "OpenCodePlugin.kt")
const source = fs.readFileSync(sourcePath, "utf8")

assert.match(source, /fun createPluginDefinition\(\): Plugin\b/)
assert.ok(!/\bdynamic\b/.test(source), "production OpenCode interop must not use dynamic")
assert.match(source, /setupPlugin\(context: Context\)/)
assert.match(source, /registerTools\(editor: ToolEditor\)/)
assert.match(source, /evaluatePermission\(event: PermissionEvaluation\)/)

console.log("TYPED_INTEROP_OK")
