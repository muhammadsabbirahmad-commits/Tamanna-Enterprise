import React, { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { NavRoute } from '../types';
import { ProductStorage } from '../storage/inventoryStorage';
import { SalesTransactionStorage } from '../storage/salesStorage';
import { PurchaseStorage, SupplierDueStorage } from '../storage/purchaseStorage';
import { CustomerDueStorage } from '../storage/dueStorage';
import { ExpenseStorage } from '../storage/financeStorage';
import { formatMoney } from '../utils/format';

interface GlobalSearchPageProps {
  revision: number;
  onNavigate: (route: NavRoute) => void;
}

type Category = 'ALL' | 'PRODUCTS' | 'SALES' | 'PURCHASES' | 'CUSTOMERS' | 'SUPPLIERS' | 'EXPENSES';

export const GlobalSearchPage: React.FC<GlobalSearchPageProps> = ({ revision, onNavigate }) => {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<Category>('ALL');

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    const items: Array<{
      id: string;
      cat: Exclude<Category, 'ALL'>;
      catLabel: string;
      title: string;
      subtitle: string;
      value: string;
      route: NavRoute;
    }> = [];

    if (category === 'ALL' || category === 'PRODUCTS') {
      ProductStorage.getProducts()
        .filter((p) => !q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q))
        .forEach((p) =>
          items.push({
            id: 'prod-' + p.code,
            cat: 'PRODUCTS',
            catLabel: 'পণ্য',
            title: p.name,
            subtitle: `${p.code} · স্টক: ${p.stockQuantity}`,
            value: formatMoney(p.salePrice),
            route: 'products',
          })
        );
    }

    if (category === 'ALL' || category === 'SALES') {
      SalesTransactionStorage.getTransactions()
        .filter(
          (t) =>
            !q ||
            t.transactionId.toLowerCase().includes(q) ||
            t.customer.toLowerCase().includes(q) ||
            t.mobile.toLowerCase().includes(q)
        )
        .forEach((t) =>
          items.push({
            id: 'sale-' + t.transactionId,
            cat: 'SALES',
            catLabel: 'বিক্রয়',
            title: `${t.customer} (${t.transactionId})`,
            subtitle: `${t.date} · মাধ্যম: ${t.paymentMethod}`,
            value: formatMoney(t.total),
            route: 'sales',
          })
        );
    }

    if (category === 'ALL' || category === 'PURCHASES') {
      PurchaseStorage.getPurchases()
        .filter(
          (p) =>
            !q ||
            p.productName.toLowerCase().includes(q) ||
            p.supplier.toLowerCase().includes(q) ||
            p.memoNumber.toLowerCase().includes(q)
        )
        .forEach((p) =>
          items.push({
            id: 'pur-' + p.id,
            cat: 'PURCHASES',
            catLabel: 'ক্রয়',
            title: `${p.productName} × ${p.quantity}`,
            subtitle: `${p.date} · ${p.supplier} · মেমো ${p.memoNumber || '—'}`,
            value: formatMoney(p.quantity * p.purchasePrice),
            route: 'purchases',
          })
        );
    }

    if (category === 'ALL' || category === 'CUSTOMERS') {
      CustomerDueStorage.getCustomers()
        .filter((c) => !q || c.name.toLowerCase().includes(q) || c.mobile.toLowerCase().includes(q))
        .forEach((c) =>
          items.push({
            id: 'cust-' + c.id,
            cat: 'CUSTOMERS',
            catLabel: 'ক্রেতা',
            title: c.name,
            subtitle: c.mobile || 'মোবাইল নেই',
            value: formatMoney(CustomerDueStorage.getBalance(c.name, c.mobile)),
            route: 'customer-due',
          })
        );
    }

    if (category === 'ALL' || category === 'SUPPLIERS') {
      SupplierDueStorage.getSuppliers()
        .filter((s) => !q || s.name.toLowerCase().includes(q) || s.mobile.toLowerCase().includes(q))
        .forEach((s) =>
          items.push({
            id: 'sup-' + s.id,
            cat: 'SUPPLIERS',
            catLabel: 'সরবরাহকারী',
            title: s.name,
            subtitle: s.mobile || s.address || '—',
            value: formatMoney(SupplierDueStorage.getBalance(s.name)),
            route: 'supplier-due',
          })
        );
    }

    if (category === 'ALL' || category === 'EXPENSES') {
      ExpenseStorage.getExpenses()
        .filter((e) => !q || e.category.toLowerCase().includes(q) || e.note.toLowerCase().includes(q))
        .forEach((e) =>
          items.push({
            id: 'exp-' + e.id,
            cat: 'EXPENSES',
            catLabel: 'খরচ',
            title: e.category,
            subtitle: `${e.date}${e.note ? ` · ${e.note}` : ''}`,
            value: formatMoney(e.amount),
            route: 'finance',
          })
        );
    }

    return items.slice(0, 100);
  }, [query, category, revision]);

  const cats: Array<{ id: Category; label: string }> = [
    { id: 'ALL', label: 'সব' },
    { id: 'PRODUCTS', label: 'পণ্য' },
    { id: 'SALES', label: 'বিক্রয়' },
    { id: 'PURCHASES', label: 'ক্রয়' },
    { id: 'CUSTOMERS', label: 'ক্রেতা' },
    { id: 'SUPPLIERS', label: 'সরবরাহকারী' },
    { id: 'EXPENSES', label: 'খরচ' },
  ];

  return (
    <div className="space-y-4">
      <div className="rounded-xl surface-card p-4 space-y-3">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="পণ্য, ইনভয়েস, ক্রেতা, সরবরাহকারী বা খরচ লিখুন..."
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 pl-9 pr-3 py-2.5 text-sm"
          />
        </div>
        <div className="flex flex-wrap gap-1.5">
          {cats.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setCategory(c.id)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium ${
                category === c.id
                  ? 'btn-accent'
                  : 'border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300'
              }`}
            >
              {c.label}
            </button>
          ))}
        </div>
      </div>

      <div className="rounded-xl surface-card overflow-hidden">
        {results.length === 0 ? (
          <div className="py-12 text-center text-sm text-slate-500">কোনো ফলাফল পাওয়া যায়নি</div>
        ) : (
          <div className="divide-y divide-slate-100 dark:divide-slate-800">
            {results.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => onNavigate(r.route)}
                className="w-full flex items-center justify-between gap-3 px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-slate-800/50"
              >
                <div className="min-w-0">
                  <div className="text-sm font-medium text-slate-900 dark:text-slate-100 truncate">
                    <span className="text-xs text-accent font-semibold mr-2">[{r.catLabel}]</span>
                    {r.title}
                  </div>
                  <div className="text-xs text-slate-500 truncate">{r.subtitle}</div>
                </div>
                <div className="font-mono-num text-sm font-semibold shrink-0">{r.value}</div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
