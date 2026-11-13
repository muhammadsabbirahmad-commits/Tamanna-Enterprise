import React, { useMemo, useState } from 'react';
import { Edit2, Eye, PlusCircle, Printer, RotateCcw, Search } from 'lucide-react';
import { NavRoute, Sale, SaleTransaction } from '../types';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { dateKey, daysAgoIso, formatMoney, generateTxId, nowDateTime, todayIso } from '../utils/format';
import { SaleReturnStorage, SalesStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { ProductStorage, InventoryMetaStorage } from '../storage/inventoryStorage';
import { CustomerDueStorage } from '../storage/dueStorage';
import { ActivityLogStorage, SecurityStorage, SettingsStorage } from '../storage/businessStorage';
import { Modal } from '../components/Modal';

interface SalesPageProps {
  revision: number;
  onNavigate: (route: NavRoute) => void;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const SalesPage: React.FC<SalesPageProps> = ({
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
  const [detailTx, setDetailTx] = useState<SaleTransaction | null>(null);
  const [returnTx, setReturnTx] = useState<SaleTransaction | null>(null);
  const [editTx, setEditTx] = useState<SaleTransaction | null>(null);

  // Return & Exchange modal state
  const [returnQtys, setReturnQtys] = useState<Record<string, number>>({});
  const [refundMethod, setRefundMethod] = useState('Cash');
  const [exchangeCode, setExchangeCode] = useState('');
  const [exchangeQty, setExchangeQty] = useState('1');
  const [exchangePrice, setExchangePrice] = useState('');
  const [exchangeDiscount, setExchangeDiscount] = useState('0');
  const [exchangeMethod, setExchangeMethod] = useState<'Cash' | 'bKash' | 'Bank' | 'Due'>('Cash');
  const [exchangePaidInput, setExchangePaidInput] = useState('');
  const [exchangeCustomer, setExchangeCustomer] = useState('');
  const [exchangeMobile, setExchangeMobile] = useState('');

  // Edit modal state
  const [editLines, setEditLines] = useState<
    Array<{ productCode: string; productName: string; quantity: number; salePrice: number; purchasePrice: number }>
  >([]);
  const [editDiscount, setEditDiscount] = useState('0');
  const [editDiscountType, setEditDiscountType] = useState<'AMOUNT' | 'PERCENT'>('AMOUNT');

  const allSales = useMemo(() => SalesStorage.getSales(), [revision]);
  const allProducts = useMemo(() => ProductStorage.getProducts(), [revision]);
  const allReturns = useMemo(() => SaleReturnStorage.getReturns(), [revision]);
  const allReturnLines = useMemo(() => SaleReturnStorage.getLines(), [revision]);
  const allExchanges = useMemo(() => SaleReturnStorage.getExchanges(), [revision]);

  const transactions = useMemo(() => {
    const q = query.trim().toLowerCase();
    return SalesTransactionStorage.getTransactions()
      .filter((t) => {
        const k = dateKey(t.date);
        const inDate = k >= filter.fromDate && k <= filter.toDate;
        const matchQ =
          !q ||
          t.transactionId.toLowerCase().includes(q) ||
          t.customer.toLowerCase().includes(q) ||
          t.mobile.toLowerCase().includes(q);
        return inDate && matchQ;
      })
      .sort((a, b) => b.date.localeCompare(a.date));
  }, [filter, query, revision]);

  const exchangeNetOf = (ex: (typeof allExchanges)[number]) =>
    ex.netAmount !== undefined
      ? Math.max(0, ex.netAmount)
      : Math.max(0, ex.quantity * ex.unitPrice - (ex.discount ?? 0));

  const summary = useMemo(() => {
    const grossSales = transactions.reduce((s, t) => s + t.total, 0);
    const totalPaid = transactions.reduce((s, t) => s + t.paid, 0);
    const txIds = new Set(transactions.map((t) => t.transactionId));
    const returned = allReturns
      .filter((r) => txIds.has(r.transactionId))
      .reduce((s, r) => s + r.amount, 0);
    const dueReduced = allReturns
      .filter((r) => txIds.has(r.transactionId))
      .reduce((s, r) => s + r.dueReduction, 0);
    const settledExchanges = allExchanges.filter(
      (ex) => txIds.has(ex.transactionId) && (ex.netAmount !== undefined || ex.purchasePrice !== undefined)
    );
    const exchangeSales = settledExchanges.reduce((s, ex) => s + exchangeNetOf(ex), 0);
    const exchangePaid = settledExchanges.reduce((s, ex) => s + (ex.paidAmount ?? 0), 0);
    const exchangeDue = settledExchanges.reduce((s, ex) => s + (ex.dueAmount ?? 0), 0);
    const rawDue = transactions.reduce((s, t) => s + t.due, 0);
    const effectiveDue = Math.max(0, rawDue - dueReduced) + exchangeDue;
    const netSales = Math.max(0, grossSales - returned + exchangeSales);

    return {
      grossSales,
      netSales,
      totalPaid: totalPaid + exchangePaid,
      effectiveDue,
      returned,
      exchangeSales,
    };
  }, [transactions, allReturns, allExchanges]);

  const openReturnModal = (tx: SaleTransaction) => {
    const lines = allSales.filter((s) => s.transactionId === tx.transactionId);
    const initial: Record<string, number> = {};
    lines.forEach((l) => {
      initial[l.productCode] = 0;
    });
    setReturnQtys(initial);
    setRefundMethod('Cash');
    setExchangeCode('');
    setExchangeQty('1');
    setExchangePrice('');
    setExchangeDiscount('0');
    setExchangeMethod('Cash');
    setExchangePaidInput('');
    setExchangeCustomer(tx.customer === 'নগদ ক্রেতা' ? '' : tx.customer);
    setExchangeMobile(tx.mobile);
    setReturnTx(tx);
  };

  const computeReturnAndExchangePreview = (
    tx: SaleTransaction,
    qtyMap: Record<string, number>,
    exCode: string,
    exQtyStr: string,
    exPriceStr: string,
    exDiscStr: string,
    exMethod: 'Cash' | 'bKash' | 'Bank' | 'Due',
    exPaidStr: string
  ) => {
    const lines = allSales.filter((s) => s.transactionId === tx.transactionId);
    const prevMap = SaleReturnStorage.getReturnedQuantities(tx.transactionId);
    const hasReturnable = lines.some(
      (l) => Math.max(0, l.quantity - (prevMap[l.productCode.toLowerCase()] ?? 0)) > 0
    );
    const allReturningAfterThis = lines.every((l) => {
      const prev = prevMap[l.productCode.toLowerCase()] ?? 0;
      const cur = Math.max(0, qtyMap[l.productCode] ?? 0);
      return l.quantity - prev - cur <= 0;
    });
    const discountRatio =
      tx.subtotal > 0 ? Math.min(1, Math.max(0, tx.discount / tx.subtotal)) : 0;
    const grossVal = lines.reduce(
      (s, l) => s + Math.max(0, qtyMap[l.productCode] ?? 0) * l.salePrice,
      0
    );
    const alreadyReturnedAmt = SaleReturnStorage.getReturnedAmount(tx.transactionId);
    const maxRemainingReturnableAmt = Math.max(
      0,
      Math.round((tx.total - alreadyReturnedAmt) * 100) / 100
    );
    const rawReturnVal = Math.round(grossVal * (1 - discountRatio) * 100) / 100;
    const returnVal =
      grossVal <= 0
        ? 0
        : allReturningAfterThis
        ? maxRemainingReturnableAmt
        : Math.min(maxRemainingReturnableAmt, rawReturnVal);

    const exProduct = exCode ? allProducts.find((p) => p.code === exCode) : undefined;
    const exQtyNum = Math.max(0, parseInt(exQtyStr, 10) || 0);
    const exUnitPrice = exProduct
      ? Math.max(0, parseFloat(exPriceStr || String(exProduct.salePrice)) || 0)
      : 0;
    const exGross = exProduct ? Math.round(exQtyNum * exUnitPrice * 100) / 100 : 0;
    const exDisc = exProduct
      ? Math.min(exGross, Math.max(0, parseFloat(exDiscStr) || 0))
      : 0;
    const exNet = exProduct ? Math.max(0, Math.round((exGross - exDisc) * 100) / 100) : 0;
    const exchangeDiff = exProduct ? Math.round((exNet - returnVal) * 100) / 100 : 0;

    const customerOutstanding = CustomerDueStorage.getBalance(tx.customer, tx.mobile);
    const priorReduction = SaleReturnStorage.getDueReduction(tx.transactionId);
    const remainingTxDue = Math.max(0, tx.due - priorReduction);
    const maxDueReduction = Math.min(remainingTxDue, customerOutstanding);

    let dueReduction = 0;
    let refundAmount = 0;
    let exPaid = 0;
    let exDue = 0;

    if (!exProduct) {
      dueReduction = Math.min(returnVal, maxDueReduction);
      refundAmount = Math.max(0, Math.round((returnVal - dueReduction) * 100) / 100);
    } else if (exchangeDiff >= 0) {
      dueReduction = 0;
      refundAmount = 0;
      exPaid =
        exMethod === 'Due'
          ? Math.min(exchangeDiff, Math.max(0, parseFloat(exPaidStr || '0') || 0))
          : exPaidStr === ''
          ? exchangeDiff
          : Math.min(exchangeDiff, Math.max(0, parseFloat(exPaidStr) || 0));
      exDue = Math.max(0, Math.round((exchangeDiff - exPaid) * 100) / 100);
    } else {
      const excessCredit = Math.max(0, Math.round((returnVal - exNet) * 100) / 100);
      dueReduction = Math.min(excessCredit, maxDueReduction);
      refundAmount = Math.max(0, Math.round((excessCredit - dueReduction) * 100) / 100);
      exPaid = 0;
      exDue = 0;
    }

    return {
      hasReturnable,
      returnVal,
      dueReduction,
      refundAmount,
      exProduct,
      exQtyNum,
      exUnitPrice,
      exGross,
      exDisc,
      exNet,
      exchangeDiff,
      exPaid,
      exDue,
    };
  };

  const handleReturnSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!returnTx || !canWrite) return;
    const txLines = allSales.filter((s) => s.transactionId === returnTx.transactionId);
    const returnId = generateTxId('RET');
    const nowStr = nowDateTime();

    const selectedLines = txLines
      .map((l) => {
        const qty = Math.max(0, returnQtys[l.productCode] ?? 0);
        if (qty <= 0) return null;
        return {
          returnId,
          transactionId: returnTx.transactionId,
          productCode: l.productCode,
          productName: l.productName,
          quantity: qty,
          unitPrice: l.salePrice,
        };
      })
      .filter((x): x is NonNullable<typeof x> => x !== null);

    if (selectedLines.length === 0) {
      return notify('রিটার্নের জন্য কমপক্ষে ১টি পণ্যের পরিমাণ দিন', 'error');
    }

    if (!SaleReturnStorage.canAddReturn(returnTx.transactionId, selectedLines)) {
      return notify('বিক্রিত ও পূর্বের রিটার্ন পরিমাণের চেয়ে বেশি রিটার্ন করা যাবে না', 'error');
    }

    const preview = computeReturnAndExchangePreview(
      returnTx,
      returnQtys,
      exchangeCode,
      exchangeQty,
      exchangePrice,
      exchangeDiscount,
      exchangeMethod,
      exchangePaidInput
    );

    if (exchangeCode) {
      if (
        !preview.exProduct ||
        preview.exQtyNum <= 0 ||
        preview.exProduct.stockQuantity < preview.exQtyNum
      ) {
        return notify('এক্সচেঞ্জ পণ্যের সঠিক পরিমাণ বা পর্যাপ্ত স্টক নেই', 'error');
      }
      if (preview.exDue > 0.004 && !exchangeCustomer.trim()) {
        return notify('এক্সচেঞ্জে বাকি রাখতে ক্রেতার নাম লিখুন', 'error');
      }
    }

    const ok = SaleReturnStorage.addReturn(
      {
        returnId,
        transactionId: returnTx.transactionId,
        date: nowStr,
        amount: preview.returnVal,
        dueReduction: preview.dueReduction,
        refundAmount: preview.refundAmount,
        refundMethod,
      },
      selectedLines
    );
    if (!ok) return notify('বিক্রয় রিটার্ন সংরক্ষণ ব্যর্থ হয়েছে', 'error');

    // Restore returned stock
    selectedLines.forEach((l) => {
      const prod = ProductStorage.getProducts().find((p) => p.code === l.productCode);
      if (prod) {
        const before = prod.stockQuantity;
        const after = before + l.quantity;
        ProductStorage.updateStock(prod.code, after);
        InventoryMetaStorage.addHistory(
          prod.code,
          prod.name,
          'বিক্রয় রিটার্ন',
          l.quantity,
          before,
          after,
          `রিটার্ন ${returnId}`
        );
      }
    });

    // Deduct due if applicable
    if (preview.dueReduction > 0.004) {
      CustomerDueStorage.addPayment(
        returnTx.customer,
        returnTx.mobile,
        preview.dueReduction,
        `বিক্রয় রিটার্ন: ${returnId}`
      );
    }

    // Handle optional exchange with full pricing, discount, cost & settlement
    if (preview.exProduct) {
      const exId = generateTxId('EX');
      const exSaved = SaleReturnStorage.addExchange({
        exchangeId: exId,
        returnId,
        transactionId: returnTx.transactionId,
        date: nowStr,
        productCode: preview.exProduct.code,
        productName: preview.exProduct.name,
        quantity: preview.exQtyNum,
        unitPrice: preview.exUnitPrice,
        purchasePrice: preview.exProduct.purchasePrice,
        discount: preview.exDisc,
        netAmount: preview.exNet,
        returnedValue: preview.returnVal,
        difference: preview.exchangeDiff,
        paidAmount: preview.exPaid,
        dueAmount: preview.exDue,
        paymentMethod: exchangeMethod,
      });

      if (exSaved) {
        const freshEx = ProductStorage.getProducts().find((p) => p.code === preview.exProduct!.code);
        if (freshEx) {
          ProductStorage.updateStock(freshEx.code, Math.max(0, freshEx.stockQuantity - preview.exQtyNum));
          InventoryMetaStorage.addHistory(
            freshEx.code,
            freshEx.name,
            'এক্সচেঞ্জ আউট',
            preview.exQtyNum,
            freshEx.stockQuantity,
            Math.max(0, freshEx.stockQuantity - preview.exQtyNum),
            `এক্সচেঞ্জ ${exId}`
          );
        }
        if (preview.exDue > 0.004) {
          CustomerDueStorage.addSaleDue(
            exchangeCustomer.trim() || returnTx.customer,
            exchangeMobile.trim() || returnTx.mobile,
            preview.exDue,
            `এক্সচেঞ্জ বাকি: ${preview.exProduct.name} x${preview.exQtyNum} (${exId})`,
            returnTx.transactionId
          );
        }
      }
    }

    ActivityLogStorage.add(
      preview.exProduct ? 'বিক্রয় রিটার্ন ও এক্সচেঞ্জ' : 'বিক্রয় রিটার্ন',
      `${returnTx.transactionId} -> ${returnId} (${formatMoney(preview.returnVal)}${
        preview.exProduct ? ` / এক্সচেঞ্জ: ${preview.exProduct.name} ${formatMoney(preview.exNet)}` : ''
      })`
    );
    notify(
      preview.exProduct ? 'বিক্রয় রিটার্ন ও এক্সচেঞ্জ সম্পন্ন হয়েছে' : 'বিক্রয় রিটার্ন সফলভাবে সম্পন্ন হয়েছে',
      'success'
    );
    setReturnTx(null);
    onDataChange();
  };

  const openEditModal = (tx: SaleTransaction) => {
    if (SaleReturnStorage.getReturnedAmount(tx.transactionId) > 0) {
      return notify('রিটার্ন থাকা ইনভয়েস এডিট করা যাবে না', 'error');
    }
    const lines = allSales.filter((s) => s.transactionId === tx.transactionId);
    setEditLines(
      lines.map((l) => ({
        productCode: l.productCode,
        productName: l.productName,
        quantity: l.quantity,
        salePrice: l.salePrice,
        purchasePrice: l.purchasePrice,
      }))
    );
    setEditDiscountType('AMOUNT');
    setEditDiscount(String(tx.discount));
    setEditTx(tx);
  };

  const handleEditSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editTx || !canWrite) return;
    const oldLines = allSales.filter((s) => s.transactionId === editTx.transactionId);
    const oldQtyByCode: Record<string, number> = {};
    oldLines.forEach((l) => {
      oldQtyByCode[l.productCode] = (oldQtyByCode[l.productCode] ?? 0) + l.quantity;
    });

    for (const l of editLines) {
      if (l.quantity <= 0 || l.salePrice < 0) return notify('সঠিক পরিমাণ ও দর দিন', 'error');
      const prod = ProductStorage.getProducts().find((p) => p.code === l.productCode);
      const oldQty = oldQtyByCode[l.productCode] ?? 0;
      const delta = l.quantity - oldQty;
      if (delta > 0 && (!prod || prod.stockQuantity < delta)) {
        return notify(`${l.productName}-এর পর্যাপ্ত স্টক নেই`, 'error');
      }
    }

    const subtotal = editLines.reduce((s, l) => s + l.quantity * l.salePrice, 0);
    const rawDisc = Math.max(0, parseFloat(editDiscount) || 0);
    if (editDiscountType === 'PERCENT' && rawDisc > 100) {
      return notify('শতকরা ছাড় ১০০% এর বেশি হতে পারবে না', 'error');
    }
    if (editDiscountType === 'AMOUNT' && rawDisc > subtotal) {
      return notify('ডিসকাউন্ট সাবটোটালের বেশি হতে পারবে না', 'error');
    }
    const disc =
      editDiscountType === 'PERCENT'
        ? Math.min(subtotal, Math.round(((subtotal * Math.min(100, rawDisc)) / 100) * 100) / 100)
        : Math.min(subtotal, rawDisc);
    const total = Math.max(0, Math.round((subtotal - disc) * 100) / 100);
    const newPaid = Math.min(total, Math.max(0, editTx.paid));
    const newDue = Math.max(0, Math.round((total - newPaid) * 100) / 100);

    // Sync customer due if due amount changed
    if (Math.abs(newDue - editTx.due) > 0.004) {
      const okDue = CustomerDueStorage.adjustSaleDueByTransaction(
        editTx.transactionId,
        editTx.customer,
        editTx.mobile,
        editTx.due,
        newDue
      );
      if (!okDue) {
        return notify('বাকি সমন্বয়ে ক্রেতার নাম প্রয়োজন', 'error');
      }
    }

    // Adjust stock deltas
    editLines.forEach((l) => {
      const prod = ProductStorage.getProducts().find((p) => p.code === l.productCode);
      if (prod) {
        const delta = l.quantity - (oldQtyByCode[l.productCode] ?? 0);
        if (delta !== 0) {
          ProductStorage.updateStock(prod.code, prod.stockQuantity - delta);
          InventoryMetaStorage.addHistory(
            prod.code,
            prod.name,
            'ইনভয়েস এডিট',
            Math.abs(delta),
            prod.stockQuantity,
            prod.stockQuantity - delta,
            `ইনভয়েস ${editTx.transactionId}`
          );
        }
      }
    });

    const replacementSales: Sale[] = editLines.map((l, i) => ({
      id: Date.now() + i,
      transactionId: editTx.transactionId,
      date: editTx.date,
      productCode: l.productCode,
      productName: l.productName,
      quantity: l.quantity,
      salePrice: l.salePrice,
      purchasePrice: l.purchasePrice,
      customer: editTx.mobile ? `${editTx.customer} • ${editTx.mobile}` : editTx.customer,
    }));

    SalesStorage.replaceTransactionSales(editTx.transactionId, replacementSales);
    SalesTransactionStorage.updateTransaction({
      ...editTx,
      subtotal,
      discount: disc,
      total,
      paid: newPaid,
      due: newDue,
    });
    ActivityLogStorage.add(
      'ইনভয়েস এডিট',
      `${editTx.transactionId} · ছাড় ${formatMoney(disc)} · নতুন মোট ${formatMoney(total)}`
    );
    notify('ইনভয়েস ও ডিসকাউন্ট আপডেট করা হয়েছে', 'success');
    setEditTx(null);
    onDataChange();
  };

  const returnPreview = useMemo(() => {
    if (!returnTx) return null;
    return computeReturnAndExchangePreview(
      returnTx,
      returnQtys,
      exchangeCode,
      exchangeQty,
      exchangePrice,
      exchangeDiscount,
      exchangeMethod,
      exchangePaidInput
    );
  }, [
    returnTx,
    allSales,
    allProducts,
    returnQtys,
    exchangeCode,
    exchangeQty,
    exchangePrice,
    exchangeDiscount,
    exchangeMethod,
    exchangePaidInput,
  ]);

  const editPreview = useMemo(() => {
    const subtotal = editLines.reduce((s, l) => s + l.quantity * l.salePrice, 0);
    const rawDisc = Math.max(0, parseFloat(editDiscount) || 0);
    const disc =
      editDiscountType === 'PERCENT'
        ? Math.min(subtotal, Math.round(((subtotal * Math.min(100, rawDisc)) / 100) * 100) / 100)
        : Math.min(subtotal, rawDisc);
    const total = Math.max(0, Math.round((subtotal - disc) * 100) / 100);
    const cost = editLines.reduce((s, l) => s + l.quantity * l.purchasePrice, 0);
    const paid = editTx ? Math.min(total, Math.max(0, editTx.paid)) : total;
    const due = Math.max(0, Math.round((total - paid) * 100) / 100);
    return { subtotal, disc, total, paid, due, profit: total - cost };
  }, [editLines, editDiscount, editDiscountType, editTx]);

  return (
    <div className="space-y-4">
      <div className={detailTx ? 'space-y-4 no-print' : 'space-y-4'}>
      <DateFilterBar preset={filter.preset} fromDate={filter.fromDate} toDate={filter.toDate} onChange={setFilter} />

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl surface-card p-3.5">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="ইনভয়েস ID, ক্রেতার নাম বা মোবাইল খুঁজুন..."
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 pl-9 pr-3 py-2 text-sm"
          />
        </div>
        {canWrite && (
          <button
            type="button"
            onClick={() => onNavigate('new-sale')}
            className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-sm font-medium"
          >
            <PlusCircle className="h-4 w-4" />
            <span>নতুন বিক্রয় (POS)</span>
          </button>
        )}
      </div>

      {/* Summary Strip */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500 dark:text-slate-400">নিট বিক্রয় (রিটার্ন বাদ)</div>
          <div className="mt-1 text-lg font-bold font-mono-num">{formatMoney(summary.netSales)}</div>
          {(summary.returned > 0 || summary.exchangeSales > 0) && (
            <div className="mt-0.5 text-[11px] text-slate-400 font-mono-num">
              মূল বিল: {formatMoney(summary.grossSales)}
            </div>
          )}
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500 dark:text-slate-400">মোট নগদ আদায়</div>
          <div className="mt-1 text-lg font-bold font-mono-num text-emerald-700 dark:text-emerald-400">
            {formatMoney(summary.totalPaid)}
          </div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500 dark:text-slate-400">ইনভয়েস বাকি</div>
          <div className="mt-1 text-lg font-bold font-mono-num text-amber-600 dark:text-amber-400">
            {formatMoney(summary.effectiveDue)}
          </div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500 dark:text-slate-400">বিক্রয় রিটার্ন / এক্সচেঞ্জ</div>
          <div className="mt-1 text-lg font-bold font-mono-num text-red-600 dark:text-red-400">
            {formatMoney(summary.returned)}
          </div>
          {summary.exchangeSales > 0 && (
            <div className="mt-0.5 text-[11px] text-emerald-600 dark:text-emerald-400 font-mono-num">
              এক্সচেঞ্জ: +{formatMoney(summary.exchangeSales)}
            </div>
          )}
        </div>
      </div>

      {/* Sales Table */}
      <div className="rounded-xl surface-card overflow-hidden">
        {transactions.length === 0 ? (
          <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400">
            এই সময়কালে কোনো বিক্রয় পাওয়া যায়নি
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">তারিখ ও ইনভয়েস</th>
                  <th className="px-4 py-3">ক্রেতা</th>
                  <th className="px-4 py-3">মাধ্যম</th>
                  <th className="px-4 py-3 text-right">নিট বিল</th>
                  <th className="px-4 py-3 text-right">জমা</th>
                  <th className="px-4 py-3 text-right">বাকি / রিটার্ন</th>
                  <th className="px-4 py-3 text-right">অ্যাকশন</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {transactions.map((t) => {
                  const txReturns = allReturns.filter((r) => r.transactionId === t.transactionId);
                  const txExchanges = allExchanges.filter(
                    (ex) => ex.transactionId === t.transactionId && (ex.netAmount !== undefined || ex.purchasePrice !== undefined)
                  );
                  const retAmt = txReturns.reduce((s, r) => s + r.amount, 0);
                  const dueRed = txReturns.reduce((s, r) => s + r.dueReduction, 0);
                  const exNet = txExchanges.reduce((s, ex) => s + exchangeNetOf(ex), 0);
                  const exPaid = txExchanges.reduce((s, ex) => s + (ex.paidAmount ?? 0), 0);
                  const exDue = txExchanges.reduce((s, ex) => s + (ex.dueAmount ?? 0), 0);
                  const effectiveTotal = Math.max(0, t.total - retAmt + exNet);
                  const effectivePaid = t.paid + exPaid;
                  const effectiveDue = Math.max(0, t.due - dueRed) + exDue;

                  return (
                    <tr key={t.transactionId} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                      <td className="px-4 py-3">
                        <div className="font-mono-num text-xs font-semibold text-slate-900 dark:text-slate-100">
                          {t.transactionId}
                        </div>
                        <div className="text-xs text-slate-500 dark:text-slate-400">{t.date}</div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-slate-900 dark:text-slate-100">{t.customer}</div>
                        {t.mobile && (
                          <div className="text-xs text-slate-500 dark:text-slate-400 font-mono-num">
                            {t.mobile}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-600 dark:text-slate-300">
                        <div>{t.paymentMethod}</div>
                        {t.discount > 0 && (
                          <div className="text-[11px] text-slate-400 font-mono-num">
                            ছাড়: {formatMoney(t.discount)}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num font-semibold">
                        <div>{formatMoney(effectiveTotal)}</div>
                        {(retAmt > 0 || exNet > 0) && (
                          <div className="text-[11px] font-normal text-slate-400">
                            মূল: {formatMoney(t.total)}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num text-emerald-700 dark:text-emerald-400">
                        {formatMoney(effectivePaid)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num text-xs">
                        {effectiveDue > 0.004 && (
                          <div className="text-amber-600 dark:text-amber-400 font-semibold">
                            বাকি: {formatMoney(effectiveDue)}
                          </div>
                        )}
                        {retAmt > 0 && (
                          <div className="text-red-600 dark:text-red-400">
                            রিটার্ন: {formatMoney(retAmt)}
                          </div>
                        )}
                        {exNet > 0 && (
                          <div className="text-emerald-600 dark:text-emerald-400">
                            এক্সচেঞ্জ: +{formatMoney(exNet)}
                          </div>
                        )}
                        {effectiveDue <= 0.004 && retAmt <= 0 && exNet <= 0 && (
                          <span className="text-slate-400">পরিশোধিত</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex items-center gap-1">
                          <button
                            type="button"
                            onClick={() => setDetailTx(t)}
                            title="বিস্তারিত ও প্রিন্ট"
                            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-slate-100"
                          >
                            <Eye className="h-4 w-4" />
                          </button>
                          {canWrite && (
                            <>
                              <button
                                type="button"
                                onClick={() => openReturnModal(t)}
                                title="বিক্রয় রিটার্ন / এক্সচেঞ্জ"
                                className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-amber-600 dark:hover:bg-slate-800"
                              >
                                <RotateCcw className="h-4 w-4" />
                              </button>
                              <button
                                type="button"
                                onClick={() => openEditModal(t)}
                                title="ইনভয়েস এডিট"
                                className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800"
                              >
                                <Edit2 className="h-4 w-4" />
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      </div>

      {/* Detail / Print Invoice Modal */}
      <Modal
        open={!!detailTx}
        onClose={() => setDetailTx(null)}
        title={detailTx ? `ইনভয়েস: ${detailTx.transactionId}` : ''}
        subtitle={detailTx ? `${SettingsStorage.getShopName()} · ${detailTx.date}` : ''}
        maxWidthClass="max-w-xl"
        printable
        footer={
          <button
            type="button"
            onClick={() => window.print()}
            className="inline-flex items-center gap-1.5 rounded-lg btn-accent px-4 py-2 text-sm font-medium"
          >
            <Printer className="h-4 w-4" />
            <span>প্রিন্ট / PDF</span>
          </button>
        }
      >
        {detailTx && (() => {
          const txReturns = allReturns.filter((r) => r.transactionId === detailTx.transactionId);
          const txReturnIds = new Set(txReturns.map((r) => r.returnId));
          const txReturnLines = allReturnLines.filter((l) => txReturnIds.has(l.returnId));
          const txExchanges = allExchanges.filter((ex) => ex.transactionId === detailTx.transactionId);
          const retTotal = txReturns.reduce((s, r) => s + r.amount, 0);
          const exTotal = txExchanges.reduce((s, ex) => s + exchangeNetOf(ex), 0);
          const netBill = Math.max(0, detailTx.total - retTotal + exTotal);

          return (
            <div className="space-y-4">
              <div className="flex justify-between text-xs text-slate-600 dark:text-slate-300 border-b border-slate-200 dark:border-slate-800 pb-3">
                <div>
                  <div>ক্রেতা: <strong>{detailTx.customer}</strong></div>
                  {detailTx.mobile && <div>মোবাইল: {detailTx.mobile}</div>}
                </div>
                <div className="text-right">
                  <div>মাধ্যম: {detailTx.paymentMethod}</div>
                  <div>তারিখ: {detailTx.date}</div>
                </div>
              </div>

              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500">
                    <th className="py-2 text-left">পণ্য</th>
                    <th className="py-2 text-right">পরিমাণ</th>
                    <th className="py-2 text-right">দর</th>
                    <th className="py-2 text-right">মোট</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {allSales
                    .filter((s) => s.transactionId === detailTx.transactionId)
                    .map((s) => (
                      <tr key={s.id}>
                        <td className="py-2">{s.productName}</td>
                        <td className="py-2 text-right font-mono-num">{s.quantity}</td>
                        <td className="py-2 text-right font-mono-num">{formatMoney(s.salePrice)}</td>
                        <td className="py-2 text-right font-mono-num font-semibold">
                          {formatMoney(s.quantity * s.salePrice)}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>

              {(txReturnLines.length > 0 || txExchanges.length > 0) && (
                <div className="rounded-lg border border-slate-200 dark:border-slate-700 p-3 space-y-2 text-xs">
                  <div className="font-semibold text-slate-700 dark:text-slate-200">
                    রিটার্ন ও এক্সচেঞ্জ বিবরণ
                  </div>
                  {txReturnLines.map((l) => (
                    <div key={`${l.returnId}-${l.productCode}`} className="flex justify-between text-red-600 dark:text-red-400">
                      <span>ফেরত: {l.productName} × {l.quantity}</span>
                      <span className="font-mono-num">- {formatMoney(l.quantity * l.unitPrice)}</span>
                    </div>
                  ))}
                  {txExchanges.map((ex) => (
                    <div key={ex.exchangeId} className="flex justify-between text-emerald-700 dark:text-emerald-400">
                      <span>
                        এক্সচেঞ্জ: {ex.productName} × {ex.quantity}
                        {(ex.discount ?? 0) > 0 ? ` (ছাড় ${formatMoney(ex.discount ?? 0)})` : ''}
                      </span>
                      <span className="font-mono-num">+ {formatMoney(exchangeNetOf(ex))}</span>
                    </div>
                  ))}
                </div>
              )}

              <div className="rounded-lg bg-slate-50 dark:bg-slate-800/60 p-3 space-y-1 text-xs">
                <div className="flex justify-between">
                  <span>সাবটোটাল</span>
                  <span className="font-mono-num">{formatMoney(detailTx.subtotal)}</span>
                </div>
                <div className="flex justify-between">
                  <span>ডিসকাউন্ট / ছাড়</span>
                  <span className="font-mono-num">- {formatMoney(detailTx.discount)}</span>
                </div>
                <div className="flex justify-between font-semibold">
                  <span>মূল বিল</span>
                  <span className="font-mono-num">{formatMoney(detailTx.total)}</span>
                </div>
                {retTotal > 0 && (
                  <div className="flex justify-between text-red-600 dark:text-red-400">
                    <span>মোট বিক্রয় ফেরত</span>
                    <span className="font-mono-num">- {formatMoney(retTotal)}</span>
                  </div>
                )}
                {exTotal > 0 && (
                  <div className="flex justify-between text-emerald-700 dark:text-emerald-400">
                    <span>মোট এক্সচেঞ্জ বিক্রয়</span>
                    <span className="font-mono-num">+ {formatMoney(exTotal)}</span>
                  </div>
                )}
                <div className="flex justify-between font-bold text-sm border-t border-slate-200 dark:border-slate-700 pt-1">
                  <span>সর্বশেষ নিট বিল</span>
                  <span className="font-mono-num">{formatMoney(netBill)}</span>
                </div>
                <div className="flex justify-between">
                  <span>মূল জমা / বাকি</span>
                  <span className="font-mono-num">
                    {formatMoney(detailTx.paid)} / {formatMoney(detailTx.due)}
                  </span>
                </div>
              </div>
            </div>
          );
        })()}
      </Modal>

      {/* Sale Return & Exchange Modal */}
      <Modal
        open={!!returnTx}
        onClose={() => setReturnTx(null)}
        title="বিক্রয় রিটার্ন ও এক্সচেঞ্জ"
        subtitle={returnTx ? `ইনভয়েস: ${returnTx.transactionId} · ${returnTx.customer}` : ''}
        maxWidthClass="max-w-lg"
      >
        {returnTx && returnPreview && (
          <form onSubmit={handleReturnSubmit} className="space-y-4">
            {!returnPreview.hasReturnable && (
              <div className="rounded-lg border border-amber-200 dark:border-amber-900/60 bg-amber-50/70 dark:bg-amber-950/30 p-3 text-xs text-amber-800 dark:text-amber-200">
                এই ইনভয়েসের সকল পণ্য ইতিমধ্যেই ফেরত নেওয়া হয়েছে।
              </div>
            )}

            <div className="space-y-2">
              {allSales
                .filter((s) => s.transactionId === returnTx.transactionId)
                .map((l) => {
                  const prev =
                    SaleReturnStorage.getReturnedQuantities(returnTx.transactionId)[
                      l.productCode.toLowerCase()
                    ] ?? 0;
                  const maxRem = Math.max(0, l.quantity - prev);
                  return (
                    <div
                      key={l.id}
                      className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 dark:border-slate-700 p-2.5 text-xs"
                    >
                      <div className="min-w-0">
                        <div className="font-medium truncate">{l.productName}</div>
                        <div className="text-slate-500 font-mono-num">
                          বিক্রিত: {l.quantity} · অবশিষ্ট ফেরতযোগ্য: {maxRem} · দর: {formatMoney(l.salePrice)}
                        </div>
                      </div>
                      <input
                        type="number"
                        min={0}
                        max={maxRem}
                        disabled={maxRem === 0}
                        value={returnQtys[l.productCode] ?? 0}
                        onChange={(e) =>
                          setReturnQtys((p) => ({
                            ...p,
                            [l.productCode]: Math.min(maxRem, Math.max(0, parseInt(e.target.value, 10) || 0)),
                          }))
                        }
                        className="w-20 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-right font-mono-num disabled:opacity-50"
                      />
                    </div>
                  );
                })}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                  ফেরত মাধ্যম (নগদ ফেরত হলে)
                </label>
                <select
                  value={refundMethod}
                  onChange={(e) => setRefundMethod(e.target.value)}
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-2 text-sm"
                >
                  <option value="Cash">নগদ (Cash)</option>
                  <option value="bKash">বিকাশ</option>
                  <option value="Bank">ব্যাংক</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                  বিকল্প পণ্য এক্সচেঞ্জ (ঐচ্ছিক)
                </label>
                <select
                  value={exchangeCode}
                  onChange={(e) => {
                    const nextCode = e.target.value;
                    setExchangeCode(nextCode);
                    const picked = allProducts.find((p) => p.code === nextCode);
                    setExchangePrice(picked ? String(picked.salePrice) : '');
                    setExchangeDiscount('0');
                    setExchangePaidInput('');
                  }}
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-2 text-sm"
                >
                  <option value="">কোনো এক্সচেঞ্জ নেই</option>
                  {allProducts
                    .filter((p) => p.stockQuantity > 0)
                    .map((p) => (
                      <option key={p.code} value={p.code}>
                        {p.name} · স্টক {p.stockQuantity} ({formatMoney(p.salePrice)})
                      </option>
                    ))}
                </select>
              </div>
            </div>

            {exchangeCode && returnPreview.exProduct && (
              <div className="rounded-lg border border-slate-200 dark:border-slate-700 p-3 space-y-3 bg-slate-50/50 dark:bg-slate-800/30">
                <div className="grid grid-cols-3 gap-2.5">
                  <div>
                    <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-300 mb-1">
                      পরিমাণ (সর্বোচ্চ {returnPreview.exProduct.stockQuantity})
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={returnPreview.exProduct.stockQuantity}
                      value={exchangeQty}
                      onChange={(e) => setExchangeQty(e.target.value)}
                      className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm font-mono-num"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-300 mb-1">
                      বিক্রয় দর (৳)
                    </label>
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={exchangePrice}
                      onChange={(e) => setExchangePrice(e.target.value)}
                      className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm font-mono-num"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-300 mb-1">
                      এক্সচেঞ্জ ছাড় (৳)
                    </label>
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={exchangeDiscount}
                      onChange={(e) => setExchangeDiscount(e.target.value)}
                      className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm font-mono-num"
                    />
                  </div>
                </div>

                {returnPreview.exchangeDiff > 0.004 && (
                  <div className="grid grid-cols-2 gap-2.5 pt-1">
                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-300 mb-1">
                        বাড়তি টাকার মাধ্যম
                      </label>
                      <select
                        value={exchangeMethod}
                        onChange={(e) => {
                          const m = e.target.value as 'Cash' | 'bKash' | 'Bank' | 'Due';
                          setExchangeMethod(m);
                          if (m === 'Due') setExchangePaidInput('0');
                          else setExchangePaidInput('');
                        }}
                        className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1.5 text-xs"
                      >
                        <option value="Cash">নগদ (Cash)</option>
                        <option value="bKash">বিকাশ (bKash)</option>
                        <option value="Bank">ব্যাংক</option>
                        <option value="Due">বাকি (Due)</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-300 mb-1">
                        এখন জমা ({formatMoney(returnPreview.exchangeDiff)} এর মধ্যে)
                      </label>
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        placeholder={returnPreview.exchangeDiff.toFixed(2)}
                        value={exchangePaidInput}
                        onChange={(e) => setExchangePaidInput(e.target.value)}
                        className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-sm font-mono-num"
                      />
                    </div>
                  </div>
                )}

                {returnPreview.exDue > 0.004 && !exchangeCustomer.trim() && (
                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="block text-[11px] font-medium text-amber-700 dark:text-amber-400 mb-1">
                        ক্রেতার নাম (বাকির জন্য) *
                      </label>
                      <input
                        type="text"
                        required
                        value={exchangeCustomer}
                        onChange={(e) => setExchangeCustomer(e.target.value)}
                        placeholder="ক্রেতার নাম লিখুন"
                        className="w-full rounded-lg border border-amber-300 dark:border-amber-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-xs"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-300 mb-1">
                        মোবাইল
                      </label>
                      <input
                        type="text"
                        value={exchangeMobile}
                        onChange={(e) => setExchangeMobile(e.target.value)}
                        placeholder="01XXXXXXXXX"
                        className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-xs font-mono-num"
                      />
                    </div>
                  </div>
                )}
              </div>
            )}

            <div className="rounded-lg bg-slate-50 dark:bg-slate-800/60 p-3 space-y-1 text-xs">
              <div className="flex justify-between">
                <span>মোট ফেরত মূল্য</span>
                <span className="font-mono-num font-semibold">{formatMoney(returnPreview.returnVal)}</span>
              </div>
              {exchangeCode && returnPreview.exProduct && (
                <>
                  <div className="flex justify-between">
                    <span>নতুন পণ্যের নিট মূল্য</span>
                    <span className="font-mono-num font-semibold text-emerald-700 dark:text-emerald-400">
                      {formatMoney(returnPreview.exNet)}
                    </span>
                  </div>
                  <div className="flex justify-between border-t border-slate-200 dark:border-slate-700 pt-1">
                    <span>
                      পার্থক্য ({returnPreview.exchangeDiff >= 0 ? 'ক্রেতা দেবেন' : 'ক্রেতা ফেরত পাবেন'})
                    </span>
                    <span className="font-mono-num font-semibold">
                      {formatMoney(Math.abs(returnPreview.exchangeDiff))}
                    </span>
                  </div>
                  {returnPreview.exchangeDiff > 0.004 && (
                    <div className="flex justify-between">
                      <span>নগদ জমা / বাকি</span>
                      <span className="font-mono-num">
                        {formatMoney(returnPreview.exPaid)} /{' '}
                        <strong className={returnPreview.exDue > 0 ? 'text-amber-600' : ''}>
                          {formatMoney(returnPreview.exDue)}
                        </strong>
                      </span>
                    </div>
                  )}
                </>
              )}
              {(returnPreview.dueReduction > 0 || returnPreview.refundAmount > 0 || !exchangeCode) && (
                <>
                  <div className="flex justify-between">
                    <span>পূর্বের বাকি সমন্বয়</span>
                    <span className="font-mono-num text-amber-600">{formatMoney(returnPreview.dueReduction)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>নগদ ফেরত ({refundMethod})</span>
                    <span className="font-mono-num font-semibold">{formatMoney(returnPreview.refundAmount)}</span>
                  </div>
                </>
              )}
            </div>

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setReturnTx(null)}
                className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm font-medium"
              >
                বাতিল
              </button>
              <button
                type="submit"
                disabled={!returnPreview.hasReturnable || returnPreview.returnVal <= 0}
                className="rounded-lg btn-accent px-4 py-2 text-sm font-medium disabled:opacity-50"
              >
                {exchangeCode ? 'রিটার্ন ও এক্সচেঞ্জ নিশ্চিত করুন' : 'রিটার্ন নিশ্চিত করুন'}
              </button>
            </div>
          </form>
        )}
      </Modal>

      {/* Edit Invoice & Post-Sale Discount Modal */}
      <Modal
        open={!!editTx}
        onClose={() => setEditTx(null)}
        title="ইনভয়েস ও ছাড় সম্পাদনা"
        subtitle={editTx ? `${editTx.transactionId} · ${editTx.customer}` : ''}
      >
        {editTx && (
          <form onSubmit={handleEditSave} className="space-y-4">
            <div className="space-y-2">
              {editLines.map((line, idx) => (
                <div
                  key={line.productCode}
                  className="grid grid-cols-12 items-center gap-2 rounded-lg border border-slate-200 dark:border-slate-700 p-2.5 text-xs"
                >
                  <div className="col-span-6 truncate font-medium">{line.productName}</div>
                  <div className="col-span-3">
                    <input
                      type="number"
                      min={1}
                      value={line.quantity}
                      onChange={(e) => {
                        const q = Math.max(1, parseInt(e.target.value, 10) || 1);
                        setEditLines((prev) =>
                          prev.map((x, i) => (i === idx ? { ...x, quantity: q } : x))
                        );
                      }}
                      className="w-full rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-right font-mono-num"
                    />
                  </div>
                  <div className="col-span-3">
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={line.salePrice}
                      onChange={(e) => {
                        const sp = Math.max(0, parseFloat(e.target.value) || 0);
                        setEditLines((prev) =>
                          prev.map((x, i) => (i === idx ? { ...x, salePrice: sp } : x))
                        );
                      }}
                      className="w-full rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-right font-mono-num"
                    />
                  </div>
                </div>
              ))}
            </div>

            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="text-xs font-medium text-slate-600 dark:text-slate-300">
                  ডিসকাউন্ট / ছাড়
                </label>
                <div className="inline-flex rounded border border-slate-200 dark:border-slate-700 overflow-hidden text-[11px] font-semibold">
                  <button
                    type="button"
                    onClick={() => setEditDiscountType('AMOUNT')}
                    className={`px-2 py-0.5 ${
                      editDiscountType === 'AMOUNT' ? 'btn-accent' : 'bg-slate-50 dark:bg-slate-800 text-slate-500'
                    }`}
                  >
                    ৳
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditDiscountType('PERCENT')}
                    className={`px-2 py-0.5 ${
                      editDiscountType === 'PERCENT' ? 'btn-accent' : 'bg-slate-50 dark:bg-slate-800 text-slate-500'
                    }`}
                  >
                    %
                  </button>
                </div>
              </div>
              <input
                type="number"
                min={0}
                max={editDiscountType === 'PERCENT' ? 100 : undefined}
                step="0.01"
                value={editDiscount}
                onChange={(e) => setEditDiscount(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>

            <div className="rounded-lg bg-slate-50 dark:bg-slate-800/60 p-3 space-y-1 text-xs">
              <div className="flex justify-between">
                <span>নতুন সাবটোটাল</span>
                <span className="font-mono-num">{formatMoney(editPreview.subtotal)}</span>
              </div>
              <div className="flex justify-between">
                <span>ডিসকাউন্ট</span>
                <span className="font-mono-num">- {formatMoney(editPreview.disc)}</span>
              </div>
              <div className="flex justify-between font-bold text-sm border-t border-slate-200 dark:border-slate-700 pt-1">
                <span>নতুন সর্বমোট</span>
                <span className="font-mono-num">{formatMoney(editPreview.total)}</span>
              </div>
              <div className="flex justify-between">
                <span>নগদ জমা / সমন্বয়কৃত বাকি</span>
                <span className="font-mono-num">
                  {formatMoney(editPreview.paid)} /{' '}
                  <strong className={editPreview.due > 0 ? 'text-amber-600' : ''}>
                    {formatMoney(editPreview.due)}
                  </strong>
                </span>
              </div>
              <div className="flex justify-between">
                <span>আনুমানিক ট্রেডিং লাভ</span>
                <span
                  className={`font-mono-num font-semibold ${
                    editPreview.profit >= 0 ? 'text-emerald-700 dark:text-emerald-400' : 'text-red-600'
                  }`}
                >
                  {formatMoney(editPreview.profit)}
                </span>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditTx(null)}
                className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm font-medium"
              >
                বাতিল
              </button>
              <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
                আপডেট সংরক্ষণ
              </button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
};
