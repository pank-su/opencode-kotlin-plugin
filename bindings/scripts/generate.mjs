import { spawnSync } from "node:child_process"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
    ...options,
  })
  if (result.status !== 0) {
    process.stderr.write(result.stdout ?? "")
    process.stderr.write(result.stderr ?? "")
    process.exit(result.status ?? 1)
  }
  return result.stdout ?? ""
}

process.stdout.write(run(process.execPath, ["scripts/check-upstream-contract.mjs"]))
process.stdout.write(run(process.execPath, ["scripts/sanitize-full-sdk.mjs"]))

const karakum = run("bunx", ["karakum", "--config", "karakum.config.json"])
for (const line of karakum.split("\n")) {
  if (/^(Source files count|Covered nodes|Uncovered nodes):/.test(line)) console.log(line)
}

const coveredNodes = Number(karakum.match(/Covered nodes: (\d+)/)?.[1] ?? -1)
const uncoveredNodes = Number(karakum.match(/Uncovered nodes: (\d+)/)?.[1] ?? -1)
if (coveredNodes < 1 || uncoveredNodes < 0) {
  throw new Error("Unable to parse Karakum coverage")
}

process.stdout.write(run(process.execPath, ["scripts/finalize-bindings.mjs"], {
  env: {
    ...process.env,
    KARAKUM_COVERED_NODES: String(coveredNodes),
    KARAKUM_UNCOVERED_NODES: String(uncoveredNodes),
  },
}))
