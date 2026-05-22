import { describe, expect, it, vi } from "vitest"

import { warnStorageError } from "./storage-errors"

describe("warnStorageError", () => {
  it("logs with Storage Error prefix", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    const err = new Error("quota")
    warnStorageError(err)
    expect(warn).toHaveBeenCalledWith("[Storage Error]", err)
    warn.mockRestore()
  })
})
