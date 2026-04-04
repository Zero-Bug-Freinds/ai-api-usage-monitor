/** 공통 API 응답 래퍼 — Identity BFF JSON과 동일한 형태(로그아웃 등 클라이언트 호출용). */
export type ApiResponse<T> = {
  success: boolean
  message: string
  data: T | null
}
