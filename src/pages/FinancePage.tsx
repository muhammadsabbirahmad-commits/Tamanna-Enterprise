import React, { useMemo, useState } from 'react';
import { AlertCircle, Edit2, Plus, Trash2, Wallet } from 'lucide-react';
import { Expense, NavRoute } from '../types';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { formatMoney, todayIso } from '../utils/format';
import { ExpenseStorage, PartnerStorage } from '../storage/financeStorage';
import { InventoryMetaStorage, ProductStorage } from '../storage/inventoryStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { ConfirmDialog, Modal, PinPromptModal } from '../components/Modal';

interface FinancePageProps {
  revision: number;
  onNavigate: (route: NavRoute) => void;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const FinancePage: React.FC<FinancePageProps> = ({
  revision,
  onNavigate,
  onDataChange,
  notify,
}) => {
  const canWrite = SecurityStorage.canWrite();
  const [tab, setTab] = useState<'expenses' | 'damages'>('expenses');
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: 'MONTH',
    fromDate: todayIso().slice(0, 8) + '01',
    toDate: todayIso(),
  });

  const [showExpenseForm, setShowExpenseForm] = useState(false);
  const [editingExpense, setEditingExpense] = useState<Expense | null>(null);
  const [expDate, setExpDate] = useState(todayIso());
  const [expCategory, setExpCategory] = useState('');
  const [expAmount, setExpAmount] = useState('');
  const [expNote, setExpNote] = useState('');

  const [deleteTarget, setDeleteTarget] = useState<Expense | null>(null);
  const [pinDeleteTarget, setPinDeleteTarget] = useState<Expense | null>(null);

  const [showDamageForm, setShowDamageForm] = useState(false);
  const [dmgDate, setDmgDate] = useState(todayIso());
  const [dmgCode, setDmgCode] = useState('');
  const [dmgQty, setDmgQty] = useState('1');
  const [dmgReason, setDmgReason] = useState('');

  const partners = useMemo(() => PartnerStorage.getPartners(), [revision]);
  const totalRate = partners.reduce((s, p) => s + p.profitPercentage, 0);
  const partnerRateReady = partners.length > 0 && Math.abs(totalRate - 100) <= 0.0001;

  const expenses = useMemo(() => {
    return ExpenseStorage.getExpenses()
      .filter((e) => e.date >= filter.fromDate && e.date <= filter.toDate)
      .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
  }, [filter, revision]);

  const allTimeExpenseTotal = useMemo(
    () => ExpenseStorage.getExpenses().reduce((s, e) => s + e.amount, 0),
    [revision]
  );

  const damages = useMemo(() => {
    return ExpenseStorage.getDamages()
      .filter((d) => d.date >= filter.fromDate && d.date <= filter.toDate)
      .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
  }, [filter, revision]);

  const products = useMemo(() => ProductStorage.getProducts(), [revision]);

  const periodExpenseTotal = expenses.reduce((s, e) => s + e.amount, 0);
  const periodDamageTotal = damages.reduce((s, d) => s + d.quantity * d.unitCost, 0);

  const openAddExpense = () => {
    setEditingExpense(null);
    setExpDate(todayIso());
    setExpCategory('');
    setExpAmount('');
    setExpNote('');
    setShowExpenseForm(true);
  };

  const openEditExpense = (exp: Expense) => {
    setEditingExpense(exp);
    setExpDate(exp.date);
    setExpCategory(exp.category);
    setExpAmount(String(exp.amount));
    setExpNote(exp.note);
    setShowExpenseForm(true);
  };

  const handleSaveExpense = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    const amt = parseFloat(expAmount);
    if (!expCategory.trim() || !Number.isFinite(amt) || amt <= 0) {
      return notify('খরচের খাত ও সঠিক পরিমাণ লিখুন', 'error');
    }

    if (editingExpense) {
      const ok = ExpenseStorage.updateExpense({
        ...editingExpense,
        date: expDate,
        category: expCategory.trim(),
        amount: amt,
        note: expNote.trim(),
      });
      if (!ok) {
        return notify(
          'খরচ আপডেট ব্যর্থ: নির্বাচিত তারিখে পার্টনার লভ্যাংশ বণ্টন ১০০% নিশ্চিত করুন',
          'error'
        );
      }
      ActivityLogStorage.add('খরচ আপডেট', `${expCategory.trim()} · ${formatMoney(amt)}`);
      notify('খরচ আপডেট করা হয়েছে', 'success');
    } else {
      const ok = ExpenseStorage.addExpense({
        id: Date.now(),
        date: expDate,
        category: expCategory.trim(),
        amount: amt,
        note: expNote.trim(),
      });
      if (!ok) {
        return notify(
          'খরচ যোগ ব্যর্থ: কমপক্ষে ১ জন পার্টনার ও নির্বাচিত তারিখে ১০০% বণ্টন হার প্রয়োজন',
          'error'
        );
      }
      ActivityLogStorage.add('নতুন খরচ', `${expCategory.trim()} · ${formatMoney(amt)}`);
      notify('খরচ সংরক্ষণ করা হয়েছে', 'success');
    }
    setShowExpenseForm(false);
    onDataChange();
  };

  const executeDeleteExpense = (exp: Expense) => {
    if (ExpenseStorage.deleteExpense(exp.id)) {
      ActivityLogStorage.add('খরচ ডিলিট', `${exp.category} · ${formatMoney(exp.amount)}`);
      notify('খরচ মুছে ফেলা হয়েছে', 'success');
      onDataChange();
    }
  };

  const requestDeleteExpense = (exp: Expense) => {
    if (!canWrite) return;
    if (SecurityStorage.isAdminPinSet() && !SecurityStorage.isPinFreeEntryEnabled()) {
      setPinDeleteTarget(exp);
    } else {
      setDeleteTarget(exp);
    }
  };

  const handleSaveDamage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    const prod = products.find((p) => p.code === dmgCode);
    const qty = parseInt(dmgQty || '0', 10);
    if (!prod || qty <= 0) return notify('পণ্য ও সঠিক পরিমাণ দিন', 'error');
    if (prod.stockQuantity < qty) return notify('বর্তমান স্টকের চেয়ে বেশি ড্যামেজ হতে পারবে না', 'error');

    const ok = ExpenseStorage.addDamage({
      id: Date.now(),
      date: dmgDate,
      productCode: prod.code,
      productName: prod.name,
      quantity: qty,
      unitCost: prod.purchasePrice,
      reason: dmgReason.trim() || 'নষ্ট/মেয়াদোত্তীর্ণ',
    });
    if (!ok) {
      return notify(
        'ড্যামেজ রেকর্ড ব্যর্থ: পার্টনারদের লভ্যাংশ বণ্টন ১০০% নির্ধারণ করুন',
        'error'
      );
    }

    const before = prod.stockQuantity;
    const after = before - qty;
    ProductStorage.updateStock(prod.code, after);
    const meta = InventoryMetaStorage.getMeta(prod.code);
    InventoryMetaStorage.saveMeta({ ...meta, damagedQuantity: meta.damagedQuantity + qty });
    InventoryMetaStorage.addHistory(
      prod.code,
      prod.name,
      'ড্যামেজ/নষ্ট',
      qty,
      before,
      after,
      dmgReason.trim()
    );
    ActivityLogStorage.add('ড্যামেজ ক্ষতি', `${prod.name} × ${qty} (${formatMoney(qty * prod.purchasePrice)})`);
    notify('ড্যামেজ রেকর্ড ও স্টক সমন্বয় সম্পন্ন হয়েছে', 'success');
    setShowDamageForm(false);
    onDataChange();
  };

  return (
    <div className="space-y-4">
      {!partnerRateReady && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50/70 dark:bg-amber-950/30 p-3.5 text-sm text-amber-900 dark:text-amber-200">
          <div className="flex items-start gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
            <span>
              খরচ ও ড্যামেজ ক্ষতি পার্টনারদের মাঝে বণ্টনের জন্য পার্টনার ও মোট ১০০% লভ্যাংশ বণ্টন হার নির্ধারণ করা প্রয়োজন।
            </span>
          </div>
          <button
            type="button"
            onClick={() => onNavigate('partners')}
            className="whitespace-nowrap shrink-0 rounded-lg border border-amber-300 dark:border-amber-700 px-3 py-1.5 text-xs font-semibold hover:bg-amber-100/70 dark:hover:bg-amber-900/40"
          >
            পার্টনার সেটআপ করুন
          </button>
        </div>
      )}

      <DateFilterBar preset={filter.preset} fromDate={filter.fromDate} toDate={filter.toDate} onChange={setFilter} />

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl surface-card p-2.5">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setTab('expenses')}
            className={`rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
              tab === 'expenses'
                ? 'btn-accent'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            দৈনন্দিন খরচ ({expenses.length})
          </button>
          <button
            type="button"
            onClick={() => setTab('damages')}
            className={`rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
              tab === 'damages'
                ? 'btn-accent'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            ড্যামেজ / নষ্ট ক্ষতি ({damages.length})
          </button>
        </div>

        {canWrite && (
          <button
            type="button"
            onClick={() => {
              if (tab === 'expenses') openAddExpense();
              else {
                setDmgDate(todayIso());
                setDmgCode(products[0]?.code || '');
                setDmgQty('1');
                setDmgReason('');
                setShowDamageForm(true);
              }
            }}
            className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-sm font-medium"
          >
            <Plus className="h-4 w-4" />
            <span>{tab === 'expenses' ? 'নতুন খরচ' : 'ড্যামেজ রেকর্ড'}</span>
          </button>
        )}
      </div>

      {/* Totals */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500">নির্বাচিত সময়ের খরচ</div>
          <div className="mt-1 text-lg font-bold font-mono-num">{formatMoney(periodExpenseTotal)}</div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500">সর্বমোট (All-Time) খরচ</div>
          <div className="mt-1 text-lg font-bold font-mono-num">{formatMoney(allTimeExpenseTotal)}</div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500">নির্বাচিত সময়ের ড্যামেজ ক্ষতি</div>
          <div className="mt-1 text-lg font-bold font-mono-num text-red-600 dark:text-red-400">
            {formatMoney(periodDamageTotal)}
          </div>
        </div>
      </div>

      {tab === 'expenses' ? (
        <div className="rounded-xl surface-card overflow-hidden">
          {expenses.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-500">
              <Wallet className="h-8 w-8 mx-auto text-slate-300 mb-2" />
              এই সময়কালে কোনো খরচ রেকর্ড নেই
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                  <tr>
                    <th className="px-4 py-3">তারিখ</th>
                    <th className="px-4 py-3">খাত ও নোট</th>
                    <th className="px-4 py-3">পার্টনার বণ্টন</th>
                    <th className="px-4 py-3 text-right">পরিমাণ</th>
                    {canWrite && <th className="px-4 py-3 text-right">অ্যাকশন</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {expenses.map((exp) => (
                    <tr key={exp.id} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                      <td className="px-4 py-3 text-xs font-mono-num text-slate-500">{exp.date}</td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-slate-900 dark:text-slate-100">{exp.category}</div>
                        {exp.note && <div className="text-xs text-slate-500">{exp.note}</div>}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-500">
                        {exp.allocations
                          .map((a) => `${a.partnerName}: ${formatMoney(a.amount)} (${a.percentage}%)`)
                          .join(' · ')}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num font-semibold">
                        {formatMoney(exp.amount)}
                      </td>
                      {canWrite && (
                        <td className="px-4 py-3 text-right">
                          <div className="inline-flex gap-1">
                            <button
                              type="button"
                              onClick={() => openEditExpense(exp)}
                              className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
                            >
                              <Edit2 className="h-4 w-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => requestDeleteExpense(exp)}
                              className="rounded-lg p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        <div className="rounded-xl surface-card overflow-hidden">
          {damages.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-500">কোনো ড্যামেজ রেকর্ড নেই</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                  <tr>
                    <th className="px-4 py-3">তারিখ</th>
                    <th className="px-4 py-3">পণ্য ও কারণ</th>
                    <th className="px-4 py-3 text-right">পরিমাণ × ক্রয়মূল্য</th>
                    <th className="px-4 py-3 text-right">মোট ক্ষতি</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {damages.map((d) => (
                    <tr key={d.id}>
                      <td className="px-4 py-3 text-xs font-mono-num text-slate-500">{d.date}</td>
                      <td className="px-4 py-3">
                        <div className="font-medium">{d.productName}</div>
                        <div className="text-xs text-slate-500">{d.reason}</div>
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num text-xs">
                        {d.quantity} × {formatMoney(d.unitCost)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono-num font-semibold text-red-600 dark:text-red-400">
                        {formatMoney(d.quantity * d.unitCost)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Expense Modal */}
      <Modal
        open={showExpenseForm}
        onClose={() => setShowExpenseForm(false)}
        title={editingExpense ? 'খরচ সম্পাদনা' : 'নতুন খরচ যোগ করুন'}
        maxWidthClass="max-w-md"
      >
        <form onSubmit={handleSaveExpense} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                তারিখ
              </label>
              <input
                type="date"
                required
                value={expDate}
                onChange={(e) => setExpDate(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                টাকা (৳) *
              </label>
              <input
                type="number"
                min={0.01}
                step="0.01"
                required
                value={expAmount}
                onChange={(e) => setExpAmount(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              খরচের খাত *
            </label>
            <input
              type="text"
              required
              value={expCategory}
              onChange={(e) => setExpCategory(e.target.value)}
              placeholder="যেমন: দোকান ভাড়া, বিদ্যুৎ বিল, পরিবহন"
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              মন্তব্য
            </label>
            <input
              type="text"
              value={expNote}
              onChange={(e) => setExpNote(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowExpenseForm(false)}
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

      {/* Damage Modal */}
      <Modal
        open={showDamageForm}
        onClose={() => setShowDamageForm(false)}
        title="ড্যামেজ / নষ্ট পণ্য রেকর্ড"
        maxWidthClass="max-w-md"
      >
        <form onSubmit={handleSaveDamage} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                তারিখ
              </label>
              <input
                type="date"
                required
                value={dmgDate}
                onChange={(e) => setDmgDate(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                পরিমাণ *
              </label>
              <input
                type="number"
                min={1}
                required
                value={dmgQty}
                onChange={(e) => setDmgQty(e.target.value)}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              পণ্য *
            </label>
            <select
              value={dmgCode}
              onChange={(e) => setDmgCode(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            >
              {products.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name} (স্টক: {p.stockQuantity})
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
              কারণ
            </label>
            <input
              type="text"
              value={dmgReason}
              onChange={(e) => setDmgReason(e.target.value)}
              placeholder="মেয়াদোত্তীর্ণ বা নষ্ট"
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowDamageForm(false)}
              className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm"
            >
              বাতিল
            </button>
            <button type="submit" className="rounded-lg bg-red-600 hover:bg-red-700 text-white px-4 py-2 text-sm font-medium">
              ক্ষতি রেকর্ড করুন
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && executeDeleteExpense(deleteTarget)}
        title="খরচ মুছে ফেলবেন?"
        message={deleteTarget ? `${deleteTarget.category} (${formatMoney(deleteTarget.amount)}) মুছে ফেলা হবে।` : ''}
        confirmLabel="ডিলিট"
        danger
      />

      <PinPromptModal
        open={!!pinDeleteTarget}
        onClose={() => setPinDeleteTarget(null)}
        onVerified={() => pinDeleteTarget && executeDeleteExpense(pinDeleteTarget)}
        verifyPin={(pin) => SecurityStorage.verifyAdminPin(pin)}
      />
    </div>
  );
};
