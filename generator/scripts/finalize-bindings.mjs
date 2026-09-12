import assert from "node:assert/strict"
import crypto from "node:crypto"
import fs from "node:fs"
import path from "node:path"
import ts from "typescript"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const packageRoot = path.join(root, "node_modules", "@opencode-ai", "plugin")
const upstreamRoot = path.join(packageRoot, "dist")
const sourceRoot = path.join(packageRoot, ".karakum-sanitized", "dist")
const projectRoot = path.resolve(root, "..")
const generatedRoot = path.join(projectRoot, "core", "build", "generated", "kotlin")
const manifestPath = path.join(projectRoot, "core", "build", "bindings-manifest.json")
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

const toPosix = value => value.split(path.sep).join("/")

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
  const first = toPosix(relative).split("/")[0]
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
const upstreamFiles = walk(upstreamRoot, file => file.endsWith(".d.ts"))
const sourceFiles = walk(sourceRoot, file => file.endsWith(".d.ts"))
const localReExports = []
const externalReExports = []
const unresolvedLocalReExports = []

function exportedName(statement, relative) {
  if (!statement.name || !ts.isIdentifier(statement.name)) return null
  if (statement.name.text !== "prepareSource") return statement.name.text
  if (relative === "source.bun.d.ts") return "prepareSourceBun"
  if (relative === "source.node.d.ts") return "prepareSourceNode"
  return statement.name.text
}

function resolveLocalExport(file, specifier) {
  if (specifier == null) return toPosix(path.relative(upstreamRoot, file))
  const base = path.resolve(path.dirname(file), specifier.replace(/\.js$/, ""))
  for (const candidate of [`${base}.d.ts`, path.join(base, "index.d.ts")]) {
    if (fs.existsSync(candidate)) return toPosix(path.relative(upstreamRoot, candidate))
  }
  return null
}

for (const file of upstreamFiles) {
  const relative = toPosix(path.relative(upstreamRoot, file))
  const group = groupForSource(relative)
  const source = ts.createSourceFile(file, fs.readFileSync(file, "utf8"), ts.ScriptTarget.Latest, true)
  for (const statement of source.statements) {
    if (ts.isExportDeclaration(statement)) {
      const specifier = statement.moduleSpecifier && ts.isStringLiteral(statement.moduleSpecifier)
        ? statement.moduleSpecifier.text
        : null
      const names = statement.exportClause && ts.isNamedExports(statement.exportClause)
        ? statement.exportClause.elements.map(element => element.name.text).sort()
        : statement.exportClause && ts.isNamespaceExport(statement.exportClause)
          ? [statement.exportClause.name.text]
          : ["*"]
      const record = { source: relative, specifier, names }
      if (specifier == null || specifier.startsWith(".")) {
        const target = resolveLocalExport(file, specifier)
        if (target == null) unresolvedLocalReExports.push(record)
        else localReExports.push({ ...record, target })
      } else {
        externalReExports.push(record)
      }
      continue
    }
    if (!isExported(statement)) continue
    if (ts.isVariableStatement(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        if (ts.isIdentifier(declaration.name)) expected.add(`${group}:${declaration.name.text}`)
      }
      continue
    }
    const name = exportedName(statement, relative)
    if (name != null) expected.add(`${group}:${name}`)
  }
}

localReExports.sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)))
externalReExports.sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)))
unresolvedLocalReExports.sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)))

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
const sourceRelativeFiles = upstreamFiles
  .map(file => toPosix(path.relative(upstreamRoot, file)))
  .sort()
const digest = crypto.createHash("sha256")
for (const relative of sourceRelativeFiles) {
  const file = path.join(upstreamRoot, ...relative.split("/"))
  digest.update(relative).update("\0").update(fs.readFileSync(file)).update("\0")
}

const manifest = {
  sdk: {
    package: "@opencode-ai/plugin",
    version: packageJson.version,
  },
  pathFormat: "posix",
  sourceFileCount: upstreamFiles.length,
  sourceFiles: sourceRelativeFiles,
  sourceDigestSha256: digest.digest("hex"),
  generatedFileCount: generatedFiles.length,
  exportedDeclarationCount: expected.size,
  missingDeclarations,
  reExports: {
    total: localReExports.length + externalReExports.length + unresolvedLocalReExports.length,
    local: localReExports,
    external: externalReExports,
    unresolvedLocal: unresolvedLocalReExports,
  },
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
if (unresolvedLocalReExports.length > 0) {
  throw new Error(`Unresolved local re-exports: ${JSON.stringify(unresolvedLocalReExports)}`)
}
if (missingDeclarations.length > 0) {
  throw new Error(`Karakum missed exported declarations: ${missingDeclarations.join(", ")}`)
}
console.log(`BINDINGS_GENERATED files=${generatedFiles.length} declarations=${expected.size} opaque=${opaqueTypeOccurrences}`)
