import type { ApiResponse } from "@/lib/api/identity/types"

type ApiFetchOptions = {
  authRequired?: boolean
  onUnauthorized?: () => void
}

type ApiFetchResult<T> = {
  response: Response
  json: ApiResponse<T> | null
}

function defaultUnauthorizedHandler() {
  if (typeof window !== "undefined") {
    window.location.assign("/login")
  }
}

/**
 * 프론트 공통 API 호출 래퍼.
 * - `authRequired=true`인 요청에서만 401 공통 처리(`/login` 이동)를 수행한다.
 * - 일반 API 요청은 401을 반환값으로 그대로 넘겨 화면 에러 처리에 사용한다.
 */
export async function apiFetch<T>(
  input: RequestInfo | URL,
  init?: RequestInit,
  options?: ApiFetchOptions
): Promise<ApiFetchResult<T>> {
  const response = await fetch(input, init)

  let json: ApiResponse<T> | null = null
  try {
    json = (await response.json()) as ApiResponse<T>
  } catch {
    json = null
  }

  if (response.status === 401 && options?.authRequired) {
    const handleUnauthorized = options.onUnauthorized ?? defaultUnauthorizedHandler
    handleUnauthorized()
  }

  return { response, json }
}
