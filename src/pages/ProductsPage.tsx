import React, { useMemo, useState } from 'react';
import { Edit2, PackagePlus, Plus, Search, Trash2, Warehouse } from 'lucide-react';
import { Product, ProductSort } from '../types';
import { ProductStorage, InventoryMetaStorage } from '../storage/inventoryStorage';
import { SaleReturnStorage, SalesStorage } from '../storage/salesStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { formatMoney } from '../utils/format';
import { ConfirmDialog, Modal, PinPromptModal } from '../components/Modal';

interface ProductsPageProps {
  revision: number;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const ProductsPage: React.FC<ProductsPageProps> = ({ revision, onDataChange, notify }) => {
  const canWrite = SecurityStorage.canWrite();
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<ProductSort>('NEWEST');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);

  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [purchasePrice, setPurchasePrice] = useState('');
  const [salePrice, setSalePrice] = useState('');
  const [stockQuantity, setStockQuantity] = useState('');

  const [stockTarget, setStockTarget] = useState<Product | null>(null);
  const [stockDelta, setStockDelta] = useState('');
  const [stockType, setStockType] = useState<'IN' | 'OUT'>('IN');
  const [stockNote, setStockNote] = useState('');

  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null);
  const [pinDeleteTarget, setPinDeleteTarget] = useState<Product | null>(null);

  const soldByCode = useMemo(() => {
    const map: Record<string, number> = {};
    SalesStorage.getSales().forEach((s) => {
      const k = s.productCode.toUpperCase();
      map[k] = (map[k] ?? 0) + s.quantity;
    });
    SaleReturnStorage.getLines().forEach((l) => {
      const k = l.productCode.toUpperCase();
      map[k] = Math.max(0, (map[k] ?? 0) - l.quantity);
    });
    SaleReturnStorage.getExchanges().forEach((ex) => {
      const k = ex.productCode.toUpperCase();
      map[k] = (map[k] ?? 0) + ex.quantity;
    });
    return map;
  }, [revision]);

  const products = useMemo(() => {
    const list = ProductStorage.getProducts().filter((p) => {
      const q = query.trim().toLowerCase();
      if (!q) return true;
      return p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q);
    });

    return [...list].sort((a, b) => {
      if (sort === 'NEWEST') return b.createdAt - a.createdAt;
      if (sort === 'OLDEST') return a.createdAt - b.createdAt;
      if (sort === 'STOCK_OUT') return a.stockQuantity - b.stockQuantity;
      return (soldByCode[b.code.toUpperCase()] ?? 0) - (soldByCode[a.code.toUpperCase()] ?? 0);
    });
  }, [query, sort, soldByCode, revision]);

  const totalStockValue = useMemo(
    () => products.reduce((s, p) => s + Math.max(0, p.stockQuantity) * p.purchasePrice, 0),
    [products]
  );

  const openAdd = () => {
    setEditing(null);
    setCode(ProductStorage.nextProductCode());
    setName('');
    setPurchasePrice('');
    setSalePrice('');
    setStockQuantity('0');
    setShowForm(true);
  };

  const openEdit = (p: Product) => {
    setEditing(p);
    setCode(p.code);
    setName(p.name);
    setPurchasePrice(String(p.purchasePrice));
    setSalePrice(String(p.salePrice));
    setStockQuantity(String(p.stockQuantity));
    setShowForm(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return notify('শুধুমাত্র অ্যাডমিন পণ্য পরিবর্তন করতে পারবেন', 'error');
    const pp = parseFloat(purchasePrice);
    const sp = parseFloat(salePrice);
    const sq = parseInt(stockQuantity || '0', 10);
    if (!name.trim()) return notify('পণ্যের নাম লিখুন', 'error');
    if (isNaN(pp) || pp < 0 || isNaN(sp) || sp < 0 || isNaN(sq) || sq < 0) {
      return notify('সঠিক দাম এবং স্টক সংখ্যা দিন', 'error');
    }

    if (editing) {
      const ok = ProductStorage.updateProduct({
        ...editing,
        name: name.trim(),
        purchasePrice: pp,
        salePrice: sp,
        stockQuantity: sq,
      });
      if (!ok) return notify('পণ্য আপডেট ব্যর্থ হয়েছে', 'error');
      ActivityLogStorage.add('পণ্য আপডেট', `${editing.code} - ${name.trim()}`);
      notify('পণ্য আপডেট হয়েছে', 'success');
    } else {
      const ok = ProductStorage.addProduct({
        code: code.trim().toUpperCase(),
        name: name.trim(),
        purchasePrice: pp,
        salePrice: sp,
        stockQuantity: sq,
      });
      if (!ok) {
        return notify('পণ্য যোগ ব্যর্থ হয়েছে (কোড P-228622+ ও ইউনিক হতে হবে)', 'error');
      }
      if (sq > 0) {
        InventoryMetaStorage.addHistory(
          code.trim().toUpperCase(),
          name.trim(),
          'প্রাথমিক স্টক',
          sq,
          0,
          sq,
          'নতুন পণ্য যোগ'
        );
      }
      ActivityLogStorage.add('নতুন পণ্য', `${code.trim().toUpperCase()} - ${name.trim()}`);
      notify('নতুন পণ্য যোগ করা হয়েছে', 'success');
    }
    setShowForm(false);
    onDataChange();
  };

  const handleStockUpdate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!stockTarget || !canWrite) return;
    const qty = parseInt(stockDelta, 10);
    if (isNaN(qty) || qty <= 0) return notify('সঠিক পরিমাণ লিখুন', 'error');
    const before = stockTarget.stockQuantity;
    const after = stockType === 'IN' ? before + qty : before - qty;
    if (after < 0) return notify('স্টকের চেয়ে বেশি পরিমাণ কমানো যাবে না', 'error');

    if (!ProductStorage.updateStock(stockTarget.code, after)) {
      return notify('স্টক আপডেট ব্যর্থ হয়েছে', 'error');
    }
    InventoryMetaStorage.addHistory(
      stockTarget.code,
      stockTarget.name,
      stockType === 'IN' ? 'স্টক ইন' : 'স্টক আউট',
      qty,
      before,
      after,
      stockNote.trim()
    );
    ActivityLogStorage.add(
      'স্টক সমন্বয়',
      `${stockTarget.code} (${before} -> ${after}) ${stockNote.trim()}`
    );
    notify('স্টক আপডেট হয়েছে', 'success');
    setStockTarget(null);
    onDataChange();
  };

  const executeDelete = (p: Product) => {
    if (ProductStorage.deleteProduct(p.code)) {
      ActivityLogStorage.add('পণ্য ডিলিট', `${p.code} - ${p.name}`);
      notify(`${p.name} মুছে ফেলা হয়েছে`, 'success');
      onDataChange();
    } else {
      notify('পণ্য মুছে ফেলা যায়নি', 'error');
    }
  };

  const requestDelete = (p: Product) => {
    if (!canWrite) return notify('শুধুমাত্র অ্যাডমিন ডিলিট করতে পারবেন', 'error');
    if (SecurityStorage.isAdminPinSet() && !SecurityStorage.isPinFreeEntryEnabled()) {
      setPinDeleteTarget(p);
    } else {
      setDeleteTarget(p);
    }
  };

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 rounded-xl surface-card p-3.5">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="পণ্যের নাম বা কোড (P-228622) খুঁজুন..."
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 pl-9 pr-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value as ProductSort)}
            className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm text-slate-700 dark:text-slate-200"
          >
            <option value="NEWEST">নতুন আগে</option>
            <option value="OLDEST">পুরোনো আগে</option>
            <option value="STOCK_OUT">কম স্টক আগে</option>
            <option value="MOST_SOLD">বেশি বিক্রিত আগে</option>
          </select>

          {canWrite && (
            <button
              type="button"
              onClick={openAdd}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-sm font-medium"
            >
              <Plus className="h-4 w-4" />
              <span>নতুন পণ্য</span>
            </button>
          )}
        </div>
      </div>

      {/* Summary line */}
      <div className="flex flex-wrap items-center justify-between gap-2 px-1 text-xs text-slate-500 dark:text-slate-400">
        <span>
          মোট পণ্য: <strong className="font-mono-num text-slate-800 dark:text-slate-200">{products.length}</strong>টি
        </span>
        <span>
          বর্তমান স্টকের ক্রয়মূল্য:{' '}
          <strong className="font-mono-num text-slate-800 dark:text-slate-200">
            {formatMoney(totalStockValue)}
          </strong>
        </span>
      </div>

      {/* Product Table */}
      <div className="rounded-xl surface-card overflow-hidden">
        {products.length === 0 ? (
          <div className="py-12 px-4 text-center">
            <PackagePlus className="h-8 w-8 mx-auto text-slate-300 dark:text-slate-600" />
            <p className="mt-2 text-sm font-medium text-slate-700 dark:text-slate-200">
              কোনো পণ্য পাওয়া যায়নি
            </p>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              নতুন পণ্য যোগ করে স্টক ও বিক্রয় শুরু করুন।
            </p>
            {canWrite && (
              <button
                type="button"
                onClick={openAdd}
                className="mt-4 whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-4 py-2 text-sm font-medium"
              >
                <Plus className="h-4 w-4" />
                <span>পণ্য যোগ করুন</span>
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">কোড</th>
                  <th className="px-4 py-3">পণ্যের নাম</th>
                  <th className="px-4 py-3 text-right">ক্রয়মূল্য</th>
                  <th className="px-4 py-3 text-right">বিক্রয়মূল্য</th>
                  <th className="px-4 py-3 text-right">মজুদ স্টক</th>
                  <th className="px-4 py-3 text-right">বিক্রিত</th>
                  {canWrite && <th className="px-4 py-3 text-right">অ্যাকশন</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {products.map((p) => {
                  const limit = InventoryMetaStorage.getMeta(p.code).lowStockLimit;
                  const low = p.stockQuantity <= limit;
                  return (
                    <tr
                      key={p.code}
                      className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40 transition-colors"
                    >
                      <td className="px-4 py-3 font-mono-num text-xs text-slate-500 dark:text-slate-400">
                        {p.code}
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                        {p.name}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num text-slate-700 dark:text-slate-300">
                        {formatMoney(p.purchasePrice)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num font-semibold text-slate-900 dark:text-slate-100">
                        {formatMoney(p.salePrice)}
                      </td>
                      <td
                        className={`px-4 py-3 text-right font-mono-num font-semibold ${
                          p.stockQuantity <= 0
                            ? 'text-red-600 dark:text-red-400'
                            : low
                            ? 'text-amber-600 dark:text-amber-400'
                            : 'text-emerald-700 dark:text-emerald-400'
                        }`}
                      >
                        {p.stockQuantity} পিস
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num text-slate-500 dark:text-slate-400">
                        {soldByCode[p.code.toUpperCase()] ?? 0}
                      </td>
                      {canWrite && (
                        <td className="px-4 py-3 text-right">
                          <div className="inline-flex items-center justify-end gap-1">
                            <button
                              type="button"
                              onClick={() => {
                                setStockTarget(p);
                                setStockType('IN');
                                setStockDelta('');
                                setStockNote('');
                              }}
                              title="স্টক ইন/আউট"
                              className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-slate-100"
                            >
                              <Warehouse className="h-4 w-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => openEdit(p)}
                              title="এডিট"
                              className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-slate-100"
                            >
                              <Edit2 className="h-4 w-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => requestDelete(p)}
                              title="ডিলিট"
                              className="rounded-lg p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50 dark:hover:text-red-400"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
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

      {/* Add / Edit Modal */}
      <Modal
        open={showForm}
        onClose={() => setShowForm(false)}
        title={editing ? 'পণ্য তথ্য সম্পাদনা' : 'নতুন পণ্য যোগ করুন'}
        subtitle={editing ? `কোড: ${editing.code}` : 'কোড P-228622 বা তার পরবর্তী ফরম্যাটে হবে'}
      >
        <form onSubmit={handleSave} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                পণ্য কোড
              </label>
              <input
                type="text"
                value={code}
                disabled={!!editing}
                onChange={(e) => setCode(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-2 text-sm font-mono-num text-slate-900 dark:text-slate-100 disabled:opacity-60"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                মজুদ স্টক (পিস)
              </label>
              <input
                type="number"
                min={0}
                value={stockQuantity}
                onChange={(e) => setStockQuantity(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num text-slate-900 dark:text-slate-100"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              পণ্যের নাম *
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="যেমন: প্রিমিয়াম বাসমতি চাল (৫ কেজি)"
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                ক্রয়মূল্য (৳) *
              </label>
              <input
                type="number"
                min={0}
                step="0.01"
                required
                value={purchasePrice}
                onChange={(e) => setPurchasePrice(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num text-slate-900 dark:text-slate-100"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                বিক্রয়মূল্য (৳) *
              </label>
              <input
                type="number"
                min={0}
                step="0.01"
                required
                value={salePrice}
                onChange={(e) => setSalePrice(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num text-slate-900 dark:text-slate-100"
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="whitespace-nowrap shrink-0 rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              বাতিল
            </button>
            <button
              type="submit"
              className="whitespace-nowrap shrink-0 rounded-lg btn-accent px-4 py-2 text-sm font-medium"
            >
              {editing ? 'আপডেট করুন' : 'সংরক্ষণ করুন'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Stock Adjustment Modal */}
      <Modal
        open={!!stockTarget}
        onClose={() => setStockTarget(null)}
        title="স্টক ইন / আউট"
        subtitle={
          stockTarget
            ? `${stockTarget.name} (${stockTarget.code}) · বর্তমান স্টক: ${stockTarget.stockQuantity} পিস`
            : ''
        }
        maxWidthClass="max-w-md"
      >
        <form onSubmit={handleStockUpdate} className="space-y-4">
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setStockType('IN')}
              className={`rounded-lg py-2 text-sm font-medium border ${
                stockType === 'IN'
                  ? 'btn-accent border-transparent'
                  : 'border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300'
              }`}
            >
              স্টক ইন (+)
            </button>
            <button
              type="button"
              onClick={() => setStockType('OUT')}
              className={`rounded-lg py-2 text-sm font-medium border ${
                stockType === 'OUT'
                  ? 'bg-amber-600 text-white border-transparent'
                  : 'border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300'
              }`}
            >
              স্টক আউট (-)
            </button>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              পরিমাণ (পিস)
            </label>
            <input
              type="number"
              min={1}
              required
              value={stockDelta}
              onChange={(e) => setStockDelta(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              মন্তব্য / কারণ
            </label>
            <input
              type="text"
              value={stockNote}
              onChange={(e) => setStockNote(e.target.value)}
              placeholder="যেমন: গণনা সমন্বয়"
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setStockTarget(null)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm font-medium"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              আপডেট করুন
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && executeDelete(deleteTarget)}
        title="পণ্য মুছে ফেলবেন?"
        message={
          deleteTarget
            ? `${deleteTarget.name} (${deleteTarget.code}) তালিকা থেকে মুছে ফেলা হবে।`
            : ''
        }
        confirmLabel="ডিলিট করুন"
        danger
      />

      <PinPromptModal
        open={!!pinDeleteTarget}
        onClose={() => setPinDeleteTarget(null)}
        onVerified={() => pinDeleteTarget && executeDelete(pinDeleteTarget)}
        verifyPin={(pin) => SecurityStorage.verifyAdminPin(pin)}
      />
    </div>
  );
};
