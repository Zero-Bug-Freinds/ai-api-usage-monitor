#!/usr/bin/env node
/**
 * team-web 가 node_modules/@ai-usage/shell 복사본·.next 캐시 대신
 * packages/shell 최신 소스를 쓰도록 정리한다.
 */
import { execSync } from "node:child_process"
import { rmSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..")
const teamWebDir = join(repoRoot, "services", "team-service", "web")
const shellLink = join(teamWebDir, "node_modules", "@ai-usage", "shell")

rmSync(join(teamWebDir, ".next"), { recursive: true, force: true })

try {
  rmSync(shellLink, { recursive: true, force: true })
} catch {
  // ignore
}

execSync("pnpm install --filter team-web...", {
  cwd: repoRoot,
  stdio: "inherit",
})

console.log("[refresh-shell-for-team-web] team-web .next cleared; workspace @ai-usage/shell relinked.")
