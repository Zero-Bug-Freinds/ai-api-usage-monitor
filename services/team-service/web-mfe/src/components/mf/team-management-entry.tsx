"use client"

import { TeamManagementView } from "@web/components/team/team-management-view"

/** MF expose 엔트리: 호스트 aside가 레이아웃·폭을 담당하므로 추가 래퍼 없이 뷰만 내보냄 */
export default function TeamManagementEntry() {
  return <TeamManagementView />
}
