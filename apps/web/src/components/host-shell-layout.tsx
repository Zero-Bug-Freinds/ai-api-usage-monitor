"use client";

import type { ReactNode } from "react";
import { ConsoleLayoutOverride, ConsoleSidebar } from "@ai-usage/shell";

export function HostShellLayout({ children }: { children: ReactNode }) {
  return (
    <ConsoleLayoutOverride
      primarySidebar={
        <ConsoleSidebar profile="team" />
      }
    >
      {children}
    </ConsoleLayoutOverride>
  );
}
