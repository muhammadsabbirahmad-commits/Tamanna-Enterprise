import { BusinessProfitReport, DailyBusinessProfit, Partner } from '../types';
import { dateKey } from '../utils/format';
import { ExpenseStorage, PartnerStorage } from '../storage/financeStorage';
import { SaleReturnStorage, SalesStorage, SalesTransactionStorage } from '../storage/salesStorage';

interface MutableDaily {
  grossSales: number;
  salesDiscount: number;
  cogs: number;
  returnRevenue: number;
  returnCost: number;
  expenses: number;
  damageLoss: number;
}

function makeDaily(): MutableDaily {
  return {
    grossSales: 0,
    salesDiscount: 0,
    cogs: 0,
    returnRevenue: 0,
    returnCost: 0,
    expenses: 0,
    damageLoss: 0,
  };
}

export function calculateExpenseRecovery(days: DailyBusinessProfit[]): {
  distributableByDay: Record<string, number>;
  unrecoveredOperatingExpenses: number;
} {
  let unpaid = 0;
  const distributableByDay: Record<string, number> = {};
  const sorted = [...days].sort((a, b) => a.date.localeCompare(b.date));
  for (const result of sorted) {
    unpaid += Math.max(0, result.expenses);
    const tradingAfterDamage =
      result.grossSales -
      result.salesDiscount -
      result.costOfGoodsSold -
      result.returnRevenue +
      result.returnCost -
      result.damageLoss;
    const recovered = Math.min(Math.max(0, tradingAfterDamage), unpaid);
    unpaid = Math.max(0, unpaid - recovered);
    distributableByDay[result.date] = tradingAfterDamage - recovered;
  }
  return { distributableByDay, unrecoveredOperatingExpenses: unpaid };
}

export const BusinessProfitCalculator = {
  calculate(fromDate?: string, toDate?: string): BusinessProfitReport {
    const sales = SalesStorage.getSales();
    const txList = SalesTransactionStorage.getTransactions();
    const transactions = new Map(txList.map((t) => [t.transactionId, t]));
    const expenses = ExpenseStorage.getExpenses();
    const damages = ExpenseStorage.getDamages();
    const returns = SaleReturnStorage.getReturns();
    const returnLines = SaleReturnStorage.getLines();
    const exchanges = SaleReturnStorage.getExchanges();
    const warnings: string[] = [];

    const datedSales = sales.filter((s) => {
      const k = dateKey(s.date);
      return k.length > 0 && (!toDate || k <= toDate);
    });

    const missingTxIds = Array.from(
      new Set(datedSales.filter((s) => !transactions.has(s.transactionId)).map((s) => s.transactionId))
    );
    missingTxIds.forEach((id) => {
      warnings.push(
        `বিক্রয় ${id}: ইনভয়েসের ডিসকাউন্ট/পেমেন্ট রেকর্ড নেই; বিক্রয় লাইনের ভিত্তিতে লাভ গণনা করা হয়েছে, যাচাই প্রয়োজন।`
      );
    });

    txList.forEach((tx) => {
      const k = dateKey(tx.date);
      if (!k || (fromDate && k < fromDate) || (toDate && k > toDate)) return;
      const lines = datedSales.filter((s) => s.transactionId === tx.transactionId);
      if (lines.length === 0) {
        warnings.push(
          `ইনভয়েস ${tx.transactionId}: ইনভয়েসের রেকর্ড আছে কিন্তু বিক্রয়ের পণ্য-লাইন নেই; পণ্যের ক্রয়মূল্য ছাড়া প্রকৃত লাভ নির্ণয় সম্ভব নয়।`
        );
      }
      const lineSubtotal = lines.reduce(
        (sum, s) => sum + Math.max(0, s.quantity) * Math.max(0, s.salePrice),
        0
      );
      if (Math.abs(lineSubtotal - Math.max(0, tx.subtotal)) > 0.02) {
        warnings.push(
          `ইনভয়েস ${tx.transactionId}: পণ্যের লাইনের মোট ${lineSubtotal.toFixed(2)} টাকা, ইনভয়েস সাবটোটাল ${tx.subtotal.toFixed(2)} টাকা; ডিসকাউন্ট বণ্টনের আগে যাচাই প্রয়োজন।`
        );
      }
      if (Math.abs(tx.subtotal - tx.discount - tx.total) > 0.02) {
        warnings.push(`ইনভয়েস ${tx.transactionId}: সাবটোটাল, ডিসকাউন্ট ও সর্বমোটের মধ্যে অমিল আছে।`);
      }
    });

    const days = new Map<string, MutableDaily>();
    const partnerDays = new Map<string, MutableDaily>();

    const bucket = (d: string) => {
      const k = dateKey(d);
      let b = days.get(k);
      if (!b) {
        b = makeDaily();
        days.set(k, b);
      }
      return b;
    };

    const partnerBucket = (d: string) => {
      const k = dateKey(d);
      let b = partnerDays.get(k);
      if (!b) {
        b = makeDaily();
        partnerDays.set(k, b);
      }
      return b;
    };

    sales.forEach((sale) => {
      const k = dateKey(sale.date);
      if (!k || (toDate && k > toDate)) return;
      const gross = Math.max(0, sale.quantity) * Math.max(0, sale.salePrice);
      const tx = transactions.get(sale.transactionId);
      const invoiceSubtotal = tx ? Math.max(0, tx.subtotal) : 0;
      const discount =
        tx && invoiceSubtotal > 0
          ? Math.min(gross, (Math.max(0, tx.discount) * gross) / invoiceSubtotal)
          : 0;
      const cogs = Math.max(0, sale.quantity) * Math.max(0, sale.purchasePrice);

      const item = bucket(k);
      item.grossSales += gross;
      item.salesDiscount += discount;
      item.cogs += cogs;

      const pItem = partnerBucket(k);
      pItem.grossSales += gross;
      pItem.salesDiscount += discount;
      pItem.cogs += cogs;
    });

    returns.forEach((ret) => {
      const rDate = dateKey(ret.date);
      if (!rDate || (toDate && rDate > toDate)) return;
      const item = bucket(rDate);
      item.returnRevenue += Math.max(0, ret.amount);
      const linesForReturn = returnLines.filter((l) => l.returnId === ret.returnId);
      const grossReturnedValue = linesForReturn.reduce(
        (s, l) => s + Math.max(0, l.quantity) * Math.max(0, l.unitPrice),
        0
      );

      linesForReturn.forEach((line) => {
        const originals = sales.filter(
          (s) =>
            s.transactionId === line.transactionId &&
            s.productCode.trim().toLowerCase() === line.productCode.trim().toLowerCase()
        );
        if (originals.length === 0) {
          warnings.push(
            `রিটার্ন ${ret.returnId}: ${line.productCode} পণ্যের মূল বিক্রয়-লাইন পাওয়া যায়নি; লাভের হিসাব যাচাই প্রয়োজন।`
          );
        }
        const originalUnits = originals.reduce((s, o) => s + Math.max(0, o.quantity), 0);
        const weightedUnitCost =
          originalUnits > 0
            ? originals.reduce(
                (s, o) => s + Math.max(0, o.quantity) * Math.max(0, o.purchasePrice),
                0
              ) / originalUnits
            : 0;
        const returnedCost = Math.max(0, line.quantity) * weightedUnitCost;
        item.returnCost += returnedCost;

        const originalDate = originals[0]?.date ? dateKey(originals[0].date) : '';
        if (originalDate && grossReturnedValue > 0) {
          const grossLineValue = Math.max(0, line.quantity) * Math.max(0, line.unitPrice);
          const netLineReturn = (Math.max(0, ret.amount) * grossLineValue) / grossReturnedValue;
          const pItem = partnerBucket(originalDate);
          pItem.grossSales -= grossLineValue;
          pItem.salesDiscount -= grossLineValue - netLineReturn;
          pItem.cogs -= returnedCost;
        }
      });
    });

    exchanges.forEach((ex) => {
      const k = dateKey(ex.date);
      if (!k || (toDate && k > toDate)) return;
      if (ex.purchasePrice === undefined) {
        if (!fromDate || k >= fromDate) {
          warnings.push(
            `এক্সচেঞ্জ ${ex.exchangeId}: বিকল্প পণ্যের বিক্রয়মূল্য ও ক্রয়মূল্য আলাদা সংরক্ষিত নেই; এই এক্সচেঞ্জের প্রকৃত লাভ নিশ্চিতভাবে গণনা করা যায়নি।`
          );
        }
        return;
      }

      const qty = Math.max(0, ex.quantity);
      const gross = qty * Math.max(0, ex.unitPrice);
      const disc = Math.min(gross, Math.max(0, ex.discount ?? 0));
      const cogs = qty * Math.max(0, ex.purchasePrice);

      const item = bucket(k);
      item.grossSales += gross;
      item.salesDiscount += disc;
      item.cogs += cogs;

      const pItem = partnerBucket(k);
      pItem.grossSales += gross;
      pItem.salesDiscount += disc;
      pItem.cogs += cogs;
    });

    expenses.forEach((exp) => {
      const k = dateKey(exp.date);
      if (!k || (toDate && k > toDate)) return;
      bucket(k).expenses += Math.max(0, exp.amount);
      partnerBucket(k).expenses += Math.max(0, exp.amount);
    });

    damages.forEach((dmg) => {
      const k = dateKey(dmg.date);
      if (!k || (toDate && k > toDate)) return;
      const loss = Math.max(0, dmg.quantity * dmg.unitCost);
      bucket(k).damageLoss += loss;
      partnerBucket(k).damageLoss += loss;
    });

    const toDailyList = (m: Map<string, MutableDaily>): DailyBusinessProfit[] =>
      Array.from(m.entries())
        .map(([date, item]) => ({
          date,
          grossSales: item.grossSales,
          salesDiscount: item.salesDiscount,
          costOfGoodsSold: item.cogs,
          returnRevenue: item.returnRevenue,
          returnCost: item.returnCost,
          expenses: item.expenses,
          damageLoss: item.damageLoss,
          netProfit:
            item.grossSales -
            item.salesDiscount -
            item.cogs -
            item.returnRevenue +
            item.returnCost -
            item.expenses -
            item.damageLoss,
        }))
        .sort((a, b) => a.date.localeCompare(b.date));

    const dailyResults = toDailyList(days);
    const partnerDailyResults = toDailyList(partnerDays);
    const recovery = calculateExpenseRecovery(partnerDailyResults);

    const visibleDays = dailyResults.filter((d) => !fromDate || d.date >= fromDate);
    const partnerDistributionByDay: Record<string, number> = {};
    Object.entries(recovery.distributableByDay).forEach(([k, v]) => {
      if (!fromDate || k >= fromDate) partnerDistributionByDay[k] = v;
    });

    return {
      days: visibleDays,
      warnings: Array.from(new Set(warnings)),
      partnerDistributionByDay,
      unrecoveredOperatingExpenses: recovery.unrecoveredOperatingExpenses,
      grossSales: visibleDays.reduce((s, d) => s + d.grossSales, 0),
      salesDiscount: visibleDays.reduce((s, d) => s + d.salesDiscount, 0),
      costOfGoodsSold: visibleDays.reduce((s, d) => s + d.costOfGoodsSold, 0),
      returnRevenue: visibleDays.reduce((s, d) => s + d.returnRevenue, 0),
      returnCost: visibleDays.reduce((s, d) => s + d.returnCost, 0),
      expenses: visibleDays.reduce((s, d) => s + d.expenses, 0),
      damageLoss: visibleDays.reduce((s, d) => s + d.damageLoss, 0),
      netProfit: visibleDays.reduce((s, d) => s + d.netProfit, 0),
    };
  },
};

export function allocatePartnerProfitByDay(
  partners: Partner[],
  dailyNet: Record<string, number>
): { profitByPartner: Record<number, number>; lossByPartner: Record<number, number> } {
  const profitTotals: Record<number, number> = {};
  const lossTotals: Record<number, number> = {};
  partners.forEach((p) => {
    profitTotals[p.id] = 0;
    lossTotals[p.id] = 0;
  });

  const splitCents = (
    amountCents: number,
    rates: Array<[number, number]>,
    target: Record<number, number>
  ) => {
    if (amountCents <= 0 || rates.length === 0) return;
    const totalRate = rates.reduce((s, [, pct]) => s + pct, 0);
    if (totalRate <= 0) return;
    const rawShares = rates.map(([partnerId, pct]) => ({
      partnerId,
      raw: (amountCents * pct) / totalRate,
    }));
    const baseShares = new Map<number, number>();
    rawShares.forEach((s) => baseShares.set(s.partnerId, Math.floor(s.raw)));
    let remaining =
      amountCents - Array.from(baseShares.values()).reduce((a, b) => a + b, 0);
    const order = [...rawShares].sort((a, b) => {
      const diff = b.raw - Math.floor(b.raw) - (a.raw - Math.floor(a.raw));
      if (Math.abs(diff) > 1e-9) return diff;
      return a.partnerId - b.partnerId;
    });
    let idx = 0;
    while (remaining > 0 && order.length > 0) {
      const id = order[idx % order.length].partnerId;
      baseShares.set(id, (baseShares.get(id) ?? 0) + 1);
      remaining--;
      idx++;
    }
    baseShares.forEach((cents, id) => {
      target[id] = (target[id] ?? 0) + cents;
    });
  };

  Object.keys(dailyNet)
    .sort()
    .forEach((day) => {
      const net = dailyNet[day];
      if (!Number.isFinite(net)) return;
      const rates: Array<[number, number]> = partners.map((p) => [
        p.id,
        PartnerStorage.getPercentageForDate(p.id, day),
      ]);
      const validRates = rates.every(([, pct]) => Number.isFinite(pct) && pct >= 0 && pct <= 100);
      const totalRate = rates.reduce((s, [, pct]) => s + pct, 0);
      if (!validRates || Math.abs(totalRate - 100) > 0.0001) return;
      const cents = Math.round(Math.abs(net) * 100);
      if (net >= 0) splitCents(cents, rates, profitTotals);
      else splitCents(cents, rates, lossTotals);
    });

  const profitByPartner: Record<number, number> = {};
  const lossByPartner: Record<number, number> = {};
  partners.forEach((p) => {
    profitByPartner[p.id] = (profitTotals[p.id] ?? 0) / 100;
    lossByPartner[p.id] = (lossTotals[p.id] ?? 0) / 100;
  });
  return { profitByPartner, lossByPartner };
}
