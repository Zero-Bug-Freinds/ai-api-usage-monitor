"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";

type RemoteErrorBoundaryProps = {
  /** `renderFallback`이 없을 때 사용합니다. */
  fallback?: ReactNode;
  /** 에러 시 UI와 재시도 동작을 함께 제공합니다(`fallback`보다 우선). */
  renderFallback?: (ctx: { retry: () => void }) => ReactNode;
  children: ReactNode;
  resetKey?: string;
};

type RemoteErrorBoundaryState = {
  hasError: boolean;
};

export class RemoteErrorBoundary extends Component<RemoteErrorBoundaryProps, RemoteErrorBoundaryState> {
  state: RemoteErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): RemoteErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("[RemoteErrorBoundary]", error, errorInfo);
  }

  componentDidUpdate(prevProps: RemoteErrorBoundaryProps) {
    if (this.state.hasError && this.props.resetKey !== prevProps.resetKey) {
      this.setState({ hasError: false });
    }
  }

  render() {
    if (this.state.hasError) {
      if (this.props.renderFallback) {
        return this.props.renderFallback({
          retry: () => {
            this.setState({ hasError: false });
          },
        });
      }
      return this.props.fallback ?? null;
    }
    return this.props.children;
  }
}
