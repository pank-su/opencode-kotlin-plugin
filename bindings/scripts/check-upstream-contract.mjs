import assert from "node:assert/strict"
import fs from "node:fs"
import path from "node:path"
import ts from "typescript"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const packageRoot = path.join(root, "node_modules", "@opencode-ai", "plugin")
const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, "package.json"), "utf8"))

assert.equal(packageJson.version, "0.0.0-beta-19271", "Unexpected OpenCode plugin SDK version")

function source(relativePath) {
  const fileName = path.join(packageRoot, relativePath)
  return ts.createSourceFile(
    fileName,
    fs.readFileSync(fileName, "utf8"),
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS,
  )
}

function declaration(file, name) {
  const node = file.statements.find((statement) => statement.name?.text === name)
  assert.ok(node, `Missing declaration ${name} in ${file.fileName}`)
  return node.getText(file)
}

function includesAll(label, text, fragments) {
  for (const fragment of fragments) {
    assert.ok(text.includes(fragment), `${label} no longer contains: ${fragment}`)
  }
}

const plugin = source("dist/promise/plugin.d.ts")
includesAll("Plugin", declaration(plugin, "Plugin"), ["readonly id: string", "readonly setup:"])
includesAll("Context", declaration(plugin, "Context"), [
  "readonly permission: PermissionDomain",
  "readonly tool: ToolDomain",
])

const permission = source("dist/promise/permission.d.ts")
includesAll("PermissionEvaluation", declaration(permission, "PermissionEvaluation"), [
  "readonly action: string",
  "readonly resources: ReadonlyArray<string>",
  "effect: Permission.Effect",
  "message?: string",
])
includesAll("PermissionDomain", declaration(permission, "PermissionDomain"), [
  "readonly hook: Hooks<PermissionHooks>",
])

const tool = source("dist/promise/tool.d.ts")
includesAll("ToolEditor", declaration(tool, "ToolEditor"), [
  "namespace(namespace: Tool.Namespace): void",
  "add<",
])
includesAll("ToolDomain", declaration(tool, "ToolDomain"), [
  "readonly transform: Transform<ToolEditor>",
])

console.log(`UPSTREAM_CONTRACT_OK @opencode-ai/plugin@${packageJson.version}`)
