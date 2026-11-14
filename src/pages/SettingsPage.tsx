import React, { useEffect, useMemo, useState } from 'react';
import {
  Cloud,
  CloudOff,
  Database,
  Download,
  LogOut,
  RefreshCw,
  Shield,
  Smartphone,
  Trash2,
  Upload,
} from 'lucide-react';
import { ThemeMode } from '../types';
import {
  ActivityLogStorage,
  SecurityStorage,
  SettingsStorage,
  ThemeStorage,
  applyThemeToDom,
} from '../storage/businessStorage';
import { cloudSync, SyncStatusSnapshot } from '../storage/syncEngine';
import {
  clearAllBusinessData,
  exportAllBackupJson,
  importAllBackupJson,
  previewBackupJson,
  seedSampleBusinessData,
} from '../engine/sampleData';
import { ConfirmDialog, Modal } from '../components/Modal';
import { PWAInstallButton } from '../components/PWAInstallButton';

interface SettingsPageProps {
  revision: number;
  syncStatus: SyncStatusSnapshot;
  onDataChange: () => void;
  notify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  revision,
  syncStatus,
  onDataChange,
  notify,
}) => {
  const [shopName, setShopName] = useState(SettingsStorage.getShopName());
  const [theme, setTheme] = useState<ThemeMode>(ThemeStorage.getTheme());
  const [role, setRole] = useState(SecurityStorage.getCurrentUser()?.role ?? 'ADMIN');
  const [pinFree, setPinFree] = useState(SecurityStorage.isPinFreeEntryEnabled());
  const [currentPin, setCurrentPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [confirmClear, setConfirmClear] = useState(false);
  const [confirmSeedSample, setConfirmSeedSample] = useState(false);
  const [pendingRestore, setPendingRestore] = useState<{
    raw: string;
    fileName: string;
    keyCount: number;
    productCount: number;
    saleCount: number;
  } | null>(null);

  useEffect(() => {
    setShopName(SettingsStorage.getShopName());
    setTheme(ThemeStorage.getTheme());
    setRole(SecurityStorage.getCurrentUser()?.role ?? 'ADMIN');
    setPinFree(SecurityStorage.isPinFreeEntryEnabled());
  }, [revision]);

  const logs = useMemo(() => ActivityLogStorage.get(), [revision]);

  const handleSaveShop = (e: React.FormEvent) => {
    e.preventDefault();
    SettingsStorage.saveShopName(shopName);
    ActivityLogStorage.add('দোকানের নাম পরিবর্তন', shopName.trim() || 'Tamanna Enterprise');
    onDataChange();
    notify('দোকানের নাম সংরক্ষিত হয়েছে', 'success');
  };

  const handleThemeChange = (next: ThemeMode) => {
    setTheme(next);
    ThemeStorage.saveTheme(next);
    onDataChange();
    notify('থিম পরিবর্তন করা হয়েছে', 'success');
  };

  const handleRoleChange = (nextRole: 'ADMIN' | 'PARTNER') => {
    SecurityStorage.switchRole(nextRole);
    setRole(nextRole);
    onDataChange();
    notify(`বর্তমান ভূমিকা: ${SecurityStorage.roleLabel(nextRole)}`, 'info');
  };

  const handleSavePin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!SecurityStorage.changeAdminPin(currentPin, newPin)) {
      return notify('বর্তমান পিন সঠিক নয়', 'error');
    }
    setCurrentPin('');
    setNewPin('');
    notify(newPin.trim() ? 'অ্যাডমিন পিন আপডেট হয়েছে' : 'অ্যাডমিন পিন সরানো হয়েছে', 'success');
  };

  const handleDownloadJson = () => {
    const json = exportAllBackupJson();
    const blob = new Blob([json], { type: 'application/json;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `tamanna-enterprise-backup-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    notify('ব্যাকআপ JSON ফাইল ডাউনলোড হয়েছে', 'success');
  };

  const handleRestoreJson = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const raw = String(reader.result || '');
      const preview = previewBackupJson(raw);
      if (!preview.ok) {
        return notify(preview.error || 'রিস্টোর ব্যর্থ হয়েছে', 'error');
      }
      setPendingRestore({
        raw,
        fileName: file.name,
        keyCount: preview.keyCount,
        productCount: preview.productCount,
        saleCount: preview.saleCount,
      });
    };
    reader.readAsText(file);
  };

  const executeRestore = (mode: 'merge' | 'replace') => {
    if (!pendingRestore) return;
    const res = importAllBackupJson(pendingRestore.raw, mode);
    if (!res.ok) return notify(res.error || 'রিস্টোর ব্যর্থ হয়েছে', 'error');
    applyThemeToDom(ThemeStorage.getTheme());
    onDataChange();
    setPendingRestore(null);
    notify(
      mode === 'merge'
        ? `আগের ডাটা অক্ষুণ্ণ রেখে ${res.count}টি নতুন/আপডেটেড রেকর্ড মার্জ করা হয়েছে`
        : `সফলভাবে ${res.count}টি ডাটা রেকর্ড প্রতিস্থাপন করা হয়েছে`,
      'success'
    );
  };

  const handleGoogleSignIn = async () => {
    try {
      await cloudSync.signInWithGoogle();
      notify('গুগল (Gmail) রিয়েল-টাইম সিঙ্ক সফলভাবে চালু হয়েছে', 'success');
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : 'গুগল সাইন-ইন ব্যর্থ হয়েছে';
      notify(msg, 'error');
    }
  };

  const handleGoogleSignOut = async () => {
    await cloudSync.signOutUser();
    notify('গুগল অ্যাকাউন্ট থেকে সাইন-আউট করা হয়েছে', 'info');
  };

  const handleManualSync = async () => {
    try {
      await cloudSync.triggerManualSync();
      notify('সব পরিবর্তন ক্লাউডের সাথে সিঙ্ক করা হয়েছে', 'success');
    } catch {
      notify('সিঙ্ক করতে সমস্যা হয়েছে', 'error');
    }
  };

  return (
    <div className="space-y-5 max-w-4xl">
      {/* Multi-device Gmail Cloud Sync & Offline PWA */}
      <div className="rounded-xl surface-card border-accent-soft p-4 sm:p-5 space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              {syncStatus.online ? (
                <Cloud className="h-4 w-4 text-accent" />
              ) : (
                <CloudOff className="h-4 w-4 text-amber-600" />
              )}
              <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                মাল্টি-ডিভাইস গুগল (Gmail) রিয়েল-টাইম সিঙ্ক ও অফলাইন মোড
              </h3>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              আপনার যেকোনো ফোনে একই Gmail দিয়ে সাইন-ইন করলে বর্তমান পণ্য ও সব হিসাব নিরাপদে সংরক্ষিত থেকে সব ফোনে রিয়েল-টাইমে আপডেট হবে। ইন্টারনেট না থাকলেও অফলাইনে কাজ চলবে এবং নেট পেলেই অটো-সিঙ্ক হবে।
            </p>
          </div>
          <div className="flex items-center gap-2">
            <PWAInstallButton />
          </div>
        </div>

        {syncStatus.user ? (
          <div className="rounded-lg border surface-subtle p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              {syncStatus.user.photoURL ? (
                <img
                  src={syncStatus.user.photoURL}
                  alt={syncStatus.user.displayName}
                  className="h-9 w-9 rounded-lg object-cover border border-[var(--border-subtle)] shrink-0"
                />
              ) : (
                <div className="h-9 w-9 rounded-lg bg-accent-soft text-accent flex items-center justify-center font-bold text-sm shrink-0">
                  {(syncStatus.user.email || 'G')[0].toUpperCase()}
                </div>
              )}
              <div className="space-y-0.5 text-xs min-w-0">
                <div className="font-semibold text-slate-900 dark:text-slate-100 truncate">
                  সংযুক্ত জিমেইল: <span className="font-mono-num text-accent">{syncStatus.user.email}</span>
                </div>
                <div className="text-slate-500 dark:text-slate-400 font-mono-num">
                  অবস্থা: {syncStatus.online ? 'অনলাইন' : 'অফলাইন ক্যাশ'} · সিঙ্কড রেকর্ড:{' '}
                  {syncStatus.syncedCount}টি
                  {syncStatus.lastSyncedAt ? ` · সর্বশেষ: ${syncStatus.lastSyncedAt}` : ''}
                  {syncStatus.hasPendingWrites ? ' · আপলোড অপেক্ষমান' : ''}
                </div>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 shrink-0">
              <button
                type="button"
                onClick={handleManualSync}
                disabled={syncStatus.syncing}
                className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg btn-accent px-3 py-1.5 text-xs font-medium disabled:opacity-50"
              >
                <RefreshCw className={`h-3.5 w-3.5 ${syncStatus.syncing ? 'animate-spin' : ''}`} />
                <span>এখনই সিঙ্ক করুন</span>
              </button>
              <button
                type="button"
                onClick={handleGoogleSignOut}
                className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-700"
              >
                <LogOut className="h-3.5 w-3.5" />
                <span>সাইন-আউট</span>
              </button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-3 pt-1">
            <button
              type="button"
              onClick={handleGoogleSignIn}
              className="whitespace-nowrap shrink-0 inline-flex items-center gap-2 rounded-lg btn-accent px-4 py-2 text-xs sm:text-sm font-semibold"
            >
              <Smartphone className="h-4 w-4" />
              <span>Google (Gmail) দিয়ে সাইন-ইন ও সিঙ্ক চালু করুন</span>
            </button>
            <span className="text-xs text-slate-500 dark:text-slate-400">
              আগে থেকে সংরক্ষিত স্থানীয় পণ্য ও হিসাব মুছবে না
            </span>
          </div>
        )}

        {syncStatus.error && (
          <div className="rounded-lg border border-red-200 dark:border-red-900/60 bg-red-50/70 dark:bg-red-950/30 px-3 py-2 text-xs text-red-700 dark:text-red-300">
            সিঙ্ক ত্রুটি: {syncStatus.error}
          </div>
        )}
      </div>

      {/* Shop & Theme */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <form
          onSubmit={handleSaveShop}
          className="rounded-xl surface-card p-4 space-y-3"
        >
          <h3 className="text-sm font-semibold">দোকানের তথ্য</h3>
          <div>
            <label className="block text-xs text-slate-500 mb-1">প্রতিষ্ঠানের নাম</label>
            <input
              type="text"
              value={shopName}
              onChange={(e) => setShopName(e.target.value)}
              className="w-full rounded-lg border border-[var(--border-subtle)] bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          </div>
          <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-xs font-medium">
            সংরক্ষণ করুন
          </button>
        </form>

        <div className="rounded-xl surface-card p-4 space-y-3">
          <h3 className="text-sm font-semibold">আন্তর্জাতিক কালার গ্রেডিং ও থিম</h3>
          <div className="grid grid-cols-2 gap-2">
            {(
              [
                ['green', 'এমেরাল্ড (ডিফল্ট)', '#047857', '#f3f6f4'],
                ['blue', 'সায়াফায়ার ব্লু', '#1d4ed8', '#f3f6fb'],
                ['purple', 'রয়্যাল অ্যামেথিস্ট', '#6d28d9', '#f5f4fa'],
                ['dark', 'অবসিডিয়ান ডার্ক', '#10b981', '#080d17'],
              ] as const
            ).map(([id, label, dotColor, bgSwatch]) => (
              <button
                key={id}
                type="button"
                onClick={() => handleThemeChange(id)}
                className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-medium transition-all ${
                  theme === id
                    ? 'btn-accent border-transparent'
                    : 'border-[var(--border-subtle)] text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'
                }`}
              >
                <span
                  className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded border border-black/15"
                  style={{ backgroundColor: bgSwatch }}
                >
                  <span
                    className="h-2 w-2 rounded-xs"
                    style={{ backgroundColor: dotColor }}
                  />
                </span>
                <span className="truncate">{label}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Role & Security */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="rounded-xl surface-card p-4 space-y-3">
          <div className="flex items-center gap-2">
            <Shield className="h-4 w-4 text-accent" />
            <h3 className="text-sm font-semibold">ব্যবহারকারীর ভূমিকা ও পিন</h3>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => handleRoleChange('ADMIN')}
              className={`flex-1 rounded-lg border py-2 text-xs font-medium ${
                role === 'ADMIN'
                  ? 'btn-accent border-transparent'
                  : 'border-slate-200 dark:border-slate-700'
              }`}
            >
              অ্যাডমিন (পূর্ণ অনুমতি)
            </button>
            <button
              type="button"
              onClick={() => handleRoleChange('PARTNER')}
              className={`flex-1 rounded-lg border py-2 text-xs font-medium ${
                role === 'PARTNER'
                  ? 'btn-accent border-transparent'
                  : 'border-slate-200 dark:border-slate-700'
              }`}
            >
              পার্টনার (রিড-অনলি)
            </button>
          </div>
          <label className="flex items-center gap-2 text-xs pt-1 cursor-pointer">
            <input
              type="checkbox"
              checked={pinFree}
              onChange={(e) => {
                setPinFree(e.target.checked);
                SecurityStorage.setPinFreeEntryEnabled(e.target.checked);
              }}
              className="rounded"
            />
            <span>পিন-মুক্ত ডিলিট মোড চালু রাখুন</span>
          </label>
        </div>

        <form
          onSubmit={handleSavePin}
          className="rounded-xl surface-card p-4 space-y-3"
        >
          <h3 className="text-sm font-semibold">
            অ্যাডমিন ডিলিট পিন ({SecurityStorage.isAdminPinSet() ? 'সেট করা আছে' : 'সেট নেই'})
          </h3>
          {SecurityStorage.isAdminPinSet() && (
            <input
              type="password"
              placeholder="বর্তমান পিন"
              value={currentPin}
              onChange={(e) => setCurrentPin(e.target.value)}
              className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
            />
          )}
          <input
            type="password"
            placeholder="নতুন পিন (খালি রাখলে পিন মুছে যাবে)"
            value={newPin}
            onChange={(e) => setNewPin(e.target.value)}
            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm"
          />
          <button type="submit" className="rounded-lg btn-accent px-4 py-2 text-xs font-medium">
            পিন আপডেট করুন
          </button>
        </form>
      </div>

      {/* Backup & Data Management */}
      <div className="rounded-xl surface-card p-4 space-y-3">
        <h3 className="text-sm font-semibold">ডাটা ব্যাকআপ, রিস্টোর ও নমুনা ডাটা</h3>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleDownloadJson}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800"
          >
            <Download className="h-4 w-4" /> JSON ব্যাকআপ ডাউনলোড
          </button>

          <label className="cursor-pointer inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800">
            <Upload className="h-4 w-4" /> JSON ব্যাকআপ রিস্টোর
            <input type="file" accept=".json" onChange={handleRestoreJson} className="hidden" />
          </label>

          <button
            type="button"
            onClick={() => setConfirmSeedSample(true)}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800"
          >
            <Database className="h-4 w-4" /> নমুনা ডাটা লোড করুন
          </button>

          <button
            type="button"
            onClick={() => setConfirmClear(true)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white px-3.5 py-2 text-xs font-medium"
          >
            <Trash2 className="h-4 w-4" /> সব ডাটা মুছুন
          </button>
        </div>
      </div>

      {/* Activity Log */}
      <div className="rounded-xl surface-card overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 text-sm font-semibold">
          সাম্প্রতিক অ্যাক্টিভিটি লগ ({logs.length})
        </div>
        {logs.length === 0 ? (
          <div className="py-8 text-center text-xs text-slate-500">কোনো লগ নেই</div>
        ) : (
          <div className="divide-y divide-slate-100 dark:divide-slate-800 max-h-80 overflow-y-auto text-xs">
            {logs.slice(0, 60).map((l) => (
              <div key={l.id} className="flex items-center justify-between gap-3 px-4 py-2.5">
                <div className="min-w-0">
                  <span className="font-semibold text-slate-800 dark:text-slate-200">{l.action}</span>
                  <span className="text-slate-500"> · {l.details}</span>
                </div>
                <span className="font-mono-num text-slate-400 shrink-0">{l.date}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <Modal
        open={!!pendingRestore}
        onClose={() => setPendingRestore(null)}
        title="ব্যাকআপ ফাইল রিস্টোর ও মার্জ করুন"
        subtitle={pendingRestore ? pendingRestore.fileName : ''}
        maxWidthClass="max-w-md"
      >
        {pendingRestore && (
          <div className="space-y-4 text-sm">
            <div className="rounded-lg border surface-subtle p-3 text-xs space-y-1">
              <div className="font-semibold text-slate-800 dark:text-slate-100">
                ব্যাকআপ ফাইলে পাওয়া তথ্য:
              </div>
              <div className="text-slate-600 dark:text-slate-300 font-mono-num">
                ডাটা সেকশন: {pendingRestore.keyCount}টি · পণ্য: {pendingRestore.productCount}টি · বিক্রয় বিল:{' '}
                {pendingRestore.saleCount}টি
              </div>
            </div>

            <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-300">
              <strong className="text-accent">আগের ডাটার সাথে মার্জ (নিরাপদ)</strong> বেছে নিলে আপনার বর্তমানে থাকা কোনো পণ্য, বিক্রয় বা হিসাব মুছবে না — শুধু যেগুলো বর্তমান স্টোরেজে নেই সেগুলো যুক্ত হবে।
            </p>

            <div className="flex flex-col sm:flex-row sm:justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setPendingRestore(null)}
                className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800"
              >
                বাতিল
              </button>
              <button
                type="button"
                onClick={() => executeRestore('replace')}
                className="rounded-lg border border-amber-300 dark:border-amber-800 px-3.5 py-2 text-xs font-medium text-amber-700 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-950/40"
              >
                আগের ডাটা বদলে প্রতিস্থাপন
              </button>
              <button
                type="button"
                onClick={() => executeRestore('merge')}
                className="rounded-lg btn-accent px-4 py-2 text-xs font-semibold"
              >
                আগের ডাটার সাথে মার্জ করুন (নিরাপদ)
              </button>
            </div>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={confirmSeedSample}
        onClose={() => setConfirmSeedSample(false)}
        onConfirm={() => {
          seedSampleBusinessData();
          onDataChange();
          notify('নমুনা ডাটা লোড হয়েছে', 'success');
        }}
        title="নমুনা ডাটা লোড করবেন?"
        message="সতর্কতা: নমুনা ডাটা লোড করলে আপনার বর্তমান সব আসল পণ্য, বিক্রয় ও হিসাবের ডাটা মুছে গিয়ে ডেমো ডাটা বসে যাবে। প্রয়োজনে আগে JSON ব্যাকআপ ডাউনলোড করে নিন।"
        confirmLabel="হ্যাঁ, নমুনা ডাটা লোড করুন"
        danger
      />

      <ConfirmDialog
        open={confirmClear}
        onClose={() => setConfirmClear(false)}
        onConfirm={() => {
          clearAllBusinessData();
          onDataChange();
          notify('সকল ব্যবসায়িক ডাটা মুছে ফেলা হয়েছে', 'info');
        }}
        title="সকল ডাটা মুছে ফেলবেন?"
        message="এতে সকল পণ্য, বিক্রয়, ক্রয়, বাকি, পার্টনার এবং হিসাবের ডাটা স্থায়ীভাবে মুছে যাবে।"
        confirmLabel="সব মুছুন"
        danger
      />
    </div>
  );
};
