export const LATENCY_MS_THRESHOLD = 2000

/** 평균 지연 — indigo-700, 범례·선 on white 대비 ≥ 4.5:1 */
export const LATENCY_MAIN_LINE = "#4338CA"

/** Min–Max band — indigo 계열 연한 영역, 경계는 indigo 톤 유지 */
export const LATENCY_BAND_FILL = "rgba(67, 56, 202, 0.14)"

/** P95 지연 — violet-800, 연보라 대체로 범례 가독성 */
export const LATENCY_P95_LINE = "#5B21B6"

/** P99 지연 — violet-700, 성능 계열 내 단계 구분 */
export const LATENCY_P99_LINE = "#6D28D9"

/** 성공률 — emerald-600, 안정성(녹색) 군 */
export const LATENCY_SUCCESS_RATE_LINE = "#059669"

/** 오류율 — rose-600, 안정성(적색) 군 */
export const LATENCY_ERROR_RATE_LINE = "#E11D48"

/** 2s 기준선 — 경고 유지 */
export const LATENCY_THRESHOLD_LINE = "#ef4444"

/** 범례 라벨 — 라이트 배경 WCAG 대비 */
export const LATENCY_LEGEND_LABEL_FILL = "#262626"
