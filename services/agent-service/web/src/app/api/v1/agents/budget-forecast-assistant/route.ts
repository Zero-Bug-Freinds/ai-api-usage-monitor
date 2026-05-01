import { NextRequest, NextResponse } from "next/server"

function backendOrigin(): string {
  return (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "http://agent-service:8096").replace(/\/$/, "")
}

export async function POST(request: NextRequest) {
  const body = await request.text()
  const targetUrl = `${backendOrigin()}/api/v1/agents/budget-forecast-assistant`

  try {
    const response = await fetch(targetUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      cache: "no-store",
    })

    const responseBody = await response.text()
    const contentType = response.headers.get("content-type") ?? "application/json"
    return new NextResponse(responseBody, {
      status: response.status,
      headers: { "content-type": contentType },
    })
  } catch {
    return NextResponse.json(
      {
        message: "budget forecast backend 호출에 실패했습니다.",
      },
      { status: 502 },
    )
  }
}
