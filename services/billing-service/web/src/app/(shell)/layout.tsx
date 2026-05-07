import type { ReactNode } from "react";

import { ConsoleShell } from "@ai-usage/shell";

export default function ShellLayout({ children }: { children: ReactNode }) {
  return <ConsoleShell profile="billing">{children}</ConsoleShell>;
}
