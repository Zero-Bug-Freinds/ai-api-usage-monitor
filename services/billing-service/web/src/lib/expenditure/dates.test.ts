import { describe, expect, it } from "vitest";

import { currentMonthStartKst, rangeLastDays } from "./dates";

describe("rangeLastDays", () => {
  it("returns 30-day window strings", () => {
    const r = rangeLastDays(30);
    expect(r.from.length).toBe(10);
    expect(r.to.length).toBe(10);
  });
});

describe("currentMonthStartKst", () => {
  it("is YYYY-MM-01", () => {
    const s = currentMonthStartKst();
    expect(s).toMatch(/^\d{4}-\d{2}-01$/);
  });
});
