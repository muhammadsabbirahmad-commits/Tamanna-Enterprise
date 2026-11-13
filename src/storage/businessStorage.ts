import { ActivityLogEntry, AppUser, ThemeMode } from '../types';
import { nowDateTime } from '../utils/format';

const BUSINESS_PREFS = 'tamanna_business_storage';
const KEY_ACTIVE_BUSINESS_ID = 'active_business_id';

type StorageMutationListener = (namespace: string, key: string, businessId: string) => void;
type BulkMutationListener = () => void;

const mutationListeners = new Set<StorageMutationListener>();
const bulkListeners = new Set<BulkMutationListener>();
let suppressSyncDepth = 0;

export function onStorageMutated(listener: StorageMutationListener): () => void {
  mutationListeners.add(listener);
  return () => mutationListeners.delete(listener);
}

export function onBulkStorageChanged(listener: BulkMutationListener): () => void {
  bulkListeners.add(listener);
  return () => bulkListeners.delete(listener);
}

export function notifyBulkStorageChanged(): void {
  if (suppressSyncDepth > 0) return;
  bulkListeners.forEach((fn) => fn());
}

export function withRemoteSyncSuppression(fn: () => void): void {
  suppressSyncDepth++;
  try {
    fn();
  } finally {
    suppressSyncDepth--;
  }
}

export function getActiveBusinessId(): string {
  return localStorage.getItem(`${BUSINESS_PREFS}:${KEY_ACTIVE_BUSINESS_ID}`) || 'default';
}

export function setActiveBusinessId(id: string): void {
  localStorage.setItem(`${BUSINESS_PREFS}:${KEY_ACTIVE_BUSINESS_ID}`, id.trim() || 'default');
}

export function getScopedKey(namespace: string, key: string, bizOverride?: string): string {
  const biz = bizOverride ?? getActiveBusinessId();
  return biz === 'default' ? `${namespace}:${key}` : `biz_${biz}_${namespace}:${key}`;
}

export function readJson<T>(namespace: string, key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(getScopedKey(namespace, key));
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export function writeJson<T>(namespace: string, key: string, value: T): boolean {
  try {
    localStorage.setItem(getScopedKey(namespace, key), JSON.stringify(value));
    if (suppressSyncDepth === 0) {
      const biz = getActiveBusinessId();
      mutationListeners.forEach((fn) => fn(namespace, key, biz));
    }
    return true;
  } catch {
    return false;
  }
}

export function readString(namespace: string, key: string, fallback: string): string {
  return localStorage.getItem(getScopedKey(namespace, key)) ?? fallback;
}

export function writeString(namespace: string, key: string, value: string): void {
  localStorage.setItem(getScopedKey(namespace, key), value);
  if (suppressSyncDepth === 0) {
    const biz = getActiveBusinessId();
    mutationListeners.forEach((fn) => fn(namespace, key, biz));
  }
}

// SettingsStorage
const SETTINGS_NS = 'tamanna_enterprise_settings';
const DEFAULT_SHOP_NAME = 'Tamanna Enterprise';

export const SettingsStorage = {
  getShopName(): string {
    return readString(SETTINGS_NS, 'shop_name', DEFAULT_SHOP_NAME) || DEFAULT_SHOP_NAME;
  },
  saveShopName(name: string): void {
    writeString(SETTINGS_NS, 'shop_name', name.trim() || DEFAULT_SHOP_NAME);
  },
};

// ThemeStorage
const THEME_NS = 'tamanna_enterprise_theme';

export const ThemeStorage = {
  getTheme(): ThemeMode {
    const val = localStorage.getItem(`${THEME_NS}:theme`);
    if (val === 'blue' || val === 'purple' || val === 'dark' || val === 'green') return val;
    return 'green';
  },
  saveTheme(theme: ThemeMode): void {
    localStorage.setItem(`${THEME_NS}:theme`, theme);
    applyThemeToDom(theme);
  },
};

export function applyThemeToDom(theme: ThemeMode): void {
  const root = document.documentElement;
  if (theme === 'dark') {
    root.classList.add('dark');
    root.removeAttribute('data-theme');
  } else {
    root.classList.remove('dark');
    root.setAttribute('data-theme', theme);
  }
}

// SecurityStorage
const SEC_NS = 'tamanna_enterprise_security';

function simpleHash(input: string): string {
  // Deterministic SHA-like fallback hash for synchronous PIN/password verification in browser
  let h1 = 0xdeadbeef ^ input.length;
  let h2 = 0x41c6ce57 ^ input.length;
  for (let i = 0; i < input.length; i++) {
    const ch = input.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return (4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(16);
}

const DEFAULT_ADMIN: AppUser = {
  id: 'default-admin',
  username: 'admin',
  passwordHash: '',
  role: 'ADMIN',
  approved: true,
  googleEmail: '',
};

export const SecurityStorage = {
  getUsers(): AppUser[] {
    try {
      const raw = localStorage.getItem(`${SEC_NS}:users`);
      if (!raw) return [DEFAULT_ADMIN];
      const parsed = JSON.parse(raw) as AppUser[];
      return Array.isArray(parsed) && parsed.length > 0 ? parsed : [DEFAULT_ADMIN];
    } catch {
      return [DEFAULT_ADMIN];
    }
  },
  saveUsers(users: AppUser[]): void {
    localStorage.setItem(`${SEC_NS}:users`, JSON.stringify(users));
  },
  ensureInitialized(): void {
    if (!localStorage.getItem(`${SEC_NS}:users`)) {
      this.saveUsers([DEFAULT_ADMIN]);
    }
    if (!localStorage.getItem(`${SEC_NS}:current_user`)) {
      localStorage.setItem(`${SEC_NS}:current_user`, DEFAULT_ADMIN.id);
    }
  },
  getCurrentUser(): AppUser | null {
    this.ensureInitialized();
    const id = localStorage.getItem(`${SEC_NS}:current_user`);
    if (!id) return null;
    return this.getUsers().find((u) => u.id === id) ?? null;
  },
  loginAs(user: AppUser): void {
    const users = this.getUsers();
    if (!users.some((u) => u.id === user.id)) {
      this.saveUsers([...users, user]);
    }
    localStorage.setItem(`${SEC_NS}:current_user`, user.id);
  },
  logout(): void {
    localStorage.removeItem(`${SEC_NS}:current_user`);
  },
  switchRole(role: 'ADMIN' | 'PARTNER'): void {
    const users = this.getUsers();
    let target = users.find((u) => u.role === role);
    if (!target) {
      target = {
        id: role === 'ADMIN' ? 'default-admin' : 'partner-viewer',
        username: role === 'ADMIN' ? 'admin' : 'partner',
        passwordHash: '',
        role,
        approved: true,
        googleEmail: '',
      };
      this.saveUsers([...users, target]);
    }
    localStorage.setItem(`${SEC_NS}:current_user`, target.id);
  },
  canWrite(): boolean {
    return this.getCurrentUser()?.role === 'ADMIN';
  },
  canManage(): boolean {
    return this.getCurrentUser()?.role === 'ADMIN';
  },
  isAdminPinSet(): boolean {
    const admin = this.getUsers().find((u) => u.role === 'ADMIN');
    return !!admin && admin.passwordHash.length > 0;
  },
  verifyAdminPin(pin: string): boolean {
    const admin = this.getUsers().find((u) => u.role === 'ADMIN');
    if (!admin) return false;
    if (!admin.passwordHash) return true;
    return admin.passwordHash === simpleHash(pin);
  },
  changeAdminPin(currentPin: string, newPin: string): boolean {
    const users = this.getUsers();
    const idx = users.findIndex((u) => u.role === 'ADMIN');
    if (idx < 0) return false;
    const admin = users[idx];
    if (admin.passwordHash && admin.passwordHash !== simpleHash(currentPin)) return false;
    users[idx] = { ...admin, passwordHash: newPin.trim() ? simpleHash(newPin.trim()) : '' };
    this.saveUsers(users);
    return true;
  },
  isPinFreeEntryEnabled(): boolean {
    const val = localStorage.getItem(`${SEC_NS}:pin_free_entry`);
    return val === null ? true : val === 'true';
  },
  setPinFreeEntryEnabled(enabled: boolean): void {
    localStorage.setItem(`${SEC_NS}:pin_free_entry`, String(enabled));
  },
  roleLabel(role: string): string {
    return role === 'ADMIN' ? 'অ্যাডমিন' : role === 'PARTNER' ? 'পার্টনার' : role;
  },
};

// ActivityLogStorage
const LOG_NS = 'tamanna_enterprise_activity_log';
const MAX_LOGS = 500;

export const ActivityLogStorage = {
  get(): ActivityLogEntry[] {
    const list = readJson<ActivityLogEntry[]>(LOG_NS, 'logs', []);
    return [...list].sort((a, b) => b.id - a.id);
  },
  add(action: string, details: string): boolean {
    return this.addAndGetId(action, details) > 0;
  },
  addAndGetId(action: string, details: string): number {
    const user = SecurityStorage.getCurrentUser()?.username || 'admin';
    const current = this.get();
    const used = new Set(current.map((e) => e.id));
    let id = Date.now();
    while (used.has(id)) id++;
    const entry: ActivityLogEntry = {
      id,
      date: nowDateTime(),
      username: user,
      action,
      details,
    };
    const next = [entry, ...current].slice(0, MAX_LOGS);
    return writeJson(LOG_NS, 'logs', next) ? id : 0;
  },
  removeById(id: number): boolean {
    if (id <= 0) return false;
    const current = this.get();
    if (!current.some((e) => e.id === id)) return false;
    return writeJson(
      LOG_NS,
      'logs',
      current.filter((e) => e.id !== id)
    );
  },
};
