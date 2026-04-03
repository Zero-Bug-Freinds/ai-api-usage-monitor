import type { Metadata } from "next"

import { LandingHomeWithSession } from "@/components/landing/landing-header"

export const metadata: Metadata = {
  title: "AI API Usage Monitor",
  description: "AI API 사용량·과금 모니터링",
}

export default function Home() {
  return (
    <div className="flex min-h-full flex-col bg-zinc-50 font-sans dark:bg-black">
      <LandingHomeWithSession />
    </div>
  )
}
