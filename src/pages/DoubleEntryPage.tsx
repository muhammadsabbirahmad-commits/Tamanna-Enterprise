import React, { useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, Plus, Trash2, Wallet } from 'lucide-react';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { buildLedger } from '../engine/ledgerBuilder';
import {
  CashAccountStorage,
  DailyBalanceStorage,
  OpeningBalanceStorage,
  PartnerStorage,
} from '../storage/financeStorage';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { formatMoney, monthStartIso, todayIso } from '../utils/format';
import { Modal } from '../components/Modal';

interface DoubleEntryPageProps {
  revision: number;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const DoubleEntryPage: React.FC<DoubleEntryPageProps> = ({ revision, onDataChange, notify }) => {
  const canWrite = SecurityStorage.canWrite();
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: 'MONTH',
    fromDate: monthStartIso(),
    toDate: todayIso(),
  });
  const [tab, setTab] = useState<'trial' | 'journal' | 'cash'>('trial');
  const [showOpeningModal, setShowOpeningModal] = useState(false);
  const [showDailyModal, setShowDailyModal] = useState(false);
  const [showAccountModal, setShowAccountModal] = useState(false);

  // Opening balance form
  const [obDate, setObDate] = useState(filter.fromDate);
  const [obCash, setObCash] = useState('0');
  const [obBank, setObBank] = useState('0');
  const [obStock, setObStock] = useState('0');
  const [obRec, setObRec] = useState('0');
  const [obPay, setObPay] = useState('0');
  const [obOther, setObOther] = useState('0');
  const [obVerified, setObVerified] = useState(true);

  // Daily balance form
  const [dbDate, setDbDate] = useState(todayIso());
  const [dbAccount, setDbAccount] = useState<'CASH' | 'BANK'>('CASH');
  const [dbMode, setDbMode] = useState<'SET' | 'ADD' | 'SUB'>('SET');
  const [dbAmount, setDbAmount] = useState('');
  const [dbNote, setDbNote] = useState('');

  // Manual cash account form
  const [accName, setAccName] = useState('');
  const [accBalance, setAccBalance] = useState('');

  const openOpeningModal = () => {
    const existing = OpeningBalanceStorage.get();
    setObDate(existing?.date || filter.fromDate);
    setObCash(String(existing?.cash ?? 0));
    setObBank(String(existing?.bank ?? 0));
    setObStock(String(existing?.stock ?? 0));
    setObRec(String(existing?.customerReceivable ?? 0));
    setObPay(String(existing?.supplierPayable ?? 0));
    setObOther(String(existing?.otherLiabilities ?? 0));
    setObVerified(existing?.verified ?? true);
    setShowOpeningModal(true);
  };

  const openDailyModal = () => {
    setDbDate(todayIso());
    setDbAccount('CASH');
    setDbMode('SET');
    setDbAmount('');
    setDbNote('');
    setShowDailyModal(true);
  };

  const ledger = useMemo(
    () => buildLedger(filter.fromDate, filter.toDate),
    [filter, revision]
  );

  const manualAccounts = useMemo(
    () => CashAccountStorage.getAccounts(),
    [revision]
  );
  const dailyEntries = useMemo(
    () => DailyBalanceStorage.get().filter((e) => e.date >= filter.fromDate && e.date <= filter.toDate),
    [filter, revision]
  );

  const capitalizedAlready = useMemo(
    () => PartnerStorage.isProfitPeriodCapitalized(filter.fromDate, filter.toDate),
    [filter, revision]
  );
  const remainingDistributable = Math.max(0, ledger.distributableProfit - ledger.withdrawalTotal);

  const opening = OpeningBalanceStorage.get();
  const allDaily = DailyBalanceStorage.get();
  const cashClosing = DailyBalanceStorage.balance(
    allDaily,
    'CASH',
    filter.toDate,
    opening?.cash,
    opening?.date
  );
  const bankClosing = DailyBalanceStorage.balance(
    allDaily,
    'BANK',
    filter.toDate,
    opening?.bank,
    opening?.date
  );

  const handleSaveOpening = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    const ok = OpeningBalanceStorage.save({
      date: obDate,
      cash: Math.max(0, parseFloat(obCash) || 0),
      bank: Math.max(0, parseFloat(obBank) || 0),
      stock: Math.max(0, parseFloat(obStock) || 0),
      customerReceivable: Math.max(0, parseFloat(obRec) || 0),
      supplierPayable: Math.max(0, parseFloat(obPay) || 0),
      otherLiabilities: Math.max(0, parseFloat(obOther) || 0),
      verified: obVerified,
      updatedAt: Date.now(),
    });
    if (!ok) return notify('প্রারম্ভিক ব্যালান্স সংরক্ষণ ব্যর্থ হয়েছে', 'error');
    ActivityLogStorage.add('প্রারম্ভিক ব্যালান্স', `তারিখ: ${obDate} · যাচাই: ${obVerified ? 'হ্যাঁ' : 'না'}`);
    notify('প্রারম্ভিক ব্যালান্স সংরক্ষিত হয়েছে', 'success');
    setShowOpeningModal(false);
    onDataChange();
  };

  const handleSaveDaily = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    const amt = parseFloat(dbAmount);
    if (!Number.isFinite(amt) || amt < 0) return notify('সঠিক পরিমাণ দিন', 'error');
    const ok = DailyBalanceStorage.save({
      id: 'db-' + Date.now(),
      date: dbDate,
      account: dbAccount,
      mode: dbMode,
      amount: amt,
      note: dbNote.trim(),
      updatedAt: Date.now(),
      deleted: false,
    });
    if (!ok) return notify('সংরক্ষণ ব্যর্থ', 'error');
    ActivityLogStorage.add('দৈনিক ক্যাশ/ব্যাংক', `${dbAccount} ${dbMode} ${formatMoney(amt)}`);
    notify('দৈনিক ব্যালান্স এন্ট্রি সংরক্ষিত হয়েছে', 'success');
    setShowDailyModal(false);
    onDataChange();
  };

  const handleSaveAccount = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    const bal = parseFloat(accBalance);
    if (!accName.trim() || !Number.isFinite(bal) || bal < 0) return notify('সঠিক তথ্য দিন', 'error');
    CashAccountStorage.saveAccount({
      id: 'acc-' + Date.now(),
      name: accName.trim(),
      balance: bal,
      updatedAt: Date.now(),
    });
    setAccName('');
    setAccBalance('');
    setShowAccountModal(false);
    notify('ম্যানুয়াল ক্যাশ অ্যাকাউন্ট যোগ হয়েছে', 'success');
    onDataChange();
  };

  const handleCapitalizeProfit = () => {
    if (!canWrite) return;
    if (remainingDistributable <= 0.004) return notify('মূলধনে যোগ করার মতো অবশিষ্ট লাভ নেই', 'info');
    const ok = PartnerStorage.capitalizeRemainingProfit(
      filter.fromDate,
      filter.toDate,
      remainingDistributable
    );
    if (!ok) {
      return notify('পার্টনার বণ্টন হার ১০০% এবং এই সময়কাল পূর্বে মূলধনে যোগ হয়নি তা নিশ্চিত করুন', 'error');
    }
    ActivityLogStorage.add(
      'অবণ্টিত লভ্যাংশ মূলধনে যোগ',
      `${filter.fromDate}..${filter.toDate} · ${formatMoney(remainingDistributable)}`
    );
    notify('অবণ্টিত লভ্যাংশ পার্টনার মূলধনে যোগ হয়েছে', 'success');
    onDataChange();
  };

  const tb = ledger.trialBalance;

  return (
    <div className="space-y-4">
      <DateFilterBar
        preset={filter.preset}
        fromDate={filter.fromDate}
        toDate={filter.toDate}
        onChange={setFilter}
        includeAll={false}
      />

      {/* Trial Balance Verification Banner */}
      <div
        className={`flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border p-4 ${
          tb.closingBalanced && tb.allEntriesBalanced
            ? 'border-emerald-200 dark:border-emerald-900/60 bg-emerald-50/70 dark:bg-emerald-950/30'
            : 'border-amber-200 dark:border-amber-900/60 bg-amber-50/70 dark:bg-amber-950/30'
        }`}
      >
        <div className="flex items-start gap-2.5">
          {tb.closingBalanced && tb.allEntriesBalanced ? (
            <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400 shrink-0 mt-0.5" />
          ) : (
            <AlertTriangle className="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
          )}
          <div>
            <div className="text-sm font-semibold">
              {tb.closingBalanced && tb.allEntriesBalanced
                ? 'ডাবল এন্ট্রি ও ট্রায়াল ব্যালান্স সম্পূর্ণ সুষম (Balanced)'
                : 'ট্রায়াল ব্যালান্স বা উৎস রেকর্ডে সতর্কতা/অমিল রয়েছে'}
            </div>
            <div className="mt-0.5 text-xs text-slate-600 dark:text-slate-300 font-mono-num">
              সমাপনী ডেবিট: {formatMoney(tb.closingDebit)} · সমাপনী ক্রেডিট: {formatMoney(tb.closingCredit)}
            </div>
          </div>
        </div>
        {canWrite && (
          <div className="flex flex-wrap items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={openOpeningModal}
              className="whitespace-nowrap rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-medium"
            >
              প্রারম্ভিক ব্যালান্স
            </button>
            <button
              type="button"
              onClick={openDailyModal}
              className="whitespace-nowrap rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-medium"
            >
              দৈনিক ক্যাশ/ব্যাংক
            </button>
            {!capitalizedAlready && remainingDistributable > 0.004 && (
              <button
                type="button"
                onClick={handleCapitalizeProfit}
                className="whitespace-nowrap rounded-lg btn-accent px-3 py-1.5 text-xs font-medium"
              >
                অবণ্টিত লাভ মূলধনে যোগ ({formatMoney(remainingDistributable)})
              </button>
            )}
          </div>
        )}
      </div>

      {/* KPI Strip */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500">মোট ডেবিট (সময়কাল)</div>
          <div className="mt-1 text-lg font-bold font-mono-num">{formatMoney(tb.periodDebit)}</div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500">মোট ক্রেডিট (সময়কাল)</div>
          <div className="mt-1 text-lg font-bold font-mono-num">{formatMoney(tb.periodCredit)}</div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500">নগদ ইন / আউট</div>
          <div className="mt-1 text-sm font-bold font-mono-num">
            +{formatMoney(ledger.cashIn)} / -{formatMoney(ledger.cashOut)}
          </div>
        </div>
        <div className="rounded-xl surface-card p-3.5">
          <div className="text-xs text-slate-500">নিট লাভ / লভ্যাংশ উত্তোলন</div>
          <div className="mt-1 text-sm font-bold font-mono-num">
            {formatMoney(ledger.netProfit)} / {formatMoney(ledger.withdrawalTotal)}
          </div>
        </div>
      </div>

      {/* Warnings */}
      {ledger.dataWarnings.length > 0 && (
        <div className="rounded-xl border border-amber-200 dark:border-amber-900/60 bg-white dark:bg-slate-900 p-4 space-y-1.5">
          <div className="text-xs font-semibold text-amber-700 dark:text-amber-400">
            হিসাব যাচাই সতর্কতা ({ledger.dataWarnings.length})
          </div>
          <ul className="list-disc pl-5 text-xs text-slate-600 dark:text-slate-300 space-y-1">
            {ledger.dataWarnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Sub Tabs */}
      <div className="flex items-center gap-2 rounded-xl surface-card p-2">
        <button
          type="button"
          onClick={() => setTab('trial')}
          className={`rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
            tab === 'trial' ? 'btn-accent' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          ট্রায়াল ব্যালান্স ({tb.accounts.length})
        </button>
        <button
          type="button"
          onClick={() => setTab('journal')}
          className={`rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
            tab === 'journal' ? 'btn-accent' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          জার্নাল লেজার ({ledger.rows.length})
        </button>
        <button
          type="button"
          onClick={() => setTab('cash')}
          className={`rounded-lg px-3.5 py-2 text-xs sm:text-sm font-medium ${
            tab === 'cash' ? 'btn-accent' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          ক্যাশ ও ব্যাংক অ্যাকাউন্ট
        </button>
      </div>

      {tab === 'trial' && (
        <div className="rounded-xl surface-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">অ্যাকাউন্ট</th>
                  <th className="px-3 py-3 text-right">প্রারম্ভিক Dr</th>
                  <th className="px-3 py-3 text-right">প্রারম্ভিক Cr</th>
                  <th className="px-3 py-3 text-right">সময়কাল Dr</th>
                  <th className="px-3 py-3 text-right">সময়কাল Cr</th>
                  <th className="px-3 py-3 text-right">সমাপনী Dr</th>
                  <th className="px-3 py-3 text-right">সমাপনী Cr</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-mono-num">
                {tb.accounts.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-10 text-center font-sans text-slate-500">
                      এই সময়কালে কোনো অ্যাকাউন্ট লেনদেন নেই
                    </td>
                  </tr>
                ) : (
                  tb.accounts.map((a) => (
                    <tr key={a.account} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                      <td className="px-4 py-2.5 font-sans font-medium text-slate-900 dark:text-slate-100">
                        {a.account}
                      </td>
                      <td className="px-3 py-2.5 text-right">{formatMoney(a.openingDebit)}</td>
                      <td className="px-3 py-2.5 text-right">{formatMoney(a.openingCredit)}</td>
                      <td className="px-3 py-2.5 text-right">{formatMoney(a.periodDebit)}</td>
                      <td className="px-3 py-2.5 text-right">{formatMoney(a.periodCredit)}</td>
                      <td className="px-3 py-2.5 text-right font-semibold">{formatMoney(a.closingDebit)}</td>
                      <td className="px-3 py-2.5 text-right font-semibold">{formatMoney(a.closingCredit)}</td>
                    </tr>
                  ))
                )}
              </tbody>
              {tb.accounts.length > 0 && (
                <tfoot className="border-t-2 border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60 font-mono-num font-bold">
                  <tr>
                    <td className="px-4 py-3 font-sans">সর্বমোট</td>
                    <td className="px-3 py-3 text-right">{formatMoney(tb.openingDebit)}</td>
                    <td className="px-3 py-3 text-right">{formatMoney(tb.openingCredit)}</td>
                    <td className="px-3 py-3 text-right">{formatMoney(tb.periodDebit)}</td>
                    <td className="px-3 py-3 text-right">{formatMoney(tb.periodCredit)}</td>
                    <td className="px-3 py-3 text-right">{formatMoney(tb.closingDebit)}</td>
                    <td className="px-3 py-3 text-right">{formatMoney(tb.closingCredit)}</td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        </div>
      )}

      {tab === 'journal' && (
        <div className="rounded-xl surface-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs font-semibold text-slate-600 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">তারিখ</th>
                  <th className="px-4 py-3">বিবরণ ও নোট</th>
                  <th className="px-4 py-3">ডেবিট খাত</th>
                  <th className="px-4 py-3">ক্রেডিট খাত</th>
                  <th className="px-4 py-3 text-right">ডেবিট</th>
                  <th className="px-4 py-3 text-right">ক্রেডিট</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {ledger.rows.map((r, i) => (
                  <tr key={i} className="hover:bg-slate-50/70 dark:hover:bg-slate-800/40">
                    <td className="px-4 py-2.5 font-mono-num text-xs text-slate-500">{r.date}</td>
                    <td className="px-4 py-2.5">
                      <div className="font-medium text-slate-900 dark:text-slate-100">{r.description}</div>
                      {r.note && <div className="text-xs text-slate-500">{r.note}</div>}
                    </td>
                    <td className="px-4 py-2.5 text-xs">{r.debitAccount || '—'}</td>
                    <td className="px-4 py-2.5 text-xs">{r.creditAccount || '—'}</td>
                    <td className="px-4 py-2.5 text-right font-mono-num">{formatMoney(r.debitAmount)}</td>
                    <td className="px-4 py-2.5 text-right font-mono-num">{formatMoney(r.creditAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'cash' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="rounded-xl surface-card p-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">দৈনিক ক্যাশ ও ব্যাংক ব্যালান্স</h3>
              {canWrite && (
                <button
                  type="button"
                  onClick={openDailyModal}
                  className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"
                >
                  <Plus className="h-3.5 w-3.5" /> এন্ট্রি যোগ
                </button>
              )}
            </div>
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="rounded-lg border border-slate-200 dark:border-slate-800 p-3">
                <div className="text-slate-500">ক্যাশ সমাপনী ({filter.toDate})</div>
                <div className="mt-1 text-base font-bold font-mono-num">{formatMoney(cashClosing)}</div>
              </div>
              <div className="rounded-lg border border-slate-200 dark:border-slate-800 p-3">
                <div className="text-slate-500">ব্যাংক সমাপনী ({filter.toDate})</div>
                <div className="mt-1 text-base font-bold font-mono-num">{formatMoney(bankClosing)}</div>
              </div>
            </div>
            <div className="divide-y divide-slate-100 dark:divide-slate-800 text-xs">
              {dailyEntries.map((d) => (
                <div key={d.id} className="flex items-center justify-between py-2">
                  <div>
                    <span className="font-mono-num font-medium">{d.date}</span> · {d.account} ({d.mode})
                    {d.note && <span className="text-slate-500"> · {d.note}</span>}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono-num font-semibold">{formatMoney(d.amount)}</span>
                    {canWrite && (
                      <button
                        type="button"
                        onClick={() => {
                          DailyBalanceStorage.delete(d.id);
                          onDataChange();
                        }}
                        className="text-slate-400 hover:text-red-600"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-xl surface-card p-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">ম্যানুয়াল ক্যাশ অ্যাকাউন্ট</h3>
              {canWrite && (
                <button
                  type="button"
                  onClick={() => setShowAccountModal(true)}
                  className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"
                >
                  <Wallet className="h-3.5 w-3.5" /> অ্যাকাউন্ট যোগ
                </button>
              )}
            </div>
            {manualAccounts.length === 0 ? (
              <p className="text-xs text-slate-500 py-6 text-center">কোনো ম্যানুয়াল অ্যাকাউন্ট নেই</p>
            ) : (
              <div className="divide-y divide-slate-100 dark:divide-slate-800 text-sm">
                {manualAccounts.map((acc) => (
                  <div key={acc.id} className="flex items-center justify-between py-2.5">
                    <span>{acc.name}</span>
                    <div className="flex items-center gap-2">
                      <span className="font-mono-num font-semibold">{formatMoney(acc.balance)}</span>
                      {canWrite && (
                        <button
                          type="button"
                          onClick={() => {
                            CashAccountStorage.deleteAccount(acc.id);
                            onDataChange();
                          }}
                          className="text-slate-400 hover:text-red-600"
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
      )}

      {/* Opening Balances Modal */}
      <Modal
        open={showOpeningModal}
        onClose={() => setShowOpeningModal(false)}
        title="প্রারম্ভিক ব্যালান্স (Opening Balances)"
        subtitle="রিপোর্টের শুরুর তারিখের সাথে মিল রেখে প্রারম্ভিক সম্পদ ও দেনা লিখুন"
      >
        <form onSubmit={handleSaveOpening} className="space-y-3">
          <div>
            <label className="block text-xs font-medium mb-1">প্রারম্ভিক তারিখ</label>
            <input
              type="date"
              required
              value={obDate}
              onChange={(e) => setObDate(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3 text-xs">
            <div>
              <label className="block font-medium mb-1">নগদ (৳)</label>
              <input type="number" min={0} step="0.01" value={obCash} onChange={(e) => setObCash(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 font-mono-num" />
            </div>
            <div>
              <label className="block font-medium mb-1">ব্যাংক (৳)</label>
              <input type="number" min={0} step="0.01" value={obBank} onChange={(e) => setObBank(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 font-mono-num" />
            </div>
            <div>
              <label className="block font-medium mb-1">প্রারম্ভিক স্টক (৳)</label>
              <input type="number" min={0} step="0.01" value={obStock} onChange={(e) => setObStock(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 font-mono-num" />
            </div>
            <div>
              <label className="block font-medium mb-1">ক্রেতার পাওনা (৳)</label>
              <input type="number" min={0} step="0.01" value={obRec} onChange={(e) => setObRec(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 font-mono-num" />
            </div>
            <div>
              <label className="block font-medium mb-1">সরবরাহকারীর দেনা (৳)</label>
              <input type="number" min={0} step="0.01" value={obPay} onChange={(e) => setObPay(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 font-mono-num" />
            </div>
            <div>
              <label className="block font-medium mb-1">অন্যান্য দেনা (৳)</label>
              <input type="number" min={0} step="0.01" value={obOther} onChange={(e) => setObOther(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 font-mono-num" />
            </div>
          </div>
          <label className="flex items-center gap-2 text-xs">
            <input
              type="checkbox"
              checked={obVerified}
              onChange={(e) => setObVerified(e.target.checked)}
              className="rounded"
            />
            <span>যাচাইকৃত হিসেবে ট্রায়াল ব্যালান্সে পোস্ট করুন</span>
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setShowOpeningModal(false)} className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm">
              বাতিল
            </button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">
              সংরক্ষণ
            </button>
          </div>
        </form>
      </Modal>

      {/* Daily Balance Modal */}
      <Modal open={showDailyModal} onClose={() => setShowDailyModal(false)} title="দৈনিক ক্যাশ / ব্যাংক এন্ট্রি" maxWidthClass="max-w-sm">
        <form onSubmit={handleSaveDaily} className="space-y-3">
          <input type="date" required value={dbDate} onChange={(e) => setDbDate(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <div className="grid grid-cols-2 gap-2">
            <select value={dbAccount} onChange={(e) => setDbAccount(e.target.value as 'CASH' | 'BANK')} className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-2 text-sm">
              <option value="CASH">নগদ (CASH)</option>
              <option value="BANK">ব্যাংক (BANK)</option>
            </select>
            <select value={dbMode} onChange={(e) => setDbMode(e.target.value as 'SET' | 'ADD' | 'SUB')} className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-2 text-sm">
              <option value="SET">SET (নির্ধারণ)</option>
              <option value="ADD">ADD (যোগ)</option>
              <option value="SUB">SUB (বিয়োগ)</option>
            </select>
          </div>
          <input type="number" min={0} step="0.01" required placeholder="টাকার পরিমাণ" value={dbAmount} onChange={(e) => setDbAmount(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num" />
          <input type="text" placeholder="নোট" value={dbNote} onChange={(e) => setDbNote(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setShowDailyModal(false)} className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm">বাতিল</button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">সংরক্ষণ</button>
          </div>
        </form>
      </Modal>

      {/* Manual Account Modal */}
      <Modal open={showAccountModal} onClose={() => setShowAccountModal(false)} title="ম্যানুয়াল ক্যাশ/ব্যাংক অ্যাকাউন্ট" maxWidthClass="max-w-sm">
        <form onSubmit={handleSaveAccount} className="space-y-3">
          <input type="text" required placeholder="অ্যাকাউন্টের নাম (যেমন: বিকাশ মার্চেন্ট)" value={accName} onChange={(e) => setAccName(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <input type="number" min={0} step="0.01" required placeholder="বর্তমান ব্যালান্স" value={accBalance} onChange={(e) => setAccBalance(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num" />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setShowAccountModal(false)} className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm">বাতিল</button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">যোগ করুন</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
