import React, { useMemo, useState } from 'react';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { dateKey, dateRangeList, formatMoney, monthStartIso, todayIso } from '../utils/format';
import { SaleReturnStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { PurchaseStorage } from '../storage/purchaseStorage';
import { ExpenseStorage } from '../storage/financeStorage';
import { BusinessProfitCalculator } from '../engine/profitCalculator';

export const FinancialCalendarPage: React.FC<{ revision: number }> = ({ revision }) => {
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: 'MONTH',
    fromDate: monthStartIso(),
    toDate: todayIso(),
  });

  const rows = useMemo(() => {
    const days = dateRangeList(filter.fromDate, filter.toDate).reverse();
    const txs = SalesTransactionStorage.getTransactions();
    const returns = SaleReturnStorage.getReturns();
    const exchanges = SaleReturnStorage.getExchanges().filter(
      (ex) => ex.netAmount !== undefined || ex.purchasePrice !== undefined
    );
    const purchases = PurchaseStorage.getPurchases();
    const expenses = ExpenseStorage.getExpenses();
    const profit = BusinessProfitCalculator.calculate(filter.fromDate, filter.toDate);
    const profitMap = new Map(profit.days.map((d) => [d.date, d.netProfit]));

    return days.map((date) => {
      const grossSales = txs
        .filter((t) => dateKey(t.date) === date)
        .reduce((s, t) => s + t.total, 0);
      const retAmt = returns
        .filter((r) => dateKey(r.date) === date)
        .reduce((s, r) => s + r.amount, 0);
      const exAmt = exchanges
        .filter((ex) => dateKey(ex.date) === date)
        .reduce(
          (s, ex) =>
            s +
            (ex.netAmount !== undefined
              ? Math.max(0, ex.netAmount)
              : Math.max(0, ex.quantity * ex.unitPrice - (ex.discount ?? 0))),
          0
        );
      const salesAmt = Math.max(0, grossSales - retAmt + exAmt);
      const purchaseAmt = purchases
        .filter((p) => dateKey(p.date) === date)
        .reduce((s, p) => s + p.quantity * p.purchasePrice, 0);
      const expenseAmt = expenses.filter((e) => e.date === date).reduce((s, e) => s + e.amount, 0);
      const netProfit = profitMap.get(date) ?? 0;
      return { date, salesAmt, purchaseAmt, expenseAmt, netProfit };
    });
  }, [filter, revision]);

  return (
    <div className="space-y-4">
      <DateFilterBar preset={filter.preset} fromDate={filter.fromDate} toDate={filter.toDate} onChange={setFilter} includeAll={false} />

      <div className="rounded-xl surface-card overflow-hidden">
        {rows.length === 0 ? (
          <div className="py-12 text-center text-sm text-slate-500">সঠিক তারিখ সীমা নির্বাচন করুন</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">তারিখ</th>
                  <th className="px-4 py-3 text-right">বিক্রয়</th>
                  <th className="px-4 py-3 text-right">ক্রয়</th>
                  <th className="px-4 py-3 text-right">খরচ</th>
                  <th className="px-4 py-3 text-right">নিট লাভ</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-mono-num">
                {rows.map((r) => (
                  <tr key={r.date} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                    <td className="px-4 py-2.5 text-slate-700 dark:text-slate-300">{r.date}</td>
                    <td className="px-4 py-2.5 text-right">{formatMoney(r.salesAmt)}</td>
                    <td className="px-4 py-2.5 text-right">{formatMoney(r.purchaseAmt)}</td>
                    <td className="px-4 py-2.5 text-right text-amber-600 dark:text-amber-400">
                      {formatMoney(r.expenseAmt)}
                    </td>
                    <td
                      className={`px-4 py-2.5 text-right font-semibold ${
                        r.netProfit > 0
                          ? 'text-emerald-700 dark:text-emerald-400'
                          : r.netProfit < 0
                          ? 'text-red-600 dark:text-red-400'
                          : 'text-slate-400'
                      }`}
                    >
                      {formatMoney(r.netProfit)}
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
