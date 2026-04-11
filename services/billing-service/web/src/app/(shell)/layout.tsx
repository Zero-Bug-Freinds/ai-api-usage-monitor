import type { ReactNode } from "react";

import { BillingShell } from "@/components/expenditure/billing-shell";

export default function ShellLayout({ children }: { children: ReactNode }) {
  return <BillingShell>{children}</BillingShell>;
}
