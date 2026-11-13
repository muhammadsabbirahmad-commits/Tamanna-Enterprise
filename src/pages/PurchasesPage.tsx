import React, { useMemo, useState } from 'react';
import { Eye, FileText, Plus, Search, Trash2, Upload } from 'lucide-react';
import { NavRoute, Purchase } from '../types';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { dateKey, daysAgoIso, formatMoney, nowDateTime, todayIso } from '../utils/format';
import { ProductStorage, InventoryMetaStorage } from '../storage/inventoryStorage';
import { PurchaseStorage, SupplierDueStorage } from '../storage/purchaseStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { Modal } from '../components/Modal';

interface ParsedMemoRow {
  productName: string;
  quantity: number;
  purchasePrice: number;
  salePrice: number;
  matchedCode: string;
}

interface PurchasesPageProps {
  revision: number;
  onNavigate: (route: NavRoute) => void;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const PurchasesPage: React.FC<PurchasesPageProps> = ({
  revision,
  onNavigate,
  onDataChange,
  notify,
}) => {
  const canWrite = SecurityStorage.canWrite();
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: '30D',
    fromDate: daysAgoIso(29),
    toDate: todayIso(),
  });
  const [query, setQuery] = useState('');
  const [showAdd, setShowAdd] = useState(false);
  const [showMemoParser, setShowMemoParser] = useState(false);
  const [detailPurchase, setDetailPurchase] = useState<Purchase | null>(null);

  // New purchase form
  const [productCode, setProductCode] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [purchasePrice, setPurchasePrice] = useState('');
  const [supplier, setSupplier] = useState('');
  const [memoNumber, setMemoNumber] = useState('');
  const [paidAmount, setPaidAmount] = useState('');

  // Memo parser state
  const [memoRawText, setMemoRawText] = useState('');
  const [memoSupplier, setMemoSupplier] = useState('');
  const [memoNo, setMemoNo] = useState('');
  const [parsedRows, setParsedRows] = useState<ParsedMemoRow[]>([]);

  const products = useMemo(() => ProductStorage.getProducts(), [revision]);
  const suppliers = useMemo(() => SupplierDueStorage.getSuppliers(), [revision]);

  const purchases = useMemo(() => {
    const q = query.trim().toLowerCase();
    return PurchaseStorage.getPurchases().filter((p) => {
      const k = dateKey(p.date);
      const inDate = k >= filter.fromDate && k <= filter.toDate;
      const matchQ =
        !q ||
        p.productName.toLowerCase().includes(q) ||
        p.productCode.toLowerCase().includes(q) ||
        p.supplier.toLowerCase().includes(q) ||
        p.memoNumber.toLowerCase().includes(q);
      return inDate && matchQ;
    });
  }, [filter, query, revision]);

  const totals = useMemo(() => {
    const gross = purchases.reduce((s, p) => s + p.quantity * p.purchasePrice, 0);
    const paid = purchases.reduce((s, p) => s + (p.settlementRecorded ? p.paidAmount : p.quantity * p.purchasePrice), 0);
    const due = purchases.reduce((s, p) => s + (p.settlementRecorded ? p.dueAmount : 0), 0);
    return { gross, paid, due };
  }, [purchases]);

  const selectedProduct = products.find((p) => p.code === productCode);
  const qtyNum = Math.max(0, parseInt(quantity || '0', 10) || 0);
  const priceNum = Math.max(0, parseFloat(purchasePrice || '0') || 0);
  const grossPurchase = qtyNum * priceNum;
  const effectivePaid =
    paidAmount === '' ? grossPurchase : Math.min(grossPurchase, Math.max(0, parseFloat(paidAmount) || 0));
  const duePurchase = Math.max(0, grossPurchase - effectivePaid);

  const openNewPurchase = () => {
    const first = products[0];
    setProductCode(first?.code || '');
    setQuantity('1');
    setPurchasePrice(first ? String(first.purchasePrice) : '');
    setSupplier('');
    setMemoNumber('');
    setPaidAmount('');
    setShowAdd(true);
  };

  const handleSavePurchase = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return notify('শুধুমাত্র অ্যাডমিন ক্রয় যোগ করতে পারবেন', 'error');
    if (!selectedProduct) return notify('পণ্য নির্বাচন করুন', 'error');
    if (qtyNum <= 0 || priceNum <= 0) return notify('সঠিক পরিমাণ ও ক্রয়মূল্য দিন', 'error');
    if (duePurchase > 0.004 && !supplier.trim()) {
      return notify('বাকি ক্রয়ের জন্য সরবরাহকারীর নাম লিখুন', 'error');
    }

    const nowStr = nowDateTime();
    const id = PurchaseStorage.addPurchase({
      id: Date.now(),
      date: nowStr,
      productCode: selectedProduct.code,
      productName: selectedProduct.name,
      quantity: qtyNum,
      purchasePrice: priceNum,
      supplier: supplier.trim() || 'নগদ সরবরাহকারী',
      memoNumber: memoNumber.trim(),
      paidAmount: effectivePaid,
      dueAmount: duePurchase,
      settlementRecorded: true,
    });
    if (id <= 0) return notify('ক্রয় সংরক্ষণ ব্যর্থ হয়েছে', 'error');

    const before = selectedProduct.stockQuantity;
    const after = before + qtyNum;
    ProductStorage.updateProduct({
      ...selectedProduct,
      purchasePrice: priceNum,
      stockQuantity: after,
    });
    InventoryMetaStorage.addHistory(
      selectedProduct.code,
      selectedProduct.name,
      'ক্রয়',
      qtyNum,
      before,
      after,
      `মেমো: ${memoNumber.trim() || '—'}`
    );

    if (duePurchase > 0.004) {
      SupplierDueStorage.addPurchaseDue(
        supplier.trim(),
        duePurchase,
        `ক্রয়: ${selectedProduct.name} x${qtyNum}${memoNumber.trim() ? ` • মেমো ${memoNumber.trim()}` : ''}`,
        id
      );
    } else if (supplier.trim()) {
      SupplierDueStorage.ensureSupplier(supplier.trim());
    }

    ActivityLogStorage.add(
      'পণ্য ক্রয়',
      `${selectedProduct.name} × ${qtyNum} · মোট ${formatMoney(grossPurchase)}`
    );
    notify('ক্রয় সফলভাবে সংরক্ষিত হয়েছে', 'success');
    setShowAdd(false);
    onDataChange();
  };

  // Parse Bengali/English memo lines like "বাসমতি চাল 10 680" or "P-228622, 5, 820"
  const parseMemoText = (text: string) => {
    const lines = text
      .split(/\r?\n/)
      .map((l) => l.trim())
      .filter(Boolean);
    const out: ParsedMemoRow[] = [];
    for (const line of lines) {
      const nums = line.match(/\d+(?:\.\d+)?/g);
      if (!nums || nums.length < 2) continue;
      const price = parseFloat(nums[nums.length - 1]);
      const qty = Math.max(1, Math.round(parseFloat(nums[nums.length - 2])));
      const firstNumIdx = line.indexOf(nums[nums.length - 2]);
      const namePart = line.slice(0, Math.max(0, firstNumIdx)).replace(/[,|\-–:]+$/g, '').trim();
      if (!namePart || price <= 0) continue;

      const matched = products.find(
        (p) =>
          p.name.toLowerCase() === namePart.toLowerCase() ||
          p.code.toLowerCase() === namePart.toLowerCase() ||
          p.name.toLowerCase().includes(namePart.toLowerCase())
      );

      out.push({
        productName: matched ? matched.name : namePart,
        quantity: qty,
        purchasePrice: price,
        salePrice: matched ? matched.salePrice : Math.round(price * 1.15),
        matchedCode: matched ? matched.code : '',
      });
    }
    setParsedRows(out);
    if (out.length === 0) {
      notify('টেক্সট থেকে কোনো আইটেম শনাক্ত করা যায়নি। প্রতিটি লাইনে "নাম পরিমাণ দাম" দিন।', 'info');
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const content = String(reader.result || '');
      setMemoRawText(content);
      parseMemoText(content);
    };
    reader.readAsText(file);
  };

  const handleSaveParsedMemo = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite || parsedRows.length === 0) return;
    const nowStr = nowDateTime();
    let added = 0;

    parsedRows.forEach((row, idx) => {
      if (row.quantity <= 0 || row.purchasePrice <= 0 || !row.productName.trim()) return;
      let code = row.matchedCode;
      if (!code) {
        code = ProductStorage.nextProductCode();
        ProductStorage.addProduct({
          code,
          name: row.productName.trim(),
          purchasePrice: row.purchasePrice,
          salePrice: Math.max(row.purchasePrice, row.salePrice),
          stockQuantity: 0,
        });
      }
      const prod = ProductStorage.getProducts().find((p) => p.code === code);
      if (!prod) return;

      const before = prod.stockQuantity;
      const after = before + row.quantity;
      ProductStorage.updateProduct({
        ...prod,
        purchasePrice: row.purchasePrice,
        stockQuantity: after,
      });

      const pid = PurchaseStorage.addPurchase({
        id: Date.now() + idx,
        date: nowStr,
        productCode: prod.code,
        productName: prod.name,
        quantity: row.quantity,
        purchasePrice: row.purchasePrice,
        supplier: memoSupplier.trim() || 'মেমো সরবরাহকারী',
        memoNumber: memoNo.trim(),
        paidAmount: row.quantity * row.purchasePrice,
        dueAmount: 0,
        settlementRecorded: true,
      });
      if (pid > 0) {
        InventoryMetaStorage.addHistory(
          prod.code,
          prod.name,
          'মেমো ক্রয়',
          row.quantity,
          before,
          after,
          `মেমো: ${memoNo.trim() || '—'}`
        );
        added++;
      }
    });

    if (added > 0) {
      ActivityLogStorage.add('মেমো ইম্পোর্ট', `${added}টি পণ্য মেমো থেকে স্টকে যোগ হয়েছে`);
      notify(`${added}টি ক্রয় আইটেম স্টকে যোগ হয়েছে`, 'success');
      setShowMemoParser(false);
      setParsedRows([]);
      setMemoRawText('');
      onDataChange();
    }
  };

  return (
    <div className="space-y-4">
      <DateFilterBar preset={filter.preset} fromDate={filter.fromDate} toDate={filter.toDate} onChange={setFilter} />

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl surface-card p-3.5">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="পণ্য, সাপ্লায়ার বা মেমো নম্বর খুঁজুন..."
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 pl-9 pr-3 py-2 text-sm"
          />
        </div>
        {canWrite && (
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={() => {
                setMemoRawText('');
                setParsedRows([]);
                setMemoSupplier('');
                setMemoNo('');
                setShowMemoParser(true);
              }}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700"
            >
              <FileText className="h-4 w-4" />
              <span>মেমো পার্সার</span>
            </button>
            <button
              type="button"
              onClick={openNewPurchase}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-sm font-medium"
            >
              <Plus className="h-4 w-4" />
              <span>নতুন ক্রয়</span>
            </button>
          </div>
        )}
      </div>

      {/* Summary Strip */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500 dark:text-slate-400">মোট ক্রয়মূল্য</div>
          <div className="mt-1 text-lg font-bold font-mono-num">{formatMoney(totals.gross)}</div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500 dark:text-slate-400">নগদ পরিশোধিত</div>
          <div className="mt-1 text-lg font-bold font-mono-num text-emerald-700 dark:text-emerald-400">
            {formatMoney(totals.paid)}
          </div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500 dark:text-slate-400">সাপ্লায়ার বাকি</div>
          <div className="mt-1 text-lg font-bold font-mono-num text-amber-600 dark:text-amber-400">
            {formatMoney(totals.due)}
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl surface-card overflow-hidden">
        {purchases.length === 0 ? (
          <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400">
            এই সময়কালে কোনো ক্রয় রেকর্ড নেই
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">তারিখ ও মেমো</th>
                  <th className="px-4 py-3">পণ্য</th>
                  <th className="px-4 py-3">সরবরাহকারী</th>
                  <th className="px-4 py-3 text-right">পরিমাণ × দর</th>
                  <th className="px-4 py-3 text-right">মোট</th>
                  <th className="px-4 py-3 text-right">বাকি</th>
                  <th className="px-4 py-3 text-right">বিস্তারিত</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {purchases.map((p) => {
                  const total = p.quantity * p.purchasePrice;
                  return (
                    <tr key={p.id} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                      <td className="px-4 py-3">
                        <div className="text-xs font-medium">{p.date}</div>
                        <div className="text-xs text-slate-500 font-mono-num">
                          মেমো: {p.memoNumber || '—'}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-slate-900 dark:text-slate-100">{p.productName}</div>
                        <div className="text-xs text-slate-500 font-mono-num">{p.productCode}</div>
                      </td>
                      <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{p.supplier}</td>
                      <td className="px-4 py-3 text-right font-mono-num text-xs">
                        {p.quantity} × {formatMoney(p.purchasePrice)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num font-semibold">
                        {formatMoney(total)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num text-xs">
                        {p.dueAmount > 0 ? (
                          <span className="text-amber-600 dark:text-amber-400 font-semibold">
                            {formatMoney(p.dueAmount)}
                          </span>
                        ) : (
                          <span className="text-slate-400">পরিশোধ</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          type="button"
                          onClick={() => setDetailPurchase(p)}
                          className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
                        >
                          <Eye className="h-4 w-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add Purchase Modal */}
      <Modal
        open={showAdd}
        onClose={() => setShowAdd(false)}
        title="নতুন পণ্য ক্রয় এন্ট্রি"
        subtitle="ক্রয়ের পর পণ্যের স্টক ও সাপ্লায়ার বাকি স্বয়ংক্রিয়ভাবে আপডেট হবে"
      >
        <form onSubmit={handleSavePurchase} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              পণ্য নির্বাচন *
            </label>
            {products.length === 0 ? (
              <div className="rounded-lg border border-amber-200 dark:border-amber-900/60 bg-amber-50/70 dark:bg-amber-950/30 p-3 text-xs text-amber-900 dark:text-amber-200 flex items-center justify-between gap-2">
                <span>কোনো পণ্য তালিকায় নেই। আগে পণ্য যোগ করুন অথবা মেমো পার্সার ব্যবহার করুন।</span>
                <button
                  type="button"
                  onClick={() => {
                    setShowAdd(false);
                    onNavigate('products');
                  }}
                  className="shrink-0 font-semibold underline"
                >
                  পণ্য যোগ করুন
                </button>
              </div>
            ) : (
              <select
                value={productCode}
                onChange={(e) => {
                  const c = e.target.value;
                  setProductCode(c);
                  const found = products.find((p) => p.code === c);
                  if (found) setPurchasePrice(String(found.purchasePrice));
                }}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
              >
                {products.map((p) => (
                  <option key={p.code} value={p.code}>
                    {p.name} ({p.code}) — স্টক: {p.stockQuantity}
                  </option>
                ))}
              </select>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                পরিমাণ *
              </label>
              <input
                type="number"
                min={1}
                required
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                একক ক্রয়মূল্য (৳) *
              </label>
              <input
                type="number"
                min={0.01}
                step="0.01"
                required
                value={purchasePrice}
                onChange={(e) => setPurchasePrice(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                সরবরাহকারী
              </label>
              <input
                list="purchase-suppliers"
                value={supplier}
                onChange={(e) => setSupplier(e.target.value)}
                placeholder="সরবরাহকারীর নাম"
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
              />
              <datalist id="purchase-suppliers">
                {suppliers.map((s) => (
                  <option key={s.id} value={s.name} />
                ))}
              </datalist>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                মেমো নম্বর
              </label>
              <input
                type="text"
                value={memoNumber}
                onChange={(e) => setMemoNumber(e.target.value)}
                placeholder="যেমন: MEMO-1042"
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              নগদ পরিশোধ (খালি রাখলে সম্পূর্ণ পরিশোধ ধরা হবে)
            </label>
            <input
              type="number"
              min={0}
              step="0.01"
              placeholder={grossPurchase.toFixed(2)}
              value={paidAmount}
              onChange={(e) => setPaidAmount(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
            />
          </div>

          <div className="rounded-lg bg-slate-50 dark:bg-slate-800/60 p-3 flex justify-between text-xs">
            <span>মোট: <strong className="font-mono-num">{formatMoney(grossPurchase)}</strong></span>
            <span>জমা: <strong className="font-mono-num">{formatMoney(effectivePaid)}</strong></span>
            <span>বাকি: <strong className="font-mono-num text-amber-600">{formatMoney(duePurchase)}</strong></span>
          </div>

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowAdd(false)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm font-medium"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              ক্রয় সংরক্ষণ করুন
            </button>
          </div>
        </form>
      </Modal>

      {/* Memo Text/File Parser Modal */}
      <Modal
        open={showMemoParser}
        onClose={() => setShowMemoParser(false)}
        title="মেমো টেক্সট / ফাইল পার্সার"
        subtitle="প্রতি লাইনে পণ্যের নাম, পরিমাণ এবং একক দর লিখুন বা টেক্সট/CSV ফাইল আপলোড করুন"
        maxWidthClass="max-w-2xl"
      >
        <form onSubmit={handleSaveParsedMemo} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <input
              type="text"
              value={memoSupplier}
              onChange={(e) => setMemoSupplier(e.target.value)}
              placeholder="সরবরাহকারীর নাম"
              className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
            <input
              type="text"
              value={memoNo}
              onChange={(e) => setMemoNo(e.target.value)}
              placeholder="মেমো নম্বর"
              className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-xs font-medium text-slate-600 dark:text-slate-300">
                মেমো আইটেম টেক্সট (যেমন: প্রিমিয়াম বাসমতি চাল 10 680)
              </label>
              <label className="cursor-pointer inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline">
                <Upload className="h-3.5 w-3.5" />
                <span>ফাইল আপলোড</span>
                <input type="file" accept=".txt,.csv" onChange={handleFileUpload} className="hidden" />
              </label>
            </div>
            <textarea
              rows={4}
              value={memoRawText}
              onChange={(e) => setMemoRawText(e.target.value)}
              placeholder={'সয়াবিন তেল (৫ লিটার) 15 820\nআটা প্যাকেট (২ কেজি) 20 95'}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
            />
            <button
              type="button"
              onClick={() => parseMemoText(memoRawText)}
              className="mt-2 rounded-lg border border-slate-200 dark:border-slate-700 px-3 py-1.5 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800"
            >
              টেক্সট পার্স ও যাচাই করুন
            </button>
          </div>

          {parsedRows.length > 0 && (
            <div className="space-y-2 max-h-64 overflow-y-auto border-t border-slate-200 dark:border-slate-800 pt-3">
              <div className="grid grid-cols-12 gap-2 px-1 text-[11px] font-semibold text-slate-500">
                <span className="col-span-5">পণ্যের নাম</span>
                <span className="col-span-2 text-right">পরিমাণ</span>
                <span className="col-span-2 text-right">ক্রয় দর</span>
                <span className="col-span-2 text-right">বিক্রয় দর</span>
                <span className="col-span-1 text-right" />
              </div>
              {parsedRows.map((r, i) => (
                <div key={i} className="grid grid-cols-12 gap-2 items-center text-xs">
                  <div className="col-span-5">
                    <input
                      type="text"
                      value={r.productName}
                      onChange={(e) =>
                        setParsedRows((prev) =>
                          prev.map((x, idx) => (idx === i ? { ...x, productName: e.target.value } : x))
                        )
                      }
                      className="w-full rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1.5"
                    />
                    <div className="mt-0.5 text-[10px] text-slate-400">
                      {r.matchedCode ? `বিদ্যমান (${r.matchedCode})` : 'নতুন পণ্য হিসেবে যোগ হবে'}
                    </div>
                  </div>
                  <input
                    type="number"
                    min={1}
                    value={r.quantity}
                    onChange={(e) =>
                      setParsedRows((prev) =>
                        prev.map((x, idx) =>
                          idx === i ? { ...x, quantity: parseInt(e.target.value, 10) || 1 } : x
                        )
                      )
                    }
                    className="col-span-2 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1.5 text-right font-mono-num"
                  />
                  <input
                    type="number"
                    min={0}
                    step="0.01"
                    value={r.purchasePrice}
                    onChange={(e) =>
                      setParsedRows((prev) =>
                        prev.map((x, idx) =>
                          idx === i ? { ...x, purchasePrice: parseFloat(e.target.value) || 0 } : x
                        )
                      )
                    }
                    className="col-span-2 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1.5 text-right font-mono-num"
                  />
                  <input
                    type="number"
                    min={0}
                    step="0.01"
                    value={r.salePrice}
                    onChange={(e) =>
                      setParsedRows((prev) =>
                        prev.map((x, idx) =>
                          idx === i ? { ...x, salePrice: parseFloat(e.target.value) || 0 } : x
                        )
                      )
                    }
                    className="col-span-2 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1.5 text-right font-mono-num"
                  />
                  <button
                    type="button"
                    onClick={() => setParsedRows((prev) => prev.filter((_, idx) => idx !== i))}
                    title="সারি সরান"
                    className="col-span-1 flex justify-center rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowMemoParser(false)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm font-medium"
            >
              বাতিল
            </button>
            <button
              type="submit"
              disabled={parsedRows.length === 0}
              className="rounded-lg btn-accent px-4 py-2 text-sm font-medium disabled:opacity-50"
            >
              সব আইটেম স্টকে যোগ করুন
            </button>
          </div>
        </form>
      </Modal>

      {/* Purchase Detail Modal */}
      <Modal
        open={!!detailPurchase}
        onClose={() => setDetailPurchase(null)}
        title="ক্রয় ইনভয়েস বিস্তারিত"
        maxWidthClass="max-w-md"
      >
        {detailPurchase && (
          <div className="space-y-2.5 text-sm">
            <div className="flex justify-between">
              <span className="text-slate-500">পণ্য:</span>
              <span className="font-medium">{detailPurchase.productName} ({detailPurchase.productCode})</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">তারিখ:</span>
              <span className="font-mono-num">{detailPurchase.date}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">সরবরাহকারী:</span>
              <span>{detailPurchase.supplier}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">মেমো নম্বর:</span>
              <span className="font-mono-num">{detailPurchase.memoNumber || '—'}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">পরিমাণ × একক দর:</span>
              <span className="font-mono-num">
                {detailPurchase.quantity} × {formatMoney(detailPurchase.purchasePrice)}
              </span>
            </div>
            <div className="flex justify-between border-t border-slate-200 dark:border-slate-800 pt-2 font-bold">
              <span>মোট মূল্য:</span>
              <span className="font-mono-num">
                {formatMoney(detailPurchase.quantity * detailPurchase.purchasePrice)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">পরিশোধ / বাকি:</span>
              <span className="font-mono-num">
                {formatMoney(detailPurchase.paidAmount)} / {formatMoney(detailPurchase.dueAmount)}
              </span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
