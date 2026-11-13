import { Sale, SaleExchange, SaleReturn, SaleReturnLine, SaleTransaction } from '../types';
import { readJson, writeJson } from './businessStorage';

const SALES_NS = 'tamanna_enterprise_sales';
const TX_NS = 'tamanna_enterprise_sale_transactions';
const RET_NS = 'tamanna_enterprise_sale_returns';

export const SalesStorage = {
  getSales(): Sale[] {
    const list = readJson<Sale[]>(SALES_NS, 'sales', []);
    return [...list].sort((a, b) => b.id - a.id);
  },

  addSale(sale: Sale): boolean {
    const sales = this.getSales();
    const usedIds = new Set(sales.map((s) => s.id));
    let uniqueId = sale.id;
    while (usedIds.has(uniqueId)) uniqueId++;
    const normalized: Sale = { ...sale, id: uniqueId };
    return writeJson(SALES_NS, 'sales', [...sales, normalized]);
  },

  replaceTransactionSales(transactionId: string, replacements: Sale[]): boolean {
    if (
      !transactionId.trim() ||
      replacements.length === 0 ||
      replacements.some(
        (r) => r.transactionId !== transactionId || r.quantity <= 0 || r.salePrice < 0 || r.purchasePrice < 0
      )
    ) {
      return false;
    }
    const current = this.getSales();
    if (!current.some((s) => s.transactionId === transactionId)) return false;
    const kept = current.filter((s) => s.transactionId !== transactionId);
    const keptIds = new Set(kept.map((s) => s.id));
    const normalized = replacements.map((s, idx) => {
      let id = s.id;
      if (keptIds.has(id)) {
        id = Date.now() + idx;
      }
      keptIds.add(id);
      return { ...s, id };
    });
    return writeJson(SALES_NS, 'sales', [...kept, ...normalized]);
  },

  removeByTransaction(transactionId: string): void {
    const remaining = this.getSales().filter((s) => s.transactionId !== transactionId);
    writeJson(SALES_NS, 'sales', remaining);
  },
};

export const SalesTransactionStorage = {
  getTransactions(): SaleTransaction[] {
    return readJson<SaleTransaction[]>(TX_NS, 'transactions', []);
  },

  getTransaction(transactionId: string): SaleTransaction | undefined {
    return this.getTransactions().find((t) => t.transactionId === transactionId);
  },

  addTransaction(tx: SaleTransaction): boolean {
    const list = this.getTransactions().filter((t) => t.transactionId !== tx.transactionId);
    return writeJson(TX_NS, 'transactions', [...list, tx]);
  },

  updateTransaction(tx: SaleTransaction): boolean {
    if (
      !tx.transactionId.trim() ||
      tx.subtotal < 0 ||
      tx.discount < 0 ||
      tx.total < 0 ||
      tx.paid < 0 ||
      tx.due < 0
    ) {
      return false;
    }
    const all = this.getTransactions();
    if (!all.some((t) => t.transactionId === tx.transactionId)) return false;
    return writeJson(
      TX_NS,
      'transactions',
      all.map((t) => (t.transactionId === tx.transactionId ? tx : t))
    );
  },

  removeTransaction(transactionId: string): void {
    writeJson(
      TX_NS,
      'transactions',
      this.getTransactions().filter((t) => t.transactionId !== transactionId)
    );
  },
};

export const SaleReturnStorage = {
  getReturns(): SaleReturn[] {
    return readJson<SaleReturn[]>(RET_NS, 'returns', []);
  },

  getLines(): SaleReturnLine[] {
    return readJson<SaleReturnLine[]>(RET_NS, 'lines', []);
  },

  getExchanges(): SaleExchange[] {
    return readJson<SaleExchange[]>(RET_NS, 'exchanges', []);
  },

  getReturnedQuantities(transactionId: string): Record<string, number> {
    const out: Record<string, number> = {};
    this.getLines()
      .filter((l) => l.transactionId === transactionId)
      .forEach((l) => {
        const key = l.productCode.trim().toLowerCase();
        out[key] = (out[key] ?? 0) + l.quantity;
      });
    return out;
  },

  getReturnedAmount(transactionId: string): number {
    return this.getReturns()
      .filter((r) => r.transactionId === transactionId)
      .reduce((sum, r) => sum + r.amount, 0);
  },

  getDueReduction(transactionId: string): number {
    return this.getReturns()
      .filter((r) => r.transactionId === transactionId)
      .reduce((sum, r) => sum + r.dueReduction, 0);
  },

  canAddReturn(transactionId: string, lines: SaleReturnLine[], replacingReturnId?: string): boolean {
    if (!transactionId.trim() || lines.length === 0) return false;
    if (
      lines.some(
        (l) =>
          l.transactionId !== transactionId ||
          l.quantity <= 0 ||
          !l.productCode.trim() ||
          !Number.isFinite(l.unitPrice) ||
          l.unitPrice < 0
      )
    ) {
      return false;
    }
    const soldByCode: Record<string, number> = {};
    SalesStorage.getSales()
      .filter((s) => s.transactionId === transactionId)
      .forEach((s) => {
        const k = s.productCode.trim().toLowerCase();
        soldByCode[k] = (soldByCode[k] ?? 0) + Math.max(0, s.quantity);
      });
    if (Object.keys(soldByCode).length === 0) return false;

    const alreadyReturned: Record<string, number> = {};
    this.getLines()
      .filter((l) => l.transactionId === transactionId && l.returnId !== replacingReturnId)
      .forEach((l) => {
        const k = l.productCode.trim().toLowerCase();
        alreadyReturned[k] = (alreadyReturned[k] ?? 0) + l.quantity;
      });

    const requested: Record<string, number> = {};
    lines.forEach((l) => {
      const k = l.productCode.trim().toLowerCase();
      requested[k] = (requested[k] ?? 0) + l.quantity;
    });

    return Object.entries(requested).every(([code, qty]) => {
      const sold = soldByCode[code] ?? 0;
      const prev = alreadyReturned[code] ?? 0;
      return sold > 0 && prev >= 0 && qty <= sold - prev;
    });
  },

  addReturn(item: SaleReturn, lines: SaleReturnLine[]): boolean {
    if (this.getReturns().some((r) => r.returnId === item.returnId)) return false;
    if (
      !item.returnId.trim() ||
      !item.transactionId.trim() ||
      !Number.isFinite(item.amount) ||
      item.amount < 0 ||
      !Number.isFinite(item.dueReduction) ||
      item.dueReduction < 0 ||
      !Number.isFinite(item.refundAmount) ||
      item.refundAmount < 0 ||
      !this.canAddReturn(item.transactionId, lines)
    ) {
      return false;
    }
    const ok1 = writeJson(RET_NS, 'returns', [...this.getReturns(), item]);
    const ok2 = writeJson(RET_NS, 'lines', [...this.getLines(), ...lines]);
    return ok1 && ok2;
  },

  removeReturn(returnId: string): boolean {
    const ok1 = writeJson(
      RET_NS,
      'returns',
      this.getReturns().filter((r) => r.returnId !== returnId)
    );
    const ok2 = writeJson(
      RET_NS,
      'lines',
      this.getLines().filter((l) => l.returnId !== returnId)
    );
    return ok1 && ok2;
  },

  addExchange(exchange: SaleExchange): boolean {
    if (
      !exchange.exchangeId.trim() ||
      !exchange.returnId.trim() ||
      exchange.quantity <= 0 ||
      exchange.unitPrice < 0 ||
      !Number.isFinite(exchange.unitPrice) ||
      !Number.isFinite(exchange.returnedValue) ||
      !Number.isFinite(exchange.difference) ||
      this.getExchanges().some((e) => e.exchangeId === exchange.exchangeId)
    ) {
      return false;
    }
    return writeJson(RET_NS, 'exchanges', [...this.getExchanges(), exchange]);
  },

  removeExchange(exchangeId: string): boolean {
    return writeJson(
      RET_NS,
      'exchanges',
      this.getExchanges().filter((e) => e.exchangeId !== exchangeId)
    );
  },
};
