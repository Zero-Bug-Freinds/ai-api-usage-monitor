import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { BillingShell } from "./billing-shell";

describe("BillingShell", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard");
    vi.stubEnv("NEXT_PUBLIC_IDENTITY_WEB_ORIGIN", "http://localhost:3000");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("renders usage link with site-root href, not under billing basePath", () => {
    const html = renderToStaticMarkup(
      <BillingShell>
        <span>child</span>
      </BillingShell>,
    );
    expect(html).toContain('href="http://localhost:3000/dashboard"');
    expect(html).not.toContain('href="/billing/dashboard"');
  });
});
