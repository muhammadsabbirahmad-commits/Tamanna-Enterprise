import {
  CapitalizedProfitPeriod,
  DailyBalanceEntry,
  DamageRecord,
  Expense,
  ManualCashAccount,
  OpeningBalances,
  Partner,
  PartnerCapitalContribution,
  PartnerExpenseAllocation,
  PartnerProfitWithdrawal,
  PartnerRateSnapshot,
} from '../types';
import { isValidIsoDate, moneyCents, todayIso } from '../utils/format';
import { readJson, writeJson } from './businessStorage';

const PARTNER_NS = 'tamanna_partner_storage';
const FIN_NS = 'tamanna_enterprise_finance';
const CASH_NS = 'tamanna_manual_cash_accounts';
const OB_NS = 'tamanna_opening_balances';
const DAILY_BAL_NS = 'tamanna_daily_manual_balances';

function uuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
  return 'id-' + Date.now() + '-' + Math.random().toString(36).slice(2, 9);
}

function dateFromTimestamp(ts: number): string {
  const d = new Date(ts);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export const PartnerStorage = {
  _deletedIds(): Set<number> {
    return new Set(readJson<number[]>(PARTNER_NS, 'deleted_partner_ids', []));
  },

  _addDeletedId(id: number): void {
    const ids = Array.from(new Set([...this._deletedIds(), id])).sort((a, b) => a - b);
    writeJson(PARTNER_NS, 'deleted_partner_ids', ids);
  },

  _clearDeletedId(id: number): void {
    const ids = [...this._deletedIds()].filter((x) => x !== id);
    writeJson(PARTNER_NS, 'deleted_partner_ids', ids);
  },

  getPartners(): Partner[] {
    const deleted = this._deletedIds();
    const list = readJson<Partner[]>(PARTNER_NS, 'partners', []).filter((p) => !deleted.has(p.id));
    return [...list].sort((a, b) => b.id - a.id);
  },

  getRateHistory(): PartnerRateSnapshot[] {
    const list = readJson<PartnerRateSnapshot[]>(PARTNER_NS, 'partner_profit_rate_history', []);
    return [...list].sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
  },

  getPercentageForDate(partnerId: number, date: string): number {
    const history = this.getRateHistory();
    let snapshot: PartnerRateSnapshot | undefined;
    for (const s of history) {
      if (s.effectiveDate <= date) snapshot = s;
    }
    if (snapshot) return snapshot.percentages[partnerId] ?? 0;
    return this.getPartners().find((p) => p.id === partnerId)?.profitPercentage ?? 0;
  },

  getCapitalContributions(): PartnerCapitalContribution[] {
    const raw = readJson<PartnerCapitalContribution[] | null>(
      PARTNER_NS,
      'partner_capital_contributions',
      null
    );
    if (raw === null) {
      const legacy = this.getPartners()
        .filter((p) => p.shareAmount > 0)
        .map((p) => ({
          id: `legacy-${p.id}`,
          partnerId: p.id,
          amount: p.shareAmount,
          date: dateFromTimestamp(p.id),
          note: 'পুরোনো রেকর্ড: জমার প্রকৃত তারিখ সংরক্ষিত ছিল না',
          createdAt: p.id,
          approximateDate: true,
        }));
      writeJson(PARTNER_NS, 'partner_capital_contributions', legacy);
      return legacy;
    }
    return [...raw].sort((a, b) => a.date.localeCompare(b.date) || a.createdAt - b.createdAt);
  },

  addPartner(partner: Omit<Partner, 'updatedAt'>): boolean {
    this.getCapitalContributions();
    if (!partner.name.trim() || partner.shareAmount < 0 || partner.profitPercentage !== 0) return false;
    const existing = this.getPartners();
    if (existing.some((p) => p.id === partner.id)) return false;
    this._clearDeletedId(partner.id);
    const now = Date.now();
    const newPartner: Partner = { ...partner, name: partner.name.trim(), updatedAt: now };
    const updatedPartners = [newPartner, ...existing];
    const contributions = [...this.getCapitalContributions()];
    if (partner.shareAmount > 0) {
      contributions.push({
        id: uuid(),
        partnerId: partner.id,
        amount: partner.shareAmount,
        date: todayIso(),
        note: 'প্রাথমিক মূলধন জমা',
        createdAt: now,
        approximateDate: false,
      });
    }
    return (
      writeJson(PARTNER_NS, 'partner_capital_contributions', contributions) &&
      writeJson(PARTNER_NS, 'partners', updatedPartners)
    );
  },

  updatePartner(partner: Partner): boolean {
    this.getCapitalContributions();
    if (!partner.name.trim() || !Number.isFinite(partner.shareAmount) || partner.shareAmount < 0) return false;
    const existing = this.getPartners();
    const previous = existing.find((p) => p.id === partner.id);
    if (!previous) return false;
    const now = Date.now();
    const delta = partner.shareAmount - previous.shareAmount;
    const updatedPartners = existing.map((p) =>
      p.id === partner.id
        ? { ...partner, name: partner.name.trim(), profitPercentage: p.profitPercentage, updatedAt: now }
        : p
    );
    const contributions = [...this.getCapitalContributions()];
    if (Math.abs(delta) > 0.004) {
      contributions.push({
        id: uuid(),
        partnerId: partner.id,
        amount: delta,
        date: todayIso(),
        note: delta > 0 ? 'পার্টনারের অতিরিক্ত মূলধন' : 'পার্টনারের মূলধন ফেরত/কমানো',
        createdAt: now,
        approximateDate: false,
      });
    }
    return (
      writeJson(PARTNER_NS, 'partner_capital_contributions', contributions) &&
      writeJson(PARTNER_NS, 'partners', updatedPartners)
    );
  },

  saveProfitDistribution(percentages: Record<number, number>, effectiveDate: string): boolean {
    const partners = this.getPartners();
    if (partners.length === 0 || !isValidIsoDate(effectiveDate)) return false;
    const today = todayIso();
    if (effectiveDate < today) return false;
    const partnerIds = new Set(partners.map((p) => p.id));
    const keys = Object.keys(percentages).map(Number);
    if (keys.length !== partnerIds.size || keys.some((k) => !partnerIds.has(k))) return false;
    const vals = Object.values(percentages);
    if (vals.some((v) => !Number.isFinite(v) || v < 0 || v > 100)) return false;
    const total = vals.reduce((s, v) => s + v, 0);
    if (Math.abs(total - 100) > 0.0001) return false;

    const now = Date.now();
    const updatedPartners = partners.map((p) => ({
      ...p,
      profitPercentage: percentages[p.id] ?? 0,
      updatedAt: now,
    }));
    const existing = this.getRateHistory().filter((s) => s.effectiveDate !== effectiveDate);
    if (existing.length === 0) {
      const legacySum = partners.reduce((s, p) => s + p.profitPercentage, 0);
      if (Math.abs(legacySum - 100) <= 0.0001) {
        const legacyMap: Record<number, number> = {};
        partners.forEach((p) => {
          legacyMap[p.id] = p.profitPercentage;
        });
        existing.push({ effectiveDate: '0001-01-01', percentages: legacyMap, updatedAt: 0 });
      } else if (effectiveDate === today) {
        existing.push({ effectiveDate: '0001-01-01', percentages: { ...percentages }, updatedAt: now });
      }
    }
    existing.push({ effectiveDate, percentages: { ...percentages }, updatedAt: now });
    existing.sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
    return (
      writeJson(PARTNER_NS, 'partners', updatedPartners) &&
      writeJson(PARTNER_NS, 'partner_profit_rate_history', existing)
    );
  },

  addCapitalContribution(partnerId: number, amount: number, date: string, note = ''): boolean {
    if (!Number.isFinite(amount) || amount <= 0 || !isValidIsoDate(date)) return false;
    const partners = this.getPartners();
    if (!partners.some((p) => p.id === partnerId)) return false;
    const now = Date.now();
    const updatedPartners = partners.map((p) =>
      p.id === partnerId ? { ...p, shareAmount: p.shareAmount + amount, updatedAt: now } : p
    );
    const contributions = [
      ...this.getCapitalContributions(),
      {
        id: uuid(),
        partnerId,
        amount,
        date,
        note: note.trim(),
        createdAt: now,
        approximateDate: false,
      },
    ];
    return (
      writeJson(PARTNER_NS, 'partner_capital_contributions', contributions) &&
      writeJson(PARTNER_NS, 'partners', updatedPartners)
    );
  },

  getWithdrawals(): PartnerProfitWithdrawal[] {
    const list = readJson<PartnerProfitWithdrawal[]>(PARTNER_NS, 'partner_profit_withdrawals', []);
    return [...list].sort((a, b) => a.date.localeCompare(b.date) || a.createdAt - b.createdAt);
  },

  addWithdrawal(partnerId: number, amount: number, date: string, note = ''): boolean {
    if (!this.getPartners().some((p) => p.id === partnerId)) return false;
    if (!Number.isFinite(amount) || amount <= 0 || !isValidIsoDate(date)) return false;
    const updated = [
      ...this.getWithdrawals(),
      { id: uuid(), partnerId, amount, date, note: note.trim(), createdAt: Date.now() },
    ];
    return writeJson(PARTNER_NS, 'partner_profit_withdrawals', updated);
  },

  getCapitalizedPeriods(): CapitalizedProfitPeriod[] {
    return readJson<CapitalizedProfitPeriod[]>(PARTNER_NS, 'partner_capitalized_profit_periods', []);
  },

  isProfitPeriodCapitalized(fromDate: string, toDate: string): boolean {
    if (!isValidIsoDate(fromDate) || !isValidIsoDate(toDate) || fromDate > toDate) return false;
    return this.getCapitalizedPeriods().some(
      (p) => p.fromDate && p.toDate && p.fromDate <= toDate && p.toDate >= fromDate
    );
  },

  getCapitalizedProfitAmount(fromDate: string, toDate: string): number {
    if (!isValidIsoDate(fromDate) || !isValidIsoDate(toDate) || fromDate > toDate) return 0;
    return this.getCapitalizedPeriods()
      .filter((p) => p.fromDate >= fromDate && p.toDate <= toDate && Number.isFinite(p.amount) && p.amount > 0)
      .reduce((s, p) => s + p.amount, 0);
  },

  capitalizeRemainingProfit(fromDate: string, toDate: string, amount: number): boolean {
    if (!isValidIsoDate(fromDate) || !isValidIsoDate(toDate) || fromDate > toDate) return false;
    if (!Number.isFinite(amount) || amount <= 0.004) return false;
    const periodId = `${fromDate}..${toDate}`;
    const periods = this.getCapitalizedPeriods();
    if (periods.some((p) => p.id === periodId)) return false;

    const partners = this.getPartners();
    if (partners.length === 0) return false;
    const rates: Record<number, number> = {};
    partners.forEach((p) => {
      rates[p.id] = this.getPercentageForDate(p.id, toDate);
    });
    const vals = Object.values(rates);
    if (vals.some((v) => !Number.isFinite(v) || v < 0 || v > 100)) return false;
    if (Math.abs(vals.reduce((a, b) => a + b, 0) - 100) > 0.0001) return false;

    const now = Date.now();
    let allocated = 0;
    const contributions = [...this.getCapitalContributions()];
    const updatedPartners = partners.map((p, idx) => {
      const share =
        idx === partners.length - 1
          ? Math.max(0, amount - allocated)
          : Math.round((amount * (rates[p.id] ?? 0)) / 100 * 100) / 100;
      allocated += share;
      if (share > 0.004) {
        contributions.push({
          id: uuid(),
          partnerId: p.id,
          amount: share,
          date: toDate,
          note: `অবণ্টিত লভ্যাংশ মূলধনে যোগ • ${fromDate} থেকে ${toDate}`,
          createdAt: now,
          approximateDate: false,
        });
      }
      return { ...p, shareAmount: p.shareAmount + share, updatedAt: now };
    });

    const nextPeriods: CapitalizedProfitPeriod[] = [
      ...periods,
      { id: periodId, amount, date: toDate, fromDate, toDate, createdAt: now, updatedAt: now },
    ];
    return (
      writeJson(PARTNER_NS, 'partner_capital_contributions', contributions) &&
      writeJson(PARTNER_NS, 'partners', updatedPartners) &&
      writeJson(PARTNER_NS, 'partner_capitalized_profit_periods', nextPeriods)
    );
  },

  deletePartner(id: number, effectiveDate: string): boolean {
    const partners = this.getPartners();
    const remaining = partners.filter((p) => p.id !== id);
    if (remaining.length === partners.length || remaining.length === 0) return false;
    if (Math.abs(remaining.reduce((s, p) => s + p.profitPercentage, 0) - 100) > 0.0001) return false;
    const history = [...this.getRateHistory()];
    if (history.length === 0) {
      const m: Record<number, number> = {};
      partners.forEach((p) => (m[p.id] = p.profitPercentage));
      history.push({ effectiveDate: '0001-01-01', percentages: m, updatedAt: 0 });
    }
    const filteredHistory = history.filter((h) => h.effectiveDate !== effectiveDate);
    const remMap: Record<number, number> = {};
    remaining.forEach((p) => (remMap[p.id] = p.profitPercentage));
    filteredHistory.push({ effectiveDate, percentages: remMap, updatedAt: Date.now() });
    filteredHistory.sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
    if (!writeJson(PARTNER_NS, 'partner_profit_rate_history', filteredHistory)) return false;
    this._addDeletedId(id);
    return writeJson(PARTNER_NS, 'partners', remaining);
  },
};

function buildAllocations(
  date: string,
  amount: number,
  allowEstimatedLegacyRate: boolean
): { allocations: PartnerExpenseAllocation[]; estimated: boolean } | null {
  if (!isValidIsoDate(date) || !Number.isFinite(amount) || amount <= 0) return null;
  const partners = PartnerStorage.getPartners();
  if (partners.length === 0) return null;

  const history = PartnerStorage.getRateHistory();
  let snapshot: PartnerRateSnapshot | undefined;
  for (const s of history) {
    if (s.effectiveDate <= date) snapshot = s;
  }
  const rates: Record<number, number> = snapshot
    ? snapshot.percentages
    : Object.fromEntries(partners.map((p) => [p.id, p.profitPercentage]));

  if (!snapshot && date < todayIso() && !allowEstimatedLegacyRate) return null;
  const vals = Object.values(rates);
  if (vals.length === 0 || vals.some((v) => !Number.isFinite(v) || v < 0 || v > 100)) return null;
  if (Math.abs(vals.reduce((a, b) => a + b, 0) - 100) > 0.0001) return null;

  const activeById = new Map(partners.map((p) => [p.id, p]));
  const orderedRates = Object.entries(rates)
    .map(([id, pct]) => [Number(id), pct] as [number, number])
    .sort((a, b) => a[0] - b[0]);

  const totalCents = moneyCents(amount);
  if (totalCents <= 0) return null;

  const rawShares = orderedRates.map(([id, pct]) => ({
    id,
    pct,
    raw: (totalCents * pct) / 100,
  }));
  const baseShares = rawShares.map((s) => Math.floor(s.raw));
  let remaining = totalCents - baseShares.reduce((a, b) => a + b, 0);
  const remainderOrder = rawShares
    .map((_, i) => i)
    .sort((a, b) => {
      const diff = rawShares[b].raw - baseShares[b] - (rawShares[a].raw - baseShares[a]);
      if (Math.abs(diff) > 1e-9) return diff;
      return rawShares[a].id - rawShares[b].id;
    });
  const centsByPartner = [...baseShares];
  let idx = 0;
  while (remaining > 0 && remainderOrder.length > 0) {
    centsByPartner[remainderOrder[idx % remainderOrder.length]] += 1;
    remaining--;
    idx++;
  }

  const allocations: PartnerExpenseAllocation[] = rawShares.map((s, i) => ({
    partnerId: s.id,
    partnerName: activeById.get(s.id)?.name ?? `পূর্বতন পার্টনার (#${s.id})`,
    percentage: s.pct,
    amount: centsByPartner[i] / 100,
  }));

  if (allocations.reduce((s, a) => s + moneyCents(a.amount), 0) !== totalCents) return null;
  return { allocations, estimated: !snapshot && date < todayIso() };
}

function isValidAllocation(items: PartnerExpenseAllocation[], amount: number): boolean {
  if (!items || items.length === 0 || !Number.isFinite(amount) || amount <= 0) return false;
  if (
    items.some(
      (i) =>
        i.partnerId <= 0 ||
        !Number.isFinite(i.percentage) ||
        i.percentage < 0 ||
        !Number.isFinite(i.amount) ||
        i.amount < 0
    )
  ) {
    return false;
  }
  const uniqueIds = new Set(items.map((i) => i.partnerId));
  if (uniqueIds.size !== items.length) return false;
  const pctSum = items.reduce((s, i) => s + i.percentage, 0);
  const amtCents = items.reduce((s, i) => s + moneyCents(i.amount), 0);
  return Math.abs(pctSum - 100) <= 0.0001 && amtCents === moneyCents(amount);
}

export const ExpenseStorage = {
  _deletedExpenseIds(): Set<number> {
    return new Set(readJson<number[]>(FIN_NS, 'deleted_expense_ids', []));
  },

  getExpenses(): Expense[] {
    const deleted = this._deletedExpenseIds();
    return readJson<Expense[]>(FIN_NS, 'expenses', []).filter((e) => !deleted.has(e.id));
  },

  addExpense(x: Omit<Expense, 'allocations' | 'allocationEstimated' | 'updatedAt'>): boolean {
    if (x.id <= 0 || !Number.isFinite(x.amount) || x.amount <= 0 || !isValidIsoDate(x.date)) return false;
    const normalizedAmount = moneyCents(x.amount) / 100;
    if (normalizedAmount <= 0) return false;
    const alloc = buildAllocations(x.date, normalizedAmount, false);
    if (!alloc) return false;
    const items = readJson<Expense[]>(FIN_NS, 'expenses', []);
    if (items.some((e) => e.id === x.id)) return false;
    const created: Expense = {
      ...x,
      amount: normalizedAmount,
      allocations: alloc.allocations,
      allocationEstimated: alloc.estimated,
      updatedAt: Date.now(),
    };
    return writeJson(FIN_NS, 'expenses', [...items, created]);
  },

  updateExpense(x: Expense): boolean {
    if (!Number.isFinite(x.amount) || x.amount <= 0 || !isValidIsoDate(x.date)) return false;
    const normalizedAmount = moneyCents(x.amount) / 100;
    if (normalizedAmount <= 0) return false;
    const items = readJson<Expense[]>(FIN_NS, 'expenses', []);
    let found = false;
    let failedAlloc = false;
    const updated = items.map((old) => {
      if (old.id !== x.id) return old;
      found = true;
      const sameFin = old.date === x.date && moneyCents(old.amount) === moneyCents(normalizedAmount);
      let alloc: { allocations: PartnerExpenseAllocation[]; estimated: boolean } | null = null;
      if (sameFin && isValidAllocation(old.allocations, normalizedAmount)) {
        alloc = { allocations: old.allocations, estimated: old.allocationEstimated };
      } else {
        alloc = buildAllocations(x.date, normalizedAmount, false);
      }
      if (!alloc) {
        failedAlloc = true;
        return old;
      }
      return {
        ...x,
        amount: normalizedAmount,
        allocations: alloc.allocations,
        allocationEstimated: alloc.estimated,
        updatedAt: Date.now(),
      };
    });
    if (!found || failedAlloc) return false;
    return writeJson(FIN_NS, 'expenses', updated);
  },

  deleteExpense(expenseId: number): boolean {
    if (expenseId <= 0) return false;
    const items = readJson<Expense[]>(FIN_NS, 'expenses', []);
    if (!items.some((e) => e.id === expenseId)) return false;
    const deleted = Array.from(new Set([...this._deletedExpenseIds(), expenseId]));
    writeJson(FIN_NS, 'deleted_expense_ids', deleted);
    return writeJson(
      FIN_NS,
      'expenses',
      items.filter((e) => e.id !== expenseId)
    );
  },

  getDamages(): DamageRecord[] {
    return readJson<DamageRecord[]>(FIN_NS, 'damages', []);
  },

  addDamage(x: Omit<DamageRecord, 'allocations' | 'allocationEstimated'>): boolean {
    if (
      x.id <= 0 ||
      !isValidIsoDate(x.date) ||
      x.quantity <= 0 ||
      !Number.isFinite(x.unitCost) ||
      x.unitCost <= 0
    ) {
      return false;
    }
    const totalLoss = x.quantity * x.unitCost;
    const alloc = buildAllocations(x.date, totalLoss, false);
    if (!alloc) return false;
    const items = this.getDamages();
    if (items.some((d) => d.id === x.id)) return false;
    const created: DamageRecord = {
      ...x,
      allocations: alloc.allocations,
      allocationEstimated: alloc.estimated,
    };
    return writeJson(FIN_NS, 'damages', [...items, created]);
  },

  getPartnerExpenseTotal(partnerId: number, fromDate?: string, toDate?: string): number {
    const ordinary = this.getExpenses()
      .filter((e) => (!fromDate || e.date >= fromDate) && (!toDate || e.date <= toDate))
      .flatMap((e) => e.allocations)
      .filter((a) => a.partnerId === partnerId)
      .reduce((s, a) => s + a.amount, 0);
    const damage = this.getDamages()
      .filter((d) => (!fromDate || d.date >= fromDate) && (!toDate || d.date <= toDate))
      .flatMap((d) => d.allocations)
      .filter((a) => a.partnerId === partnerId)
      .reduce((s, a) => s + a.amount, 0);
    return ordinary + damage;
  },

  getUnallocatedExpenseTotal(): number {
    const ordinary = this.getExpenses()
      .filter((e) => !isValidAllocation(e.allocations, e.amount))
      .reduce((s, e) => s + e.amount, 0);
    const damage = this.getDamages()
      .filter((d) => !isValidAllocation(d.allocations, d.quantity * d.unitCost))
      .reduce((s, d) => s + d.quantity * d.unitCost, 0);
    return ordinary + damage;
  },

  getEstimatedAllocationExpenseTotal(): number {
    const ordinary = this.getExpenses()
      .filter((e) => e.allocationEstimated)
      .reduce((s, e) => s + e.amount, 0);
    const damage = this.getDamages()
      .filter((d) => d.allocationEstimated)
      .reduce((s, d) => s + d.quantity * d.unitCost, 0);
    return ordinary + damage;
  },
};

export const CashAccountStorage = {
  getAccounts(): ManualCashAccount[] {
    return readJson<ManualCashAccount[]>(CASH_NS, 'accounts', []);
  },
  saveAccount(account: ManualCashAccount): boolean {
    if (!account.name.trim() || !Number.isFinite(account.balance) || account.balance < 0) return false;
    const items = this.getAccounts();
    const idx = items.findIndex((a) => a.id === account.id);
    if (idx >= 0) items[idx] = { ...account, name: account.name.trim(), updatedAt: Date.now() };
    else items.push({ ...account, name: account.name.trim(), updatedAt: Date.now() });
    return writeJson(CASH_NS, 'accounts', items);
  },
  deleteAccount(id: string): boolean {
    return writeJson(
      CASH_NS,
      'accounts',
      this.getAccounts().filter((a) => a.id !== id)
    );
  },
};

export const OpeningBalanceStorage = {
  get(): OpeningBalances | null {
    return readJson<OpeningBalances | null>(OB_NS, 'opening_balance_v1', null);
  },
  save(value: OpeningBalances): boolean {
    const amounts = [
      value.cash,
      value.bank,
      value.stock,
      value.customerReceivable,
      value.supplierPayable,
      value.otherLiabilities,
    ];
    if (!isValidIsoDate(value.date) || amounts.some((v) => !Number.isFinite(v) || v < 0)) return false;
    return writeJson(OB_NS, 'opening_balance_v1', { ...value, updatedAt: Date.now() });
  },
};

export const DailyBalanceStorage = {
  _readAll(): DailyBalanceEntry[] {
    return readJson<DailyBalanceEntry[]>(DAILY_BAL_NS, 'entries', []).filter(
      (e) =>
        e.id &&
        isValidIsoDate(e.date) &&
        (e.account === 'CASH' || e.account === 'BANK') &&
        (e.mode === 'SET' || e.mode === 'ADD' || e.mode === 'SUB') &&
        Number.isFinite(e.amount) &&
        e.amount >= 0
    );
  },
  get(): DailyBalanceEntry[] {
    return this._readAll().filter((e) => !e.deleted);
  },
  save(entry: DailyBalanceEntry): boolean {
    const all = this._readAll().filter((e) => e.id !== entry.id);
    all.push(entry);
    all.sort((a, b) => a.date.localeCompare(b.date) || a.updatedAt - b.updatedAt);
    return writeJson(DAILY_BAL_NS, 'entries', all);
  },
  delete(id: string): boolean {
    const now = Date.now();
    const all = this._readAll().map((e) => (e.id === id ? { ...e, updatedAt: now, deleted: true } : e));
    return writeJson(DAILY_BAL_NS, 'entries', all);
  },
  balance(
    entries: DailyBalanceEntry[],
    account: 'CASH' | 'BANK',
    toDate: string,
    openingAmount?: number,
    openingDate?: string
  ): number {
    let total = openingDate && openingDate <= toDate ? openingAmount ?? 0 : 0;
    entries
      .filter((e) => e.account === account && e.date <= toDate && (!openingDate || e.date >= openingDate))
      .sort((a, b) => a.date.localeCompare(b.date) || a.updatedAt - b.updatedAt)
      .forEach((e) => {
        if (e.mode === 'SET') total = e.amount;
        else if (e.mode === 'ADD') total += e.amount;
        else if (e.mode === 'SUB') total -= e.amount;
      });
    return total;
  },
};
