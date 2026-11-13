import React, { useMemo, useState } from 'react';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { BusinessProfitCalculator } from '../engine/profitCalculator';
import { formatMoney, monthStartIso, todayIso } from '../utils/format';

export const ProfitPage: React.FC<{ revision: number }> = ({ revision }) => {
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: 'MONTH',
    fromDate: monthStartIso(),
    toDate: todayIso(),
  });

  const report = useMemo(
    () => BusinessProfitCalculator.calculate(filter.fromDate, filter.toDate),
    [filter, revision]
  );

  const netSales = report.grossSales - report.salesDiscount - report.returnRevenue;
  const netCogs = report.costOfGoodsSold - report.returnCost;
  const grossProfit = netSales - netCogs;

  return (
    <div className="space-y-4">
      <DateFilterBar preset={filter.preset} fromDate={filter.fromDate} toDate={filter.toDate} onChange={setFilter} />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="rounded-xl surface-card p-4">
          <div className="text-xs text-slate-500">নিট বিক্রয় (ডিসকাউন্ট ও রিটার্ন বাদে)</div>
          <div className="mt-1 text-xl font-bold font-mono-num">{formatMoney(netSales)}</div>
        </div>
        <div className="rounded-xl surface-card p-4">
          <div className="text-xs text-slate-500">মোট লাভ (Gross Profit)</div>
          <div className="mt-1 text-xl font-bold font-mono-num">{formatMoney(grossProfit)}</div>
        </div>
        <div className="rounded-xl surface-card p-4">
          <div className="text-xs text-slate-500">খরচ ও ড্যামেজ ক্ষতি</div>
          <div className="mt-1 text-xl font-bold font-mono-num text-amber-600 dark:text-amber-400">
            {formatMoney(report.expenses + report.damageLoss)}
          </div>
        </div>
        <div className="rounded-xl surface-card p-4">
          <div className="text-xs text-slate-500">নিট মুনাফা (Net Profit)</div>
          <div
            className={`mt-1 text-xl font-bold font-mono-num ${
              report.netProfit >= 0
                ? 'text-emerald-700 dark:text-emerald-400'
                : 'text-red-600 dark:text-red-400'
            }`}
          >
            {formatMoney(report.netProfit)}
          </div>
        </div>
      </div>

      {report.unrecoveredOperatingExpenses > 0.004 && (
        <div className="rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50/70 dark:bg-amber-950/30 p-3.5 text-xs text-amber-900 dark:text-amber-200">
          অপরিশোধিত অপারেটিং খরচ ক্যারি-ফরোয়ার্ড: <strong className="font-mono-num">{formatMoney(report.unrecoveredOperatingExpenses)}</strong> (ভবিষ্যতের ট্রেডিং লাভ থেকে সমন্বয় হবে)।
        </div>
      )}

      {report.warnings.length > 0 && (
        <div className="rounded-xl border border-amber-200 dark:border-amber-900/60 bg-white dark:bg-slate-900 p-4 text-xs text-slate-600 dark:text-slate-300 space-y-1">
          <div className="font-semibold text-amber-700 dark:text-amber-400">লাভ গণনার সতর্কতা</div>
          <ul className="list-disc pl-5 space-y-1">
            {report.warnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="rounded-xl surface-card overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 text-sm font-semibold">
          দৈনিক লাভ-ক্ষতি বিবরণী
        </div>
        {report.days.length === 0 ? (
          <div className="py-12 text-center text-sm text-slate-500">নির্বাচিত সময়কালে কোনো লেনদেন নেই</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">তারিখ</th>
                  <th className="px-3 py-3 text-right">বিক্রয়</th>
                  <th className="px-3 py-3 text-right">ডিসকাউন্ট ও রিটার্ন</th>
                  <th className="px-3 py-3 text-right">নিট COGS</th>
                  <th className="px-3 py-3 text-right">খরচ ও ড্যামেজ</th>
                  <th className="px-4 py-3 text-right">নিট লাভ</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-mono-num">
                {report.days.map((d) => (
                  <tr key={d.date} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                    <td className="px-4 py-2.5 text-slate-600 dark:text-slate-300">{d.date}</td>
                    <td className="px-3 py-2.5 text-right">{formatMoney(d.grossSales)}</td>
                    <td className="px-3 py-2.5 text-right text-slate-500">
                      -{formatMoney(d.salesDiscount + d.returnRevenue)}
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      {formatMoney(d.costOfGoodsSold - d.returnCost)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-amber-600 dark:text-amber-400">
                      {formatMoney(d.expenses + d.damageLoss)}
                    </td>
                    <td
                      className={`px-4 py-2.5 text-right font-bold ${
                        d.netProfit >= 0
                          ? 'text-emerald-700 dark:text-emerald-400'
                          : 'text-red-600 dark:text-red-400'
                      }`}
                    >
                      {formatMoney(d.netProfit)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
