"use client";

/**
 * 서버 번들 전용: Module Federation 없이 `team/*`·`usage/*` 가상 모듈을 해석할 때 쓰는 플레이스홀더.
 * 실제 리모트는 클라이언트 청크에서만 로드된다(team-page-content의 dynamic·ssr:false).
 */
export default function MfRemoteStub() {
    return null;
}