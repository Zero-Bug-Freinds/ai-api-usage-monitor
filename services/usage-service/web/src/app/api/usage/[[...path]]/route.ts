import { NextResponse } from "next/server"

import { encodeUsagePathSegments, proxyUsageToGateway } from "@/lib/usage/usage-gateway-bff-proxy"

type RouteContext = { params: Promise<{ path?: string[] }> }

async function proxyUsage(request: Request, context: RouteContext): Promise<Response> {
  const { path: segments } = await context.params
  const pathParts = segments ?? []
  if (pathParts.length === 0) {
    return NextResponse.json({ message: "사용량 API 경로가 필요합니다" }, { status: 404, headers: { "Cache-Control": "no-store" } })
  }

  const usagePath = encodeUsagePathSegments(pathParts)
  return proxyUsageToGateway(request, usagePath)
}

export async function GET(request: Request, context: RouteContext) {
  return proxyUsage(request, context)
}

export async function HEAD(request: Request, context: RouteContext) {
  return proxyUsage(request, context)
}

export async function POST(request: Request, context: RouteContext) {
  return proxyUsage(request, context)
}

export async function PUT(request: Request, context: RouteContext) {
  return proxyUsage(request, context)
}

export async function PATCH(request: Request, context: RouteContext) {
  return proxyUsage(request, context)
}

export async function DELETE(request: Request, context: RouteContext) {
  return proxyUsage(request, context)
}
