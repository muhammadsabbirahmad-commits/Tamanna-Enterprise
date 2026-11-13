import React, { useState } from 'react';
import {
  AlertTriangle,
  BarChart3,
  BookOpen,
  CalendarDays,
  Cloud,
  CloudOff,
  FileSpreadsheet,
  HandCoins,
  LayoutDashboard,
  Menu,
  Package,
  PlusCircle,
  RefreshCw,
  Search,
  Settings,
  ShoppingBag,
  ShoppingCart,
  TrendingUp,
  Truck,
  Users,
  Wallet,
  Warehouse,
  X,
} from 'lucide-react';
import { NavRoute } from '../types';
import { SecurityStorage, SettingsStorage } from '../storage/businessStorage';
import { SyncStatusSnapshot } from '../storage/syncEngine';
import { PWAInstallButton } from './PWAInstallButton';

interface NavGroup {
  label: string;
  items: Array<{
    route: NavRoute;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
  }>;
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'মূল মেনু',
    items: [
      { route: 'dashboard', label: 'ড্যাশবোর্ড', icon: LayoutDashboard },
      { route: 'new-sale', label: 'নতুন বিক্রয় (POS)', icon: PlusCircle },
      { route: 'double-entry', label: 'ডাবল এন্ট্রি হিসাব', icon: BookOpen },
      { route: 'search', label: 'গ্লোবাল সার্চ', icon: Search },
    ],
  },
  {
    label: 'পণ্য ও কেনাবেচা',
    items: [
      { route: 'products', label: 'পণ্য তালিকা', icon: Package },
      { route: 'sales', label: 'বিক্রয় তালিকা', icon: ShoppingCart },
      { route: 'purchases', label: 'ক্রয় ও মেমো', icon: ShoppingBag },
      { route: 'stock', label: 'স্টক ও মেয়াদ', icon: Warehouse },
      { route: 'stock-alert', label: 'স্টক অ্যালার্ট', icon: AlertTriangle },
    ],
  },
  {
    label: 'বাকি ও ফিন্যান্স',
    items: [
      { route: 'customer-due', label: 'ক্রেতার বাকি', icon: HandCoins },
      { route: 'supplier-due', label: 'সরবরাহকারীর দেনা', icon: Truck },
      { route: 'finance', label: 'খরচ ও ড্যামেজ', icon: Wallet },
      { route: 'partners', label: 'পার্টনার ও মূলধন', icon: Users },
    ],
  },
  {
    label: 'রিপোর্ট ও বিশ্লেষণ',
    items: [
      { route: 'profit', label: 'লাভ-ক্ষতি বিশ্লেষণ', icon: TrendingUp },
      { route: 'financial-dashboard', label: 'আর্থিক চার্ট ও ট্রেন্ড', icon: BarChart3 },
      { route: 'financial-calendar', label: 'আর্থিক ক্যালেন্ডার', icon: CalendarDays },
      { route: 'reports', label: 'রিপোর্ট ও এক্সপোর্ট', icon: FileSpreadsheet },
      { route: 'settings', label: 'সেটিংস ও ব্যাকআপ', icon: Settings },
    ],
  },
];

export const ROUTE_TITLES: Record<NavRoute, { title: string; subtitle: string }> = {
  dashboard: { title: 'ড্যাশবোর্ড', subtitle: 'দোকানের দৈনন্দিন সারসংক্ষেপ ও দ্রুত কার্যক্রম' },
  'double-entry': { title: 'ডাবল এন্ট্রি হিসাব', subtitle: 'ট্রায়াল ব্যালান্স, ডেবিট-ক্রেডিট লেজার ও প্রারম্ভিক ব্যালান্স' },
  products: { title: 'পণ্য তালিকা', subtitle: 'পণ্য কোড (P-228622+), ক্রয়-বিক্রয় মূল্য ও স্টক ব্যবস্থাপনা' },
  purchases: { title: 'ক্রয় ও মেমো', subtitle: 'সরবরাহকারী থেকে পণ্য ক্রয়, বাকি নিষ্পত্তি ও মেমো পার্সার' },
  sales: { title: 'বিক্রয় তালিকা', subtitle: 'ইনভয়েস তালিকা, এডিট, প্রিন্ট ও বিক্রয় রিটার্ন/এক্সচেঞ্জ' },
  'new-sale': { title: 'নতুন বিক্রয় (POS)', subtitle: 'কোড বা নাম দিয়ে পণ্য যোগ করে দ্রুত বিল তৈরি করুন' },
  stock: { title: 'স্টক ও মেয়াদ', subtitle: 'স্টক সমন্বয়, ব্যাচ, মেয়াদ এবং স্টক ইতিহাস' },
  'stock-alert': { title: 'স্টক অ্যালার্ট', subtitle: 'কম স্টক ও আসন্ন মেয়াদোত্তীর্ণ পণ্যের সতর্কতা' },
  'customer-due': { title: 'ক্রেতার বাকি', subtitle: 'ক্রেতাদের বকেয়া তালিকা ও আদায় রেকর্ড' },
  'supplier-due': { title: 'সরবরাহকারীর দেনা', subtitle: 'সাপ্লায়ার বকেয়া ও পরিশোধ ব্যবস্থাপনা' },
  finance: { title: 'খরচ ও ড্যামেজ', subtitle: 'দৈনন্দিন খরচ ও নষ্ট পণ্যের পার্টনার-বণ্টনকৃত হিসাব' },
  profit: { title: 'লাভ-ক্ষতি বিশ্লেষণ', subtitle: 'দৈনিক নিট লাভ ও অপারেটিং খরচ রিকভারি বিশ্লেষণ' },
  'financial-dashboard': { title: 'আর্থিক চার্ট ও ট্রেন্ড', subtitle: 'সময়কালভিত্তিক বিক্রয়, ক্রয় ও মুনাফার তুলনামূলক চিত্র' },
  'financial-calendar': { title: 'আর্থিক ক্যালেন্ডার', subtitle: 'প্রতিদিনের বিক্রয়, ক্রয়, খরচ ও মুনাফার দৈনিক খতিয়ান' },
  partners: { title: 'পার্টনার ও মূলধন', subtitle: 'অংশীদারদের মূলধন, লভ্যাংশ বণ্টন হার (১০০%) ও উত্তোলন' },
  reports: { title: 'রিপোর্ট ও এক্সপোর্ট', subtitle: 'প্রিন্ট/পিডিএফ এবং CSV ফরম্যাটে পূর্ণাঙ্গ ব্যবসায়িক রিপোর্ট' },
  search: { title: 'গ্লোবাল সার্চ', subtitle: 'পণ্য, বিক্রয়, ক্রয়, ক্রেতা ও সাপ্লায়ার জুড়ে অনুসন্ধান' },
  settings: { title: 'সেটিংস ও ব্যাকআপ', subtitle: 'দোকানের নাম, থিম, ভূমিকা, পিন নিরাপত্তা ও JSON ব্যাকআপ' },
};

interface LayoutProps {
  route: NavRoute;
  onNavigate: (route: NavRoute) => void;
  syncStatus?: SyncStatusSnapshot;
  onSignInGoogle?: () => void;
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({
  route,
  onNavigate,
  syncStatus,
  onSignInGoogle,
  children,
}) => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const shopName = SettingsStorage.getShopName();
  const currentUser = SecurityStorage.getCurrentUser();
  const canWrite = SecurityStorage.canWrite();
  const meta = ROUTE_TITLES[route];

  const sidebarContent = (
    <div className="flex h-full flex-col surface-sidebar border-r">
      <div className="flex items-center justify-between gap-2.5 px-4 py-4 border-b border-[var(--border-subtle)]">
        <button
          type="button"
          onClick={() => {
            onNavigate('dashboard');
            setMobileOpen(false);
          }}
          className="flex items-center gap-3 min-w-0 text-left group"
        >
          <div className="relative shrink-0 rounded-xl p-0.5 border border-[var(--border-subtle)] bg-white dark:bg-slate-800 shadow-xs">
            <img
              src="/logo.png"
              alt={shopName}
              className="h-8 w-8 rounded-lg object-cover"
            />
          </div>
          <div className="min-w-0">
            <div className="truncate text-sm font-bold tracking-tight text-slate-900 dark:text-slate-100 group-hover:text-accent transition-colors">
              {shopName}
            </div>
            <div className="truncate text-[11px] text-slate-500 dark:text-slate-400">
              {SecurityStorage.roleLabel(currentUser?.role || 'ADMIN')}
              {!canWrite ? ' · শুধুমাত্র দেখার অনুমতি' : ''}
            </div>
          </div>
        </button>
        <button
          type="button"
          onClick={() => setMobileOpen(false)}
          className="lg:hidden rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
          aria-label="মেনু বন্ধ"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-3.5 space-y-5">
        {NAV_GROUPS.map((group) => (
          <div key={group.label}>
            <div className="px-2.5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
              {group.label}
            </div>
            <div className="space-y-0.5">
              {group.items.map((item) => {
                const Icon = item.icon;
                const active = route === item.route;
                return (
                  <button
                    key={item.route}
                    type="button"
                    onClick={() => {
                      onNavigate(item.route);
                      setMobileOpen(false);
                    }}
                    className={`w-full flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] transition-all duration-150 ${
                      active
                        ? 'bg-accent-soft text-accent font-semibold border border-accent-soft'
                        : 'border border-transparent text-slate-600 dark:text-slate-300 font-medium hover:bg-slate-100/80 dark:hover:bg-slate-800/70 hover:text-slate-900 dark:hover:text-slate-100'
                    }`}
                  >
                    <Icon
                      className={`h-4 w-4 shrink-0 ${
                        active ? 'text-accent' : 'text-slate-400 dark:text-slate-500'
                      }`}
                    />
                    <span className="truncate">{item.label}</span>
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </nav>
    </div>
  );

  return (
    <div className="min-h-screen flex surface-canvas text-slate-900 dark:text-slate-100">
      {/* Desktop Sidebar */}
      <aside className="hidden lg:block w-64 shrink-0 no-print sticky top-0 h-screen">
        {sidebarContent}
      </aside>

      {/* Mobile Drawer */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 lg:hidden flex bg-slate-950/55 backdrop-blur-[2px] no-print"
          onClick={(e) => {
            if (e.target === e.currentTarget) setMobileOpen(false);
          }}
        >
          <div className="w-64 h-full shadow-2xl">{sidebarContent}</div>
        </div>
      )}

      {/* Main Content */}
      <div className="flex-1 min-w-0 flex flex-col">
        <header className="sticky top-0 z-20 flex items-center justify-between gap-4 border-b surface-header backdrop-blur-md px-4 sm:px-6 py-3 no-print">
          <div className="flex items-center gap-3 min-w-0">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              className="lg:hidden rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 p-2 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700"
              aria-label="মেনু খুলুন"
            >
              <Menu className="h-4 w-4" />
            </button>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="truncate text-base sm:text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">
                  {meta.title}
                </h1>
              </div>
              <p className="hidden sm:block truncate text-xs text-slate-500 dark:text-slate-400">
                {meta.subtitle}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <PWAInstallButton compact />

            {syncStatus && (
              syncStatus.user ? (
                <button
                  type="button"
                  onClick={() => onNavigate('settings')}
                  title={`${syncStatus.user.email} · ${syncStatus.syncedCount} রেকর্ড`}
                  className={`whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors ${
                    !syncStatus.online
                      ? 'border-amber-300/80 dark:border-amber-700/70 bg-amber-50/90 dark:bg-amber-950/40 text-amber-800 dark:text-amber-200'
                      : 'border-accent-soft bg-accent-soft text-accent'
                  }`}
                >
                  {syncStatus.syncing ? (
                    <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                  ) : syncStatus.online ? (
                    <Cloud className="h-3.5 w-3.5" />
                  ) : (
                    <CloudOff className="h-3.5 w-3.5" />
                  )}
                  <span className="hidden md:inline">
                    {syncStatus.syncing
                      ? 'সিঙ্ক হচ্ছে...'
                      : !syncStatus.online
                      ? 'অফলাইন'
                      : syncStatus.hasPendingWrites
                      ? 'অপেক্ষমান'
                      : 'সিঙ্কড'}
                  </span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => (onSignInGoogle ? onSignInGoogle() : onNavigate('settings'))}
                  className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                >
                  <Cloud className="h-3.5 w-3.5 text-accent" />
                  <span className="hidden sm:inline">Gmail সিঙ্ক</span>
                </button>
              )
            )}

            <button
              type="button"
              onClick={() => onNavigate('search')}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-3 py-1.5 text-xs sm:text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
            >
              <Search className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">সার্চ</span>
            </button>
            {route !== 'new-sale' && (
              <button
                type="button"
                onClick={() => onNavigate('new-sale')}
                className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-1.5 text-xs sm:text-sm font-semibold"
              >
                <PlusCircle className="h-3.5 w-3.5" />
                <span>নতুন বিক্রয়</span>
              </button>
            )}
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6 max-w-[1440px] w-full mx-auto">{children}</main>
      </div>
    </div>
  );
};
