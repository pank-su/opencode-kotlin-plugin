import assert from "node:assert/strict"
import crypto from "node:crypto"
import fs from "node:fs"
import path from "node:path"
import { spawnSync } from "node:child_process"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const bindingsRoot = path.join(root, "bindings")

function walk(directory) {
  const result = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) result.push(...walk(file))
    else result.push(file)
  }
  return result.sort()
}

function generatedDigest() {
  const digest = crypto.createHash("sha256")
  const targets = [
    path.join(bindingsRoot, "build", "generated", "kotlin"),
    path.join(bindingsRoot, "build", "bindings-manifest.json"),
  ]
  for (const target of targets) {
    const files = fs.statSync(target).isDirectory() ? walk(target) : [target]
    for (const file of files) {
      digest.update(path.relative(bindingsRoot, file)).update("\0")
      digest.update(fs.readFileSync(file)).update("\0")
    }
  }
  return digest.digest("hex")
}

const before = generatedDigest()
const result = spawnSync("bun", ["run", "generate"], {
  cwd: bindingsRoot,
  encoding: "utf8",
  maxBuffer: 16 * 1024 * 1024,
})
if (result.status !== 0) {
  process.stderr.write(result.stdout ?? "")
  process.stderr.write(result.stderr ?? "")
  process.exit(result.status ?? 1)
}
const after = generatedDigest()
assert.equal(after, before, "bindings regeneration changed the generated tree")
console.log(`BINDINGS_DETERMINISTIC_OK sha256=${after}`)
