import React, { useCallback, useEffect, useState } from 'react';
import { NavRoute } from './types';
import { SecurityStorage, ThemeStorage, applyThemeToDom } from './storage/businessStorage';
import { cloudSync, SyncStatusSnapshot } from './storage/syncEngine';
import { Layout } from './components/Layout';
import { OfflineIndicator } from './components/OfflineIndicator';
import { ToastContainer, ToastItem } from './components/Toast';
import { DashboardPage } from './pages/DashboardPage';
import { DoubleEntryPage } from './pages/DoubleEntryPage';
import { ProductsPage } from './pages/ProductsPage';
import { PurchasesPage } from './pages/PurchasesPage';
import { SalesPage } from './pages/SalesPage';
import { NewSalePage } from './pages/NewSalePage';
import { StockPage } from './pages/StockPage';
import { CustomerDuePage } from './pages/CustomerDuePage';
import { SupplierDuePage } from './pages/SupplierDuePage';
import { FinancePage } from './pages/FinancePage';
import { ProfitPage } from './pages/ProfitPage';
import { FinancialDashboardPage } from './pages/FinancialDashboardPage';
import { FinancialCalendarPage } from './pages/FinancialCalendarPage';
import { PartnersPage } from './pages/PartnersPage';
import { ReportsPage } from './pages/ReportsPage';
import { GlobalSearchPage } from './pages/GlobalSearchPage';
import { SettingsPage } from './pages/SettingsPage';

export function App() {
  const [route, setRoute] = useState<NavRoute>('dashboard');
  const [revision, setRevision] = useState(0);
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [syncStatus, setSyncStatus] = useState<SyncStatusSnapshot>(() => cloudSync.getSnapshot());

  const bumpData = useCallback(() => {
    setRevision((r) => r + 1);
  }, []);

  useEffect(() => {
    SecurityStorage.ensureInitialized();
    applyThemeToDom(ThemeStorage.getTheme());
    cloudSync.init(bumpData);
    const unsub = cloudSync.subscribe(setSyncStatus);
    return () => {
      unsub();
    };
  }, [bumpData]);

  const notify = useCallback(
    (message: string, type: 'success' | 'error' | 'info' = 'info') => {
      const id = Date.now() + Math.floor(Math.random() * 1000);
      setToasts((prev) => [...prev, { id, message, type }]);
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, 3600);
    },
    []
  );

  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const handleQuickGoogleSignIn = useCallback(async () => {
    try {
      await cloudSync.signInWithGoogle();
      notify('গুগল অ্যাকাউন্টের সাথে রিয়েল-টাইম সিঙ্ক চালু হয়েছে', 'success');
    } catch {
      notify('গুগল সাইন-ইন সম্পন্ন করা যায়নি; অনুগ্রহ করে সেটিংস থেকে আবার চেষ্টা করুন', 'error');
    }
  }, [notify]);

  const renderPage = () => {
    switch (route) {
      case 'dashboard':
        return (
          <DashboardPage
            revision={revision}
            onNavigate={setRoute}
            onDataChange={bumpData}
            notify={notify}
            syncStatus={syncStatus}
            onSignInGoogle={handleQuickGoogleSignIn}
          />
        );
      case 'double-entry':
        return <DoubleEntryPage revision={revision} onDataChange={bumpData} notify={notify} />;
      case 'products':
        return <ProductsPage revision={revision} onDataChange={bumpData} notify={notify} />;
      case 'purchases':
        return <PurchasesPage revision={revision} onNavigate={setRoute} onDataChange={bumpData} notify={notify} />;
      case 'sales':
        return <SalesPage revision={revision} onNavigate={setRoute} onDataChange={bumpData} notify={notify} />;
      case 'new-sale':
        return <NewSalePage revision={revision} onNavigate={setRoute} onDataChange={bumpData} notify={notify} />;
      case 'stock':
        return <StockPage revision={revision} initialTab="overview" onDataChange={bumpData} notify={notify} />;
      case 'stock-alert':
        return <StockPage revision={revision} initialTab="alerts" onDataChange={bumpData} notify={notify} />;
      case 'customer-due':
        return <CustomerDuePage revision={revision} onDataChange={bumpData} notify={notify} />;
      case 'supplier-due':
        return <SupplierDuePage revision={revision} onDataChange={bumpData} notify={notify} />;
      case 'finance':
        return <FinancePage revision={revision} onNavigate={setRoute} onDataChange={bumpData} notify={notify} />;
      case 'profit':
        return <ProfitPage revision={revision} />;
      case 'financial-dashboard':
        return <FinancialDashboardPage revision={revision} />;
      case 'financial-calendar':
        return <FinancialCalendarPage revision={revision} />;
      case 'partners':
        return <PartnersPage revision={revision} onDataChange={bumpData} notify={notify} />;
      case 'reports':
        return <ReportsPage revision={revision} />;
      case 'search':
        return <GlobalSearchPage revision={revision} onNavigate={setRoute} />;
      case 'settings':
        return (
          <SettingsPage
            revision={revision}
            syncStatus={syncStatus}
            onDataChange={bumpData}
            notify={notify}
          />
        );
      default:
        return (
          <DashboardPage
            revision={revision}
            onNavigate={setRoute}
            onDataChange={bumpData}
            notify={notify}
            syncStatus={syncStatus}
            onSignInGoogle={handleQuickGoogleSignIn}
          />
        );
    }
  };

  return (
    <>
      <Layout
        route={route}
        onNavigate={setRoute}
        syncStatus={syncStatus}
        onSignInGoogle={handleQuickGoogleSignIn}
      >
        {renderPage()}
      </Layout>
      <OfflineIndicator />
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
    </>
  );
}

export default App;
