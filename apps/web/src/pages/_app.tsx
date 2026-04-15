import type { AppProps } from "next/app";
import { HostShellLayout } from "@/components/host-shell-layout";
import "@/styles/globals.css";

export default function App({ Component, pageProps }: AppProps) {
  return (
    <HostShellLayout>
      <Component {...pageProps} />
    </HostShellLayout>
  );
}
