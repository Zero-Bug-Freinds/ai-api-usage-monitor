/**
 * Resolves the Next.js CLI from this app's dependency tree (works with pnpm without a local node_modules/.bin).
 */
const path = require("path")
const { spawnSync } = require("child_process")

const appRoot = path.join(__dirname, "..")
let nextBin
try {
  nextBin = require.resolve("next/dist/bin/next", { paths: [appRoot] })
} catch {
  console.error(
    "Could not resolve next/dist/bin/next. Run `pnpm install` from the repository root.",
  )
  process.exit(1)
}

const args = process.argv.slice(2)
const result = spawnSync(process.execPath, [nextBin, ...args], {
  stdio: "inherit",
  cwd: appRoot,
  env: process.env,
})

process.exit(result.status ?? 1)
