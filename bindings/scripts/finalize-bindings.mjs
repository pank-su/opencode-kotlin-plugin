import assert from "node:assert/strict"
import crypto from "node:crypto"
import fs from "node:fs"
import path from "node:path"
import ts from "typescript"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const packageRoot = path.join(root, "node_modules", "@opencode-ai", "plugin")
const sourceRoot = path.join(packageRoot, ".karakum-sanitized", "dist")
const generatedRoot = path.join(root, "build", "generated", "kotlin")
const manifestPath = path.join(root, "build", "bindings-manifest.json")
const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, "package.json"), "utf8"))

function walk(directory, predicate) {
  const result = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) result.push(...walk(file, predicate))
    else if (predicate(file)) result.push(file)
  }
  return result.sort()
}

function patchRuntimeAlias(relativePath, kotlinName) {
  const file = path.join(generatedRoot, relativePath)
  let content = fs.readFileSync(file, "utf8")
  const pattern = new RegExp(`^(external fun(?: <[^>]+>)? ${kotlinName}\\()`, "m")
  assert.match(content, pattern, `missing runtime alias target ${kotlinName}`)
  content = content.replace(pattern, `@JsName("prepareSource")\n$1`)
  fs.writeFileSync(file, content)
}

patchRuntimeAlias("opencode/plugin/prepareSourceBun.kt", "prepareSourceBun")
patchRuntimeAlias("opencode/plugin/prepareSourceNode.kt", "prepareSourceNode")

const groupForSource = relative => {
  const first = relative.split(path.sep)[0]
  return ["promise", "effect", "tui"].includes(first) ? first : "core"
}
const groupForKotlin = relative => {
  const normalized = relative.split(path.sep).join("/")
  for (const group of ["promise", "effect", "tui"]) {
    if (normalized.startsWith(`opencode/plugin/${group}/`)) return group
  }
  return "core"
}

function isExported(statement) {
  return statement.modifiers?.some(modifier => modifier.kind === ts.SyntaxKind.ExportKeyword) ?? false
}

const expected = new Set()
const sourceFiles = walk(sourceRoot, file => file.endsWith(".d.ts"))
for (const file of sourceFiles) {
  const relative = path.relative(sourceRoot, file)
  const group = groupForSource(relative)
  const source = ts.createSourceFile(file, fs.readFileSync(file, "utf8"), ts.ScriptTarget.Latest, true)
  for (const statement of source.statements) {
    if (!isExported(statement)) continue
    if (ts.isVariableStatement(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        if (ts.isIdentifier(declaration.name)) expected.add(`${group}:${declaration.name.text}`)
      }
      continue
    }
    if (statement.name && ts.isIdentifier(statement.name)) expected.add(`${group}:${statement.name.text}`)
  }
}

const declarationPattern = /\b(?:sealed\s+)?(?:external\s+)?(?:interface|class|object)\s+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)|\btypealias\s+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)|\bexternal\s+(?:val|var)\s+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)|\bexternal\s+fun\s+(?:<[^>]+>\s*)?(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)/g
const actual = new Set()
const generatedFiles = walk(generatedRoot, file => file.endsWith(".kt"))
for (const file of generatedFiles) {
  const content = fs.readFileSync(file, "utf8")
  const cleaned = content
    .replace(/^\/\/ unhandled import:.*\n/gm, "")
    .replace(/\n{3,}/g, "\n\n")
  if (cleaned !== content) fs.writeFileSync(file, cleaned)
}

const groups = { core: 0, promise: 0, effect: 0, tui: 0 }
let opaqueTypeOccurrences = 0
for (const file of generatedFiles) {
  const relative = path.relative(generatedRoot, file)
  const group = groupForKotlin(relative)
  groups[group] += 1
  const content = fs.readFileSync(file, "utf8")
  opaqueTypeOccurrences += content.match(/\bAny\?/g)?.length ?? 0
  for (const match of content.matchAll(declarationPattern)) {
    const rawName = match.slice(1).find(Boolean)
    actual.add(`${group}:${rawName.replaceAll("`", "")}`)
  }
}

const missingDeclarations = [...expected].filter(name => !actual.has(name)).sort()
const digest = crypto.createHash("sha256")
for (const file of sourceFiles) {
  digest.update(path.relative(sourceRoot, file)).update("\0").update(fs.readFileSync(file)).update("\0")
}

const manifest = {
  sdk: {
    package: "@opencode-ai/plugin",
    version: packageJson.version,
  },
  sourceFileCount: sourceFiles.length,
  sourceDigestSha256: digest.digest("hex"),
  generatedFileCount: generatedFiles.length,
  exportedDeclarationCount: expected.size,
  missingDeclarations,
  groups,
  opaqueTypeOccurrences,
  karakum: {
    version: JSON.parse(fs.readFileSync(path.join(root, "node_modules", "karakum", "package.json"), "utf8")).version,
    coveredNodes: Number(process.env.KARAKUM_COVERED_NODES),
    uncoveredNodes: Number(process.env.KARAKUM_UNCOVERED_NODES),
  },
}

fs.mkdirSync(path.dirname(manifestPath), { recursive: true })
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`)
if (missingDeclarations.length > 0) {
  throw new Error(`Karakum missed exported declarations: ${missingDeclarations.join(", ")}`)
}
console.log(`BINDINGS_GENERATED files=${generatedFiles.length} declarations=${expected.size} opaque=${opaqueTypeOccurrences}`)
