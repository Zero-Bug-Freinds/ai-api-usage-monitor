/**
 * Stable row keys for React lists. `crypto.randomUUID` is unavailable on HTTP (non-secure context);
 * staging ALB is often http:// until TLS is enabled.
 */
export function createRandomId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID()
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0
    const value = char === "x" ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}
