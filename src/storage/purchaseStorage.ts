import { Purchase, Supplier, SupplierDueEntry } from '../types';
import { nowDateTime } from '../utils/format';
import { readJson, writeJson } from './businessStorage';

const PUR_NS = 'tamanna_enterprise_purchases';
const SUP_DUE_NS = 'tamanna_supplier_due';

export const PurchaseStorage = {
  getPurchases(): Purchase[] {
    const list = readJson<Purchase[]>(PUR_NS, 'purchases', []);
    return [...list].sort((a, b) => b.id - a.id);
  },

  addPurchase(purchase: Purchase): number {
    const purchases = this.getPurchases();
    const usedIds = new Set(purchases.map((p) => p.id));
    let uniqueId = purchase.id || Date.now();
    while (usedIds.has(uniqueId)) uniqueId++;
    const normalized: Purchase = { ...purchase, id: uniqueId };
    const saved = writeJson(PUR_NS, 'purchases', [...purchases, normalized]);
    return saved ? uniqueId : 0;
  },

  removeById(id: number): boolean {
    const purchases = this.getPurchases();
    const filtered = purchases.filter((p) => p.id !== id);
    if (filtered.length === purchases.length) return false;
    return writeJson(PUR_NS, 'purchases', filtered);
  },
};

export const SupplierDueStorage = {
  getEntries(): SupplierDueEntry[] {
    const list = readJson<SupplierDueEntry[]>(SUP_DUE_NS, 'entries', []);
    return [...list].sort((a, b) => b.id - a.id);
  },

  getSuppliers(): Supplier[] {
    const list = readJson<Supplier[]>(SUP_DUE_NS, 'suppliers', []);
    return [...list].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  },

  addSupplier(name: string, mobile: string, address: string): boolean {
    const n = name.trim();
    const m = mobile.trim();
    if (!n) return false;
    const list = this.getSuppliers();
    if (list.some((s) => s.name.toLowerCase() === n.toLowerCase() && s.mobile === m)) return false;
    const used = new Set(list.map((s) => s.id));
    let id = Date.now();
    while (used.has(id)) id++;
    return writeJson(SUP_DUE_NS, 'suppliers', [...list, { id, name: n, mobile: m, address: address.trim() }]);
  },

  ensureSupplier(name: string): void {
    const clean = name.trim();
    if (!clean) return;
    if (this.getSuppliers().some((s) => s.name.toLowerCase() === clean.toLowerCase())) return;
    this.addSupplier(clean, '', '');
  },

  addPurchaseDue(supplier: string, amount: number, note: string, purchaseId = 0): number {
    if (!supplier.trim() || amount <= 0) return 0;
    const id = this._addEntry({
      id: Date.now(),
      date: nowDateTime(),
      supplier: supplier.trim(),
      type: 'PURCHASE',
      amount,
      note,
      purchaseId,
    });
    if (id > 0) this.ensureSupplier(supplier);
    return id;
  },

  addPayment(supplier: string, amount: number, note: string): number {
    if (!supplier.trim() || amount <= 0) return 0;
    const id = this._addEntry({
      id: Date.now(),
      date: nowDateTime(),
      supplier: supplier.trim(),
      type: 'PAYMENT',
      amount: -amount,
      note,
      purchaseId: 0,
    });
    if (id > 0) this.ensureSupplier(supplier);
    return id;
  },

  removePaymentById(id: number): boolean {
    if (id <= 0) return false;
    const entries = this.getEntries();
    if (!entries.some((e) => e.id === id && e.type === 'PAYMENT')) return false;
    return writeJson(
      SUP_DUE_NS,
      'entries',
      entries.filter((e) => !(e.id === id && e.type === 'PAYMENT'))
    );
  },

  removeById(id: number): boolean {
    if (id <= 0) return false;
    const entries = this.getEntries();
    if (!entries.some((e) => e.id === id)) return false;
    return writeJson(
      SUP_DUE_NS,
      'entries',
      entries.filter((e) => e.id !== id)
    );
  },

  getBalance(supplier: string): number {
    const sum = this.getEntries()
      .filter((e) => e.supplier.toLowerCase() === supplier.trim().toLowerCase())
      .reduce((acc, e) => acc + e.amount, 0);
    return Math.max(0, sum);
  },

  getBalances(): Array<{ name: string; balance: number }> {
    return this.getSuppliers()
      .map((s) => ({ name: s.name, balance: this.getBalance(s.name) }))
      .sort((a, b) => b.balance - a.balance);
  },

  _addEntry(entry: SupplierDueEntry): number {
    const entries = this.getEntries();
    const usedIds = new Set(entries.map((e) => e.id));
    let uniqueId = entry.id || Date.now();
    while (usedIds.has(uniqueId)) uniqueId++;
    const normalized = { ...entry, id: uniqueId };
    return writeJson(SUP_DUE_NS, 'entries', [...entries, normalized]) ? uniqueId : 0;
  },
};
