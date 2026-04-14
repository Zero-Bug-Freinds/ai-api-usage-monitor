type FormatUsdOptions = {
  decimals?: number;
  /**
   * 0보다 크고 minLabel 미만이면 "<$0.0001" 같은 임계값 라벨로 표시한다.
   * 기본값은 0.0001 (USD).
   */
  minLabel?: number;
};

function toNumberLike(value: unknown): number {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw == null) return Number.NaN;
  if (typeof raw === "number") return raw;
  return Number(raw);
}

export function formatUsd(value: unknown, opts?: FormatUsdOptions): string {
  const decimals = opts?.decimals ?? 4;
  const minLabel = opts?.minLabel ?? 0.0001;

  if (value == null) return "—";

  const n = toNumberLike(value);
  if (Number.isNaN(n)) return "—";

  if (n > 0 && n < minLabel) {
    return `<$${minLabel.toFixed(decimals)}`;
  }

  return `$${n.toFixed(decimals)}`;
}

export function formatUsdTooltip(value: unknown): [string, string] {
  return [formatUsd(value), "USD"];
}

