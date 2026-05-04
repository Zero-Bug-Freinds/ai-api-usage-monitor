import { Geist, Geist_Mono } from "next/font/google";

/** identity/team-web 과 동일한 Geist 변수 — Pages Router는 `layout`이 없어 `_app`에서 `documentElement`에 부착한다(Task37-20). */
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
