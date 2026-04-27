import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { POST } from "./route";

function makeRequest(body: unknown, cookie = "access_token=test-token") {
  return new Request("http://localhost:3003/billing/api/expenditure/team/month-rollup", {
    method: "POST",
    headers: {
      cookie,
      "content-type": "application/json",
      accept: "application/json",
    },
    body: JSON.stringify(body),
  });
}

describe("POST /api/expenditure/team/month-rollup (hardened)", () => {
  const prevEnv = { ...process.env };

  beforeEach(() => {
    process.env = {
      ...prevEnv,
      API_GATEWAY_URL: "http://gateway.local",
      // 팀 멤버 조회는 항상 이 오리진을 사용하도록 고정(요청 Host 와 무관하게 검증 가능)
      BILLING_TEAM_BFF_BASE_URL: "http://team-bff.test",
    };
  });

  afterEach(() => {
    process.env = prevEnv;
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("returns 400 when teamId is missing or non-numeric", async () => {
    const fetchMock = vi.fn(async () => new Response("should not be called", { status: 500 }));
    vi.stubGlobal("fetch", fetchMock);

    const resMissing = await POST(
      makeRequest({
        monthStartDate: "2026-04-01",
        userIds: ["u1"],
      })
    );
    expect(resMissing.status).toBe(400);

    const resNonNumeric = await POST(
      makeRequest({
        teamId: "abc",
        monthStartDate: "2026-04-01",
        userIds: ["u1"],
      })
    );
    expect(resNonNumeric.status).toBe(400);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns 403 when any userId is not a team member", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url === "http://team-bff.test/api/team/v1/teams/123/members") {
        expect(init?.method).toBe("GET");
        return new Response(JSON.stringify({ success: true, data: ["member-1"] }), {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      }
      return new Response("unexpected", { status: 500 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const res = await POST(
      makeRequest({
        teamId: "123",
        monthStartDate: "2026-04-01",
        userIds: ["member-1", "not-a-member"],
      })
    );
    expect(res.status).toBe(403);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("forwards only normalized allowed userIds to gateway", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;

      if (url === "http://team-bff.test/api/team/v1/teams/999/members") {
        return new Response(JSON.stringify({ success: true, data: ["u1", "u2"] }), {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      }

      if (url === "http://gateway.local/api/v1/expenditure/team/month-rollup") {
        expect(init?.method).toBe("POST");
        const auth = (init?.headers as Headers)?.get?.("Authorization") ?? (init?.headers as Record<string, string>)?.Authorization;
        expect(auth).toBe("Bearer test-token");

        const parsed = JSON.parse(String(init?.body ?? ""));
        expect(parsed).toEqual({
          userIds: ["u1", "u2"],
          monthStartDate: "2026-04-01",
        });

        return new Response(JSON.stringify({ totalCostUsd: 1, byUser: [] }), {
          status: 200,
          headers: { "content-type": "application/json", "x-upstream": "ok" },
        });
      }

      return new Response("unexpected url", { status: 500 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const res = await POST(
      makeRequest({
        teamId: "999",
        monthStartDate: " 2026-04-01 ",
        userIds: ["u1", " u2 ", "", "u1"],
      })
    );

    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("no-store");
    expect(res.headers.get("x-upstream")).toBe("ok");
  });
});

