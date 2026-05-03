import { afterEach, describe, expect, it, vi } from "vitest";
import { currentMonthRangeKst, currentMonthStartKst, rangeLastDays } from "./dates";

describe("currentMonthRangeKst (billing KST agg_date alignment)", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  it("uses Asia/Seoul for both from and to so to is not host-local midnight drift", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-03T15:00:00.000Z"));
    vi.stubEnv("TZ", "UTC");

    const r = currentMonthRangeKst();
    expect(r.from).toBe("2026-05-01");
    expect(r.to).toBe("2026-05-04");
    expect(currentMonthStartKst()).toBe("2026-05-01");
  });
});

describe("rangeLastDays", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("uses local calendar for sliding windows", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-03T12:00:00.000Z"));
    vi.stubEnv("TZ", "UTC");
    const r = rangeLastDays(1);
    expect(r.from).toBe(r.to);
  });
});
