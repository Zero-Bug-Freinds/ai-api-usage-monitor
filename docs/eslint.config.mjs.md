# team-web `eslint.config.mjs` 원본 (FlatCompat)

`services/team-service/web/eslint.config.mjs`를 flat `defineConfig` 방식으로 교체하기 전, CI 오류 재현/롤백 참고용 원본이다.

## 원본 소스

```javascript
import { dirname } from "path"
import { fileURLToPath } from "url"
import { FlatCompat } from "@eslint/eslintrc"

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const compat = new FlatCompat({ baseDirectory: __dirname })

const eslintConfig = [...compat.extends("next/core-web-vitals", "next/typescript")]

export default eslintConfig
```
