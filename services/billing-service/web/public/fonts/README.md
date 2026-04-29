# Geist (로컬 정적 폰트)

`layout.tsx`의 `next/font/local`이 이 디렉터리의 WOFF2를 사용합니다. Docker/CI 등 외부망 제한 환경에서는 `next/font/google`(fonts.gstatic.com) 대신 이 방식을 씁니다.

파일이 없을 때 npm 레지스트리 tarball에서만 추출할 수 있다면(의존성 추가 없이):

- Sans: https://unpkg.com/geist@1.3.1/dist/fonts/geist-sans/Geist-Variable.woff2
- Mono: https://unpkg.com/geist@1.3.1/dist/fonts/geist-mono/GeistMono-Variable.woff2

저장 파일명은 `Geist-Variable.woff2`, `GeistMono-Variable.woff2`와 일치해야 합니다.
