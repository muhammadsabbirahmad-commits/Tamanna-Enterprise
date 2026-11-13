import React, { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Edit3, History, Warehouse } from 'lucide-react';
import { Product } from '../types';
import { InventoryMetaStorage, ProductStorage } from '../storage/inventoryStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { addDaysIso, formatMoney, todayIso } from '../utils/format';
import { Modal } from '../components/Modal';

interface StockPageProps {
  revision: number;
  initialTab?: 'overview' | 'alerts' | 'history';
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const StockPage: React.FC<StockPageProps> = ({
  revision,
  initialTab = 'overview',
  onDataChange,
  notify,
}) => {
  const canWrite = SecurityStorage.canWrite();
  const [tab, setTab] = useState<'overview' | 'alerts' | 'history'>(initialTab);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  const [metaTarget, setMetaTarget] = useState<Product | null>(null);
  const [batch, setBatch] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [lowStockLimit, setLowStockLimit] = useState('5');
  const [damagedQty, setDamagedQty] = useState('0');

  const products = useMemo(() => ProductStorage.getProducts(), [revision]);
  const history = useMemo(() => InventoryMetaStorage.getHistory(), [revision]);
  const today = todayIso();
  const soonDate = addDaysIso(today, 30);

  const alerts = useMemo(() => {
    return products
      .map((p) => {
        const meta = InventoryMetaStorage.getMeta(p.code);
        const isOut = p.stockQuantity <= 0;
        const isLow = p.stockQuantity > 0 && p.stockQuantity <= meta.lowStockLimit;
        const isExpired = !!meta.expiryDate && meta.expiryDate < today;
        const isExpiringSoon = !!meta.expiryDate && meta.expiryDate >= today && meta.expiryDate <= soonDate;
        return { product: p, meta, isOut, isLow, isExpired, isExpiringSoon };
      })
      .filter((x) => x.isOut || x.isLow || x.isExpired || x.isExpiringSoon);
  }, [products, today, soonDate]);

  const openMetaEditor = (p: Product) => {
    const m = InventoryMetaStorage.getMeta(p.code);
    setBatch(m.batch);
    setExpiryDate(m.expiryDate);
    setLowStockLimit(String(m.lowStockLimit));
    setDamagedQty(String(m.damagedQuantity));
    setMetaTarget(p);
  };

  const handleSaveMeta = (e: React.FormEvent) => {
    e.preventDefault();
    if (!metaTarget || !canWrite) return;
    InventoryMetaStorage.saveMeta({
      code: metaTarget.code,
      batch: batch.trim(),
      expiryDate: expiryDate.trim(),
      lowStockLimit: Math.max(0, parseInt(lowStockLimit || '5', 10) || 0),
      damagedQuantity: Math.max(0, parseInt(damagedQty || '0', 10) || 0),
    });
    ActivityLogStorage.add('স্টক মেটা আপডেট', `${metaTarget.code} · সীমা ${lowStockLimit}`);
    notify('স্টক মেটাডাটা সংরক্ষিত হয়েছে', 'success');
    setMetaTarget(null);
    onDataChange();
  };

  return (
    <div className="space-y-4">
      {/* Tabs */}
      <div className="flex flex-wrap items-center gap-2 rounded-xl surface-card p-2">
        <button
          type="button"
          onClick={() => setTab('overview')}
          className={`inline-flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
            tab === 'overview'
              ? 'btn-accent'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          <Warehouse className="h-4 w-4" />
          <span>স্টক ও ব্যাচ ({products.length})</span>
        </button>
        <button
          type="button"
          onClick={() => setTab('alerts')}
          className={`inline-flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
            tab === 'alerts'
              ? 'btn-accent'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          <AlertTriangle className="h-4 w-4" />
          <span>কম স্টক ও মেয়াদ অ্যালার্ট ({alerts.length})</span>
        </button>
        <button
          type="button"
          onClick={() => setTab('history')}
          className={`inline-flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
            tab === 'history'
              ? 'btn-accent'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          <History className="h-4 w-4" />
          <span>স্টক ইতিহাস ({history.length})</span>
        </button>
      </div>

      {tab === 'overview' && (
        <div className="rounded-xl surface-card overflow-hidden">
          {products.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-500">কোনো পণ্য নেই</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                  <tr>
                    <th className="px-4 py-3">পণ্য</th>
                    <th className="px-4 py-3">ব্যাচ ও মেয়াদ</th>
                    <th className="px-4 py-3 text-right">কম স্টক সীমা</th>
                    <th className="px-4 py-3 text-right">বর্তমান স্টক</th>
                    <th className="px-4 py-3 text-right">মজুদ মূল্য</th>
                    {canWrite && <th className="px-4 py-3 text-right">মেটা</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {products.map((p) => {
                    const meta = InventoryMetaStorage.getMeta(p.code);
                    return (
                      <tr key={p.code} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                        <td className="px-4 py-3">
                          <div className="font-medium text-slate-900 dark:text-slate-100">{p.name}</div>
                          <div className="text-xs text-slate-500 font-mono-num">{p.code}</div>
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-600 dark:text-slate-300 font-mono-num">
                          ব্যাচ: {meta.batch || '—'} · মেয়াদ: {meta.expiryDate || '—'}
                        </td>
                        <td className="px-4 py-3 text-right font-mono-num text-xs">
                          {meta.lowStockLimit}
                        </td>
                        <td
                          className={`px-4 py-3 text-right font-mono-num font-semibold ${
                            p.stockQuantity <= 0
                              ? 'text-red-600 dark:text-red-400'
                              : p.stockQuantity <= meta.lowStockLimit
                              ? 'text-amber-600 dark:text-amber-400'
                              : 'text-emerald-700 dark:text-emerald-400'
                          }`}
                        >
                          {p.stockQuantity} পিস
                        </td>
                        <td className="px-4 py-3 text-right font-mono-num">
                          {formatMoney(p.stockQuantity * p.purchasePrice)}
                        </td>
                        {canWrite && (
                          <td className="px-4 py-3 text-right">
                            <button
                              type="button"
                              onClick={() => openMetaEditor(p)}
                              className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
                              title="ব্যাচ/মেয়াদ এডিট"
                            >
                              <Edit3 className="h-4 w-4" />
                            </button>
                          </td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === 'alerts' && (
        <div className="rounded-xl surface-card overflow-hidden">
          {alerts.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400">
              কোনো কম স্টক বা মেয়াদ সতর্কতা নেই
            </div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {alerts.map(({ product, meta, isOut, isLow, isExpired, isExpiringSoon }) => (
                <div key={product.code} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5">
                  <div>
                    <div className="font-medium text-slate-900 dark:text-slate-100">
                      {product.name}{' '}
                      <span className="text-xs text-slate-500 font-mono-num">({product.code})</span>
                    </div>
                    <div className="mt-1 text-xs text-slate-600 dark:text-slate-400">
                      {[
                        isOut ? 'স্টক শূন্য' : isLow ? `কম স্টক (সীমা: ${meta.lowStockLimit})` : '',
                        isExpired
                          ? `মেয়াদোত্তীর্ণ (${meta.expiryDate})`
                          : isExpiringSoon
                          ? `শীঘ্রই মেয়াদ শেষ (${meta.expiryDate})`
                          : '',
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </div>
                  </div>
                  <div className="text-right font-mono-num font-bold text-sm text-red-600 dark:text-red-400">
                    {product.stockQuantity} পিস
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === 'history' && (
        <div className="rounded-xl surface-card overflow-hidden">
          {history.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-500">স্টক পরিবর্তনের ইতিহাস নেই</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                  <tr>
                    <th className="px-4 py-3">তারিখ</th>
                    <th className="px-4 py-3">পণ্য</th>
                    <th className="px-4 py-3">ধরন</th>
                    <th className="px-4 py-3 text-right">পরিমাণ</th>
                    <th className="px-4 py-3 text-right">আগে → পরে</th>
                    <th className="px-4 py-3">নোট</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {history.map((h) => (
                    <tr key={h.id} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                      <td className="px-4 py-2.5 text-xs font-mono-num text-slate-500">{h.date}</td>
                      <td className="px-4 py-2.5 font-medium">
                        {h.productName}{' '}
                        <span className="text-xs text-slate-400 font-mono-num">({h.code})</span>
                      </td>
                      <td className="px-4 py-2.5 text-xs">{h.type}</td>
                      <td className="px-4 py-2.5 text-right font-mono-num">{h.quantity}</td>
                      <td className="px-4 py-2.5 text-right font-mono-num text-xs">
                        {h.before} → {h.after}
                      </td>
                      <td className="px-4 py-2.5 text-xs text-slate-500">{h.note || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Meta Editor Modal */}
      <Modal
        open={!!metaTarget}
        onClose={() => setMetaTarget(null)}
        title="ব্যাচ, মেয়াদ ও স্টক সীমা"
        subtitle={metaTarget ? `${metaTarget.name} (${metaTarget.code})` : ''}
        maxWidthClass="max-w-md"
      >
        <form onSubmit={handleSaveMeta} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                ব্যাচ নম্বর
              </label>
              <input
                type="text"
                value={batch}
                onChange={(e) => setBatch(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                মেয়াদ শেষের তারিখ
              </label>
              <input
                type="date"
                value={expiryDate}
                onChange={(e) => setExpiryDate(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                কম স্টক অ্যালার্ট সীমা
              </label>
              <input
                type="number"
                min={0}
                value={lowStockLimit}
                onChange={(e) => setLowStockLimit(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                ড্যামেজ রেকর্ড সংখ্যা
              </label>
              <input
                type="number"
                min={0}
                value={damagedQty}
                onChange={(e) => setDamagedQty(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setMetaTarget(null)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm font-medium"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              সংরক্ষণ
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
