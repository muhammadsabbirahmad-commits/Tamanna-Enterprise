import React, { useMemo, useState } from 'react';
import { Plus, Search, Trash2, Truck } from 'lucide-react';
import { SupplierDueEntry } from '../types';
import { SupplierDueStorage } from '../storage/purchaseStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { formatMoney } from '../utils/format';
import { ConfirmDialog, Modal } from '../components/Modal';

interface SupplierDuePageProps {
  revision: number;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const SupplierDuePage: React.FC<SupplierDuePageProps> = ({ revision, onDataChange, notify }) => {
  const canWrite = SecurityStorage.canWrite();
  const [query, setQuery] = useState('');
  const [showAdd, setShowAdd] = useState(false);
  const [payTarget, setPayTarget] = useState<{ name: string; balance: number } | null>(null);
  const [deletePayment, setDeletePayment] = useState<SupplierDueEntry | null>(null);

  const [supName, setSupName] = useState('');
  const [supMobile, setSupMobile] = useState('');
  const [supAddress, setSupAddress] = useState('');

  const [payAmount, setPayAmount] = useState('');
  const [payNote, setPayNote] = useState('');

  const suppliers = useMemo(() => SupplierDueStorage.getSuppliers(), [revision]);
  const entries = useMemo(() => SupplierDueStorage.getEntries(), [revision]);

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return suppliers
      .map((s) => ({
        ...s,
        balance: SupplierDueStorage.getBalance(s.name),
      }))
      .filter((s) => !q || s.name.toLowerCase().includes(q) || s.mobile.toLowerCase().includes(q))
      .sort((a, b) => b.balance - a.balance);
  }, [suppliers, entries, query]);

  const totalPayable = rows.reduce((s, r) => s + r.balance, 0);

  const handleAddSupplier = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    if (!supName.trim()) return notify('সরবরাহকারীর নাম দিন', 'error');
    if (!SupplierDueStorage.addSupplier(supName.trim(), supMobile.trim(), supAddress.trim())) {
      return notify('এই সরবরাহকারী ইতিমধ্যে আছেন', 'error');
    }
    ActivityLogStorage.add('নতুন সরবরাহকারী', supName.trim());
    notify('নতুন সরবরাহকারী যোগ করা হয়েছে', 'success');
    setSupName('');
    setSupMobile('');
    setSupAddress('');
    setShowAdd(false);
    onDataChange();
  };

  const handlePay = (e: React.FormEvent) => {
    e.preventDefault();
    if (!payTarget || !canWrite) return;
    const amt = parseFloat(payAmount);
    if (!Number.isFinite(amt) || amt <= 0) return notify('সঠিক পরিমাণ দিন', 'error');
    if (amt > payTarget.balance + 0.01) {
      return notify('বকেয়ার চেয়ে বেশি পরিশোধ করা যাবে না', 'error');
    }
    const id = SupplierDueStorage.addPayment(payTarget.name, amt, payNote.trim() || 'দেনা পরিশোধ');
    if (id <= 0) return notify('পরিশোধ সংরক্ষণ ব্যর্থ হয়েছে', 'error');
    ActivityLogStorage.add('সরবরাহকারী পরিশোধ', `${payTarget.name} · ${formatMoney(amt)}`);
    notify('সরবরাহকারীকে পরিশোধ রেকর্ড হয়েছে', 'success');
    setPayTarget(null);
    onDataChange();
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl surface-card p-3.5">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="সরবরাহকারীর নাম বা মোবাইল খুঁজুন..."
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 pl-9 pr-3 py-2 text-sm"
          />
        </div>
        <div className="flex items-center gap-4">
          <div className="text-right">
            <div className="text-[11px] text-slate-500">মোট সরবরাহকারী দেনা</div>
            <div className="text-base font-bold font-mono-num text-red-600 dark:text-red-400">
              {formatMoney(totalPayable)}
            </div>
          </div>
          {canWrite && (
            <button
              type="button"
              onClick={() => setShowAdd(true)}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-sm font-medium"
            >
              <Plus className="h-4 w-4" />
              <span>নতুন সরবরাহকারী</span>
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <div className="lg:col-span-7 rounded-xl surface-card overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 text-sm font-semibold">
            সরবরাহকারী তালিকা ({rows.length})
          </div>
          {rows.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-500">কোনো সরবরাহকারী নেই</div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {rows.map((s) => (
                <div key={s.id} className="flex items-center justify-between gap-3 px-4 py-3">
                  <div className="min-w-0">
                    <div className="font-medium text-slate-900 dark:text-slate-100">{s.name}</div>
                    <div className="text-xs text-slate-500">
                      {[s.mobile, s.address].filter(Boolean).join(' · ') || 'যোগাযোগের তথ্য নেই'}
                    </div>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    <span
                      className={`font-mono-num text-sm font-bold ${
                        s.balance > 0
                          ? 'text-red-600 dark:text-red-400'
                          : 'text-emerald-700 dark:text-emerald-400'
                      }`}
                    >
                      {formatMoney(s.balance)}
                    </span>
                    {canWrite && s.balance > 0 && (
                      <button
                        type="button"
                        onClick={() => {
                          setPayTarget({ name: s.name, balance: s.balance });
                          setPayAmount(String(s.balance));
                          setPayNote('');
                        }}
                        className="whitespace-nowrap shrink-0 inline-flex items-center gap-1 rounded-lg btn-accent px-3 py-1.5 text-xs font-medium"
                      >
                        <Truck className="h-3.5 w-3.5" />
                        <span>পরিশোধ</span>
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="lg:col-span-5 rounded-xl surface-card overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 text-sm font-semibold">
            দেনা ও পরিশোধ ইতিহাস
          </div>
          {entries.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-500">কোনো লেনদেন নেই</div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800 max-h-[500px] overflow-y-auto">
              {entries.slice(0, 40).map((e) => (
                <div key={e.id} className="flex items-start justify-between gap-3 px-4 py-2.5 text-xs">
                  <div className="min-w-0">
                    <div className="font-medium text-slate-900 dark:text-slate-100">{e.supplier}</div>
                    <div className="text-slate-500 truncate">{e.date} · {e.note || e.type}</div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <div
                      className={`font-mono-num font-semibold ${
                        e.amount >= 0
                          ? 'text-red-600 dark:text-red-400'
                          : 'text-emerald-700 dark:text-emerald-400'
                      }`}
                    >
                      {e.amount >= 0 ? `+ ${formatMoney(e.amount)}` : `- ${formatMoney(-e.amount)}`}
                    </div>
                    {canWrite && e.type === 'PAYMENT' && (
                      <button
                        type="button"
                        onClick={() => setDeletePayment(e)}
                        title="পরিশোধ এন্ট্রি মুছুন"
                        className="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <Modal
        open={showAdd}
        onClose={() => setShowAdd(false)}
        title="নতুন সরবরাহকারী যোগ করুন"
        maxWidthClass="max-w-sm"
      >
        <form onSubmit={handleAddSupplier} className="space-y-4">
          <input
            type="text"
            required
            placeholder="সরবরাহকারীর নাম *"
            value={supName}
            onChange={(e) => setSupName(e.target.value)}
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
          />
          <input
            type="text"
            placeholder="মোবাইল"
            value={supMobile}
            onChange={(e) => setSupMobile(e.target.value)}
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
          />
          <input
            type="text"
            placeholder="ঠিকানা"
            value={supAddress}
            onChange={(e) => setSupAddress(e.target.value)}
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
          />
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowAdd(false)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              সংরক্ষণ
            </button>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!payTarget}
        onClose={() => setPayTarget(null)}
        title="সরবরাহকারীকে পরিশোধ"
        subtitle={
          payTarget ? `${payTarget.name} · বকেয়া: ${formatMoney(payTarget.balance)}` : ''
        }
        maxWidthClass="max-w-sm"
      >
        <form onSubmit={handlePay} className="space-y-4">
          <input
            type="number"
            min={0.01}
            step="0.01"
            required
            value={payAmount}
            onChange={(e) => setPayAmount(e.target.value)}
            placeholder="পরিশোধের পরিমাণ"
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
          />
          <input
            type="text"
            value={payNote}
            onChange={(e) => setPayNote(e.target.value)}
            placeholder="মন্তব্য / রশিদ"
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
          />
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setPayTarget(null)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              পরিশোধ নিশ্চিত
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deletePayment}
        onClose={() => setDeletePayment(null)}
        onConfirm={() => {
          if (deletePayment && SupplierDueStorage.removePaymentById(deletePayment.id)) {
            ActivityLogStorage.add(
              'সরবরাহকারী পরিশোধ ডিলিট',
              `${deletePayment.supplier} · ${formatMoney(-deletePayment.amount)}`
            );
            notify('পরিশোধ এন্ট্রি মুছে ফেলা হয়েছে', 'success');
            onDataChange();
          }
        }}
        title="পরিশোধ এন্ট্রি মুছবেন?"
        message={
          deletePayment
            ? `${deletePayment.supplier}-কে করা ${formatMoney(-deletePayment.amount)} পরিশোধ মুছে ফেলা হলে দেনা পুনরায় সমন্বয় হবে।`
            : ''
        }
        confirmLabel="ডিলিট"
        danger
      />
    </div>
  );
};
