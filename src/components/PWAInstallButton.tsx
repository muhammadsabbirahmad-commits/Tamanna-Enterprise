import React, { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { Modal } from './Modal';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export function usePWAInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState(false);
  const [isIOS, setIsIOS] = useState(false);

  useEffect(() => {
    const isStandalone =
      window.matchMedia('(display-mode: standalone)').matches ||
      (window.navigator as unknown as { standalone?: boolean }).standalone === true;
    setIsInstalled(isStandalone);

    const userAgent = window.navigator.userAgent.toLowerCase();
    setIsIOS(/iphone|ipad|ipod/.test(userAgent));

    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    const handleAppInstalled = () => {
      setIsInstalled(true);
      setDeferredPrompt(null);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  const install = async () => {
    if (!deferredPrompt) return false;
    await deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      setIsInstalled(true);
      setDeferredPrompt(null);
      return true;
    }
    return false;
  };

  return {
    isInstallable: !!deferredPrompt,
    isInstalled,
    isIOS,
    install,
  };
}

export const PWAInstallButton: React.FC<{ compact?: boolean }> = ({ compact = false }) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  if (isInstalled) {
    return null;
  }

  if (isInstallable) {
    return (
      <button
        type="button"
        onClick={install}
        className={`whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-accent-soft bg-accent-soft text-accent hover:brightness-95 transition-all ${
          compact ? 'px-2.5 py-1.5 text-xs font-medium' : 'px-3.5 py-2 text-xs sm:text-sm font-medium'
        }`}
      >
        <Download className="h-3.5 w-3.5" />
        <span>অ্যাপ ইনস্টল</span>
      </button>
    );
  }

  if (isIOS) {
    return (
      <>
        <button
          type="button"
          onClick={() => setShowIOSGuide(true)}
          className="whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 px-2.5 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800"
        >
          <Download className="h-3.5 w-3.5" />
          <span>ফোনে ইনস্টল</span>
        </button>

        <Modal
          open={showIOSGuide}
          onClose={() => setShowIOSGuide(false)}
          title="iPhone / iPad-এ ইনস্টল করুন"
          maxWidthClass="max-w-sm"
        >
          <div className="space-y-3 text-sm text-slate-600 dark:text-slate-300">
            <p>১. Safari ব্রাউজারের নিচে বা উপরে থাকা <strong>Share</strong> বাটনে ট্যাপ করুন।</p>
            <p>২. নিচে স্ক্রল করে <strong>Add to Home Screen</strong> নির্বাচন করুন।</p>
            <p className="text-xs text-slate-500">ইনস্টল করলে অফলাইনেও অ্যাপটি দ্রুত চলবে।</p>
            <button
              type="button"
              onClick={() => setShowIOSGuide(false)}
              className="w-full rounded-lg btn-accent py-2 text-xs font-medium"
            >
              বুঝেছি
            </button>
          </div>
        </Modal>
      </>
    );
  }

  return null;
};
