import assert from "node:assert/strict"
import fs from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const sourcePath = path.join(root, "src", "jsMain", "kotlin", "us", "panks", "opencode", "guard", "OpenCodePlugin.kt")
const source = fs.readFileSync(sourcePath, "utf8")

assert.match(source, /fun createPluginDefinition\(\): Plugin\s*=\s*opencodePlugin\(/)
assert.ok(!/\bdynamic\b/.test(source), "production OpenCode example must not use dynamic")
assert.ok(!/\bjson\s*\(/.test(source), "example must not construct raw JS objects")
assert.ok(!/\bInfo\s*</.test(source), "example must not depend on raw Tool.Info")
assert.ok(!/\bToolEditor\b/.test(source), "example must not depend on raw ToolEditor")
assert.match(source, /permissions\s*\{/)
assert.match(source, /tools\s*\{/)
assert.match(source, /evaluatePermission\(event: PermissionEvaluation\)/)

console.log("TYPED_INTEROP_OK")
