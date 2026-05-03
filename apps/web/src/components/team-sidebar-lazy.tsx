"use client";

import type { ConsoleSidebarProps } from "@ai-usage/shell/pages";
import { ConsoleSidebarPages } from "@ai-usage/shell/pages";

/** `next/dynamic(..., { ssr: false })` 전용 default export — 클라이언트에서만 로드된다. */
export default function TeamSidebarLazy(props: ConsoleSidebarProps) {
  return <ConsoleSidebarPages {...props} />;
}
