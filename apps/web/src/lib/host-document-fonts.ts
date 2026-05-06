import { Geist, Geist_Mono } from "next/font/google";

/** identity/team-web 과 동일한 Geist 변수 — Pages 호스트는 `pages/_document.tsx`의 `<Html>`에 부착한다. */
export const hostGeistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
  display: "swap",
});

export const hostGeistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
  display: "swap",
});
