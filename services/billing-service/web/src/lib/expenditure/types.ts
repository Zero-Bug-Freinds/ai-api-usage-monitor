export type AiProviderCode = "OPENAI" | "ANTHROPIC" | "GOOGLE";

export type ExpenditureSummary = {
  from: string;
  to: string;
  totalCostUsd: number;
  monthlyBudgetUsd: number | null;
};

export type MonthlyBudgetStatus = {
  from: string;
  to: string;
  totalCostUsd: number;
  monthlyBudgetUsd: number | null;
};

export type DailyPoint = { date: string; costUsd: number };

export type MonthlyPoint = {
  monthStartDate: string;
  costUsd: number;
  finalized: boolean;
};

export type ApiKeySeen = {
  apiKeyId: string;
  provider: AiProviderCode;
  firstSeenAt: string;
};

export type TeamMonthRollup = {
  totalCostUsd: number;
  byUser: { userId: string; costUsd: number }[];
};
