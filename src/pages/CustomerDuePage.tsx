import React, { useMemo, useState } from 'react';
import { HandCoins, Plus, Search, Trash2 } from 'lucide-react';
import { DueEntry } from '../types';
import { CustomerDueStorage } from '../storage/dueStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { formatMoney } from '../utils/format';
import { ConfirmDialog, Modal } from '../components/Modal';

interface CustomerDuePageProps {
  revision: number;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const CustomerDuePage: React.FC<CustomerDuePageProps> = ({ revision, onDataChange, notify }) => {
  const canWrite = SecurityStorage.canWrite();
  const [query, setQuery] = useState('');
  const [showAddCustomer, setShowAddCustomer] = useState(false);
  const [collectTarget, setCollectTarget] = useState<{ name: string; mobile: string; balance: number } | null>(null);
  const [deletePayment, setDeletePayment] = useState<DueEntry | null>(null);

  const [newName, setNewName] = useState('');
  const [newMobile, setNewMobile] = useState('');
  const [payAmount, setPayAmount] = useState('');
  const [payNote, setPayNote] = useState('');

  const customers = useMemo(() => CustomerDueStorage.getCustomers(), [revision]);
  const entries = useMemo(() => CustomerDueStorage.getEntries(), [revision]);

  const customerRows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return customers
      .map((c) => ({
        ...c,
        balance: CustomerDueStorage.getBalance(c.name, c.mobile),
      }))
      .filter(
        (c) => !q || c.name.toLowerCase().includes(q) || c.mobile.toLowerCase().includes(q)
      )
      .sort((a, b) => b.balance - a.balance);
  }, [customers, entries, query]);

  const totalDue = customerRows.reduce((s, c) => s + c.balance, 0);

  const handleAddCustomer = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    if (!newName.trim()) return notify('ক্রেতার নাম দিন', 'error');
    if (!CustomerDueStorage.addCustomer(newName.trim(), newMobile.trim())) {
      return notify('ক্রেতা ইতিমধ্যে তালিকায় আছেন', 'error');
    }
    ActivityLogStorage.add('নতুন ক্রেতা', `${newName.trim()} (${newMobile.trim()})`);
    notify('নতুন ক্রেতা যোগ করা হয়েছে', 'success');
    setNewName('');
    setNewMobile('');
    setShowAddCustomer(false);
    onDataChange();
  };

  const handleCollect = (e: React.FormEvent) => {
    e.preventDefault();
    if (!collectTarget || !canWrite) return;
    const amt = parseFloat(payAmount);
    if (!Number.isFinite(amt) || amt <= 0) return notify('সঠিক জমার পরিমাণ দিন', 'error');
    if (amt > collectTarget.balance + 0.01) {
      return notify('বকেয়ার চেয়ে বেশি আদায় করা যাবে না', 'error');
    }
    const id = CustomerDueStorage.addPayment(
      collectTarget.name,
      collectTarget.mobile,
      amt,
      payNote.trim() || 'বাকি আদায়'
    );
    if (id <= 0) return notify('বাকি আদায় সংরক্ষণ ব্যর্থ হয়েছে', 'error');
    ActivityLogStorage.add(
      'ক্রেতার বাকি আদায়',
      `${collectTarget.name} · ${formatMoney(amt)}`
    );
    notify('বাকি আদায় রেকর্ড করা হয়েছে', 'success');
    setCollectTarget(null);
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
            placeholder="ক্রেতার নাম বা মোবাইল নম্বর খুঁজুন..."
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 pl-9 pr-3 py-2 text-sm"
          />
        </div>
        <div className="flex items-center gap-4">
          <div className="text-right">
            <div className="text-[11px] text-slate-500">মোট ক্রেতা পাওনা</div>
            <div className="text-base font-bold font-mono-num text-amber-600 dark:text-amber-400">
              {formatMoney(totalDue)}
            </div>
          </div>
          {canWrite && (
            <button
              type="button"
              onClick={() => setShowAddCustomer(true)}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-sm font-medium"
            >
              <Plus className="h-4 w-4" />
              <span>নতুন ক্রেতা</span>
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <div className="lg:col-span-7 rounded-xl surface-card overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 text-sm font-semibold">
            ক্রেতার বকেয়া তালিকা ({customerRows.length})
          </div>
          {customerRows.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-500">কোনো ক্রেতা পাওয়া যায়নি</div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {customerRows.map((c) => (
                <div key={c.id} className="flex items-center justify-between gap-3 px-4 py-3">
                  <div className="min-w-0">
                    <div className="font-medium text-slate-900 dark:text-slate-100">{c.name}</div>
                    <div className="text-xs text-slate-500 font-mono-num">
                      {c.mobile || 'মোবাইল নেই'}
                    </div>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    <span
                      className={`font-mono-num text-sm font-bold ${
                        c.balance > 0
                          ? 'text-amber-600 dark:text-amber-400'
                          : 'text-emerald-700 dark:text-emerald-400'
                      }`}
                    >
                      {formatMoney(c.balance)}
                    </span>
                    {canWrite && c.balance > 0 && (
                      <button
                        type="button"
                        onClick={() => {
                          setCollectTarget({ name: c.name, mobile: c.mobile, balance: c.balance });
                          setPayAmount(String(c.balance));
                          setPayNote('');
                        }}
                        className="whitespace-nowrap shrink-0 inline-flex items-center gap-1 rounded-lg btn-accent px-3 py-1.5 text-xs font-medium"
                      >
                        <HandCoins className="h-3.5 w-3.5" />
                        <span>আদায়</span>
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
            সাম্প্রতিক বাকি ও আদায় লেনদেন
          </div>
          {entries.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-500">কোনো লেনদেন নেই</div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800 max-h-[500px] overflow-y-auto">
              {entries.slice(0, 40).map((e) => (
                <div key={e.id} className="flex items-start justify-between gap-3 px-4 py-2.5 text-xs">
                  <div className="min-w-0">
                    <div className="font-medium text-slate-900 dark:text-slate-100">{e.customer}</div>
                    <div className="text-slate-500 truncate">{e.date} · {e.note || e.type}</div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <div
                      className={`font-mono-num font-semibold ${
                        e.type === 'SALE'
                          ? 'text-amber-600 dark:text-amber-400'
                          : 'text-emerald-700 dark:text-emerald-400'
                      }`}
                    >
                      {e.type === 'SALE' ? '+' : '-'} {formatMoney(e.amount)}
                    </div>
                    {canWrite && e.type === 'PAYMENT' && !e.note.trim().startsWith('বিক্রয় রিটার্ন:') && (
                      <button
                        type="button"
                        onClick={() => setDeletePayment(e)}
                        title="আদায় এন্ট্রি মুছুন"
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
        open={showAddCustomer}
        onClose={() => setShowAddCustomer(false)}
        title="নতুন ক্রেতা যোগ করুন"
        maxWidthClass="max-w-sm"
      >
        <form onSubmit={handleAddCustomer} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              ক্রেতার নাম *
            </label>
            <input
              type="text"
              required
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              মোবাইল নম্বর
            </label>
            <input
              type="text"
              value={newMobile}
              onChange={(e) => setNewMobile(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowAddCustomer(false)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              যোগ করুন
            </button>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!collectTarget}
        onClose={() => setCollectTarget(null)}
        title="বাকি টাকা আদায়"
        subtitle={
          collectTarget
            ? `${collectTarget.name} · বকেয়া: ${formatMoney(collectTarget.balance)}`
            : ''
        }
        maxWidthClass="max-w-sm"
      >
        <form onSubmit={handleCollect} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              আদায়ের পরিমাণ (৳) *
            </label>
            <input
              type="number"
              min={0.01}
              step="0.01"
              required
              value={payAmount}
              onChange={(e) => setPayAmount(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              নোট / রশিদ
            </label>
            <input
              type="text"
              value={payNote}
              onChange={(e) => setPayNote(e.target.value)}
              placeholder="নগদ বা বিকাশ আদায়"
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setCollectTarget(null)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              আদায় নিশ্চিত করুন
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deletePayment}
        onClose={() => setDeletePayment(null)}
        onConfirm={() => {
          if (deletePayment && CustomerDueStorage.removePaymentById(deletePayment.id)) {
            ActivityLogStorage.add(
              'ক্রেতার আদায় ডিলিট',
              `${deletePayment.customer} · ${formatMoney(deletePayment.amount)}`
            );
            notify('আদায় এন্ট্রি মুছে ফেলা হয়েছে', 'success');
            onDataChange();
          }
        }}
        title="আদায় এন্ট্রি মুছবেন?"
        message={
          deletePayment
            ? `${deletePayment.customer}-এর ${formatMoney(deletePayment.amount)} আদায় রেকর্ড মুছে ফেলা হলে বকেয়া পুনরায় সমন্বয় হবে।`
            : ''
        }
        confirmLabel="ডিলিট"
        danger
      />
    </div>
  );
};
