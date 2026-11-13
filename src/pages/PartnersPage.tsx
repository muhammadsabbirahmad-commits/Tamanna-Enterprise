import React, { useMemo, useState } from 'react';
import { Banknote, Percent, Plus, Trash2, UserPlus } from 'lucide-react';
import { Partner } from '../types';
import { DateFilterBar, DatePreset } from '../components/DateFilterBar';
import { ExpenseStorage, PartnerStorage } from '../storage/financeStorage';
import { BusinessProfitCalculator, allocatePartnerProfitByDay } from '../engine/profitCalculator';
import { ActivityLogStorage, SecurityStorage } from '../storage/businessStorage';
import { formatMoney, monthStartIso, todayIso } from '../utils/format';
import { ConfirmDialog, Modal } from '../components/Modal';

interface PartnersPageProps {
  revision: number;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const PartnersPage: React.FC<PartnersPageProps> = ({ revision, onDataChange, notify }) => {
  const canWrite = SecurityStorage.canWrite();
  const [filter, setFilter] = useState<{ preset: DatePreset; fromDate: string; toDate: string }>({
    preset: 'MONTH',
    fromDate: monthStartIso(),
    toDate: todayIso(),
  });

  const [showAdd, setShowAdd] = useState(false);
  const [pName, setPName] = useState('');
  const [pCapital, setPCapital] = useState('');
  const [pNote, setPNote] = useState('');

  const [showRates, setShowRates] = useState(false);
  const [rateDraft, setRateDraft] = useState<Record<number, string>>({});
  const [rateDate, setRateDate] = useState(todayIso());

  const [capTarget, setCapTarget] = useState<Partner | null>(null);
  const [capAmount, setCapAmount] = useState('');
  const [capDate, setCapDate] = useState(todayIso());
  const [capNote, setCapNote] = useState('');

  const [wdTarget, setWdTarget] = useState<Partner | null>(null);
  const [wdAmount, setWdAmount] = useState('');
  const [wdDate, setWdDate] = useState(todayIso());
  const [wdNote, setWdNote] = useState('');

  const [deleteTarget, setDeleteTarget] = useState<Partner | null>(null);

  const partners = useMemo(() => PartnerStorage.getPartners(), [revision]);
  const withdrawals = useMemo(() => PartnerStorage.getWithdrawals(), [revision]);

  const periodReport = useMemo(
    () => BusinessProfitCalculator.calculate(filter.fromDate, filter.toDate),
    [filter, revision]
  );
  const periodAllocation = useMemo(
    () => allocatePartnerProfitByDay(partners, periodReport.partnerDistributionByDay),
    [partners, periodReport]
  );

  const totalCapital = partners.reduce((s, p) => s + p.shareAmount, 0);
  const totalRate = partners.reduce((s, p) => s + p.profitPercentage, 0);

  const handleAddPartner = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    const cap = Math.max(0, parseFloat(pCapital || '0') || 0);
    if (!pName.trim()) return notify('পার্টনারের নাম লিখুন', 'error');
    const ok = PartnerStorage.addPartner({
      id: Date.now(),
      name: pName.trim(),
      shareAmount: cap,
      profitPercentage: 0,
      note: pNote.trim(),
    });
    if (!ok) return notify('পার্টনার যোগ ব্যর্থ হয়েছে', 'error');
    ActivityLogStorage.add('নতুন পার্টনার', `${pName.trim()} · মূলধন ${formatMoney(cap)}`);
    notify('নতুন পার্টনার যোগ করা হয়েছে। এখন ১০০% লভ্যাংশ বণ্টন নির্ধারণ করুন।', 'success');
    setPName('');
    setPCapital('');
    setPNote('');
    setShowAdd(false);
    onDataChange();
  };

  const openRateEditor = () => {
    const draft: Record<number, string> = {};
    partners.forEach((p) => {
      draft[p.id] = String(p.profitPercentage);
    });
    setRateDraft(draft);
    setRateDate(todayIso());
    setShowRates(true);
  };

  const handleSaveRates = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWrite) return;
    const map: Record<number, number> = {};
    for (const p of partners) {
      map[p.id] = parseFloat(rateDraft[p.id] || '0') || 0;
    }
    const ok = PartnerStorage.saveProfitDistribution(map, rateDate);
    if (!ok) {
      return notify('সব পার্টনারের শতকরা হারের যোগফল অবশ্যই ১০০% এবং তারিখ আজ বা পরের হতে হবে', 'error');
    }
    ActivityLogStorage.add('লভ্যাংশ হার আপডেট', `কার্যকর তারিখ: ${rateDate}`);
    notify('লভ্যাংশ বণ্টন হার সংরক্ষিত হয়েছে', 'success');
    setShowRates(false);
    onDataChange();
  };

  const handleAddCapital = (e: React.FormEvent) => {
    e.preventDefault();
    if (!capTarget || !canWrite) return;
    const amt = parseFloat(capAmount);
    if (!Number.isFinite(amt) || amt <= 0) return notify('সঠিক মূলধন পরিমাণ দিন', 'error');
    if (!PartnerStorage.addCapitalContribution(capTarget.id, amt, capDate, capNote)) {
      return notify('মূলধন যোগ ব্যর্থ হয়েছে', 'error');
    }
    ActivityLogStorage.add('মূলধন জমা', `${capTarget.name} · ${formatMoney(amt)}`);
    notify('মূলধন জমা সংরক্ষিত হয়েছে', 'success');
    setCapTarget(null);
    onDataChange();
  };

  const handleAddWithdrawal = (e: React.FormEvent) => {
    e.preventDefault();
    if (!wdTarget || !canWrite) return;
    const amt = parseFloat(wdAmount);
    if (!Number.isFinite(amt) || amt <= 0) return notify('সঠিক উত্তোলন পরিমাণ দিন', 'error');
    if (!PartnerStorage.addWithdrawal(wdTarget.id, amt, wdDate, wdNote)) {
      return notify('লভ্যাংশ উত্তোলন ব্যর্থ হয়েছে', 'error');
    }
    ActivityLogStorage.add('লভ্যাংশ উত্তোলন', `${wdTarget.name} · ${formatMoney(amt)}`);
    notify('লভ্যাংশ উত্তোলন রেকর্ড হয়েছে', 'success');
    setWdTarget(null);
    onDataChange();
  };

  const handleDeletePartner = (p: Partner) => {
    if (!PartnerStorage.deletePartner(p.id, todayIso())) {
      return notify(
        'মুছে ফেলা যায়নি: কমপক্ষে ১ জন পার্টনার থাকতে হবে এবং অবশিষ্টদের হার ১০০% হতে হবে',
        'error'
      );
    }
    ActivityLogStorage.add('পার্টনার ডিলিট', p.name);
    notify(`${p.name} মুছে ফেলা হয়েছে`, 'success');
    onDataChange();
  };

  return (
    <div className="space-y-4">
      <DateFilterBar preset={filter.preset} fromDate={filter.fromDate} toDate={filter.toDate} onChange={setFilter} />

      {/* Top Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl surface-card p-4">
        <div className="flex flex-wrap items-center gap-6 text-xs sm:text-sm">
          <div>
            <span className="text-slate-500">মোট মূলধন: </span>
            <strong className="font-mono-num">{formatMoney(totalCapital)}</strong>
          </div>
          <div>
            <span className="text-slate-500">মোট লভ্যাংশ বণ্টন: </span>
            <strong
              className={`font-mono-num ${
                Math.abs(totalRate - 100) <= 0.0001
                  ? 'text-emerald-700 dark:text-emerald-400'
                  : 'text-amber-600 dark:text-amber-400'
              }`}
            >
              {totalRate.toFixed(1)}%
            </strong>
          </div>
        </div>

        {canWrite && (
          <div className="flex flex-wrap items-center gap-2">
            {partners.length > 0 && (
              <button
                type="button"
                onClick={openRateEditor}
                className="whitespace-nowrap inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 px-3 py-2 text-xs sm:text-sm font-medium hover:bg-slate-50 dark:hover:bg-slate-800"
              >
                <Percent className="h-4 w-4" />
                <span>লভ্যাংশ % নির্ধারণ</span>
              </button>
            )}
            <button
              type="button"
              onClick={() => setShowAdd(true)}
              className="whitespace-nowrap inline-flex items-center gap-1.5 rounded-lg btn-accent px-3.5 py-2 text-xs sm:text-sm font-medium"
            >
              <UserPlus className="h-4 w-4" />
              <span>নতুন পার্টনার</span>
            </button>
          </div>
        )}
      </div>

      {/* Partners Cards */}
      {partners.length === 0 ? (
        <div className="rounded-xl surface-card py-12 text-center text-sm text-slate-500">
          কোনো পার্টনার যোগ করা হয়নি
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {partners.map((p) => {
            const pProfit = periodAllocation.profitByPartner[p.id] ?? 0;
            const pLoss = periodAllocation.lossByPartner[p.id] ?? 0;
            const pExpenses = ExpenseStorage.getPartnerExpenseTotal(p.id, filter.fromDate, filter.toDate);
            const pWithdrawn = withdrawals
              .filter((w) => w.partnerId === p.id && w.date >= filter.fromDate && w.date <= filter.toDate)
              .reduce((s, w) => s + w.amount, 0);

            return (
              <div
                key={p.id}
                className="rounded-xl surface-card p-4 space-y-3"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">{p.name}</h3>
                    <div className="text-xs text-slate-500">
                      লভ্যাংশ অংশীদারিত্ব: <strong className="font-mono-num">{p.profitPercentage}%</strong>
                      {p.note ? ` · ${p.note}` : ''}
                    </div>
                  </div>
                  {canWrite && (
                    <button
                      type="button"
                      onClick={() => setDeleteTarget(p)}
                      className="rounded-lg p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
                      title="মুছে ফেলুন"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-2.5 text-xs bg-slate-50 dark:bg-slate-800/60 rounded-lg p-3">
                  <div>
                    <div className="text-slate-500">বর্তমান মূলধন</div>
                    <div className="mt-0.5 font-mono-num font-bold text-sm">{formatMoney(p.shareAmount)}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">সময়কালে প্রাপ্য লাভ</div>
                    <div className="mt-0.5 font-mono-num font-bold text-sm text-emerald-700 dark:text-emerald-400">
                      {formatMoney(pProfit - pLoss)}
                    </div>
                  </div>
                  <div>
                    <div className="text-slate-500">বণ্টনকৃত খরচ ও ক্ষতি</div>
                    <div className="mt-0.5 font-mono-num font-medium">{formatMoney(pExpenses)}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">লভ্যাংশ উত্তোলন</div>
                    <div className="mt-0.5 font-mono-num font-medium">{formatMoney(pWithdrawn)}</div>
                  </div>
                </div>

                {canWrite && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => {
                        setCapTarget(p);
                        setCapAmount('');
                        setCapDate(todayIso());
                        setCapNote('');
                      }}
                      className="inline-flex items-center gap-1 rounded-lg border border-slate-200 dark:border-slate-700 px-3 py-1.5 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800"
                    >
                      <Plus className="h-3.5 w-3.5" /> মূলধন জমা
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setWdTarget(p);
                        setWdAmount('');
                        setWdDate(todayIso());
                        setWdNote('');
                      }}
                      className="inline-flex items-center gap-1 rounded-lg border border-slate-200 dark:border-slate-700 px-3 py-1.5 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800"
                    >
                      <Banknote className="h-3.5 w-3.5" /> লভ্যাংশ উত্তোলন
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Add Partner Modal */}
      <Modal open={showAdd} onClose={() => setShowAdd(false)} title="নতুন পার্টনার যোগ করুন" maxWidthClass="max-w-sm">
        <form onSubmit={handleAddPartner} className="space-y-3">
          <input type="text" required placeholder="পার্টনারের নাম *" value={pName} onChange={(e) => setPName(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <input type="number" min={0} step="0.01" placeholder="প্রাথমিক মূলধন (৳)" value={pCapital} onChange={(e) => setPCapital(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num" />
          <input type="text" placeholder="নোট (ঐচ্ছিক)" value={pNote} onChange={(e) => setPNote(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setShowAdd(false)} className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm">বাতিল</button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">সংরক্ষণ</button>
          </div>
        </form>
      </Modal>

      {/* Rate Distribution Modal */}
      <Modal open={showRates} onClose={() => setShowRates(false)} title="লভ্যাংশ বণ্টন (%) নির্ধারণ" subtitle="সব পার্টনারের হার যোগ করে ঠিক ১০০% হতে হবে" maxWidthClass="max-w-md">
        <form onSubmit={handleSaveRates} className="space-y-3">
          <div>
            <label className="block text-xs font-medium mb-1">কার্যকর তারিখ (আজ বা পরবর্তী)</label>
            <input type="date" min={todayIso()} required value={rateDate} onChange={(e) => setRateDate(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          </div>
          {partners.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3">
              <span className="text-sm font-medium">{p.name}</span>
              <div className="flex items-center gap-1">
                <input
                  type="number"
                  min={0}
                  max={100}
                  step="0.01"
                  value={rateDraft[p.id] ?? '0'}
                  onChange={(e) => setRateDraft((prev) => ({ ...prev, [p.id]: e.target.value }))}
                  className="w-24 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-right text-sm font-mono-num"
                />
                <span className="text-xs text-slate-500">%</span>
              </div>
            </div>
          ))}
          <div className="flex items-center justify-between rounded-lg bg-slate-50 dark:bg-slate-800/60 px-3 py-2 text-xs">
            <span>মোট নির্ধারিত হার</span>
            {(() => {
              const draftSum = partners.reduce(
                (s, p) => s + (parseFloat(rateDraft[p.id] || '0') || 0),
                0
              );
              const valid = Math.abs(draftSum - 100) <= 0.0001;
              return (
                <strong
                  className={`font-mono-num ${
                    valid ? 'text-emerald-700 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
                  }`}
                >
                  {draftSum.toFixed(1)}% {valid ? '(সঠিক)' : '(১০০% হতে হবে)'}
                </strong>
              );
            })()}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setShowRates(false)} className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm">বাতিল</button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">হার সংরক্ষণ</button>
          </div>
        </form>
      </Modal>

      {/* Capital Contribution Modal */}
      <Modal open={!!capTarget} onClose={() => setCapTarget(null)} title={capTarget ? `মূলধন জমা: ${capTarget.name}` : ''} maxWidthClass="max-w-sm">
        <form onSubmit={handleAddCapital} className="space-y-3">
          <input type="date" required value={capDate} onChange={(e) => setCapDate(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <input type="number" min={0.01} step="0.01" required placeholder="টাকার পরিমাণ (৳)" value={capAmount} onChange={(e) => setCapAmount(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num" />
          <input type="text" placeholder="নোট" value={capNote} onChange={(e) => setCapNote(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setCapTarget(null)} className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm">বাতিল</button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">জমা দিন</button>
          </div>
        </form>
      </Modal>

      {/* Withdrawal Modal */}
      <Modal open={!!wdTarget} onClose={() => setWdTarget(null)} title={wdTarget ? `লভ্যাংশ উত্তোলন: ${wdTarget.name}` : ''} maxWidthClass="max-w-sm">
        <form onSubmit={handleAddWithdrawal} className="space-y-3">
          <input type="date" required value={wdDate} onChange={(e) => setWdDate(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <input type="number" min={0.01} step="0.01" required placeholder="উত্তোলনের পরিমাণ (৳)" value={wdAmount} onChange={(e) => setWdAmount(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm font-mono-num" />
          <input type="text" placeholder="নোট" value={wdNote} onChange={(e) => setWdNote(e.target.value)} className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm" />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setWdTarget(null)} className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-sm">বাতিল</button>
            <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-sm font-medium">নিশ্চিত করুন</button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && handleDeletePartner(deleteTarget)}
        title="পার্টনার মুছে ফেলবেন?"
        message={deleteTarget ? `${deleteTarget.name}-কে তালিকা থেকে সরানো হবে।` : ''}
        confirmLabel="ডিলিট"
        danger
      />
    </div>
  );
};
