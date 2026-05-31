export const ACCOUNT_DELETED_NOTICE_KEY = "identity.account_deleted_notice"

export const DEFAULT_ACCOUNT_DELETED_MESSAGE =
  "회원 탈퇴가 완료되었습니다. 계정에 다시 로그인할 수 없습니다."

export function storeAccountDeletedNotice(message: string): void {
  if (typeof window === "undefined") return
  sessionStorage.setItem(ACCOUNT_DELETED_NOTICE_KEY, message)
}

export function consumeAccountDeletedNotice(): string | null {
  if (typeof window === "undefined") return null
  const message = sessionStorage.getItem(ACCOUNT_DELETED_NOTICE_KEY)
  if (message) {
    sessionStorage.removeItem(ACCOUNT_DELETED_NOTICE_KEY)
  }
  return message
}
