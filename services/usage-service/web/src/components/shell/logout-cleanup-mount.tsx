"use client";

import { useLogoutCleanup } from "@ai-usage/team-workspace-cache";

/** Shell 레이아웃에 마운트하여 로그아웃 이벤트 시 클라이언트 스토리지 정리를 보장합니다. */
export function LogoutCleanupMount() {
  useLogoutCleanup();
  return null;
}
