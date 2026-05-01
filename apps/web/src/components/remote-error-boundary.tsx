"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";

type RemoteErrorBoundaryProps = {
  fallback: ReactNode;
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
      return this.props.fallback;
    }
    return this.props.children;
  }
}
