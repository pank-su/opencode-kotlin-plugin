import assert from "node:assert/strict"
import { pathToFileURL } from "node:url"
import path from "node:path"

const entry = path.resolve("build/plugin/index.mjs")
const module = await import(pathToFileURL(entry))
const plugin = module.default

assert.equal(plugin.id, "panks.kotlin-secret-guard")
assert.equal(typeof plugin.setup, "function")
console.log("PLUGIN_IMPORT_OK")
