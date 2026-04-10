/**
 * 변경 이전, 즉 원본 버전의 eslint.config.mjs
 * (출처: services/team-service/web/eslint.config.mjs — flat 설정 적용 전 백업용 문서)
 */
import { dirname } from "path"
import { fileURLToPath } from "url"
import { FlatCompat } from "@eslint/eslintrc"

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const compat = new FlatCompat({ baseDirectory: __dirname })

const eslintConfig = [...compat.extends("next/core-web-vitals", "next/typescript")]

export default eslintConfig
