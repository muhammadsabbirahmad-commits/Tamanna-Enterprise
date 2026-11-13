import React, { useMemo } from 'react';
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  BookOpen,
  CalendarDays,
  Cloud,
  CloudOff,
  Database,
  FileSpreadsheet,
  HandCoins,
  Package,
  PlusCircle,
  ShoppingBag,
  ShoppingCart,
  TrendingUp,
  Truck,
  Users,
  Wallet,
  Warehouse,
} from 'lucide-react';
import { NavRoute } from '../types';
import { dateKey, formatMoney, todayIso } from '../utils/format';
import { ProductStorage, InventoryMetaStorage } from '../storage/inventoryStorage';
import { SaleReturnStorage, SalesStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { PurchaseStorage, SupplierDueStorage } from '../storage/purchaseStorage';
import { CustomerDueStorage } from '../storage/dueStorage';
import { BusinessProfitCalculator } from '../engine/profitCalculator';
import { seedSampleBusinessData } from '../engine/sampleData';
import { SettingsStorage } from '../storage/businessStorage';
import { SyncStatusSnapshot } from '../storage/syncEngine';

interface DashboardPageProps {
  revision: number;
  onNavigate: (route: NavRoute) => void;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
  syncStatus?: SyncStatusSnapshot;
  onSignInGoogle?: () => void;
}

export const DashboardPage: React.FC<DashboardPageProps> = ({
  revision,
  onNavigate,
  onDataChange,
  notify,
  syncStatus,
  onSignInGoogle,
}) => {
  const today = todayIso();
  const shopName = SettingsStorage.getShopName();

  const stats = useMemo(() => {
    const products = ProductStorage.getProducts();
    const transactions = SalesTransactionStorage.getTransactions();
    const sales = SalesStorage.getSales();
    const purchases = PurchaseStorage.getPurchases();

    const returns = SaleReturnStorage.getReturns();
    const exchanges = SaleReturnStorage.getExchanges();

    const todaySalesTx = transactions.filter((t) => dateKey(t.date) === today);
    const todayReturnsTotal = returns
      .filter((r) => dateKey(r.date) === today)
      .reduce((s, r) => s + r.amount, 0);
    const todayExchangesTotal = exchanges
      .filter(
        (ex) =>
          dateKey(ex.date) === today &&
          (ex.netAmount !== undefined || ex.purchasePrice !== undefined)
      )
      .reduce(
        (s, ex) =>
          s +
          (ex.netAmount !== undefined
            ? Math.max(0, ex.netAmount)
            : Math.max(0, ex.quantity * ex.unitPrice - (ex.discount ?? 0))),
        0
      );
    const todaySalesTotal = Math.max(
      0,
      todaySalesTx.reduce((s, t) => s + t.total, 0) - todayReturnsTotal + todayExchangesTotal
    );

    const todayPurchases = purchases.filter((p) => dateKey(p.date) === today);
    const todayPurchaseTotal = todayPurchases.reduce(
      (s, p) => s + p.quantity * p.purchasePrice,
      0
    );

    const profitReport = BusinessProfitCalculator.calculate(today, today);
    const totalStockUnits = products.reduce((s, p) => s + Math.max(0, p.stockQuantity), 0);
    const totalStockValue = products.reduce(
      (s, p) => s + Math.max(0, p.stockQuantity) * Math.max(0, p.purchasePrice),
      0
    );

    const lowStockProducts = products.filter((p) => {
      const limit = InventoryMetaStorage.getMeta(p.code).lowStockLimit;
      return p.stockQuantity <= limit;
    });

    const customerDueTotal = Object.values(CustomerDueStorage.getBalances()).reduce(
      (s, v) => s + v,
      0
    );
    const supplierDueTotal = SupplierDueStorage.getBalances().reduce(
      (s, x) => s + x.balance,
      0
    );

    const recentSales = sales.slice(0, 6);

    return {
      isEmpty:
        products.length === 0 &&
        transactions.length === 0 &&
        purchases.length === 0,
      productCount: products.length,
      todaySalesTotal,
      todaySalesCount: todaySalesTx.length,
      todayPurchaseTotal,
      todayPurchaseCount: todayPurchases.length,
      todayProfit: profitReport.netProfit,
      totalStockUnits,
      totalStockValue,
      lowStockProducts,
      customerDueTotal,
      supplierDueTotal,
      recentSales,
    };
  }, [today, revision]);

  const handleSeedSample = () => {
    seedSampleBusinessData();
    onDataChange();
    notify('নমুনা ব্যবসায়িক ডাটা লোড করা হয়েছে', 'success');
  };

  const modules: Array<{
    route: NavRoute;
    title: string;
    desc: string;
    icon: React.ComponentType<{ className?: string }>;
  }> = [
    { route: 'double-entry', title: 'ডাবল এন্ট্রি হিসাব', desc: 'ট্রায়াল ব্যালান্স ও জার্নাল', icon: BookOpen },
    { route: 'products', title: 'পণ্য তালিকা', desc: `${stats.productCount}টি পণ্য`, icon: Package },
    { route: 'new-sale', title: 'নতুন বিক্রয় (POS)', desc: 'দ্রুত বিল ও ইনভয়েস', icon: PlusCircle },
    { route: 'sales', title: 'বিক্রয় তালিকা', desc: 'ইনভয়েস, রিটার্ন ও এডিট', icon: ShoppingCart },
    { route: 'purchases', title: 'ক্রয় ও মেমো', desc: 'সরবরাহকারী ক্রয় ও OCR পার্সার', icon: ShoppingBag },
    { route: 'stock', title: 'স্টক ও মেয়াদ', desc: 'ব্যাচ ও স্টক সমন্বয়', icon: Warehouse },
    { route: 'stock-alert', title: 'স্টক অ্যালার্ট', desc: `${stats.lowStockProducts.length}টি কম স্টক`, icon: AlertTriangle },
    { route: 'customer-due', title: 'ক্রেতার বাকি', desc: formatMoney(stats.customerDueTotal), icon: HandCoins },
    { route: 'supplier-due', title: 'সরবরাহকারীর দেনা', desc: formatMoney(stats.supplierDueTotal), icon: Truck },
    { route: 'finance', title: 'খরচ ও ড্যামেজ', desc: 'দৈনন্দিন ব্যয় ও ক্ষতি', icon: Wallet },
    { route: 'partners', title: 'পার্টনার ও মূলধন', desc: 'লভ্যাংশ বণ্টন ও মূলধন', icon: Users },
    { route: 'profit', title: 'লাভ-ক্ষতি বিশ্লেষণ', desc: 'দৈনিক নিট লাভ হিসাব', icon: TrendingUp },
    { route: 'financial-dashboard', title: 'আর্থিক চার্ট', desc: 'বিক্রয় ও মুনাফার ট্রেন্ড', icon: BarChart3 },
    { route: 'financial-calendar', title: 'আর্থিক ক্যালেন্ডার', desc: 'দৈনিক খতিয়ান সারণি', icon: CalendarDays },
    { route: 'reports', title: 'রিপোর্ট ও এক্সপোর্ট', desc: 'প্রিন্ট/পিডিএফ ও CSV', icon: FileSpreadsheet },
  ];

  return (
    <div className="space-y-6">
      {stats.isEmpty && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl surface-card p-5">
          <div>
            <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">
              {shopName}-এ স্বাগতম
            </h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
              আপনার দোকানে এখনো কোনো পণ্য বা লেনদেন যোগ করা হয়নি। সরাসরি পণ্য যোগ করে শুরু করুন অথবা এক ক্লিকে নমুনা ডাটা লোড করে পুরো সিস্টেমটি দেখুন।
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={handleSeedSample}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-3.5 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
            >
              <Database className="h-4 w-4" />
              <span>নমুনা ডাটা লোড করুন</span>
            </button>
            <button
              type="button"
              onClick={() => onNavigate('products')}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-sm font-semibold"
            >
              <PlusCircle className="h-4 w-4" />
              <span>পণ্য যোগ করুন</span>
            </button>
          </div>
        </div>
      )}

      {/* Multi-device Gmail Cloud Sync Status Banner */}
      {syncStatus && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl surface-card px-4 py-3">
          <div className="flex items-center gap-3 min-w-0">
            <div
              className={`rounded-lg p-2 shrink-0 ${
                syncStatus.user
                  ? syncStatus.online
                    ? 'bg-accent-soft text-accent'
                    : 'bg-amber-50 dark:bg-amber-950/50 text-amber-600 dark:text-amber-400'
                  : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400'
              }`}
            >
              {syncStatus.online ? <Cloud className="h-4 w-4" /> : <CloudOff className="h-4 w-4" />}
            </div>
            <div className="min-w-0 text-xs">
              {syncStatus.user ? (
                <>
                  <div className="font-semibold text-slate-900 dark:text-slate-100 truncate">
                    ক্লাউড সিঙ্ক সক্রিয়:{' '}
                    <span className="font-mono-num text-accent">{syncStatus.user.email}</span>
                  </div>
                  <div className="text-slate-500 dark:text-slate-400 font-mono-num">
                    {syncStatus.online ? 'অনলাইন রিয়েল-টাইম সিঙ্ক' : 'অফলাইন ক্যাশ মোড'} ·{' '}
                    {syncStatus.syncedCount}টি রেকর্ড সুরক্ষিত
                    {syncStatus.lastSyncedAt ? ` · সর্বশেষ: ${syncStatus.lastSyncedAt}` : ''}
                  </div>
                </>
              ) : (
                <>
                  <div className="font-semibold text-slate-900 dark:text-slate-100">
                    মাল্টি-ডিভাইস Gmail ক্লাউড সিঙ্ক ও অফলাইন মোড
                  </div>
                  <div className="text-slate-500 dark:text-slate-400">
                    আপনার বর্তমান পণ্য ও হিসাব অক্ষুণ্ণ রেখে সব ফোনে রিয়েল-টাইম আপডেট পেতে Gmail দিয়ে সংযুক্ত করুন।
                  </div>
                </>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {syncStatus.user ? (
              <button
                type="button"
                onClick={() => onNavigate('settings')}
                className="whitespace-nowrap shrink-0 rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
              >
                সিঙ্ক পরিচালনা
              </button>
            ) : (
              <button
                type="button"
                onClick={() => (onSignInGoogle ? onSignInGoogle() : onNavigate('settings'))}
                className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-1.5 text-xs font-semibold"
              >
                <Cloud className="h-3.5 w-3.5" />
                <span>Gmail দিয়ে সংযুক্ত করুন</span>
              </button>
            )}
          </div>
        </div>
      )}

      {/* KPI Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">আজকের বিক্রয়</span>
            <span className="rounded-lg bg-emerald-50 dark:bg-emerald-950/50 p-1.5 text-emerald-700 dark:text-emerald-400">
              <ShoppingCart className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-2.5 text-2xl font-bold tracking-tight font-mono-num text-slate-900 dark:text-slate-100">
            {formatMoney(stats.todaySalesTotal)}
          </div>
          <div className="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
            আজকের ইনভয়েস: <span className="font-mono-num font-semibold text-slate-700 dark:text-slate-300">{stats.todaySalesCount}টি</span>
          </div>
        </div>

        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">আজকের ক্রয়</span>
            <span className="rounded-lg bg-blue-50 dark:bg-blue-950/50 p-1.5 text-blue-700 dark:text-blue-400">
              <ShoppingBag className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-2.5 text-2xl font-bold tracking-tight font-mono-num text-slate-900 dark:text-slate-100">
            {formatMoney(stats.todayPurchaseTotal)}
          </div>
          <div className="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
            ক্রয় এন্ট্রি: <span className="font-mono-num font-semibold text-slate-700 dark:text-slate-300">{stats.todayPurchaseCount}টি</span>
          </div>
        </div>

        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">আজকের নিট লাভ</span>
            <span
              className={`rounded-lg p-1.5 ${
                stats.todayProfit >= 0
                  ? 'bg-accent-soft text-accent'
                  : 'bg-red-50 dark:bg-red-950/50 text-red-600 dark:text-red-400'
              }`}
            >
              <TrendingUp className="h-4 w-4" />
            </span>
          </div>
          <div
            className={`mt-2.5 text-2xl font-bold tracking-tight font-mono-num ${
              stats.todayProfit >= 0
                ? 'text-emerald-700 dark:text-emerald-400'
                : 'text-red-600 dark:text-red-400'
            }`}
          >
            {formatMoney(stats.todayProfit)}
          </div>
          <div className="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
            COGS ও খরচ বাদ দিয়ে নিট
          </div>
        </div>

        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">মোট স্টক মূল্য</span>
            <span className="rounded-lg bg-amber-50 dark:bg-amber-950/50 p-1.5 text-amber-700 dark:text-amber-400">
              <Warehouse className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-2.5 text-2xl font-bold tracking-tight font-mono-num text-slate-900 dark:text-slate-100">
            {formatMoney(stats.totalStockValue)}
          </div>
          <div className="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
            মোট মজুদ: <span className="font-mono-num font-semibold text-slate-700 dark:text-slate-300">{stats.totalStockUnits}</span> পিস · কম স্টক:{' '}
            <span
              className={`font-mono-num font-semibold ${
                stats.lowStockProducts.length > 0
                  ? 'text-amber-600 dark:text-amber-400'
                  : 'text-slate-700 dark:text-slate-300'
              }`}
            >
              {stats.lowStockProducts.length}
            </span>
          </div>
        </div>
      </div>

      {/* Quick Module Directory */}
      <div>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <h2 className="text-sm font-semibold tracking-tight text-slate-800 dark:text-slate-200">
            দ্রুত মডিউল নেভিগেশন
          </h2>
          <span className="text-xs text-slate-500 dark:text-slate-400 font-mono-num">
            ক্রেতার বাকি: <strong className="text-amber-600 dark:text-amber-400">{formatMoney(stats.customerDueTotal)}</strong> · সাপ্লায়ার দেনা:{' '}
            <strong className="text-red-600 dark:text-red-400">{formatMoney(stats.supplierDueTotal)}</strong>
          </span>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3">
          {modules.map((m) => {
            const Icon = m.icon;
            return (
              <button
                key={m.route}
                type="button"
                onClick={() => onNavigate(m.route)}
                className="group flex items-start gap-3 rounded-xl surface-card-interactive p-3.5 text-left"
              >
                <div className="rounded-lg bg-accent-soft p-2 text-accent shrink-0 transition-transform duration-150 group-hover:scale-105">
                  <Icon className="h-4 w-4" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-1">
                    <span className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
                      {m.title}
                    </span>
                    <ArrowRight className="h-3.5 w-3.5 text-slate-300 dark:text-slate-600 group-hover:text-accent group-hover:translate-x-0.5 transition-all shrink-0" />
                  </div>
                  <div className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400 font-mono-num">
                    {m.desc}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Bottom Panels: Recent Sales + Low Stock */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between mb-3 pb-2.5 border-b border-[var(--border-subtle)]">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              সাম্প্রতিক বিক্রয় আইটেম
            </h3>
            <button
              type="button"
              onClick={() => onNavigate('sales')}
              className="text-xs font-semibold text-accent hover:underline"
            >
              সব দেখুন
            </button>
          </div>
          {stats.recentSales.length === 0 ? (
            <p className="py-8 text-center text-xs text-slate-500 dark:text-slate-400">
              এখনো কোনো বিক্রয় রেকর্ড নেই
            </p>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {stats.recentSales.map((s) => (
                <div key={s.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <div className="min-w-0">
                    <div className="truncate font-medium text-slate-900 dark:text-slate-100">
                      {s.productName}
                    </div>
                    <div className="truncate text-xs text-slate-500 dark:text-slate-400">
                      {s.date} · {s.customer || 'নগদ ক্রেতা'} · {s.quantity} পিস
                    </div>
                  </div>
                  <div className="text-right font-mono-num font-semibold text-slate-900 dark:text-slate-100 shrink-0">
                    {formatMoney(s.quantity * s.salePrice)}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-xl surface-card p-4">
          <div className="flex items-center justify-between mb-3 pb-2.5 border-b border-[var(--border-subtle)]">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              কম স্টক সতর্কতা
            </h3>
            <button
              type="button"
              onClick={() => onNavigate('stock-alert')}
              className="text-xs font-semibold text-accent hover:underline"
            >
              অ্যালার্ট দেখুন
            </button>
          </div>
          {stats.lowStockProducts.length === 0 ? (
            <p className="py-8 text-center text-xs text-slate-500 dark:text-slate-400">
              সব পণ্যের স্টক স্বাভাবিক আছে
            </p>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {stats.lowStockProducts.slice(0, 6).map((p) => {
                const limit = InventoryMetaStorage.getMeta(p.code).lowStockLimit;
                return (
                  <div key={p.code} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                    <div className="min-w-0">
                      <div className="truncate font-medium text-slate-900 dark:text-slate-100">
                        {p.name}
                      </div>
                      <div className="text-xs text-slate-500 dark:text-slate-400 font-mono-num">
                        {p.code} · সীমা: {limit}
                      </div>
                    </div>
                    <div
                      className={`text-right font-mono-num text-sm font-semibold shrink-0 ${
                        p.stockQuantity <= 0
                          ? 'text-red-600 dark:text-red-400'
                          : 'text-amber-600 dark:text-amber-400'
                      }`}
                    >
                      {p.stockQuantity} পিস
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
