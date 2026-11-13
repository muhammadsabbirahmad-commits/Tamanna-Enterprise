import React, { useMemo, useState } from 'react';
import { CheckCircle2, Minus, Plus, Search, ShoppingCart, Trash2 } from 'lucide-react';
import { NavRoute, Product } from '../types';
import { ProductStorage, InventoryMetaStorage } from '../storage/inventoryStorage';
import { SalesStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { CustomerDueStorage } from '../storage/dueStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { formatMoney, generateTxId, nowDateTime } from '../utils/format';

interface CartItem {
  product: Product;
  quantity: number;
  salePrice: number;
}

interface NewSalePageProps {
  revision: number;
  onNavigate: (route: NavRoute) => void;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const NewSalePage: React.FC<NewSalePageProps> = ({
  revision,
  onNavigate,
  onDataChange,
  notify,
}) => {
  const canWrite = SecurityStorage.canWrite();
  const [search, setSearch] = useState('');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [customerName, setCustomerName] = useState('');
  const [customerMobile, setCustomerMobile] = useState('');
  const [discountInput, setDiscountInput] = useState('0');
  const [discountType, setDiscountType] = useState<'AMOUNT' | 'PERCENT'>('AMOUNT');
  const [paidInput, setPaidInput] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<'Cash' | 'bKash' | 'Bank' | 'Due'>('Cash');
  const [lastCompletedTx, setLastCompletedTx] = useState<string | null>(null);

  const products = useMemo(() => {
    const q = search.trim().toLowerCase();
    return ProductStorage.getProducts().filter(
      (p) => !q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q)
    );
  }, [search, revision]);

  const customers = useMemo(() => CustomerDueStorage.getCustomers(), [revision]);

  const subtotal = cart.reduce((s, item) => s + item.quantity * item.salePrice, 0);
  const rawDiscountVal = Math.max(0, parseFloat(discountInput) || 0);
  const discount =
    discountType === 'PERCENT'
      ? Math.min(subtotal, Math.round((subtotal * Math.min(100, rawDiscountVal)) / 100 * 100) / 100)
      : Math.min(subtotal, rawDiscountVal);
  const netTotal = Math.max(0, Math.round((subtotal - discount) * 100) / 100);
  const effectivePaid =
    paymentMethod === 'Due'
      ? Math.min(netTotal, Math.max(0, parseFloat(paidInput || '0') || 0))
      : paidInput === ''
      ? netTotal
      : Math.min(netTotal, Math.max(0, parseFloat(paidInput) || 0));
  const dueAmount = Math.max(0, Math.round((netTotal - effectivePaid) * 100) / 100);
  const estimatedCost = cart.reduce((s, item) => s + item.quantity * item.product.purchasePrice, 0);
  const estimatedProfit = netTotal - estimatedCost;

  const addToCart = (product: Product) => {
    if (product.stockQuantity <= 0) {
      return notify(`${product.name}-এর স্টক শেষ`, 'error');
    }
    setCart((prev) => {
      const existing = prev.find((c) => c.product.code === product.code);
      if (existing) {
        if (existing.quantity + 1 > product.stockQuantity) {
          notify(`সর্বোচ্চ ${product.stockQuantity} পিস স্টকে আছে`, 'error');
          return prev;
        }
        return prev.map((c) =>
          c.product.code === product.code ? { ...c, quantity: c.quantity + 1 } : c
        );
      }
      return [...prev, { product, quantity: 1, salePrice: product.salePrice }];
    });
  };

  const handleBarcodeKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const q = search.trim().toLowerCase();
    if (!q) return;
    const exact = ProductStorage.getProducts().find(
      (p) => p.code.toLowerCase() === q || p.name.toLowerCase() === q
    );
    if (exact) {
      addToCart(exact);
      setSearch('');
    } else if (products.length === 1) {
      addToCart(products[0]);
      setSearch('');
    }
  };

  const updateQty = (code: string, nextQty: number) => {
    setCart((prev) =>
      prev
        .map((item) => {
          if (item.product.code !== code) return item;
          if (nextQty <= 0) return null;
          if (nextQty > item.product.stockQuantity) {
            notify(`স্টকে মাত্র ${item.product.stockQuantity} পিস আছে`, 'error');
            return item;
          }
          return { ...item, quantity: nextQty };
        })
        .filter((x): x is CartItem => x !== null)
    );
  };

  const updatePrice = (code: string, nextPrice: number) => {
    if (!Number.isFinite(nextPrice) || nextPrice < 0) return;
    setCart((prev) =>
      prev.map((item) => (item.product.code === code ? { ...item, salePrice: nextPrice } : item))
    );
  };

  const handleCheckout = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return notify('শুধুমাত্র অ্যাডমিন বিক্রয় এন্ট্রি করতে পারবেন', 'error');
    if (cart.length === 0) return notify('কার্টে কমপক্ষে একটি পণ্য যোগ করুন', 'error');
    if (discountType === 'PERCENT' && rawDiscountVal > 100) {
      return notify('শতকরা ছাড় ১০০% এর বেশি হতে পারবে না', 'error');
    }
    if (discountType === 'AMOUNT' && rawDiscountVal > subtotal) {
      return notify('ডিসকাউন্ট সাবটোটালের বেশি হতে পারবে না', 'error');
    }
    if (dueAmount > 0.004 && !customerName.trim()) {
      return notify('বাকি বিক্রয়ের জন্য ক্রেতার নাম লিখুন', 'error');
    }

    // Re-validate latest stock
    const latestProducts = ProductStorage.getProducts();
    for (const item of cart) {
      const fresh = latestProducts.find((p) => p.code === item.product.code);
      if (!fresh || fresh.stockQuantity < item.quantity) {
        return notify(`${item.product.name}-এর পর্যাপ্ত স্টক নেই`, 'error');
      }
    }

    const txId = generateTxId('TX');
    const dateStr = nowDateTime();
    const cleanCustomer = customerName.trim() || 'নগদ ক্রেতা';
    const cleanMobile = customerMobile.trim();
    const customerLabel = cleanMobile ? `${cleanCustomer} • ${cleanMobile}` : cleanCustomer;

    const txSaved = SalesTransactionStorage.addTransaction({
      transactionId: txId,
      date: dateStr,
      customer: cleanCustomer,
      mobile: cleanMobile,
      subtotal,
      discount,
      total: netTotal,
      paid: effectivePaid,
      due: dueAmount,
      paymentMethod,
    });
    if (!txSaved) return notify('ইনভয়েস সংরক্ষণ ব্যর্থ হয়েছে', 'error');

    const rolledBackStocks: Array<{ code: string; before: number }> = [];
    try {
      cart.forEach((item, idx) => {
        const fresh = ProductStorage.getProducts().find((p) => p.code === item.product.code)!;
        const before = fresh.stockQuantity;
        const after = before - item.quantity;
        if (!ProductStorage.updateStock(fresh.code, after)) {
          throw new Error('Stock update failed');
        }
        rolledBackStocks.push({ code: fresh.code, before });
        InventoryMetaStorage.addHistory(
          fresh.code,
          fresh.name,
          'বিক্রয়',
          item.quantity,
          before,
          after,
          `ইনভয়েস ${txId}`
        );
        const lineSaved = SalesStorage.addSale({
          id: Date.now() + idx,
          transactionId: txId,
          date: dateStr,
          productCode: fresh.code,
          productName: fresh.name,
          quantity: item.quantity,
          salePrice: item.salePrice,
          purchasePrice: fresh.purchasePrice,
          customer: customerLabel,
        });
        if (!lineSaved) throw new Error('Sale line failed');
      });

      if (dueAmount > 0.004) {
        const summaryNote =
          'বিক্রয়: ' + cart.map((c) => `${c.product.name} x${c.quantity}`).join(', ');
        const dueId = CustomerDueStorage.addSaleDue(
          cleanCustomer,
          cleanMobile,
          dueAmount,
          summaryNote,
          txId
        );
        if (dueId <= 0) throw new Error('Customer due save failed');
      } else if (customerName.trim()) {
        CustomerDueStorage.ensureCustomer(cleanCustomer, cleanMobile);
      }

      ActivityLogStorage.add(
        'নতুন বিক্রয়',
        `${txId} · ${cleanCustomer} · মোট ${formatMoney(netTotal)} · বাকি ${formatMoney(dueAmount)}`
      );
      setCart([]);
      setCustomerName('');
      setCustomerMobile('');
      setDiscountInput('0');
      setPaidInput('');
      setLastCompletedTx(txId);
      onDataChange();
      notify(`বিক্রয় সম্পন্ন হয়েছে (${txId})`, 'success');
    } catch {
      SalesStorage.removeByTransaction(txId);
      SalesTransactionStorage.removeTransaction(txId);
      rolledBackStocks.forEach((r) => ProductStorage.updateStock(r.code, r.before));
      notify('বিক্রয় সংরক্ষণে ত্রুটি হয়েছে, পূর্বের স্টক পুনরুদ্ধার করা হয়েছে', 'error');
    }
  };

  return (
    <div className="space-y-4">
      {lastCompletedTx && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-emerald-200 dark:border-emerald-900/60 bg-emerald-50/70 dark:bg-emerald-950/30 px-4 py-3">
          <div className="flex items-center gap-2 text-sm text-emerald-900 dark:text-emerald-200">
            <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
            <span>
              সর্বশেষ ইনভয়েস <strong className="font-mono-num">{lastCompletedTx}</strong> সফলভাবে তৈরি হয়েছে।
            </span>
          </div>
          <button
            type="button"
            onClick={() => onNavigate('sales')}
            className="whitespace-nowrap shrink-0 text-xs font-semibold text-emerald-800 dark:text-emerald-300 underline"
          >
            ইনভয়েস তালিকা দেখুন
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Left: Product Picker */}
        <div className="lg:col-span-7 space-y-3">
          <div className="rounded-xl surface-card p-3.5">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={handleBarcodeKeyDown}
                placeholder="পণ্য কোড (যেমন P-228622) স্ক্যান/লিখুন বা নাম দিয়ে খুঁজুন (Enter চাপলে যোগ হবে)"
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 pl-9 pr-3 py-2.5 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
              />
            </div>
          </div>

          <div className="rounded-xl surface-card overflow-hidden">
            {products.length === 0 ? (
              <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400">
                কোনো পণ্য পাওয়া যায়নি
              </div>
            ) : (
              <div className="divide-y divide-slate-100 dark:divide-slate-800 max-h-[540px] overflow-y-auto">
                {products.map((p) => (
                  <div
                    key={p.code}
                    className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-800/50"
                  >
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                        {p.name}
                      </div>
                      <div className="text-xs text-slate-500 dark:text-slate-400 font-mono-num">
                        {p.code} · স্টক:{' '}
                        <span
                          className={
                            p.stockQuantity <= 0
                              ? 'text-red-600 dark:text-red-400 font-semibold'
                              : 'font-semibold'
                          }
                        >
                          {p.stockQuantity}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <span className="text-sm font-bold font-mono-num text-slate-900 dark:text-slate-100">
                        {formatMoney(p.salePrice)}
                      </span>
                      <button
                        type="button"
                        disabled={!canWrite || p.stockQuantity <= 0}
                        onClick={() => addToCart(p)}
                        className="whitespace-nowrap shrink-0 inline-flex items-center gap-1 rounded-lg btn-accent px-3 py-1.5 text-xs font-medium disabled:opacity-40"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        <span>যোগ</span>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right: Cart & Checkout */}
        <div className="lg:col-span-5">
          <form
            onSubmit={handleCheckout}
            className="rounded-xl surface-card p-4 space-y-4 sticky top-20"
          >
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <ShoppingCart className="h-4 w-4 text-accent" />
                <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                  বিক্রয় কার্ট ({cart.length})
                </h2>
              </div>
              {cart.length > 0 && (
                <button
                  type="button"
                  onClick={() => setCart([])}
                  className="text-xs text-red-600 dark:text-red-400 hover:underline"
                >
                  কার্ট খালি করুন
                </button>
              )}
            </div>

            {cart.length === 0 ? (
              <p className="py-8 text-center text-xs text-slate-500 dark:text-slate-400">
                বাম পাশের তালিকা থেকে পণ্য যোগ করুন
              </p>
            ) : (
              <div className="space-y-2.5 max-h-60 overflow-y-auto pr-1">
                {cart.map((item) => (
                  <div
                    key={item.product.code}
                    className="rounded-lg border border-slate-200 dark:border-slate-800 p-2.5 space-y-2"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="truncate text-xs font-semibold text-slate-900 dark:text-slate-100">
                          {item.product.name}
                        </div>
                        <div className="text-[11px] text-slate-500 dark:text-slate-400 font-mono-num">
                          {item.product.code} · ক্রয়: {formatMoney(item.product.purchasePrice)}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => updateQty(item.product.code, 0)}
                        className="text-slate-400 hover:text-red-600"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <div className="inline-flex items-center rounded-lg border border-slate-200 dark:border-slate-700">
                        <button
                          type="button"
                          onClick={() => updateQty(item.product.code, item.quantity - 1)}
                          className="px-2 py-1 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800"
                        >
                          <Minus className="h-3 w-3" />
                        </button>
                        <input
                          type="number"
                          min={1}
                          max={item.product.stockQuantity}
                          value={item.quantity}
                          onChange={(e) =>
                            updateQty(item.product.code, parseInt(e.target.value, 10) || 1)
                          }
                          className="w-12 text-center text-xs font-mono-num bg-transparent focus:outline-none"
                        />
                        <button
                          type="button"
                          onClick={() => updateQty(item.product.code, item.quantity + 1)}
                          className="px-2 py-1 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800"
                        >
                          <Plus className="h-3 w-3" />
                        </button>
                      </div>

                      <div className="flex items-center gap-1.5">
                        <span className="text-[11px] text-slate-400">দর:</span>
                        <input
                          type="number"
                          min={0}
                          step="0.01"
                          value={item.salePrice}
                          onChange={(e) =>
                            updatePrice(item.product.code, parseFloat(e.target.value) || 0)
                          }
                          className="w-20 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-right text-xs font-mono-num"
                        />
                      </div>

                      <div className="text-xs font-bold font-mono-num text-slate-900 dark:text-slate-100">
                        {formatMoney(item.quantity * item.salePrice)}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Customer & Payment */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-2 border-t border-slate-100 dark:border-slate-800">
              <div>
                <label className="block text-[11px] font-medium text-slate-500 dark:text-slate-400 mb-1">
                  ক্রেতার নাম
                </label>
                <input
                  list="pos-customers"
                  value={customerName}
                  onChange={(e) => {
                    const val = e.target.value;
                    setCustomerName(val);
                    const match = customers.find(
                      (c) => c.name.toLowerCase() === val.trim().toLowerCase()
                    );
                    if (match && match.mobile) setCustomerMobile(match.mobile);
                  }}
                  placeholder="নগদ ক্রেতা"
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm"
                />
                <datalist id="pos-customers">
                  {customers.map((c) => (
                    <option key={c.id} value={c.name} />
                  ))}
                </datalist>
              </div>
              <div>
                <label className="block text-[11px] font-medium text-slate-500 dark:text-slate-400 mb-1">
                  মোবাইল নম্বর
                </label>
                <input
                  type="text"
                  value={customerMobile}
                  onChange={(e) => setCustomerMobile(e.target.value)}
                  placeholder="01XXXXXXXXX"
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm font-mono-num"
                />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2.5">
              <div>
                <label className="block text-[11px] font-medium text-slate-500 dark:text-slate-400 mb-1">
                  পেমেন্ট মাধ্যম
                </label>
                <select
                  value={paymentMethod}
                  onChange={(e) => {
                    const m = e.target.value as 'Cash' | 'bKash' | 'Bank' | 'Due';
                    setPaymentMethod(m);
                    if (m === 'Due') setPaidInput('0');
                    else setPaidInput('');
                  }}
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1.5 text-xs"
                >
                  <option value="Cash">নগদ (Cash)</option>
                  <option value="bKash">বিকাশ (bKash)</option>
                  <option value="Bank">ব্যাংক</option>
                  <option value="Due">সম্পূর্ণ বাকি</option>
                </select>
              </div>
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-[11px] font-medium text-slate-500 dark:text-slate-400">
                    ছাড় / ডিসকাউন্ট
                  </label>
                  <div className="inline-flex rounded border border-slate-200 dark:border-slate-700 overflow-hidden text-[10px] font-semibold">
                    <button
                      type="button"
                      onClick={() => setDiscountType('AMOUNT')}
                      className={`px-1.5 py-0.5 ${
                        discountType === 'AMOUNT'
                          ? 'btn-accent'
                          : 'bg-slate-50 dark:bg-slate-800 text-slate-500'
                      }`}
                    >
                      ৳
                    </button>
                    <button
                      type="button"
                      onClick={() => setDiscountType('PERCENT')}
                      className={`px-1.5 py-0.5 ${
                        discountType === 'PERCENT'
                          ? 'btn-accent'
                          : 'bg-slate-50 dark:bg-slate-800 text-slate-500'
                      }`}
                    >
                      %
                    </button>
                  </div>
                </div>
                <input
                  type="number"
                  min={0}
                  max={discountType === 'PERCENT' ? 100 : undefined}
                  step="0.01"
                  value={discountInput}
                  onChange={(e) => setDiscountInput(e.target.value)}
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm font-mono-num"
                />
              </div>
              <div>
                <label className="block text-[11px] font-medium text-slate-500 dark:text-slate-400 mb-1">
                  জমা (Paid)
                </label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  placeholder={netTotal.toFixed(2)}
                  value={paidInput}
                  onChange={(e) => setPaidInput(e.target.value)}
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm font-mono-num"
                />
              </div>
            </div>

            {/* Totals Summary */}
            <div className="rounded-lg bg-slate-50 dark:bg-slate-800/70 p-3 space-y-1.5 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">সাবটোটাল</span>
                <span className="font-mono-num font-medium">{formatMoney(subtotal)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">ডিসকাউন্ট</span>
                <span className="font-mono-num text-slate-700 dark:text-slate-300">
                  - {formatMoney(discount)}
                </span>
              </div>
              <div className="flex justify-between text-sm font-bold pt-1 border-t border-slate-200 dark:border-slate-700">
                <span>সর্বমোট বিল</span>
                <span className="font-mono-num">{formatMoney(netTotal)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">নগদ জমা / বাকি</span>
                <span className="font-mono-num">
                  {formatMoney(effectivePaid)} /{' '}
                  <strong className={dueAmount > 0 ? 'text-amber-600 dark:text-amber-400' : ''}>
                    {formatMoney(dueAmount)}
                  </strong>
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">আনুমানিক লাভ (ট্রেডিং)</span>
                <span
                  className={`font-mono-num font-semibold ${
                    estimatedProfit >= 0
                      ? 'text-emerald-700 dark:text-emerald-400'
                      : 'text-red-600 dark:text-red-400'
                  }`}
                >
                  {formatMoney(estimatedProfit)}
                </span>
              </div>
            </div>

            <button
              type="submit"
              disabled={!canWrite || cart.length === 0}
              className="w-full rounded-lg btn-accent py-2.5 text-sm font-semibold disabled:opacity-50"
            >
              বিক্রয় নিশ্চিত করুন ও বিল সেভ করুন
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
