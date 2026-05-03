"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";
import { Button } from "@ai-usage/ui";

type ShellRouterErrorBoundaryProps = {
  children: ReactNode;
};

type ShellRouterErrorBoundaryState = {
  hasError: boolean;
};

/**
 * Task37-8: `HostRuntimeSafeguard` 바로 아래 자식으로 두어
 * 셸·페이지 렌더 중 예외를 격리한다. 재현 시 스택이 여기서 잡히면 가드 **내부** 트리에서 발생한 것.
 */
export class ShellRouterErrorBoundary extends Component<
  ShellRouterErrorBoundaryProps,
  ShellRouterErrorBoundaryState
> {
  state: ShellRouterErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ShellRouterErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("[ShellRouterErrorBoundary]", error, errorInfo);
  }

  private handleReload = () => {
    if (typeof window !== "undefined") {
      window.location.reload();
    }
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background px-4">
          <p className="text-center text-sm text-muted-foreground">
            셸을 불러오는 중 문제가 발생했습니다. 새로고침 후 다시 시도해 주세요.
          </p>
          <Button type="button" variant="outline" size="sm" onClick={this.handleReload}>
            새로고침
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}
