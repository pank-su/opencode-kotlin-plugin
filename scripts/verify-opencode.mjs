import assert from "node:assert/strict"
import { spawnSync } from "node:child_process"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const executable = path.join(root, "node_modules", ".bin", "opencode2")
const sleepBuffer = new Int32Array(new SharedArrayBuffer(4))

let result
let line
for (let attempt = 1; attempt <= 3; attempt += 1) {
  result = spawnSync(executable, ["plugin", "list", "--log-level", "error"], {
    cwd: root,
    encoding: "utf8",
  })
  if (result.status === 0) {
    line = result.stdout
      .split("\n")
      .find(value => value.includes("panks.kotlin-secret-guard"))
    if (line) break
  }
  if (attempt < 3) Atomics.wait(sleepBuffer, 0, 0, 250 * attempt)
}

assert.equal(result.status, 0, result.stderr || result.stdout)
assert.ok(line, `Plugin is absent from OpenCode 2 output after 3 attempts:\n${result.stdout}`)
assert.match(line, /local/)
assert.match(line, /build\/plugin\/index\.mjs/)
console.log(`OPENCODE_PLUGIN_ACTIVE: ${line.trim()}`)
