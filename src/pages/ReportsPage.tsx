import React, { useMemo, useState } from 'react';
import { Download, Printer } from 'lucide-react';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { dateKey, downloadCsv, formatMoney, monthStartIso, todayIso } from '../utils/format';
import { SaleReturnStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { PurchaseStorage, SupplierDueStorage } from '../storage/purchaseStorage';
import { ExpenseStorage, PartnerStorage } from '../storage/financeStorage';
import { ProductStorage } from '../storage/inventoryStorage';
import { CustomerDueStorage } from '../storage/dueStorage';
import { BusinessProfitCalculator } from '../engine/profitCalculator';
import { SettingsStorage } from '../storage/businessStorage';

export const ReportsPage: React.FC<{ revision: number }> = ({ revision }) => {
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: 'MONTH',
    fromDate: monthStartIso(),
    toDate: todayIso(),
  });

  const [sections, setSections] = useState({
    sales: true,
    purchases: true,
    expenses: true,
    stock: true,
    dues: true,
    partners: true,
  });

  const data = useMemo(() => {
    const inRange = (d: string) => {
      const k = dateKey(d);
      return k >= filter.fromDate && k <= filter.toDate;
    };
    const salesTx = SalesTransactionStorage.getTransactions().filter((t) => inRange(t.date));
    const saleReturns = SaleReturnStorage.getReturns().filter((r) => inRange(r.date));
    const saleExchanges = SaleReturnStorage.getExchanges().filter(
      (ex) => inRange(ex.date) && (ex.netAmount !== undefined || ex.purchasePrice !== undefined)
    );
    const purchases = PurchaseStorage.getPurchases().filter((p) => inRange(p.date));
    const expenses = ExpenseStorage.getExpenses().filter((e) => inRange(e.date));
    const products = ProductStorage.getProducts();
    const customerDues = Object.entries(CustomerDueStorage.getBalances());
    const supplierDues = SupplierDueStorage.getBalances().filter((s) => s.balance > 0);
    const partners = PartnerStorage.getPartners();
    const profit = BusinessProfitCalculator.calculate(filter.fromDate, filter.toDate);

    const grossSales = salesTx.reduce((s, t) => s + t.total, 0);
    const totalReturns = saleReturns.reduce((s, r) => s + r.amount, 0);
    const totalExchanges = saleExchanges.reduce(
      (s, ex) =>
        s +
        (ex.netAmount !== undefined
          ? Math.max(0, ex.netAmount)
          : Math.max(0, ex.quantity * ex.unitPrice - (ex.discount ?? 0))),
      0
    );
    const netSales = Math.max(0, grossSales - totalReturns + totalExchanges);
    const totalPaid =
      salesTx.reduce((s, t) => s + t.paid, 0) +
      saleExchanges.reduce((s, ex) => s + (ex.paidAmount ?? 0), 0);
    const totalDiscount =
      salesTx.reduce((s, t) => s + t.discount, 0) +
      saleExchanges.reduce((s, ex) => s + (ex.discount ?? 0), 0);

    return {
      salesTx,
      saleReturns,
      saleExchanges,
      grossSales,
      totalReturns,
      totalExchanges,
      netSales,
      totalPaid,
      totalDiscount,
      purchases,
      expenses,
      products,
      customerDues,
      supplierDues,
      partners,
      profit,
    };
  }, [filter, revision]);

  const handleExportCsv = () => {
    const rows: string[][] = [
      [SettingsStorage.getShopName(), `রিপোর্ট সময়কাল: ${filter.fromDate} থেকে ${filter.toDate}`],
      ['নিট লাভ', String(data.profit.netProfit), 'মোট খরচ', String(data.profit.expenses), 'ড্যামেজ ক্ষতি', String(data.profit.damageLoss)],
      [],
    ];
    if (sections.sales) {
      rows.push(
        ['=== বিক্রয় ও রিটার্ন সারসংক্ষেপ ==='],
        ['নিট বিক্রয়', String(data.netSales), 'রিটার্ন', String(data.totalReturns), 'এক্সচেঞ্জ বিক্রয়', String(data.totalExchanges), 'মোট ছাড়', String(data.totalDiscount)],
        ['তারিখ', 'ইনভয়েস', 'ক্রেতা', 'ছাড়', 'মোট', 'জমা', 'বাকি']
      );
      data.salesTx.forEach((t) =>
        rows.push([t.date, t.transactionId, t.customer, String(t.discount), String(t.total), String(t.paid), String(t.due)])
      );
      rows.push([]);
    }
    if (sections.purchases) {
      rows.push(['=== ক্রয় সারসংক্ষেপ ==='], ['তারিখ', 'পণ্য', 'সরবরাহকারী', 'পরিমাণ', 'দর', 'মোট']);
      data.purchases.forEach((p) =>
        rows.push([
          p.date,
          p.productName,
          p.supplier,
          String(p.quantity),
          String(p.purchasePrice),
          String(p.quantity * p.purchasePrice),
        ])
      );
      rows.push([]);
    }
    if (sections.expenses) {
      rows.push(['=== খরচ ==='], ['তারিখ', 'খাত', 'নোট', 'পরিমাণ']);
      data.expenses.forEach((e) => rows.push([e.date, e.category, e.note, String(e.amount)]));
      rows.push([]);
    }
    if (sections.stock) {
      rows.push(['=== বর্তমান স্টক ==='], ['কোড', 'পণ্য', 'স্টক', 'ক্রয়মূল্য', 'স্টক মূল্য']);
      data.products.forEach((p) =>
        rows.push([
          p.code,
          p.name,
          String(p.stockQuantity),
          String(p.purchasePrice),
          String(p.stockQuantity * p.purchasePrice),
        ])
      );
      rows.push([]);
    }
    if (sections.dues) {
      rows.push(['=== ক্রেতার পাওনা ==='], ['ক্রেতা', 'বকেয়া পরিমাণ']);
      data.customerDues.forEach(([k, v]) => rows.push([k, String(v)]));
      rows.push([]);
      rows.push(['=== সরবরাহকারীর দেনা ==='], ['সরবরাহকারী', 'বকেয়া পরিমাণ']);
      data.supplierDues.forEach((s) => rows.push([s.name, String(s.balance)]));
      rows.push([]);
    }
    if (sections.partners) {
      rows.push(['=== পার্টনার তালিকা ==='], ['নাম', 'লভ্যাংশ হার (%)', 'বর্তমান মূলধন']);
      data.partners.forEach((p) => rows.push([p.name, String(p.profitPercentage), String(p.shareAmount)]));
      rows.push([]);
    }
    downloadCsv(`tamanna-report-${filter.fromDate}-to-${filter.toDate}.csv`, rows);
  };

  return (
    <div className="space-y-4">
      <div className="no-print space-y-4">
        <DateFilterBar preset={filter.preset} fromDate={filter.fromDate} toDate={filter.toDate} onChange={setFilter} />

        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl surface-card p-4">
          <div className="flex flex-wrap items-center gap-4 text-xs sm:text-sm">
            {(
              [
                ['sales', 'বিক্রয়'],
                ['purchases', 'ক্রয়'],
                ['expenses', 'খরচ'],
                ['stock', 'স্টক'],
                ['dues', 'বাকি/দেনা'],
                ['partners', 'পার্টনার'],
              ] as const
            ).map(([k, label]) => (
              <label key={k} className="inline-flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={sections[k]}
                  onChange={(e) => setSections((s) => ({ ...s, [k]: e.target.checked }))}
                  className="rounded"
                />
                <span>{label}</span>
              </label>
            ))}
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={handleExportCsv}
              className="whitespace-nowrap inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-xs sm:text-sm font-medium hover:bg-slate-50 dark:hover:bg-slate-700"
            >
              <Download className="h-4 w-4" />
              <span>CSV ডাউনলোড</span>
            </button>
            <button
              type="button"
              onClick={() => window.print()}
              className="whitespace-nowrap inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-xs sm:text-sm font-medium"
            >
              <Printer className="h-4 w-4" />
              <span>প্রিন্ট / PDF</span>
            </button>
          </div>
        </div>
      </div>

      {/* Printable Report Sheet */}
      <div className="rounded-xl surface-card p-6 space-y-6">
        <div className="border-b border-slate-200 dark:border-slate-800 pb-4 flex justify-between items-start">
          <div>
            <h2 className="text-lg font-bold">{SettingsStorage.getShopName()} — ব্যবসায়িক রিপোর্ট</h2>
            <p className="text-xs text-slate-500 font-mono-num">
              সময়কাল: {filter.fromDate} থেকে {filter.toDate}
            </p>
          </div>
          <div className="text-right text-xs">
            <div>নিট লাভ: <strong className="font-mono-num">{formatMoney(data.profit.netProfit)}</strong></div>
            <div>মোট খরচ: <strong className="font-mono-num">{formatMoney(data.profit.expenses)}</strong></div>
          </div>
        </div>

        {sections.sales && (
          <div>
            <h3 className="text-sm font-semibold mb-2">বিক্রয় সারসংক্ষেপ ({data.salesTx.length}টি ইনভয়েস)</h3>
            <p className="text-xs text-slate-600 dark:text-slate-300 font-mono-num">
              নিট বিক্রয়: {formatMoney(data.netSales)} (মূল বিল: {formatMoney(data.grossSales)} · রিটার্ন: -{formatMoney(data.totalReturns)} · এক্সচেঞ্জ: +{formatMoney(data.totalExchanges)}) · মোট ছাড়:{' '}
              {formatMoney(data.totalDiscount)} · নগদ আদায়: {formatMoney(data.totalPaid)}
            </p>
          </div>
        )}

        {sections.purchases && (
          <div>
            <h3 className="text-sm font-semibold mb-2">ক্রয় সারসংক্ষেপ ({data.purchases.length}টি)</h3>
            <p className="text-xs text-slate-600 dark:text-slate-300 font-mono-num">
              মোট ক্রয়: {formatMoney(data.purchases.reduce((s, p) => s + p.quantity * p.purchasePrice, 0))}
            </p>
          </div>
        )}

        {sections.expenses && (
          <div>
            <h3 className="text-sm font-semibold mb-2">খরচ ({data.expenses.length}টি)</h3>
            <p className="text-xs text-slate-600 dark:text-slate-300 font-mono-num">
              মোট খরচ: {formatMoney(data.expenses.reduce((s, e) => s + e.amount, 0))}
            </p>
          </div>
        )}

        {sections.stock && (
          <div>
            <h3 className="text-sm font-semibold mb-2">বর্তমান স্টক ({data.products.length}টি পণ্য)</h3>
            <p className="text-xs text-slate-600 dark:text-slate-300 font-mono-num">
              মোট পিস: {data.products.reduce((s, p) => s + p.stockQuantity, 0)} · স্টক মূল্য:{' '}
              {formatMoney(data.products.reduce((s, p) => s + p.stockQuantity * p.purchasePrice, 0))}
            </p>
          </div>
        )}

        {sections.dues && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
            <div>
              <h3 className="text-sm font-semibold mb-1.5">ক্রেতার পাওনা ({data.customerDues.length})</h3>
              {data.customerDues.map(([k, v]) => (
                <div key={k} className="flex justify-between py-1 border-b border-slate-100 dark:border-slate-800">
                  <span>{k}</span>
                  <span className="font-mono-num font-medium">{formatMoney(v)}</span>
                </div>
              ))}
            </div>
            <div>
              <h3 className="text-sm font-semibold mb-1.5">সরবরাহকারীর দেনা ({data.supplierDues.length})</h3>
              {data.supplierDues.map((s) => (
                <div key={s.name} className="flex justify-between py-1 border-b border-slate-100 dark:border-slate-800">
                  <span>{s.name}</span>
                  <span className="font-mono-num font-medium">{formatMoney(s.balance)}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {sections.partners && (
          <div>
            <h3 className="text-sm font-semibold mb-1.5">পার্টনার তালিকা ({data.partners.length})</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
              {data.partners.map((p) => (
                <div key={p.id} className="flex justify-between rounded-lg border border-slate-200 dark:border-slate-800 p-2.5">
                  <span>{p.name} ({p.profitPercentage}%)</span>
                  <span className="font-mono-num font-semibold">{formatMoney(p.shareAmount)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
