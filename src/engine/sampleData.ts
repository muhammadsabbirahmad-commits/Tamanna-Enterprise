import {
  ActivityLogStorage,
  SettingsStorage,
  notifyBulkStorageChanged,
  writeJson,
} from '../storage/businessStorage';
import { CustomerDueStorage } from '../storage/dueStorage';
import {
  CashAccountStorage,
  DailyBalanceStorage,
  ExpenseStorage,
  OpeningBalanceStorage,
  PartnerStorage,
} from '../storage/financeStorage';
import { InventoryMetaStorage, ProductStorage } from '../storage/inventoryStorage';
import { PurchaseStorage, SupplierDueStorage } from '../storage/purchaseStorage';
import { SaleReturnStorage, SalesStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { daysAgoIso, todayIso } from '../utils/format';

export function exportAllBackupJson(): string {
  const dump: Record<string, string> = {};
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key && (key.startsWith('tamanna_') || key.startsWith('biz_'))) {
      dump[key] = localStorage.getItem(key) ?? '';
    }
  }
  return JSON.stringify(
    {
      app: 'Tamanna Enterprise',
      exportedAt: new Date().toISOString(),
      version: 1,
      data: dump,
    },
    null,
    2
  );
}

const ARRAY_ID_EXTRACTORS: Array<{
  suffix: string;
  getId: (item: Record<string, unknown>) => string;
}> = [
  { suffix: 'tamanna_enterprise_products:products', getId: (x) => String(x.code || '').trim().toUpperCase() },
  { suffix: 'tamanna_inventory_meta:meta', getId: (x) => String(x.code || '').trim().toUpperCase() },
  { suffix: 'tamanna_inventory_meta:history', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_enterprise_sales:sales', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_enterprise_sale_transactions:transactions', getId: (x) => String(x.transactionId || '') },
  { suffix: 'tamanna_enterprise_sale_returns:returns', getId: (x) => String(x.returnId || '') },
  {
    suffix: 'tamanna_enterprise_sale_returns:lines',
    getId: (x) => `${String(x.returnId || '')}__${String(x.productCode || '').trim().toUpperCase()}`,
  },
  { suffix: 'tamanna_enterprise_sale_returns:exchanges', getId: (x) => String(x.exchangeId || '') },
  { suffix: 'tamanna_enterprise_purchases:purchases', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_supplier_due:suppliers', getId: (x) => String(x.id || x.name || '') },
  { suffix: 'tamanna_supplier_due:entries', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_customer_due:customers', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_customer_due:entries', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_enterprise_finance:expenses', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_enterprise_finance:damages', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_partner_storage:partners', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_partner_storage:partner_profit_rate_history', getId: (x) => String(x.effectiveDate || '') },
  { suffix: 'tamanna_partner_storage:partner_capital_contributions', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_partner_storage:partner_profit_withdrawals', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_partner_storage:partner_capitalized_profit_periods', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_manual_cash_accounts:accounts', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_daily_manual_balances:entries', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_enterprise_activity_log:logs', getId: (x) => String(x.id || '') },
  { suffix: 'tamanna_enterprise_security:users', getId: (x) => String(x.id || '') },
];

function getRecordTimestamp(item: Record<string, unknown>): number {
  return Number(item.updatedAt) || Number(item.createdAt) || 0;
}

function mergeArrayJson(existingRaw: string | null, incomingRaw: string, getId: (x: Record<string, unknown>) => string): { json: string; addedOrUpdated: number } {
  const existingArr = existingRaw ? (JSON.parse(existingRaw) as unknown) : [];
  const incomingArr = JSON.parse(incomingRaw) as unknown;
  if (!Array.isArray(incomingArr)) return { json: existingRaw ?? '[]', addedOrUpdated: 0 };
  if (!Array.isArray(existingArr) || existingArr.length === 0) {
    return { json: incomingRaw, addedOrUpdated: incomingArr.length };
  }

  const byId = new Map<string, Record<string, unknown>>();
  const extras: unknown[] = [];

  existingArr.forEach((rawItem) => {
    if (!rawItem || typeof rawItem !== 'object') {
      extras.push(rawItem);
      return;
    }
    const obj = rawItem as Record<string, unknown>;
    const id = getId(obj);
    if (id) byId.set(id, obj);
    else extras.push(obj);
  });

  let addedOrUpdated = 0;
  incomingArr.forEach((rawItem) => {
    if (!rawItem || typeof rawItem !== 'object') return;
    const obj = rawItem as Record<string, unknown>;
    const id = getId(obj);
    if (!id) return;
    const cur = byId.get(id);
    if (!cur) {
      byId.set(id, obj);
      addedOrUpdated++;
    } else if (getRecordTimestamp(obj) > getRecordTimestamp(cur)) {
      byId.set(id, obj);
      addedOrUpdated++;
    }
  });

  return {
    json: JSON.stringify([...Array.from(byId.values()), ...extras]),
    addedOrUpdated,
  };
}

export function previewBackupJson(raw: string): {
  ok: boolean;
  keyCount: number;
  productCount: number;
  saleCount: number;
  error?: string;
} {
  try {
    const parsed = JSON.parse(raw) as { data?: Record<string, string> };
    if (!parsed || typeof parsed.data !== 'object' || !parsed.data) {
      return { ok: false, keyCount: 0, productCount: 0, saleCount: 0, error: 'অবৈধ ব্যাকআপ ফাইল ফরম্যাট।' };
    }
    let keyCount = 0;
    let productCount = 0;
    let saleCount = 0;
    Object.entries(parsed.data).forEach(([k, v]) => {
      if ((k.startsWith('tamanna_') || k.startsWith('biz_')) && typeof v === 'string') {
        keyCount++;
        if (k.endsWith('tamanna_enterprise_products:products')) {
          try {
            const arr = JSON.parse(v);
            if (Array.isArray(arr)) productCount += arr.length;
          } catch {
            // ignore
          }
        }
        if (k.endsWith('tamanna_enterprise_sale_transactions:transactions')) {
          try {
            const arr = JSON.parse(v);
            if (Array.isArray(arr)) saleCount += arr.length;
          } catch {
            // ignore
          }
        }
      }
    });
    return { ok: true, keyCount, productCount, saleCount };
  } catch {
    return { ok: false, keyCount: 0, productCount: 0, saleCount: 0, error: 'JSON ফাইলটি পার্স করা যায়নি।' };
  }
}

export function importAllBackupJson(
  raw: string,
  mode: 'merge' | 'replace' = 'merge'
): { ok: boolean; count: number; error?: string } {
  try {
    const parsed = JSON.parse(raw) as { data?: Record<string, string> };
    if (!parsed || typeof parsed.data !== 'object' || !parsed.data) {
      return { ok: false, count: 0, error: 'অবৈধ ব্যাকআপ ফাইল ফরম্যাট।' };
    }

    let count = 0;
    Object.entries(parsed.data).forEach(([k, v]) => {
      if (!(k.startsWith('tamanna_') || k.startsWith('biz_')) || typeof v !== 'string') return;
      if (k.startsWith('tamanna_sync_meta:')) return;

      if (mode === 'replace') {
        localStorage.setItem(k, v);
        count++;
        return;
      }

      const existingRaw = localStorage.getItem(k);
      if (existingRaw === null) {
        localStorage.setItem(k, v);
        count++;
        return;
      }

      const extractor = ARRAY_ID_EXTRACTORS.find((e) => k.endsWith(e.suffix));
      if (extractor) {
        try {
          const merged = mergeArrayJson(existingRaw, v, extractor.getId);
          if (merged.addedOrUpdated > 0) {
            localStorage.setItem(k, merged.json);
            count += merged.addedOrUpdated;
          }
        } catch {
          // keep existing data untouched on parse mismatch
        }
        return;
      }

      if (
        k.endsWith('tamanna_enterprise_finance:deleted_expense_ids') ||
        k.endsWith('tamanna_partner_storage:deleted_partner_ids')
      ) {
        try {
          const curIds = JSON.parse(existingRaw) as number[];
          const incIds = JSON.parse(v) as number[];
          const union = Array.from(new Set([...(Array.isArray(curIds) ? curIds : []), ...(Array.isArray(incIds) ? incIds : [])])).sort(
            (a, b) => a - b
          );
          if (JSON.stringify(union) !== JSON.stringify(curIds)) {
            localStorage.setItem(k, JSON.stringify(union));
            count++;
          }
        } catch {
          // keep existing
        }
        return;
      }

      if (k.endsWith('tamanna_enterprise_products:next_product_code')) {
        const curNum = parseInt(existingRaw, 10) || 0;
        const incNum = parseInt(v, 10) || 0;
        if (incNum > curNum) {
          localStorage.setItem(k, String(incNum));
          count++;
        }
        return;
      }

      if (k.endsWith('tamanna_opening_balances:opening_balance_v1')) {
        try {
          const curObj = JSON.parse(existingRaw) as Record<string, unknown>;
          const incObj = JSON.parse(v) as Record<string, unknown>;
          if (getRecordTimestamp(incObj) > getRecordTimestamp(curObj)) {
            localStorage.setItem(k, v);
            count++;
          }
        } catch {
          // keep existing
        }
        return;
      }

      if (k.endsWith('tamanna_enterprise_settings:shop_name')) {
        if ((!existingRaw.trim() || existingRaw.trim() === 'Tamanna Enterprise') && v.trim() && v.trim() !== 'Tamanna Enterprise') {
          localStorage.setItem(k, v);
          count++;
        }
      }
      // For existing theme, current_user, pin prefs, etc.: keep existing local value untouched in merge mode
    });

    if (count > 0) notifyBulkStorageChanged();
    return { ok: true, count };
  } catch {
    return { ok: false, count: 0, error: 'JSON ফাইলটি পার্স করা যায়নি।' };
  }
}

export function clearAllBusinessData(): void {
  const toRemove: string[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (
      key &&
      (key.startsWith('tamanna_enterprise_products') ||
        key.startsWith('tamanna_inventory_meta') ||
        key.startsWith('tamanna_enterprise_sales') ||
        key.startsWith('tamanna_enterprise_sale_transactions') ||
        key.startsWith('tamanna_enterprise_sale_returns') ||
        key.startsWith('tamanna_enterprise_purchases') ||
        key.startsWith('tamanna_supplier_due') ||
        key.startsWith('tamanna_customer_due') ||
        key.startsWith('tamanna_partner_storage') ||
        key.startsWith('tamanna_enterprise_finance') ||
        key.startsWith('tamanna_manual_cash_accounts') ||
        key.startsWith('tamanna_opening_balances') ||
        key.startsWith('tamanna_daily_manual_balances') ||
        key.startsWith('tamanna_enterprise_activity_log'))
    ) {
      toRemove.push(key);
    }
  }
  toRemove.forEach((k) => localStorage.removeItem(k));
  notifyBulkStorageChanged();
}

export function seedSampleBusinessData(): void {
  clearAllBusinessData();
  const today = todayIso();
  const yesterday = daysAgoIso(1);
  const threeDaysAgo = daysAgoIso(3);

  SettingsStorage.saveShopName('Tamanna Enterprise');

  // 1. Partners (60% / 40%)
  const p1 = { id: 1001, name: 'সাব্বির আহমেদ', shareAmount: 250000, profitPercentage: 0, note: 'প্রতিষ্ঠাতা ও ব্যবস্থাপনা অংশীদার' };
  const p2 = { id: 1002, name: 'তামান্না আক্তার', shareAmount: 150000, profitPercentage: 0, note: 'বিনিয়োগ অংশীদার' };
  PartnerStorage.addPartner(p1);
  PartnerStorage.addPartner(p2);
  PartnerStorage.saveProfitDistribution({ 1001: 60, 1002: 40 }, threeDaysAgo > today ? today : today);
  // Set historical effective rate from 30 days ago directly via scoped writeJson so past-date expenses allocate cleanly
  writeJson('tamanna_partner_storage', 'partner_profit_rate_history', [
    { effectiveDate: daysAgoIso(30), percentages: { 1001: 60, 1002: 40 }, updatedAt: Date.now() },
  ]);
  writeJson('tamanna_partner_storage', 'partners', [
    { ...p1, profitPercentage: 60, updatedAt: Date.now() },
    { ...p2, profitPercentage: 40, updatedAt: Date.now() },
  ]);
  writeJson('tamanna_partner_storage', 'partner_capital_contributions', [
    {
      id: 'cap-1',
      partnerId: 1001,
      amount: 250000,
      date: threeDaysAgo,
      note: 'প্রাথমিক মূলধন জমা',
      createdAt: Date.now() - 300000,
      approximateDate: false,
    },
    {
      id: 'cap-2',
      partnerId: 1002,
      amount: 150000,
      date: threeDaysAgo,
      note: 'প্রাথমিক মূলধন জমা',
      createdAt: Date.now() - 290000,
      approximateDate: false,
    },
  ]);

  // 2. Products
  const sampleProducts = [
    { code: 'P-228622', name: 'প্রিমিয়াম বাসমতি চাল (৫ কেজি)', purchasePrice: 680, salePrice: 780, stockQuantity: 24 },
    { code: 'P-228623', name: 'সয়াবিন তেল (৫ লিটার)', purchasePrice: 820, salePrice: 900, stockQuantity: 18 },
    { code: 'P-228624', name: 'মসুর ডাল দেশি (১ কেজি)', purchasePrice: 125, salePrice: 150, stockQuantity: 4 },
    { code: 'P-228625', name: 'আটা প্যাকেট (২ কেজি)', purchasePrice: 95, salePrice: 118, stockQuantity: 35 },
    { code: 'P-228626', name: 'চিনি রিফাইন্ড (১ কেজি)', purchasePrice: 122, salePrice: 140, stockQuantity: 2 },
  ];
  sampleProducts.forEach((p) => ProductStorage.addProduct(p));

  InventoryMetaStorage.saveMeta({ code: 'P-228622', batch: 'B-2026-01', expiryDate: daysAgoIso(-180), lowStockLimit: 8, damagedQuantity: 0 });
  InventoryMetaStorage.saveMeta({ code: 'P-228624', batch: 'B-2026-02', expiryDate: daysAgoIso(-20), lowStockLimit: 10, damagedQuantity: 0 });
  InventoryMetaStorage.saveMeta({ code: 'P-228626', batch: 'B-2026-03', expiryDate: daysAgoIso(-90), lowStockLimit: 5, damagedQuantity: 0 });

  // 3. Purchases & Supplier Dues
  const pur1Id = PurchaseStorage.addPurchase({
    id: Date.now() - 200000,
    date: `${threeDaysAgo} 10:15`,
    productCode: 'P-228622',
    productName: 'প্রিমিয়াম বাসমতি চাল (৫ কেজি)',
    quantity: 30,
    purchasePrice: 680,
    supplier: 'মেসার্স করিম ট্রেডার্স',
    memoNumber: 'MEMO-1042',
    paidAmount: 15000,
    dueAmount: 5400,
    settlementRecorded: true,
  });
  SupplierDueStorage.addPurchaseDue('মেসার্স করিম ট্রেডার্স', 5400, 'ক্রয়: প্রিমিয়াম বাসমতি চাল x30 • মেমো MEMO-1042', pur1Id);

  PurchaseStorage.addPurchase({
    id: Date.now() - 100000,
    date: `${today} 09:30`,
    productCode: 'P-228623',
    productName: 'সয়াবিন তেল (৫ লিটার)',
    quantity: 20,
    purchasePrice: 820,
    supplier: 'পদ্মা ডিস্ট্রিবিউটরস',
    memoNumber: 'MEMO-1089',
    paidAmount: 16400,
    dueAmount: 0,
    settlementRecorded: true,
  });

  // 4. Sales & Customer Dues
  const tx1 = 'TX-202611121015001';
  SalesTransactionStorage.addTransaction({
    transactionId: tx1,
    date: `${yesterday} 16:40`,
    customer: 'রহিম স্টোর',
    mobile: '01711223344',
    subtotal: 6420,
    discount: 120,
    total: 6300,
    paid: 4300,
    due: 2000,
    paymentMethod: 'Cash',
  });
  SalesStorage.addSale({
    id: Date.now() - 150000,
    transactionId: tx1,
    date: `${yesterday} 16:40`,
    productCode: 'P-228622',
    productName: 'প্রিমিয়াম বাসমতি চাল (৫ কেজি)',
    quantity: 6,
    salePrice: 780,
    purchasePrice: 680,
    customer: 'রহিম স্টোর • 01711223344',
  });
  SalesStorage.addSale({
    id: Date.now() - 149000,
    transactionId: tx1,
    date: `${yesterday} 16:40`,
    productCode: 'P-228625',
    productName: 'আটা প্যাকেট (২ কেজি)',
    quantity: 14,
    salePrice: 118,
    purchasePrice: 95,
    customer: 'রহিম স্টোর • 01711223344',
  });
  CustomerDueStorage.addSaleDue('রহিম স্টোর', '01711223344', 2000, 'বিক্রয়: প্রিমিয়াম বাসমতি চাল x6, আটা প্যাকেট x14');

  const tx2 = 'TX-202611131120002';
  SalesTransactionStorage.addTransaction({
    transactionId: tx2,
    date: `${today} 11:20`,
    customer: 'নগদ ক্রেতা',
    mobile: '',
    subtotal: 3180,
    discount: 0,
    total: 3180,
    paid: 3180,
    due: 0,
    paymentMethod: 'bKash',
  });
  SalesStorage.addSale({
    id: Date.now() - 50000,
    transactionId: tx2,
    date: `${today} 11:20`,
    productCode: 'P-228623',
    productName: 'সয়াবিন তেল (৫ লিটার)',
    quantity: 2,
    salePrice: 900,
    purchasePrice: 820,
    customer: 'নগদ ক্রেতা',
  });
  SalesStorage.addSale({
    id: Date.now() - 49000,
    transactionId: tx2,
    date: `${today} 11:20`,
    productCode: 'P-228624',
    productName: 'মসুর ডাল দেশি (১ কেজি)',
    quantity: 9,
    salePrice: 150,
    purchasePrice: 125,
    customer: 'নগদ ক্রেতা',
  });

  // 5. Operating Expenses & Manual Cash Accounts
  ExpenseStorage.addExpense({
    id: Date.now() - 80000,
    date: yesterday,
    category: 'দোকান ভাড়া ও বিদ্যুৎ',
    amount: 450,
    note: 'দৈনিক ইউটিলিটি খরচ',
  });
  ExpenseStorage.addExpense({
    id: Date.now() - 40000,
    date: today,
    category: 'পরিবহন খরচ',
    amount: 220,
    note: 'মালামাল আনা-নেওয়া',
  });

  CashAccountStorage.saveAccount({ id: 'cash-main', name: 'দোকানের ক্যাশ ড্রয়ার', balance: 62500, updatedAt: Date.now() });
  CashAccountStorage.saveAccount({ id: 'bank-islami', name: 'ইসলামী ব্যাংক কারেন্ট', balance: 185000, updatedAt: Date.now() });

  OpeningBalanceStorage.save({
    date: threeDaysAgo,
    cash: 250000,
    bank: 150000,
    stock: 0,
    customerReceivable: 0,
    supplierPayable: 0,
    otherLiabilities: 0,
    verified: true,
    updatedAt: Date.now(),
  });

  DailyBalanceStorage.save({
    id: 'db-cash-today',
    date: today,
    account: 'CASH',
    mode: 'SET',
    amount: 62500,
    note: 'আজকের সন্ধ্যার ক্যাশ গণনা',
    updatedAt: Date.now(),
    deleted: false,
  });
  DailyBalanceStorage.save({
    id: 'db-bank-today',
    date: today,
    account: 'BANK',
    mode: 'SET',
    amount: 185000,
    note: 'ব্যাংক ব্যালান্স আপডেট',
    updatedAt: Date.now(),
    deleted: false,
  });

  ActivityLogStorage.add('নমুনা ডাটা লোড', 'Tamanna Enterprise নমুনা ব্যবসায়িক ডাটা লোড করা হয়েছে');
}
