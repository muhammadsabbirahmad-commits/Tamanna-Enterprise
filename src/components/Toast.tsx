import React from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

export interface ToastItem {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
}

export const ToastContainer: React.FC<{
  toasts: ToastItem[];
  onDismiss: (id: number) => void;
}> = ({ toasts, onDismiss }) => {
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 max-w-sm w-full pointer-events-none no-print">
      {toasts.map((t) => {
        const icon =
          t.type === 'success' ? (
            <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
          ) : t.type === 'error' ? (
            <AlertCircle className="h-4 w-4 text-red-600 dark:text-red-400 shrink-0" />
          ) : (
            <Info className="h-4 w-4 text-blue-600 dark:text-blue-400 shrink-0" />
          );
        const border =
          t.type === 'success'
            ? 'border-emerald-200 dark:border-emerald-900/60'
            : t.type === 'error'
            ? 'border-red-200 dark:border-red-900/60'
            : 'border-slate-200 dark:border-slate-700';

        return (
          <div
            key={t.id}
            className={`pointer-events-auto flex items-start gap-2.5 rounded-xl surface-card border ${border} px-4 py-3 shadow-xl`}
          >
            <div className="mt-0.5">{icon}</div>
            <p className="text-sm text-slate-800 dark:text-slate-100 flex-1">{t.message}</p>
            <button
              type="button"
              onClick={() => onDismiss(t.id)}
              className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
              aria-label="বন্ধ করুন"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
};
