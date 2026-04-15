"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";

type RemoteErrorBoundaryProps = {
  fallback: ReactNode;
  children: ReactNode;
};

type RemoteErrorBoundaryState = {
  hasError: boolean;
};

export class RemoteErrorBoundary extends Component<RemoteErrorBoundaryProps, RemoteErrorBoundaryState> {
  state: RemoteErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): RemoteErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(_error: Error, _errorInfo: ErrorInfo) {}

  render() {
    if (this.state.hasError) {
      return this.props.fallback;
    }
    return this.props.children;
  }
}
