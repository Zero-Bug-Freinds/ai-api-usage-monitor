import {
  encodeUsagePathSegments,
  proxyUsageToGateway,
  usageProxyJsonError,
} from "@/lib/usage/api/usage-gateway-bff-proxy"

type RouteContext = { params: Promise<{ path?: string[] }> }

/**
 * 팀 콘솔 MF 전용: 공개 URL `/dashboard/api/usage/team/bff/<...path>`
 * → 게이트웨이 `/api/v1/usage/bff/<...path>` (catch-all 세그먼트 앞에 `bff` 고정).
 */
async function proxyTeamUsageBff(request: Request, context: RouteContext): Promise<Response> {
  const { path: segments } = await context.params
  const pathParts = segments ?? []
  if (pathParts.length === 0) {
    return usageProxyJsonError(404)
  }

  const rest = encodeUsagePathSegments(pathParts)
  const usagePath = `bff/${rest}`

  return proxyUsageToGateway(request, usagePath)
}

export async function GET(request: Request, context: RouteContext) {
  return proxyTeamUsageBff(request, context)
}

export async function HEAD(request: Request, context: RouteContext) {
  return proxyTeamUsageBff(request, context)
}

export async function POST(request: Request, context: RouteContext) {
  return proxyTeamUsageBff(request, context)
}

export async function PUT(request: Request, context: RouteContext) {
  return proxyTeamUsageBff(request, context)
}

export async function PATCH(request: Request, context: RouteContext) {
  return proxyTeamUsageBff(request, context)
}

export async function DELETE(request: Request, context: RouteContext) {
  return proxyTeamUsageBff(request, context)
}
