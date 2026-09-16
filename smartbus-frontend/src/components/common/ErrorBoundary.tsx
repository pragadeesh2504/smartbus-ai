import React, { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children: ReactNode;
  componentName?: string;
  fallbackTitle?: string;
  fallbackMessage?: string;
  onReset?: () => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught an unhandled error:', error, errorInfo);
  }

  private handleReload = () => {
    if (this.props.onReset) {
      this.props.onReset();
    }
    this.setState({ hasError: false, error: null });
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div className="bg-white border border-brandBorder rounded-3xl p-6 shadow-sm flex flex-col items-center justify-center text-center space-y-4 my-4">
          <div className="p-3 bg-brandRed/10 border border-brandRed/20 rounded-2xl text-brandRed">
            <AlertTriangle className="w-8 h-8" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-brandNavy uppercase tracking-wider">
              {this.props.fallbackTitle || 'Rendering Encountered an Issue'}
            </h3>
            <p className="text-xs text-brandTextSecondary mt-1 font-semibold max-w-sm">
              {this.props.fallbackMessage || this.state.error?.message || 'A visual component encountered an unexpected error.'}
            </p>
            {this.state.error && (
              <div className="mt-2 text-[10px] text-brandRed bg-brandRed/5 border border-brandRed/20 p-2 rounded-xl max-w-md overflow-x-auto text-left font-mono">
                <p className="font-bold">{this.state.error.name}: {this.state.error.message}</p>
                {this.state.error.stack && (
                  <pre className="mt-1 text-[9px] text-brandRed/80 whitespace-pre-wrap">{this.state.error.stack.slice(0, 400)}</pre>
                )}
              </div>
            )}
          </div>
          <button
            onClick={this.handleReload}
            className="flex items-center gap-2 px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white text-xs font-bold rounded-xl shadow-sm transition-all"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Reload Component
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
