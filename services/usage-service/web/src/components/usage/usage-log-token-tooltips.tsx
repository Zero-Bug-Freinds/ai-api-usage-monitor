export function reasoningTokensTooltipContent() {
  return (
    <div className="space-y-1">
      <p className="font-medium text-foreground">추론 토큰 산출</p>
      <p>Google / OpenAI: 모델이 직접 응답 전문에 포함하여 제공한 실제 추론 수치입니다.</p>
      <p>Anthropic: 현재 사용 기록이 없어 추론 토큰 상세값이 없는 경우가 있습니다.</p>
      <p>공통: 모델의 사고 과정(Reasoning) 및 시스템 처리 비용을 포함합니다.</p>
    </div>
  )
}

export function outputTokensTooltipContent() {
  return (
    <div className="space-y-1">
      <p className="font-medium text-foreground">출력 토큰 산출</p>
      <p>출력 토큰에서는 추론 토큰을 제외한 순수 응답량만 표시합니다.</p>
    </div>
  )
}
