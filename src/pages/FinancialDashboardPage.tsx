import React, { useMemo, useState } from 'react';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { dateKey, dateRangeList, formatMoney, monthStartIso, todayIso } from '../utils/format';
import { SaleReturnStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { PurchaseStorage } from '../storage/purchaseStorage';
import { BusinessProfitCalculator } from '../engine/profitCalculator';

export const FinancialDashboardPage: React.FC<{ revision: number }> = ({ revision }) => {
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: 'MONTH',
    fromDate: monthStartIso(),
    toDate: todayIso(),
  });
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  const series = useMemo(() => {
    const days = dateRangeList(filter.fromDate, filter.toDate).slice(-31);
    const txs = SalesTransactionStorage.getTransactions();
    const returns = SaleReturnStorage.getReturns();
    const exchanges = SaleReturnStorage.getExchanges().filter(
      (ex) => ex.netAmount !== undefined || ex.purchasePrice !== undefined
    );
    const purchases = PurchaseStorage.getPurchases();
    const profitReport = BusinessProfitCalculator.calculate(filter.fromDate, filter.toDate);
    const profitByDay = new Map(profitReport.days.map((d) => [d.date, d.netProfit]));

    return days.map((d) => {
      const grossSales = txs
        .filter((t) => dateKey(t.date) === d)
        .reduce((s, t) => s + t.total, 0);
      const retAmt = returns
        .filter((r) => dateKey(r.date) === d)
        .reduce((s, r) => s + r.amount, 0);
      const exAmt = exchanges
        .filter((ex) => dateKey(ex.date) === d)
        .reduce(
          (s, ex) =>
            s +
            (ex.netAmount !== undefined
              ? Math.max(0, ex.netAmount)
              : Math.max(0, ex.quantity * ex.unitPrice - (ex.discount ?? 0))),
          0
        );
      const sales = Math.max(0, grossSales - retAmt + exAmt);
      const purchase = purchases
        .filter((p) => dateKey(p.date) === d)
        .reduce((s, p) => s + p.quantity * p.purchasePrice, 0);
      const profit = profitByDay.get(d) ?? 0;
      return { date: d, sales, purchase, profit };
    });
  }, [filter, revision]);

  const totals = useMemo(() => {
    return series.reduce(
      (acc, r) => ({
        sales: acc.sales + r.sales,
        purchase: acc.purchase + r.purchase,
        profit: acc.profit + r.profit,
      }),
      { sales: 0, purchase: 0, profit: 0 }
    );
  }, [series]);

  const maxVal = Math.max(
    1,
    ...series.flatMap((r) => [Math.abs(r.sales), Math.abs(r.purchase), Math.abs(r.profit)])
  );

  const width = 820;
  const height = 260;
  const padLeft = 72;
  const padRight = 20;
  const padTop = 20;
  const padBottom = 24;
  const plotW = width - padLeft - padRight;
  const plotH = height - padTop - padBottom;

  const coordFor = (idx: number, val: number) => {
    const x = series.length <= 1 ? padLeft : padLeft + (idx / (series.length - 1)) * plotW;
    const clamped = Math.max(0, val);
    const y = padTop + plotH - (clamped / maxVal) * plotH;
    return { x, y };
  };

  const toPoints = (getter: (r: (typeof series)[number]) => number) => {
    if (series.length <= 1) return '';
    return series
      .map((r, i) => {
        const { x, y } = coordFor(i, getter(r));
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  };

  const toAreaPath = (getter: (r: (typeof series)[number]) => number) => {
    if (series.length <= 1) return '';
    const pts = series.map((r, i) => coordFor(i, getter(r)));
    const baseY = padTop + plotH;
    const d = pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
    return `${d} L ${pts[pts.length - 1].x.toFixed(1)} ${baseY.toFixed(1)} L ${pts[0].x.toFixed(1)} ${baseY.toFixed(1)} Z`;
  };

  const hovered = hoverIdx !== null && series[hoverIdx] ? series[hoverIdx] : null;

  return (
    <div className="space-y-4">
      <DateFilterBar
        preset={filter.preset}
        fromDate={filter.fromDate}
        toDate={filter.toDate}
        onChange={setFilter}
        includeAll={false}
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">সময়কালে মোট বিক্রয়</span>
            <span className="h-2.5 w-2.5 rounded-xs bg-emerald-500" />
          </div>
          <div className="mt-1.5 text-xl font-bold tracking-tight font-mono-num text-emerald-700 dark:text-emerald-400">
            {formatMoney(totals.sales)}
          </div>
        </div>
        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">সময়কালে মোট ক্রয়</span>
            <span className="h-2.5 w-2.5 rounded-xs bg-blue-500" />
          </div>
          <div className="mt-1.5 text-xl font-bold tracking-tight font-mono-num text-blue-700 dark:text-blue-400">
            {formatMoney(totals.purchase)}
          </div>
        </div>
        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">সময়কালে নিট লাভ</span>
            <span className="h-2.5 w-2.5 rounded-xs bg-amber-500" />
          </div>
          <div className="mt-1.5 text-xl font-bold tracking-tight font-mono-num text-amber-600 dark:text-amber-400">
            {formatMoney(totals.profit)}
          </div>
        </div>
      </div>

      {/* SVG Trend Chart */}
      <div className="rounded-xl surface-card p-4 sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div>
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              দৈনিক বিক্রয়, ক্রয় ও মুনাফার ট্রেন্ড (সর্বশেষ ৩১ দিন)
            </h3>
            {hovered ? (
              <div className="mt-1 text-xs font-mono-num text-slate-600 dark:text-slate-300">
                <strong className="text-slate-900 dark:text-slate-100">{hovered.date}</strong> · বিক্রয়:{' '}
                <strong className="text-emerald-700 dark:text-emerald-400">{formatMoney(hovered.sales)}</strong> · ক্রয়:{' '}
                <strong className="text-blue-700 dark:text-blue-400">{formatMoney(hovered.purchase)}</strong> · লাভ:{' '}
                <strong className="text-amber-600 dark:text-amber-400">{formatMoney(hovered.profit)}</strong>
              </div>
            ) : (
              <div className="mt-1 text-xs text-slate-400 dark:text-slate-500">
                যেকোনো দিনের বিস্তারিত দেখতে চার্টের ওপর মাউস রাখুন
              </div>
            )}
          </div>
          <div className="flex items-center gap-4 text-xs text-slate-600 dark:text-slate-300">
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-xs bg-emerald-500 inline-block" /> বিক্রয়
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-xs bg-blue-500 inline-block" /> ক্রয়
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-xs bg-amber-500 inline-block" /> লাভ
            </span>
          </div>
        </div>

        {series.length <= 1 ? (
          <div className="py-12 text-center text-xs text-slate-500">
            চার্ট দেখাতে কমপক্ষে ২ দিনের তারিখ সীমা নির্বাচন করুন
          </div>
        ) : (
          <div className="w-full overflow-x-auto">
            <svg
              viewBox={`0 0 ${width} ${height}`}
              className="w-full min-w-[540px] h-64 select-none"
              onMouseLeave={() => setHoverIdx(null)}
            >
              <defs>
                <linearGradient id="salesAreaGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--chart-sales)" stopOpacity="0.22" />
                  <stop offset="100%" stopColor="var(--chart-sales)" stopOpacity="0.01" />
                </linearGradient>
                <linearGradient id="profitAreaGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--chart-profit)" stopOpacity="0.16" />
                  <stop offset="100%" stopColor="var(--chart-profit)" stopOpacity="0.0" />
                </linearGradient>
              </defs>

              {[0, 0.25, 0.5, 0.75, 1].map((frac) => {
                const y = padTop + plotH - frac * plotH;
                const tickVal = Math.round(maxVal * frac);
                return (
                  <g key={frac}>
                    <line
                      x1={padLeft}
                      y1={y}
                      x2={width - padRight}
                      y2={y}
                      stroke="currentColor"
                      className="text-slate-200 dark:text-slate-800"
                      strokeDasharray={frac === 0 ? undefined : '3 3'}
                    />
                    <text
                      x={padLeft - 8}
                      y={y + 3}
                      textAnchor="end"
                      className="fill-slate-400 dark:fill-slate-500 text-[10px] font-mono"
                    >
                      {tickVal >= 1000 ? `${(tickVal / 1000).toFixed(tickVal >= 10000 ? 0 : 1)}k` : tickVal}
                    </text>
                  </g>
                );
              })}

              <path d={toAreaPath((r) => r.sales)} fill="url(#salesAreaGrad)" />
              <path d={toAreaPath((r) => r.profit)} fill="url(#profitAreaGrad)" />

              <polyline
                fill="none"
                stroke="var(--chart-purchase)"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                points={toPoints((r) => r.purchase)}
              />
              <polyline
                fill="none"
                stroke="var(--chart-sales)"
                strokeWidth="2.6"
                strokeLinecap="round"
                strokeLinejoin="round"
                points={toPoints((r) => r.sales)}
              />
              <polyline
                fill="none"
                stroke="var(--chart-profit)"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                points={toPoints((r) => r.profit)}
              />

              {/* Hover crosshair & active dots */}
              {hoverIdx !== null && series[hoverIdx] && (
                <g>
                  <line
                    x1={coordFor(hoverIdx, 0).x}
                    y1={padTop}
                    x2={coordFor(hoverIdx, 0).x}
                    y2={padTop + plotH}
                    stroke="currentColor"
                    className="text-slate-300 dark:text-slate-600"
                    strokeDasharray="3 3"
                  />
                  <circle
                    cx={coordFor(hoverIdx, series[hoverIdx].sales).x}
                    cy={coordFor(hoverIdx, series[hoverIdx].sales).y}
                    r="4"
                    fill="var(--chart-sales)"
                    stroke="#ffffff"
                    strokeWidth="1.5"
                  />
                  <circle
                    cx={coordFor(hoverIdx, series[hoverIdx].purchase).x}
                    cy={coordFor(hoverIdx, series[hoverIdx].purchase).y}
                    r="3.5"
                    fill="var(--chart-purchase)"
                    stroke="#ffffff"
                    strokeWidth="1.5"
                  />
                  <circle
                    cx={coordFor(hoverIdx, series[hoverIdx].profit).x}
                    cy={coordFor(hoverIdx, series[hoverIdx].profit).y}
                    r="3.5"
                    fill="var(--chart-profit)"
                    stroke="#ffffff"
                    strokeWidth="1.5"
                  />
                </g>
              )}

              {/* Invisible hover columns */}
              {series.map((r, i) => {
                const colW = plotW / Math.max(1, series.length - 1);
                const cx = coordFor(i, 0).x;
                return (
                  <rect
                    key={r.date}
                    x={Math.max(padLeft, cx - colW / 2)}
                    y={padTop}
                    width={colW}
                    height={plotH}
                    fill="transparent"
                    onMouseEnter={() => setHoverIdx(i)}
                  />
                );
              })}
            </svg>
            <div className="flex justify-between text-[11px] font-mono-num text-slate-400 pl-[72px] pr-5">
              <span>{series[0]?.date}</span>
              <span>{series[Math.floor(series.length / 2)]?.date}</span>
              <span>{series[series.length - 1]?.date}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
