import assert from "node:assert/strict"
import fs from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const coreRoot = path.join(root, "core")
const generatedRoot = path.join(coreRoot, "build", "generated", "kotlin")
const manifestPath = path.join(coreRoot, "build", "bindings-manifest.json")

assert.ok(fs.existsSync(manifestPath), `missing generated manifest: ${manifestPath}`)
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"))

assert.equal(manifest.sdk.package, "@opencode-ai/plugin")
assert.equal(manifest.sdk.version, "0.0.0-beta-19271")
assert.equal(manifest.sourceFileCount, 57)
assert.equal(manifest.pathFormat, "posix")
assert.deepEqual(manifest.sourceFiles, [...manifest.sourceFiles].sort())
assert.equal(manifest.reExports.total, 49)
assert.equal(manifest.reExports.unresolvedLocal.length, 0)
assert.ok(manifest.reExports.external.length > 0, "external re-exports must be tracked as opaque")
assert.equal(manifest.missingDeclarations.length, 0, `missing declarations: ${manifest.missingDeclarations.join(", ")}`)

function kotlinFiles(directory) {
  return fs.readdirSync(directory, { recursive: true, withFileTypes: true })
    .filter(entry => entry.isFile() && entry.name.endsWith(".kt"))
    .map(entry => path.join(entry.parentPath, entry.name))
    .sort()
}

const files = kotlinFiles(generatedRoot)
assert.equal(files.length, manifest.generatedFileCount)
assert.ok(files.length >= 300, `expected at least 300 generated Kotlin files, got ${files.length}`)

for (const group of ["core", "promise", "effect", "tui"]) {
  assert.ok(manifest.groups[group] > 0, `empty generated group: ${group}`)
}

for (const relative of [
  "opencode/plugin/App.kt",
  "opencode/plugin/promise/Plugin.kt",
  "opencode/plugin/promise/PermissionDomain.kt",
  "opencode/plugin/promise/ToolDomain.kt",
  "opencode/plugin/effect/Plugin.kt",
  "opencode/plugin/tui/UI.kt",
]) {
  assert.ok(fs.existsSync(path.join(generatedRoot, relative)), `missing representative API: ${relative}`)
}

for (const [relative, pattern] of [
  ["opencode/plugin/promise/ToolEditor.kt", /fun get\(id: String\): .*\?/],
  ["opencode/plugin/effect/ToolEditor.kt", /fun get\(id: String\): .*\?/],
  ["opencode/plugin/tui/Dialog.kt", /fun confirm\(options: DialogConfirmOptions\): js\.promise\.Promise<Boolean\?>/],
  ["opencode/plugin/tui/Dialog.kt", /fun prompt\(options: DialogPromptOptions\): js\.promise\.Promise<String\?>/],
]) {
  const content = fs.readFileSync(path.join(generatedRoot, relative), "utf8")
  assert.match(content, pattern, `nullable union was lost in ${relative}`)
}

for (const file of files) {
  const content = fs.readFileSync(file, "utf8")
  assert.ok(!content.includes(".karakum-sanitized"), `sanitized path leaked into ${file}`)
  assert.ok(!content.includes("/Users/"), `absolute path leaked into ${file}`)
  assert.ok(!content.includes("// unhandled import:"), `unhandled import leaked into ${file}`)
}

console.log(`BINDINGS_CONTRACT_OK files=${files.length} declarations=${manifest.exportedDeclarationCount}`)
