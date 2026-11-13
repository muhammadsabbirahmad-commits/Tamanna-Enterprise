import React from 'react';
import { daysAgoIso, monthStartIso, todayIso } from '../utils/format';

export type DatePreset = 'TODAY' | 'YESTERDAY' | '7D' | '30D' | 'MONTH' | 'ALL' | 'CUSTOM';

interface DateFilterBarProps {
  preset: DatePreset;
  fromDate: string;
  toDate: string;
  onChange: (next: { preset: DatePreset; fromDate: string; toDate: string }) => void;
  includeAll?: boolean;
}

export const DateFilterBar: React.FC<DateFilterBarProps> = ({
  preset,
  fromDate,
  toDate,
  onChange,
  includeAll = true,
}) => {
  const applyPreset = (p: DatePreset) => {
    const today = todayIso();
    if (p === 'TODAY') onChange({ preset: p, fromDate: today, toDate: today });
    else if (p === 'YESTERDAY') {
      const y = daysAgoIso(1);
      onChange({ preset: p, fromDate: y, toDate: y });
    } else if (p === '7D') onChange({ preset: p, fromDate: daysAgoIso(6), toDate: today });
    else if (p === '30D') onChange({ preset: p, fromDate: daysAgoIso(29), toDate: today });
    else if (p === 'MONTH') onChange({ preset: p, fromDate: monthStartIso(), toDate: today });
    else if (p === 'ALL') onChange({ preset: p, fromDate: '2000-01-01', toDate: today });
  };

  const buttons: Array<{ id: DatePreset; label: string }> = [
    { id: 'TODAY', label: 'আজ' },
    { id: 'YESTERDAY', label: 'গতকাল' },
    { id: '7D', label: '৭ দিন' },
    { id: '30D', label: '৩০ দিন' },
    { id: 'MONTH', label: 'এই মাস' },
    ...(includeAll ? [{ id: 'ALL' as DatePreset, label: 'সব' }] : []),
  ];

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl surface-card p-3">
      <div className="flex flex-wrap items-center gap-1.5 rounded-lg surface-subtle border p-1">
        {buttons.map((b) => (
          <button
            key={b.id}
            type="button"
            onClick={() => applyPreset(b.id)}
            className={`whitespace-nowrap shrink-0 rounded-md px-3 py-1.5 text-xs font-medium transition-all ${
              preset === b.id
                ? 'btn-accent'
                : 'text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-slate-100'
            }`}
          >
            {b.label}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2 text-xs">
        <input
          type="date"
          value={fromDate}
          onChange={(e) => onChange({ preset: 'CUSTOM', fromDate: e.target.value, toDate })}
          className="rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-2.5 py-1.5 text-xs font-mono-num text-slate-800 dark:text-slate-100"
        />
        <span className="text-slate-400">থেকে</span>
        <input
          type="date"
          value={toDate}
          onChange={(e) => onChange({ preset: 'CUSTOM', fromDate, toDate: e.target.value })}
          className="rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-2.5 py-1.5 text-xs font-mono-num text-slate-800 dark:text-slate-100"
        />
      </div>
    </div>
  );
};
