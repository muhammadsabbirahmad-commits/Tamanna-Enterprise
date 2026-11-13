import {
  User,
  onAuthStateChanged,
  signInWithPopup,
  signOut,
} from 'firebase/auth';
import {
  collection,
  doc,
  getDoc,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  Unsubscribe,
} from 'firebase/firestore';
import {
  OperationType,
  auth,
  db,
  googleProvider,
  handleFirestoreError,
  testConnection,
} from '../firebase/config';
import {
  SettingsStorage,
  getActiveBusinessId,
  getScopedKey,
  onBulkStorageChanged,
  onStorageMutated,
  withRemoteSyncSuppression,
} from './businessStorage';

export interface SyncStatusSnapshot {
  ready: boolean;
  user: {
    uid: string;
    email: string;
    displayName: string;
    photoURL: string;
  } | null;
  online: boolean;
  syncing: boolean;
  hasPendingWrites: boolean;
  syncedCount: number;
  lastSyncedAt: string;
  error: string | null;
}

type CollectionKind = 'array' | 'object' | 'string' | 'number_array';

interface CollectionSpec {
  namespace: string;
  key: string;
  collectionKey: string;
  kind: CollectionKind;
  maxItems?: number;
  getItemId?: (item: Record<string, unknown>) => string;
  getItemUpdatedAt?: (item: Record<string, unknown>) => number;
}

const COLLECTION_SPECS: CollectionSpec[] = [
  {
    namespace: 'tamanna_enterprise_products',
    key: 'products',
    collectionKey: 'products',
    kind: 'array',
    getItemId: (x) => String(x.code || '').trim().toUpperCase(),
    getItemUpdatedAt: (x) => Number(x.updatedAt) || Number(x.createdAt) || 0,
  },
  {
    namespace: 'tamanna_enterprise_products',
    key: 'next_product_code',
    collectionKey: 'settings',
    kind: 'string',
  },
  {
    namespace: 'tamanna_inventory_meta',
    key: 'meta',
    collectionKey: 'inventory_meta',
    kind: 'array',
    getItemId: (x) => String(x.code || '').trim().toUpperCase(),
  },
  {
    namespace: 'tamanna_inventory_meta',
    key: 'history',
    collectionKey: 'stock_history',
    kind: 'array',
    maxItems: 200,
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_enterprise_sales',
    key: 'sales',
    collectionKey: 'sales',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_enterprise_sale_transactions',
    key: 'transactions',
    collectionKey: 'sale_transactions',
    kind: 'array',
    getItemId: (x) => String(x.transactionId || ''),
  },
  {
    namespace: 'tamanna_enterprise_sale_returns',
    key: 'returns',
    collectionKey: 'sale_returns',
    kind: 'array',
    getItemId: (x) => String(x.returnId || ''),
  },
  {
    namespace: 'tamanna_enterprise_sale_returns',
    key: 'lines',
    collectionKey: 'sale_return_lines',
    kind: 'array',
    getItemId: (x) => `${String(x.returnId || '')}__${String(x.productCode || '').trim().toUpperCase()}`,
  },
  {
    namespace: 'tamanna_enterprise_sale_returns',
    key: 'exchanges',
    collectionKey: 'sale_exchanges',
    kind: 'array',
    getItemId: (x) => String(x.exchangeId || ''),
  },
  {
    namespace: 'tamanna_enterprise_purchases',
    key: 'purchases',
    collectionKey: 'purchases',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_supplier_due',
    key: 'suppliers',
    collectionKey: 'suppliers',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_supplier_due',
    key: 'entries',
    collectionKey: 'supplier_dues',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_customer_due',
    key: 'customers',
    collectionKey: 'customers',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_customer_due',
    key: 'entries',
    collectionKey: 'customer_dues',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_enterprise_finance',
    key: 'expenses',
    collectionKey: 'expenses',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
    getItemUpdatedAt: (x) => Number(x.updatedAt) || 0,
  },
  {
    namespace: 'tamanna_enterprise_finance',
    key: 'deleted_expense_ids',
    collectionKey: 'settings',
    kind: 'number_array',
  },
  {
    namespace: 'tamanna_enterprise_finance',
    key: 'damages',
    collectionKey: 'damages',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_partner_storage',
    key: 'partners',
    collectionKey: 'partners',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
    getItemUpdatedAt: (x) => Number(x.updatedAt) || 0,
  },
  {
    namespace: 'tamanna_partner_storage',
    key: 'deleted_partner_ids',
    collectionKey: 'settings',
    kind: 'number_array',
  },
  {
    namespace: 'tamanna_partner_storage',
    key: 'partner_profit_rate_history',
    collectionKey: 'partner_rates',
    kind: 'array',
    getItemId: (x) => String(x.effectiveDate || ''),
    getItemUpdatedAt: (x) => Number(x.updatedAt) || 0,
  },
  {
    namespace: 'tamanna_partner_storage',
    key: 'partner_capital_contributions',
    collectionKey: 'partner_capital',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
    getItemUpdatedAt: (x) => Number(x.createdAt) || 0,
  },
  {
    namespace: 'tamanna_partner_storage',
    key: 'partner_profit_withdrawals',
    collectionKey: 'partner_withdrawals',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
    getItemUpdatedAt: (x) => Number(x.createdAt) || 0,
  },
  {
    namespace: 'tamanna_partner_storage',
    key: 'partner_capitalized_profit_periods',
    collectionKey: 'partner_capitalized',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
    getItemUpdatedAt: (x) => Number(x.updatedAt) || Number(x.createdAt) || 0,
  },
  {
    namespace: 'tamanna_manual_cash_accounts',
    key: 'accounts',
    collectionKey: 'cash_accounts',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
    getItemUpdatedAt: (x) => Number(x.updatedAt) || 0,
  },
  {
    namespace: 'tamanna_opening_balances',
    key: 'opening_balance_v1',
    collectionKey: 'opening_balance',
    kind: 'object',
    getItemUpdatedAt: (x) => Number(x.updatedAt) || 0,
  },
  {
    namespace: 'tamanna_daily_manual_balances',
    key: 'entries',
    collectionKey: 'daily_balances',
    kind: 'array',
    getItemId: (x) => String(x.id || ''),
    getItemUpdatedAt: (x) => Number(x.updatedAt) || 0,
  },
  {
    namespace: 'tamanna_enterprise_activity_log',
    key: 'logs',
    collectionKey: 'activity_logs',
    kind: 'array',
    maxItems: 200,
    getItemId: (x) => String(x.id || ''),
  },
  {
    namespace: 'tamanna_enterprise_settings',
    key: 'shop_name',
    collectionKey: 'settings',
    kind: 'string',
  },
];

const SYNC_TS_KEY = 'tamanna_sync_meta:timestamps';
const SYNC_TOMB_KEY = 'tamanna_sync_meta:tombstones';

function fnv1aHex(str: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

function makeRecordId(businessId: string, collectionKey: string, itemId: string): string {
  const safeBiz = businessId.replace(/[^a-zA-Z0-9_\-]/g, '_').slice(0, 24) || 'default';
  const safeCol = collectionKey.replace(/[^a-zA-Z0-9_\-]/g, '_').slice(0, 32);
  const rawSafe = itemId.replace(/[^a-zA-Z0-9_\-]/g, '_');
  const needsHash = rawSafe !== itemId || rawSafe.length > 56 || rawSafe.length === 0;
  const suffix = needsHash ? `${rawSafe.slice(0, 40)}_${fnv1aHex(itemId)}` : rawSafe;
  return `${safeBiz}__${safeCol}__${suffix}`.slice(0, 128);
}

function sanitizeValue(val: unknown): unknown {
  if (val === null || val === undefined) return null;
  if (typeof val === 'number') return Number.isFinite(val) ? val : 0;
  if (typeof val === 'string' || typeof val === 'boolean') return val;
  if (Array.isArray(val)) return val.map((x) => sanitizeValue(x));
  if (typeof val === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(val as Record<string, unknown>)) {
      if (v !== undefined) out[k] = sanitizeValue(v);
    }
    return out;
  }
  return String(val);
}

function sanitizePayload(obj: Record<string, unknown>): Record<string, unknown> {
  return sanitizeValue(obj) as Record<string, unknown>;
}

function loadMap(key: string): Record<string, number> {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as Record<string, number>) : {};
  } catch {
    return {};
  }
}

function saveMap(key: string, data: Record<string, number>): void {
  try {
    localStorage.setItem(key, JSON.stringify(data));
  } catch {
    // ignore quota errors
  }
}

interface RemoteDocState {
  recordId: string;
  businessId: string;
  collectionKey: string;
  itemId: string;
  payload: Record<string, unknown>;
  deleted: boolean;
  updatedAt: number;
  payloadJson: string;
}

interface LocalRecordEntry {
  recordId: string;
  businessId: string;
  collectionKey: string;
  itemId: string;
  payload: Record<string, unknown>;
  payloadJson: string;
  updatedAt: number;
}

function readLocalCollectionEntries(spec: CollectionSpec, businessId: string): LocalRecordEntry[] {
  const storageKey = getScopedKey(spec.namespace, spec.key, businessId);
  const raw = localStorage.getItem(storageKey);
  if (!raw) return [];
  const tsMap = loadMap(SYNC_TS_KEY);
  const out: LocalRecordEntry[] = [];

  if (spec.kind === 'string') {
    const payload = sanitizePayload({ value: raw });
    const itemId = spec.key.slice(0, 128);
    const recordId = makeRecordId(businessId, spec.collectionKey, itemId);
    out.push({
      recordId,
      businessId,
      collectionKey: spec.collectionKey,
      itemId,
      payload,
      payloadJson: JSON.stringify(payload),
      updatedAt: tsMap[recordId] || Date.now(),
    });
    return out;
  }

  try {
    const parsed = JSON.parse(raw);
    if (spec.kind === 'number_array' && Array.isArray(parsed)) {
      const payload = sanitizePayload({ ids: parsed.filter((x) => typeof x === 'number') });
      const itemId = spec.key.slice(0, 128);
      const recordId = makeRecordId(businessId, spec.collectionKey, itemId);
      out.push({
        recordId,
        businessId,
        collectionKey: spec.collectionKey,
        itemId,
        payload,
        payloadJson: JSON.stringify(payload),
        updatedAt: tsMap[recordId] || Date.now(),
      });
      return out;
    }

    if (spec.kind === 'object' && parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const itemObj = parsed as Record<string, unknown>;
      const payload = sanitizePayload(itemObj);
      const itemId = spec.key.slice(0, 128);
      const recordId = makeRecordId(businessId, spec.collectionKey, itemId);
      const explicitTs = spec.getItemUpdatedAt ? spec.getItemUpdatedAt(itemObj) : 0;
      out.push({
        recordId,
        businessId,
        collectionKey: spec.collectionKey,
        itemId,
        payload,
        payloadJson: JSON.stringify(payload),
        updatedAt: explicitTs || tsMap[recordId] || Date.now(),
      });
      return out;
    }

    if (spec.kind === 'array' && Array.isArray(parsed)) {
      const slice = spec.maxItems ? parsed.slice(0, spec.maxItems) : parsed;
      slice.forEach((rawItem) => {
        if (!rawItem || typeof rawItem !== 'object') return;
        const itemObj = rawItem as Record<string, unknown>;
        const itemIdRaw = spec.getItemId ? spec.getItemId(itemObj) : '';
        if (!itemIdRaw) return;
        const itemId = itemIdRaw.slice(0, 128);
        const recordId = makeRecordId(businessId, spec.collectionKey, itemId);
        const payload = sanitizePayload(itemObj);
        const explicitTs = spec.getItemUpdatedAt ? spec.getItemUpdatedAt(itemObj) : 0;
        out.push({
          recordId,
          businessId,
          collectionKey: spec.collectionKey,
          itemId,
          payload,
          payloadJson: JSON.stringify(payload),
          updatedAt: explicitTs || tsMap[recordId] || 1,
        });
      });
    }
  } catch {
    return [];
  }
  return out;
}

class CloudSyncService {
  private status: SyncStatusSnapshot = {
    ready: false,
    user: null,
    online: typeof navigator !== 'undefined' ? navigator.onLine : true,
    syncing: false,
    hasPendingWrites: false,
    syncedCount: 0,
    lastSyncedAt: '',
    error: null,
  };

  private statusListeners = new Set<(s: SyncStatusSnapshot) => void>();
  private dataAppliedCallback: (() => void) | null = null;
  private snapshotUnsub: Unsubscribe | null = null;
  private authUnsub: Unsubscribe | null = null;
  private storageUnsub: (() => void) | null = null;
  private bulkUnsub: (() => void) | null = null;
  private initialized = false;
  private shopVerifiedForUid: string | null = null;
  private remoteDocs = new Map<string, RemoteDocState>();
  private lastKnownLocalHashes = new Map<string, Map<string, string>>();
  private pushQueueTimer: ReturnType<typeof setTimeout> | null = null;
  private pendingPushSpecs = new Set<string>();

  init(onRemoteDataApplied: () => void): void {
    this.dataAppliedCallback = onRemoteDataApplied;
    if (this.initialized) return;
    this.initialized = true;

    testConnection().catch(() => {});

    window.addEventListener('online', () => {
      this.updateStatus({ online: true });
      if (auth.currentUser) {
        this.pushAllLocalChanges().catch(() => {});
      }
    });
    window.addEventListener('offline', () => {
      this.updateStatus({ online: false });
    });

    // Seed initial hashes for existing localStorage data so deletions are tracked accurately
    this.snapshotLocalHashes(getActiveBusinessId());

    this.storageUnsub = onStorageMutated((namespace, key, businessId) => {
      this.handleLocalMutation(namespace, key, businessId);
    });

    this.bulkUnsub = onBulkStorageChanged(() => {
      this.handleBulkLocalChange(getActiveBusinessId());
    });

    this.authUnsub = onAuthStateChanged(auth, (user) => {
      this.handleAuthChanged(user).catch((err) => {
        console.error('Auth sync error:', err);
      });
    });
  }

  destroy(): void {
    this.snapshotUnsub?.();
    this.authUnsub?.();
    this.storageUnsub?.();
    this.bulkUnsub?.();
    this.initialized = false;
  }

  subscribe(listener: (s: SyncStatusSnapshot) => void): () => void {
    this.statusListeners.add(listener);
    listener(this.status);
    return () => this.statusListeners.delete(listener);
  }

  getSnapshot(): SyncStatusSnapshot {
    return this.status;
  }

  private updateStatus(patch: Partial<SyncStatusSnapshot>): void {
    this.status = { ...this.status, ...patch };
    this.statusListeners.forEach((fn) => fn(this.status));
  }

  async signInWithGoogle(): Promise<void> {
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      const offlineMsg = 'অফলাইনে গুগল সাইন-ইন করা যায় না। ইন্টারনেট চালু করে আবার চেষ্টা করুন।';
      this.updateStatus({ error: offlineMsg, syncing: false });
      throw new Error(offlineMsg);
    }
    this.updateStatus({ error: null, syncing: true });
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (err) {
      const code = (err as { code?: string })?.code || '';
      let msg = err instanceof Error ? err.message : 'গুগল সাইন-ইন ব্যর্থ হয়েছে';
      if (code === 'auth/popup-blocked') {
        msg = 'ব্রাউজার পপ-আপ ব্লক করেছে। অনুগ্রহ করে পপ-আপ অনুমতি দিয়ে আবার চেষ্টা করুন।';
      } else if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') {
        msg = 'গুগল সাইন-ইন বাতিল করা হয়েছে।';
      } else if (code === 'auth/network-request-failed') {
        msg = 'নেটওয়ার্ক সমস্যার কারণে গুগল সাইন-ইন করা যায়নি।';
      }
      this.updateStatus({ error: msg, syncing: false });
      throw new Error(msg);
    }
  }

  async signOutUser(): Promise<void> {
    this.snapshotUnsub?.();
    this.snapshotUnsub = null;
    this.shopVerifiedForUid = null;
    this.remoteDocs.clear();
    await signOut(auth);
    this.updateStatus({
      user: null,
      syncing: false,
      hasPendingWrites: false,
      syncedCount: 0,
      error: null,
    });
  }

  async triggerManualSync(): Promise<void> {
    if (!auth.currentUser) return;
    await this.pushAllLocalChanges();
  }

  private snapshotLocalHashes(businessId: string): void {
    COLLECTION_SPECS.forEach((spec) => {
      const entries = readLocalCollectionEntries(spec, businessId);
      const map = new Map<string, string>();
      entries.forEach((e) => map.set(e.recordId, e.payloadJson));
      this.lastKnownLocalHashes.set(`${businessId}:${spec.namespace}:${spec.key}`, map);
    });
  }

  private handleLocalMutation(namespace: string, key: string, businessId: string): void {
    const spec = COLLECTION_SPECS.find((s) => s.namespace === namespace && s.key === key);
    if (!spec) return;

    const hashKey = `${businessId}:${namespace}:${key}`;
    const prevMap = this.lastKnownLocalHashes.get(hashKey) ?? new Map<string, string>();
    const currentEntries = readLocalCollectionEntries(spec, businessId);
    const nextMap = new Map<string, string>();
    const tsMap = loadMap(SYNC_TS_KEY);
    const tombMap = loadMap(SYNC_TOMB_KEY);
    const now = Date.now();

    currentEntries.forEach((entry) => {
      nextMap.set(entry.recordId, entry.payloadJson);
      const prevJson = prevMap.get(entry.recordId);
      if (prevJson !== entry.payloadJson) {
        tsMap[entry.recordId] = Math.max(now, entry.updatedAt);
        delete tombMap[entry.recordId];
      }
    });

    // Track deletions
    prevMap.forEach((_, oldRecordId) => {
      if (!nextMap.has(oldRecordId)) {
        tombMap[oldRecordId] = now;
        tsMap[oldRecordId] = now;
      }
    });

    saveMap(SYNC_TS_KEY, tsMap);
    saveMap(SYNC_TOMB_KEY, tombMap);
    this.lastKnownLocalHashes.set(hashKey, nextMap);

    if (spec.namespace === 'tamanna_enterprise_settings' && spec.key === 'shop_name') {
      this.shopVerifiedForUid = null;
    }

    if (auth.currentUser) {
      this.pendingPushSpecs.add(hashKey);
      this.schedulePush();
    }
  }

  private handleBulkLocalChange(businessId: string): void {
    const tsMap = loadMap(SYNC_TS_KEY);
    const tombMap = loadMap(SYNC_TOMB_KEY);
    const now = Date.now();

    COLLECTION_SPECS.forEach((spec) => {
      const hashKey = `${businessId}:${spec.namespace}:${spec.key}`;
      const prevMap = this.lastKnownLocalHashes.get(hashKey) ?? new Map<string, string>();
      const currentEntries = readLocalCollectionEntries(spec, businessId);
      const nextMap = new Map<string, string>();

      currentEntries.forEach((entry) => {
        nextMap.set(entry.recordId, entry.payloadJson);
        tsMap[entry.recordId] = Math.max(now, entry.updatedAt);
        delete tombMap[entry.recordId];
      });
      prevMap.forEach((_, oldRecordId) => {
        if (!nextMap.has(oldRecordId)) {
          tombMap[oldRecordId] = now;
          tsMap[oldRecordId] = now;
        }
      });
      this.lastKnownLocalHashes.set(hashKey, nextMap);
      this.pendingPushSpecs.add(hashKey);
    });

    saveMap(SYNC_TS_KEY, tsMap);
    saveMap(SYNC_TOMB_KEY, tombMap);

    if (auth.currentUser) {
      this.schedulePush();
    }
  }

  private schedulePush(): void {
    if (this.pushQueueTimer) clearTimeout(this.pushQueueTimer);
    this.pushQueueTimer = setTimeout(() => {
      this.flushPendingPushes().catch((err) => {
        console.error('Background push error:', err);
      });
    }, 150);
  }

  private async ensureShopDocument(user: User): Promise<void> {
    if (this.shopVerifiedForUid === user.uid) return;
    const shopRef = doc(db, 'shops', user.uid);
    const path = `shops/${user.uid}`;
    const shopName = SettingsStorage.getShopName().slice(0, 120) || 'Tamanna Enterprise';
    const ownerEmail = (user.email || '').slice(0, 254);

    try {
      const snap = await getDoc(shopRef);
      if (snap.exists()) {
        await updateDoc(shopRef, {
          ownerEmail,
          shopName,
          updatedAt: Date.now(),
          serverUpdatedAt: serverTimestamp(),
        });
      } else {
        await setDoc(shopRef, {
          ownerUid: user.uid,
          ownerEmail,
          shopName,
          updatedAt: Date.now(),
          serverCreatedAt: serverTimestamp(),
          serverUpdatedAt: serverTimestamp(),
        });
      }
      this.shopVerifiedForUid = user.uid;
    } catch (error) {
      const msg = error instanceof Error ? error.message : '';
      if (msg.includes('offline') || (typeof navigator !== 'undefined' && !navigator.onLine)) {
        await setDoc(shopRef, {
          ownerUid: user.uid,
          ownerEmail,
          shopName,
          updatedAt: Date.now(),
          serverCreatedAt: serverTimestamp(),
          serverUpdatedAt: serverTimestamp(),
        }).catch(() => {});
        this.shopVerifiedForUid = user.uid;
        return;
      }
      handleFirestoreError(error, OperationType.WRITE, path);
    }
  }

  private async handleAuthChanged(user: User | null): Promise<void> {
    this.snapshotUnsub?.();
    this.snapshotUnsub = null;
    this.remoteDocs.clear();

    if (!user) {
      this.shopVerifiedForUid = null;
      this.updateStatus({
        ready: true,
        user: null,
        syncing: false,
        hasPendingWrites: false,
        syncedCount: 0,
        error: null,
      });
      return;
    }

    this.updateStatus({
      ready: true,
      user: {
        uid: user.uid,
        email: user.email || '',
        displayName: user.displayName || user.email || 'User',
        photoURL: user.photoURL || '',
      },
      syncing: true,
      error: null,
    });

    await this.ensureShopDocument(user);

    const recordsPath = `shops/${user.uid}/records`;
    const q = query(collection(db, 'shops', user.uid, 'records'), where('ownerUid', '==', user.uid));

    let isFirstSnapshot = true;

    this.snapshotUnsub = onSnapshot(
      q,
      { includeMetadataChanges: true },
      (snapshot) => {
        const nextRemote = new Map<string, RemoteDocState>();
        snapshot.docs.forEach((d) => {
          const data = d.data();
          const payload = (data.payload && typeof data.payload === 'object'
            ? data.payload
            : {}) as Record<string, unknown>;
          nextRemote.set(d.id, {
            recordId: d.id,
            businessId: String(data.businessId || 'default'),
            collectionKey: String(data.collectionKey || ''),
            itemId: String(data.itemId || ''),
            payload,
            deleted: Boolean(data.deleted),
            updatedAt: Number(data.updatedAt) || 0,
            payloadJson: JSON.stringify(payload),
          });
        });

        this.remoteDocs = nextRemote;
        const applied = this.mergeRemoteIntoLocal(nextRemote, getActiveBusinessId());

        this.updateStatus({
          syncing: false,
          hasPendingWrites: snapshot.metadata.hasPendingWrites,
          syncedCount: Array.from(nextRemote.values()).filter((r) => !r.deleted).length,
          lastSyncedAt: new Date().toLocaleTimeString('bn-BD', {
            hour: '2-digit',
            minute: '2-digit',
          }),
          error: null,
        });

        if (applied && this.dataAppliedCallback) {
          this.dataAppliedCallback();
        }

        if (isFirstSnapshot && !snapshot.metadata.fromCache) {
          isFirstSnapshot = false;
          this.pushAllLocalChanges().catch(() => {});
        } else if (isFirstSnapshot && !this.status.online) {
          isFirstSnapshot = false;
        }
      },
      (error) => {
        this.updateStatus({
          syncing: false,
          error: error.message,
        });
        try {
          handleFirestoreError(error, OperationType.LIST, recordsPath);
        } catch {
          // Structured error already logged by handleFirestoreError and surfaced via syncStatus.error
        }
      }
    );
  }

  private mergeRemoteIntoLocal(remoteMap: Map<string, RemoteDocState>, businessId: string): boolean {
    const tsMap = loadMap(SYNC_TS_KEY);
    const tombMap = loadMap(SYNC_TOMB_KEY);
    let anyChanged = false;

    const remoteByCollection = new Map<string, Map<string, RemoteDocState>>();
    remoteMap.forEach((docState) => {
      if (docState.businessId !== businessId) return;
      let colMap = remoteByCollection.get(docState.collectionKey);
      if (!colMap) {
        colMap = new Map();
        remoteByCollection.set(docState.collectionKey, colMap);
      }
      colMap.set(docState.recordId, docState);
    });

    withRemoteSyncSuppression(() => {
      COLLECTION_SPECS.forEach((spec) => {
        const remoteCol = remoteByCollection.get(spec.collectionKey);
        const storageKey = getScopedKey(spec.namespace, spec.key, businessId);

        if (spec.kind === 'string') {
          const itemId = spec.key;
          const recordId = makeRecordId(businessId, spec.collectionKey, itemId);
          const remoteDoc = remoteCol?.get(recordId);
          if (!remoteDoc || remoteDoc.deleted) return;
          const remoteVal = String(remoteDoc.payload.value ?? '');
          const localVal = localStorage.getItem(storageKey);
          const localTs = tsMap[recordId] || 0;
          if (remoteVal && (localVal === null || remoteDoc.updatedAt >= localTs) && localVal !== remoteVal) {
            localStorage.setItem(storageKey, remoteVal);
            tsMap[recordId] = remoteDoc.updatedAt;
            anyChanged = true;
          }
          return;
        }

        if (spec.kind === 'number_array') {
          const itemId = spec.key;
          const recordId = makeRecordId(businessId, spec.collectionKey, itemId);
          const remoteDoc = remoteCol?.get(recordId);
          if (!remoteDoc || remoteDoc.deleted) return;
          const remoteIds = Array.isArray(remoteDoc.payload.ids)
            ? (remoteDoc.payload.ids as number[])
            : [];
          const localRaw = localStorage.getItem(storageKey);
          const localIds: number[] = localRaw ? JSON.parse(localRaw) : [];
          const mergedIds = Array.from(new Set([...localIds, ...remoteIds])).sort((a, b) => a - b);
          if (JSON.stringify(mergedIds) !== JSON.stringify(localIds)) {
            localStorage.setItem(storageKey, JSON.stringify(mergedIds));
            tsMap[recordId] = Math.max(tsMap[recordId] || 0, remoteDoc.updatedAt);
            anyChanged = true;
          }
          return;
        }

        if (spec.kind === 'object') {
          const itemId = spec.key;
          const recordId = makeRecordId(businessId, spec.collectionKey, itemId);
          const remoteDoc = remoteCol?.get(recordId);
          if (!remoteDoc || remoteDoc.deleted) return;
          const localRaw = localStorage.getItem(storageKey);
          const localObj = localRaw ? (JSON.parse(localRaw) as Record<string, unknown>) : null;
          const localTs = localObj
            ? Number(localObj.updatedAt) || tsMap[recordId] || 0
            : 0;
          if (!localObj || remoteDoc.updatedAt > localTs) {
            const nextJson = JSON.stringify(remoteDoc.payload);
            if (localRaw !== nextJson) {
              localStorage.setItem(storageKey, nextJson);
              tsMap[recordId] = remoteDoc.updatedAt;
              anyChanged = true;
            }
          }
          return;
        }

        // Array collection merge (products, sales, transactions, returns, etc.)
        const localEntries = readLocalCollectionEntries(spec, businessId);
        const mergedByRecordId = new Map<string, { payload: Record<string, unknown>; ts: number }>();

        localEntries.forEach((le) => {
          const rDoc = remoteCol?.get(le.recordId);
          const localTs = Math.max(le.updatedAt, tsMap[le.recordId] || 0);
          if (rDoc?.deleted) {
            if (rDoc.updatedAt >= localTs) {
              // Deleted remotely more recently than local edit
              tombMap[le.recordId] = rDoc.updatedAt;
              anyChanged = true;
              return;
            }
          }
          mergedByRecordId.set(le.recordId, { payload: le.payload, ts: localTs });
        });

        remoteCol?.forEach((rDoc) => {
          if (rDoc.deleted) return;
          const tombTs = tombMap[rDoc.recordId] || 0;
          if (tombTs >= rDoc.updatedAt) return; // Locally deleted after remote edit

          const existing = mergedByRecordId.get(rDoc.recordId);
          if (!existing) {
            mergedByRecordId.set(rDoc.recordId, {
              payload: rDoc.payload,
              ts: rDoc.updatedAt,
            });
            tsMap[rDoc.recordId] = rDoc.updatedAt;
            anyChanged = true;
          } else if (rDoc.updatedAt > existing.ts) {
            if (JSON.stringify(existing.payload) !== rDoc.payloadJson) {
              mergedByRecordId.set(rDoc.recordId, {
                payload: rDoc.payload,
                ts: rDoc.updatedAt,
              });
              tsMap[rDoc.recordId] = rDoc.updatedAt;
              anyChanged = true;
            }
          }
        });

        const nextArr = Array.from(mergedByRecordId.values()).map((x) => x.payload);
        const currentArrJson = localStorage.getItem(storageKey) || '[]';
        const nextArrJson = JSON.stringify(nextArr);
        if (currentArrJson !== nextArrJson && (nextArr.length > 0 || localEntries.length > 0)) {
          localStorage.setItem(storageKey, nextArrJson);
          anyChanged = true;
        }
      });
    });

    if (anyChanged) {
      saveMap(SYNC_TS_KEY, tsMap);
      saveMap(SYNC_TOMB_KEY, tombMap);
      this.snapshotLocalHashes(businessId);
    }

    return anyChanged;
  }

  private async flushPendingPushes(): Promise<void> {
    const user = auth.currentUser;
    if (!user) return;
    await this.ensureShopDocument(user);

    const businessId = getActiveBusinessId();
    const specKeys = Array.from(this.pendingPushSpecs);
    this.pendingPushSpecs.clear();

    const specsToPush = COLLECTION_SPECS.filter((s) =>
      specKeys.includes(`${businessId}:${s.namespace}:${s.key}`)
    );
    await this.pushSpecs(user, businessId, specsToPush);
  }

  private async pushAllLocalChanges(): Promise<void> {
    const user = auth.currentUser;
    if (!user) return;
    await this.ensureShopDocument(user);
    await this.pushSpecs(user, getActiveBusinessId(), COLLECTION_SPECS);
  }

  private async pushSpecs(user: User, businessId: string, specs: CollectionSpec[]): Promise<void> {
    if (specs.length === 0) return;
    const tsMap = loadMap(SYNC_TS_KEY);
    const tombMap = loadMap(SYNC_TOMB_KEY);

    interface PendingOp {
      recordId: string;
      isNew: boolean;
      data: {
        ownerUid: string;
        businessId: string;
        collectionKey: string;
        itemId: string;
        payload: Record<string, unknown>;
        deleted: boolean;
        updatedAt: number;
      };
    }

    const ops: PendingOp[] = [];
    const colKeys = new Set(specs.map((s) => s.collectionKey));
    const currentRecordIds = new Set<string>();
    const safeBusinessId = businessId.replace(/[^a-zA-Z0-9_\-]/g, '_').slice(0, 64) || 'default';

    specs.forEach((spec) => {
      const entries = readLocalCollectionEntries(spec, businessId);
      entries.forEach((entry) => {
        currentRecordIds.add(entry.recordId);
        const localTs = Math.max(entry.updatedAt, tsMap[entry.recordId] || 1);
        const remote = this.remoteDocs.get(entry.recordId);
        const needsUpload =
          !remote ||
          remote.deleted ||
          (remote.payloadJson !== entry.payloadJson && localTs >= remote.updatedAt);

        if (needsUpload) {
          ops.push({
            recordId: entry.recordId,
            isNew: !remote,
            data: {
              ownerUid: user.uid,
              businessId: safeBusinessId,
              collectionKey: entry.collectionKey,
              itemId: entry.itemId,
              payload: entry.payload,
              deleted: false,
              updatedAt: localTs,
            },
          });
        }
      });
    });

    // Push tombstones for deleted items
    this.remoteDocs.forEach((remote) => {
      if (remote.businessId !== safeBusinessId && remote.businessId !== businessId) return;
      if (!colKeys.has(remote.collectionKey)) return;
      if (!remote.deleted && !currentRecordIds.has(remote.recordId)) {
        const tombTs = tombMap[remote.recordId];
        if (tombTs && tombTs > remote.updatedAt) {
          ops.push({
            recordId: remote.recordId,
            isNew: false,
            data: {
              ownerUid: user.uid,
              businessId: safeBusinessId,
              collectionKey: remote.collectionKey,
              itemId: remote.itemId,
              payload: {},
              deleted: true,
              updatedAt: tombTs,
            },
          });
        }
      }
    });

    if (ops.length === 0) return;

    this.updateStatus({ syncing: true });
    const CHUNK = 350;
    try {
      for (let i = 0; i < ops.length; i += CHUNK) {
        const batch = writeBatch(db);
        const chunk = ops.slice(i, i + CHUNK);
        chunk.forEach((op) => {
          const ref = doc(db, 'shops', user.uid, 'records', op.recordId);
          if (op.isNew) {
            batch.set(ref, {
              ...op.data,
              serverCreatedAt: serverTimestamp(),
              serverUpdatedAt: serverTimestamp(),
            });
          } else {
            batch.update(ref, {
              payload: op.data.payload,
              deleted: op.data.deleted,
              updatedAt: op.data.updatedAt,
              serverUpdatedAt: serverTimestamp(),
            });
          }
        });
        await batch.commit();
      }
      this.updateStatus({
        syncing: false,
        lastSyncedAt: new Date().toLocaleTimeString('bn-BD', {
          hour: '2-digit',
          minute: '2-digit',
        }),
        error: null,
      });
    } catch (error) {
      this.updateStatus({ syncing: false });
      const msg = error instanceof Error ? error.message : '';
      if (msg.includes('offline') || (typeof navigator !== 'undefined' && !navigator.onLine)) {
        // Offline mutations stay queued in Firestore persistentLocalCache automatically
        this.updateStatus({ hasPendingWrites: true });
        return;
      }
      handleFirestoreError(error, OperationType.WRITE, `shops/${user.uid}/records`);
    }
  }
}

export const cloudSync = new CloudSyncService();
