/** Thrown after HTTP failure is logged; avoids duplicate {@link logTeamBffCatchError} noise. */
export class TeamBffMaskedHttpError extends Error {
  override readonly name = "TeamBffMaskedHttpError"

  constructor(message: string) {
    super(message)
  }
}

/** Masked user-facing messages for team BFF dashboard fetch failures. */
export function usageFetchErrorMessage(status: number): string {
  if (status === 400) return "팀/기간 필터를 확인해 주세요. (Code: ERR_TEAM_400)"
  if (status === 401 || status === 403) {
    return "인증이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요."
  }
  if (status === 404) return "대시보드 페이지를 찾지 못했습니다. (Code: ERR_TEAM_404)"
  if (status >= 500) return "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. (Code: ERR_TEAM_500)"
  return `사용량 데이터를 불러오지 못했습니다. (Code: ERR_TEAM_${status})`
}

/** Masked user-facing messages for team member detail BFF fetch failures. */
export function memberUsageFetchError(status: number): string {
  if (status === 400) return "잘못된 요청입니다. 입력 조건을 다시 확인해 주세요. (Code: ERR_MBR_400)"
  if (status === 401 || status === 403) {
    return "로그인 세션이 만료되었거나 접근 권한이 없습니다."
  }
  if (status === 404) return "존재하지 않거나 삭제된 멤버 정보입니다. (Code: ERR_MBR_404)"
  if (status >= 500) return "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. (Code: ERR_MBR_500)"
  return `멤버 정보를 불러오지 못했습니다. (Code: ERR_MBR_${status})`
}

export async function readUpstreamErrorMessage(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.clone().json()
    if (body && typeof body === "object") {
      const record = body as Record<string, unknown>
      if (typeof record.message === "string" && record.message.length > 0) {
        return record.message
      }
      if (typeof record.error === "string" && record.error.length > 0) {
        return record.error
      }
    }
  } catch {
    try {
      const text = await response.clone().text()
      return text.length > 0 ? text : null
    } catch {
      return null
    }
  }
  return null
}

export function logTeamBffHttpError(
  logTag: string,
  context: { request: string; status: number; statusText: string; upstreamMessage: string | null },
): void {
  console.error(`[${logTag}]`, context)
}

export function logTeamBffCatchError(logTag: string, context: Record<string, unknown>, error: unknown): void {
  console.error(`[${logTag}]`, { ...context, error })
}

/**
 * Logs upstream details, then throws {@link Error} with a masked client message.
 */
export async function assertTeamBffResponseOk(
  response: Response,
  options: {
    logTag: string
    request: string
    maskMessage: (status: number) => string
  },
): Promise<void> {
  if (response.ok) return
  const upstreamMessage = await readUpstreamErrorMessage(response)
  logTeamBffHttpError(options.logTag, {
    request: options.request,
    status: response.status,
    statusText: response.statusText,
    upstreamMessage,
  })
  throw new TeamBffMaskedHttpError(options.maskMessage(response.status))
}
