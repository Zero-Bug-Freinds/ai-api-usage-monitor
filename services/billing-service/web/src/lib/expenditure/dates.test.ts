import { describe, expect, it } from "vitest";

import { rangeLastDays } from "./dates";

describe("rangeLastDays", () => {
  it("returns 30-day window strings", () => {
    const r = rangeLastDays(30);
    expect(r.from.length).toBe(10);
    expect(r.to.length).toBe(10);
  });
});
