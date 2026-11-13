import { Customer, DueEntry } from '../types';
import { nowDateTime } from '../utils/format';
import { readJson, writeJson } from './businessStorage';

const DUE_NS = 'tamanna_customer_due';

export const CustomerDueStorage = {
  getEntries(): DueEntry[] {
    const list = readJson<DueEntry[]>(DUE_NS, 'entries', []);
    return [...list].sort((a, b) => b.id - a.id);
  },

  getCustomers(): Customer[] {
    const list = readJson<Customer[]>(DUE_NS, 'customers', []);
    return [...list].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  },

  addCustomer(name: string, mobile: string): boolean {
    const cleanName = name.trim();
    const cleanMobile = mobile.trim();
    if (!cleanName) return false;
    const customers = this.getCustomers();
    if (customers.some((c) => c.name.toLowerCase() === cleanName.toLowerCase() && c.mobile === cleanMobile)) {
      return false;
    }
    const usedIds = new Set(customers.map((c) => c.id));
    let id = Date.now();
    while (usedIds.has(id)) id++;
    return writeJson(DUE_NS, 'customers', [...customers, { id, name: cleanName, mobile: cleanMobile }]);
  },

  ensureCustomer(name: string, mobile: string): void {
    const cleanName = name.trim();
    const cleanMobile = mobile.trim();
    if (!cleanName) return;
    if (
      this.getCustomers().some(
        (c) => c.name.toLowerCase() === cleanName.toLowerCase() && c.mobile === cleanMobile
      )
    ) {
      return;
    }
    this.addCustomer(cleanName, cleanMobile);
  },

  addSaleDue(customer: string, mobile: string, amount: number, note: string, transactionId?: string): number {
    if (!customer.trim() || amount <= 0) return 0;
    const entries = this.getEntries();
    const usedIds = new Set(entries.map((e) => e.id));
    let id = Date.now();
    while (usedIds.has(id)) id++;
    const entry: DueEntry = {
      id,
      date: nowDateTime(),
      customer: customer.trim(),
      mobile: mobile.trim(),
      type: 'SALE',
      amount,
      note,
      ...(transactionId ? { transactionId } : {}),
    };
    const ok = writeJson(DUE_NS, 'entries', [...entries, entry]);
    if (ok) this.ensureCustomer(customer, mobile);
    return ok ? id : 0;
  },

  adjustSaleDueByTransaction(
    transactionId: string,
    customer: string,
    mobile: string,
    oldDue: number,
    newDue: number
  ): boolean {
    const cleanCustomer = customer.trim();
    const cleanMobile = mobile.trim();
    if (!cleanCustomer && newDue > 0.004) return false;

    const entries = this.getEntries();
    const normalizedOld = Math.max(0, Math.round(oldDue * 100) / 100);
    const normalizedNew = Math.max(0, Math.round(newDue * 100) / 100);

    let matchIndex = entries.findIndex(
      (e) => e.type === 'SALE' && e.transactionId === transactionId
    );
    if (matchIndex < 0 && normalizedOld > 0.004) {
      matchIndex = entries.findIndex(
        (e) =>
          e.type === 'SALE' &&
          !e.transactionId &&
          e.customer.trim().toLowerCase() === cleanCustomer.toLowerCase() &&
          e.mobile.trim() === cleanMobile &&
          Math.abs(e.amount - normalizedOld) < 0.05
      );
    }

    if (matchIndex >= 0) {
      const target = entries[matchIndex];
      if (normalizedNew <= 0.004) {
        return writeJson(
          DUE_NS,
          'entries',
          entries.filter((e) => e.id !== target.id)
        );
      }
      const updated = entries.map((e) =>
        e.id === target.id
          ? { ...e, amount: normalizedNew, transactionId }
          : e
      );
      return writeJson(DUE_NS, 'entries', updated);
    }

    if (normalizedNew <= 0.004 && normalizedOld <= 0.004) return true;

    const delta = Math.round((normalizedNew - normalizedOld) * 100) / 100;
    if (delta > 0.004) {
      return (
        this.addSaleDue(
          cleanCustomer,
          cleanMobile,
          delta,
          `ইনভয়েস সমন্বয়: ${transactionId}`,
          transactionId
        ) > 0
      );
    }
    if (delta < -0.004) {
      return (
        this.addPayment(
          cleanCustomer,
          cleanMobile,
          Math.abs(delta),
          `ইনভয়েস সমন্বয়: ${transactionId}`
        ) > 0
      );
    }
    return true;
  },

  addPayment(customer: string, mobile: string, amount: number, note: string): number {
    if (!customer.trim() || amount <= 0) return 0;
    const entries = this.getEntries();
    const usedIds = new Set(entries.map((e) => e.id));
    let id = Date.now();
    while (usedIds.has(id)) id++;
    const entry: DueEntry = {
      id,
      date: nowDateTime(),
      customer: customer.trim(),
      mobile: mobile.trim(),
      type: 'PAYMENT',
      amount,
      note,
    };
    const ok = writeJson(DUE_NS, 'entries', [...entries, entry]);
    if (ok) this.ensureCustomer(customer, mobile);
    return ok ? id : 0;
  },

  getBalance(customer: string, mobile = ''): number {
    const cleanC = customer.trim().toLowerCase();
    const cleanM = mobile.trim();
    const sum = this.getEntries()
      .filter((e) => e.customer.trim().toLowerCase() === cleanC && e.mobile.trim() === cleanM)
      .reduce((acc, e) => acc + (e.type === 'SALE' ? e.amount : -e.amount), 0);
    return Math.max(0, sum);
  },

  getBalances(): Record<string, number> {
    const map: Record<string, number> = {};
    this.getEntries().forEach((e) => {
      const key = e.customer.trim() + (e.mobile.trim() ? ' • ' + e.mobile.trim() : '');
      map[key] = (map[key] ?? 0) + (e.type === 'SALE' ? e.amount : -e.amount);
    });
    const positive: Record<string, number> = {};
    Object.entries(map).forEach(([k, v]) => {
      if (v > 0.004) positive[k] = v;
    });
    return positive;
  },

  removePaymentById(id: number): boolean {
    if (id <= 0) return false;
    const entries = this.getEntries();
    if (!entries.some((e) => e.id === id && e.type === 'PAYMENT')) return false;
    return writeJson(
      DUE_NS,
      'entries',
      entries.filter((e) => !(e.id === id && e.type === 'PAYMENT'))
    );
  },

  removeSaleDueById(id: number): boolean {
    if (id <= 0) return false;
    const entries = this.getEntries();
    if (!entries.some((e) => e.id === id && e.type === 'SALE')) return false;
    return writeJson(
      DUE_NS,
      'entries',
      entries.filter((e) => !(e.id === id && e.type === 'SALE'))
    );
  },
};
