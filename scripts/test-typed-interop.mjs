import assert from "node:assert/strict"
import fs from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const sourcePath = path.join(root, "src", "jsMain", "kotlin", "us", "panks", "opencode", "guard", "OpenCodePlugin.kt")
const generatedPath = path.join(
  root,
  "build", "generated", "ksp", "js", "jsMain", "kotlin",
  "us", "panks", "opencode", "guard", "createPluginDefinitionOpenCodeGenerated.kt",
)
const source = fs.readFileSync(sourcePath, "utf8")
const generated = fs.readFileSync(generatedPath, "utf8")

assert.match(source, /@OpenCodePlugin\("panks\.kotlin-secret-guard"\)/)
assert.match(source, /@OpenCodePermission/)
assert.match(source, /@OpenCodeTool\(/)
assert.match(source, /@Serializable\s+data class PathInspectionInput/)
assert.ok(!/\bdynamic\b/.test(source), "production OpenCode example must not use dynamic")
assert.ok(!/\bobjectSchema\b/.test(source), "serialized tool input must not duplicate JSON Schema")
assert.ok(!/\bjson\s*\(/.test(source), "example must not construct raw JS objects")
assert.ok(!/\bInfo\s*</.test(source), "example must not depend on raw Tool.Info")
assert.ok(!/\bToolEditor\b/.test(source), "example must not depend on raw ToolEditor")
assert.match(generated, /@JsExport\s+public fun createPluginDefinition\(\): Plugin/)
assert.match(generated, /permissions\s*\{/)
assert.match(generated, /tools\s*\{/)
assert.match(generated, /tool<us\.panks\.opencode\.guard\.PathInspectionInput>/)

console.log("ANNOTATION_INTEROP_OK")
