import {
  JournalEntry,
  JournalLine,
  LedgerRow,
  LedgerSummary,
  PartnerCapitalKind,
  PartnerCapitalMovement,
  SaleReturnLine,
} from '../types';
import { dateKey, formatMoney } from '../utils/format';
import { CustomerDueStorage } from '../storage/dueStorage';
import { ExpenseStorage, OpeningBalanceStorage, PartnerStorage } from '../storage/financeStorage';
import { ProductStorage } from '../storage/inventoryStorage';
import { PurchaseStorage, SupplierDueStorage } from '../storage/purchaseStorage';
import { SaleReturnStorage, SalesStorage, SalesTransactionStorage } from '../storage/salesStorage';
import { DoubleEntryAccounting } from './accounting';
import { BusinessProfitCalculator } from './profitCalculator';

export function buildLedger(fromDate: string, toDate: string): LedgerSummary {
  const inRange = (d: string) => {
    const k = dateKey(d);
    return k >= fromDate && k <= toDate;
  };

  const purchases = PurchaseStorage.getPurchases().filter((p) => inRange(p.date));
  const allSales = SalesStorage.getSales();
  const sales = allSales.filter((s) => inRange(s.date));
  const transactions = SalesTransactionStorage.getTransactions().filter((t) => inRange(t.date));
  const allExpenses = ExpenseStorage.getExpenses();
  const expenses = allExpenses.filter((e) => inRange(e.date));
  const allDamages = ExpenseStorage.getDamages();
  const damages = allDamages.filter((d) => inRange(d.date));
  const supplierEntries = SupplierDueStorage.getEntries().filter((e) => inRange(e.date));
  const customerEntries = CustomerDueStorage.getEntries().filter((e) => inRange(e.date));
  const returns = SaleReturnStorage.getReturns().filter((r) => inRange(r.date));
  const returnIds = new Set(returns.map((r) => r.returnId));
  const returnLines = SaleReturnStorage.getLines().filter((l) => returnIds.has(l.returnId));
  const allExchanges = SaleReturnStorage.getExchanges();
  const exchanges = allExchanges.filter((ex) => inRange(ex.date));
  const withdrawals = PartnerStorage.getWithdrawals().filter((w) => inRange(w.date));
  const allCapitalContributions = PartnerStorage.getCapitalContributions();
  const capitalContributions = allCapitalContributions.filter((c) => inRange(c.date));

  const rows: LedgerRow[] = [];
  const warnings: string[] = [];
  const sharedProfitReport = BusinessProfitCalculator.calculate(fromDate, toDate);
  warnings.push(...sharedProfitReport.warnings);

  const historicalReturnCost = (line: SaleReturnLine): number => {
    const originals = allSales.filter(
      (s) =>
        s.transactionId === line.transactionId &&
        s.productCode.trim().toLowerCase() === line.productCode.trim().toLowerCase()
    );
    if (originals.length === 0) return 0;
    const originalUnits = originals.reduce((s, o) => s + Math.max(0, o.quantity), 0);
    if (originalUnits <= 0) return 0;
    const weightedUnitCost =
      originals.reduce((s, o) => s + Math.max(0, o.quantity) * Math.max(0, o.purchasePrice), 0) /
      originalUnits;
    return Math.max(0, line.quantity) * weightedUnitCost;
  };

  // Opening Balances
  const openingBalance = OpeningBalanceStorage.get();
  if (!openingBalance) {
    warnings.push(
      'প্রারম্ভিক ব্যালান্স সংরক্ষণ করা হয়নি; পুরোনো মূলধনের বিপরীতে কোনো নগদ/স্টক/পাওনা অনুমান করে যোগ করা হয়নি।'
    );
  } else if (openingBalance.date !== fromDate) {
    warnings.push(
      `প্রারম্ভিক ব্যালান্সের তারিখ ${openingBalance.date}; নির্বাচিত হিসাবের শুরুর তারিখ ${fromDate}। উদ্বোধনী এন্ট্রি দেখতে রিপোর্টের শুরুর তারিখ একই করুন।`
    );
  } else if (!openingBalance.verified) {
    warnings.push(
      'প্রারম্ভিক ব্যালান্স সংরক্ষিত আছে, কিন্তু যাচাইকৃত হিসেবে নিশ্চিত করা হয়নি; তাই ট্রায়াল ব্যালান্সে পোস্ট করা হয়নি।'
    );
  } else {
    const openingDebit = (account: string, label: string, amount: number) => {
      if (amount > 0.004) {
        rows.push({
          date: fromDate,
          description: `প্রারম্ভিক ${label}`,
          debitAccount: account,
          creditAccount: '',
          debitAmount: amount,
          creditAmount: 0,
          note: '',
        });
      }
    };
    const openingCredit = (account: string, label: string, amount: number) => {
      if (amount > 0.004) {
        rows.push({
          date: fromDate,
          description: `প্রারম্ভিক ${label}`,
          debitAccount: '',
          creditAccount: account,
          debitAmount: 0,
          creditAmount: amount,
          note: '',
        });
      }
    };
    openingDebit('নগদ', 'নগদ', openingBalance.cash);
    openingDebit('ব্যাংক', 'ব্যাংক', openingBalance.bank);
    openingDebit('স্টক/ইনভেন্টরি', 'স্টক', openingBalance.stock);
    openingDebit('ক্রেতার পাওনা', 'ক্রেতার পাওনা', openingBalance.customerReceivable);
    openingCredit('সরবরাহকারীর দেনা', 'সরবরাহকারীর দেনা', openingBalance.supplierPayable);
    openingCredit('অন্যান্য দেনা', 'অন্যান্য দেনা', openingBalance.otherLiabilities);

    const openingCapital = allCapitalContributions
      .filter((c) => dateKey(c.date) <= openingBalance.date)
      .reduce((s, c) => s + c.amount, 0);
    if (openingCapital >= 0) openingCredit('পার্টনার মূলধন', 'পার্টনার মূলধন', openingCapital);
    else openingDebit('পার্টনার মূলধন', 'পার্টনার মূলধনের ঘাটতি', -openingCapital);

    if (
      allCapitalContributions.some(
        (c) => dateKey(c.date) <= openingBalance.date && c.approximateDate
      )
    ) {
      warnings.push(
        'প্রারম্ভিক মূলধনের কিছু পুরোনো জমার তারিখ আনুমানিক; পার্টনারভিত্তিক মূলধন যাচাই করুন।'
      );
    }
  }

  const allPartners = PartnerStorage.getPartners();
  capitalContributions.forEach((contribution) => {
    const partnerName =
      allPartners.find((p) => p.id === contribution.partnerId)?.name ?? 'পুরোনো/মুছে ফেলা পার্টনার';
    const retainedProfit = contribution.note.startsWith('অবণ্টিত লভ্যাংশ মূলধনে যোগ');
    const capitalReturned = contribution.amount < -0.004;
    const amount = Math.abs(contribution.amount);
    if (amount <= 0.004) return;
    const description = retainedProfit
      ? `অবণ্টিত মুনাফা মূলধনে স্থানান্তর • ${partnerName}`
      : capitalReturned
      ? `পার্টনার মূলধন ফেরত • ${partnerName}`
      : `পার্টনার মূলধন জমা • ${partnerName}`;
    rows.push({
      date: contribution.date,
      description,
      debitAccount: retainedProfit
        ? 'অবণ্টিত মুনাফা'
        : capitalReturned
        ? 'পার্টনার মূলধন'
        : 'নগদ/ব্যাংক',
      creditAccount: capitalReturned ? 'নগদ/ব্যাংক' : 'পার্টনার মূলধন',
      debitAmount: amount,
      creditAmount: amount,
      note: contribution.note + (contribution.approximateDate ? ' • তারিখ আনুমানিক' : ''),
    });
    if (contribution.approximateDate) {
      warnings.push(
        `${partnerName}-এর পুরোনো মূলধনের প্রকৃত জমার তারিখ সংরক্ষিত ছিল না; পার্টনার তৈরির তারিখকে আনুমানিক ধরা হয়েছে।`
      );
    }
  });

  // Purchases
  const purchaseDueEntries = supplierEntries.filter((e) => e.type === 'PURCHASE');
  const consumedLegacyDueIds = new Set<number>();
  purchases.forEach((purchase) => {
    const gross = Math.max(0, purchase.quantity) * Math.max(0, purchase.purchasePrice);
    if (gross <= 0.004) return;

    const linkedEntries = purchaseDueEntries.filter((e) => e.purchaseId === purchase.id);
    const expectedLegacyNote =
      `ক্রয়: ${purchase.productName} x${purchase.quantity}` +
      (purchase.memoNumber.trim() ? ` • মেমো ${purchase.memoNumber.trim()}` : '');

    const legacyMatches = !purchase.settlementRecorded
      ? purchaseDueEntries.filter(
          (e) =>
            e.purchaseId <= 0 &&
            !consumedLegacyDueIds.has(e.id) &&
            e.supplier.trim().toLowerCase() === purchase.supplier.trim().toLowerCase() &&
            dateKey(e.date) === dateKey(purchase.date) &&
            e.note.trim() === expectedLegacyNote
        )
      : [];

    const duplicateLegacyPurchaseCount = !purchase.settlementRecorded
      ? purchases.filter(
          (it) =>
            !it.settlementRecorded &&
            it.supplier.trim().toLowerCase() === purchase.supplier.trim().toLowerCase() &&
            dateKey(it.date) === dateKey(purchase.date) &&
            it.productName === purchase.productName &&
            it.quantity === purchase.quantity &&
            it.memoNumber.trim() === purchase.memoNumber.trim()
        ).length
      : 0;

    let dueFromSource: number | null = 0;
    if (purchase.settlementRecorded) {
      dueFromSource = purchase.dueAmount;
    } else if (linkedEntries.length === 1) {
      dueFromSource = linkedEntries[0].amount;
    } else if (linkedEntries.length > 1) {
      warnings.push(`ক্রয় ইনভয়েস ${purchase.id}: একই ইনভয়েসে একাধিক বাকি রেকর্ড আছে; যাচাই প্রয়োজন।`);
      dueFromSource = null;
    } else if (legacyMatches.length === 1 && duplicateLegacyPurchaseCount === 1) {
      consumedLegacyDueIds.add(legacyMatches[0].id);
      dueFromSource = legacyMatches[0].amount;
    } else if (legacyMatches.length > 0) {
      warnings.push(
        `পুরোনো ক্রয় ${purchase.memoNumber || purchase.id}: একই মেমো/পণ্য/তারিখের একাধিক বাকি রেকর্ড মেলায় ইনভয়েসভিত্তিক ভাগ নির্ধারণ করা যায়নি।`
      );
      dueFromSource = null;
    }

    if (
      purchase.settlementRecorded &&
      linkedEntries.length === 1 &&
      Math.abs(linkedEntries[0].amount - purchase.dueAmount) > 0.01
    ) {
      warnings.push(
        `ইনভয়েস ${purchase.memoNumber || purchase.id}: ক্রয় রেকর্ডের বাকি ${formatMoney(
          purchase.dueAmount
        )} এবং সরবরাহকারী রেকর্ডের বাকি ${formatMoney(linkedEntries[0].amount)} মেলেনি।`
      );
    }

    if (dueFromSource === null) {
      rows.push({
        date: dateKey(purchase.date),
        description: `পণ্য ক্রয় • ${purchase.productName} × ${purchase.quantity} • নিষ্পত্তি যাচাই বাকি`,
        debitAccount: 'স্টক/ইনভেন্টরি',
        creditAccount: '',
        debitAmount: gross,
        creditAmount: 0,
        note: `মেমো: ${purchase.memoNumber || '—'}; মোট ${formatMoney(gross)}; নগদ/বাকি ভাগ পুরোনো রেকর্ড থেকে নিশ্চিত করা যায়নি।`,
      });
      return;
    }

    const due = Math.min(gross, Math.max(0, dueFromSource));
    if (Math.abs(dueFromSource - due) > 0.01) {
      warnings.push(
        `ইনভয়েস ${purchase.memoNumber || purchase.id}: বাকি মোট ক্রয়মূল্যের বেশি; প্রকৃত অঙ্ক যাচাই করুন।`
      );
    }
    const paid = gross - due;
    if (
      purchase.settlementRecorded &&
      Math.abs(purchase.paidAmount + purchase.dueAmount - gross) > 0.01
    ) {
      warnings.push(
        `ইনভয়েস ${purchase.memoNumber || purchase.id}: জমা ও বাকি মিলে মোট ক্রয়মূল্য হচ্ছে না।`
      );
    }
    const invoiceNote = `মেমো: ${purchase.memoNumber || '—'} • ইনভয়েস ID: ${purchase.id}`;
    if (paid > 0.004) {
      rows.push({
        date: dateKey(purchase.date),
        description: `পণ্য ক্রয় • ${purchase.productName} × ${purchase.quantity}`,
        debitAccount: 'স্টক/ইনভেন্টরি',
        creditAccount: 'নগদ',
        debitAmount: paid,
        creditAmount: paid,
        note: `${invoiceNote}; ক্রয়ের মোট ${formatMoney(gross)}`,
      });
    }
    if (due > 0.004) {
      rows.push({
        date: dateKey(purchase.date),
        description: `পণ্য ক্রয় বাকি • ${purchase.productName} × ${purchase.quantity}`,
        debitAccount: 'স্টক/ইনভেন্টরি',
        creditAccount: 'সরবরাহকারীর দেনা',
        debitAmount: due,
        creditAmount: due,
        note: invoiceNote,
      });
    }
  });

  // Sales revenue & receivables
  transactions.forEach((tx) => {
    const gross = Math.max(0, tx.total);
    if (gross <= 0) return;
    const paid = Math.min(gross, Math.max(0, tx.paid));
    const due = gross - paid;
    if (Math.abs(tx.paid + tx.due - gross) > 0.02) {
      warnings.push(`বিক্রয় ${tx.transactionId}: মোট, জমা ও বাকি টাকার মধ্যে অমিল আছে।`);
    }
    if (paid > 0.004) {
      rows.push({
        date: dateKey(tx.date),
        description: `বিক্রয় আদায় • ${tx.customer.trim() || 'নামহীন ক্রেতা'}`,
        debitAccount: 'নগদ',
        creditAccount: 'বিক্রয় আয়',
        debitAmount: paid,
        creditAmount: paid,
        note: `ইনভয়েস: ${tx.transactionId}`,
      });
    }
    if (due > 0.004) {
      rows.push({
        date: dateKey(tx.date),
        description: `বিক্রয় বাকি • ${tx.customer.trim() || 'নামহীন ক্রেতা'}`,
        debitAccount: 'ক্রেতার পাওনা',
        creditAccount: 'বিক্রয় আয়',
        debitAmount: due,
        creditAmount: due,
        note: `ইনভয়েস: ${tx.transactionId}`,
      });
    }
  });

  const transactionIds = new Set(transactions.map((t) => t.transactionId));
  const orphanSaleTx = Array.from(
    new Set(sales.filter((s) => !transactionIds.has(s.transactionId)).map((s) => s.transactionId))
  );
  orphanSaleTx.forEach((id) => {
    warnings.push(
      `বিক্রয় লাইন ${id} আছে, কিন্তু বিক্রয়ের মূল পেমেন্ট রেকর্ড নেই; তাই ডেবিট-ক্রেডিটে যোগ করা হয়নি।`
    );
  });

  // COGS
  sales.forEach((sale) => {
    const cost = Math.max(0, sale.quantity) * Math.max(0, sale.purchasePrice);
    if (cost > 0) {
      rows.push({
        date: dateKey(sale.date),
        description: `বিক্রিত পণ্যের ক্রয়মূল্য • ${sale.productName} × ${sale.quantity}`,
        debitAccount: 'বিক্রিত পণ্যের খরচ',
        creditAccount: 'স্টক/ইনভেন্টরি',
        debitAmount: cost,
        creditAmount: cost,
        note: '',
      });
    }
  });

  // Returns
  returns.forEach((ret) => {
    const refunded = Math.max(0, ret.refundAmount);
    const dueReduction = Math.max(0, ret.dueReduction);
    const originalLines = returnLines.filter((l) => l.returnId === ret.returnId);
    const expectedReturnAmount = originalLines.reduce((sum, line) => {
      const originals = allSales.filter(
        (s) =>
          s.transactionId === line.transactionId &&
          s.productCode.trim().toLowerCase() === line.productCode.trim().toLowerCase()
      );
      const units = originals.reduce((s, o) => s + Math.max(0, o.quantity), 0);
      const unitSalePrice =
        units > 0
          ? originals.reduce((s, o) => s + Math.max(0, o.quantity) * Math.max(0, o.salePrice), 0) /
            units
          : 0;
      const tx = SalesTransactionStorage.getTransaction(line.transactionId);
      const discountRate =
        tx && tx.subtotal > 0 ? Math.min(1, Math.max(0, tx.discount / tx.subtotal)) : 0;
      return sum + Math.max(0, line.quantity) * unitSalePrice * (1 - discountRate);
    }, 0);

    if (originalLines.length === 0) {
      warnings.push(
        `রিটার্ন ${ret.returnId}: রিটার্নের পণ্য-লাইন পাওয়া যায়নি; বিক্রয় ফেরত ও স্টক/COGS মিলিয়ে দেখুন।`
      );
    } else if (Math.abs(ret.amount - expectedReturnAmount) > 0.02) {
      warnings.push(
        `রিটার্ন ${ret.returnId}: সংরক্ষিত ফেরত মূল্য ${formatMoney(
          ret.amount
        )}; মূল বিক্রয়-লাইন ও ডিসকাউন্ট অনুযায়ী হওয়ার কথা ${formatMoney(expectedReturnAmount)}।`
      );
    }

    const linkedExchanges = allExchanges.filter(
      (ex) => ex.returnId === ret.returnId && (ex.netAmount !== undefined || ex.purchasePrice !== undefined)
    );
    const creditApplied =
      linkedExchanges.length > 0
        ? Math.max(0, Math.round((ret.amount - refunded - dueReduction) * 100) / 100)
        : 0;

    if (refunded > 0) {
      rows.push({
        date: dateKey(ret.date),
        description: 'বিক্রয় ফেরত • নগদ ফেরত',
        debitAccount: 'বিক্রয় ফেরত',
        creditAccount: 'নগদ',
        debitAmount: refunded,
        creditAmount: refunded,
        note: `রিটার্ন: ${ret.returnId}`,
      });
    }
    if (dueReduction > 0) {
      rows.push({
        date: dateKey(ret.date),
        description: 'বিক্রয় ফেরত • ক্রেতার বাকি কমেছে',
        debitAccount: 'বিক্রয় ফেরত',
        creditAccount: 'ক্রেতার পাওনা',
        debitAmount: dueReduction,
        creditAmount: dueReduction,
        note: `রিটার্ন: ${ret.returnId}`,
      });
    }
    if (creditApplied > 0.004) {
      rows.push({
        date: dateKey(ret.date),
        description: 'বিক্রয় ফেরত • এক্সচেঞ্জ ক্রেডিট সমন্বয়',
        debitAccount: 'বিক্রয় ফেরত',
        creditAccount: 'এক্সচেঞ্জ সমন্বয়',
        debitAmount: creditApplied,
        creditAmount: creditApplied,
        note: `রিটার্ন: ${ret.returnId}`,
      });
    }
    if (Math.abs(ret.amount - refunded - dueReduction - creditApplied) > 0.02) {
      warnings.push(
        `রিটার্ন ${ret.returnId}: ফেরতের মোট টাকা ও নগদ ফেরত/বাকি/এক্সচেঞ্জ সমন্বয়ের অঙ্ক মেলেনি।`
      );
    }

    originalLines.forEach((line) => {
      const originalExists = allSales.some(
        (s) =>
          s.transactionId === line.transactionId &&
          s.productCode.trim().toLowerCase() === line.productCode.trim().toLowerCase()
      );
      const costValue = historicalReturnCost(line);
      if (!originalExists) {
        warnings.push(
          `রিটার্ন ${ret.returnId}: ${line.productCode} পণ্যের মূল বিক্রয়-লাইন পাওয়া যায়নি; ফেরত পণ্যের ক্রয়মূল্য যাচাই প্রয়োজন।`
        );
      }
      if (costValue > 0) {
        rows.push({
          date: dateKey(ret.date),
          description: `ফেরত পণ্য স্টকে • ${line.productName} × ${line.quantity}`,
          debitAccount: 'স্টক/ইনভেন্টরি',
          creditAccount: 'বিক্রিত পণ্যের খরচ',
          debitAmount: costValue,
          creditAmount: costValue,
          note: '',
        });
      }
    });
  });

  // Actual customer collections
  const actualCustomerCollections = customerEntries.filter(
    (e) =>
      e.type === 'PAYMENT' &&
      e.amount > 0 &&
      !e.note.trim().startsWith('বিক্রয় রিটার্ন:') &&
      !e.note.trim().startsWith('ইনভয়েস সমন্বয়:')
  );
  actualCustomerCollections.forEach((entry) => {
    rows.push({
      date: dateKey(entry.date),
      description: `ক্রেতার বাকি আদায় • ${entry.customer}`,
      debitAccount: 'নগদ',
      creditAccount: 'ক্রেতার পাওনা',
      debitAmount: entry.amount,
      creditAmount: entry.amount,
      note: entry.note,
    });
  });

  const settledExchanges = exchanges.filter(
    (ex) => ex.netAmount !== undefined || ex.purchasePrice !== undefined
  );
  const legacyIncompleteExchanges = exchanges.filter(
    (ex) => ex.netAmount === undefined && ex.purchasePrice === undefined
  );

  legacyIncompleteExchanges.forEach((exchange) => {
    warnings.push(
      `এক্সচেঞ্জ ${exchange.exchangeId}: বিকল্প পণ্যের বিক্রয়মূল্য ও COGS এখনো স্বতন্ত্র জার্নাল এন্ট্রি নয়; এই সময়ের লাভ/ট্রায়াল ব্যালান্সে এক্সচেঞ্জ আলাদাভাবে যাচাই করুন।`
    );
  });

  let exchangeNetTotal = 0;
  let exchangePaidTotal = 0;
  let exchangeCogsTotal = 0;

  settledExchanges.forEach((ex) => {
    const exGross = Math.max(0, ex.quantity) * Math.max(0, ex.unitPrice);
    const exDisc = Math.min(exGross, Math.max(0, ex.discount ?? 0));
    const exNet =
      ex.netAmount !== undefined ? Math.max(0, ex.netAmount) : Math.max(0, exGross - exDisc);
    const exPaid = Math.min(exNet, Math.max(0, ex.paidAmount ?? 0));
    const exDue = Math.min(Math.max(0, exNet - exPaid), Math.max(0, ex.dueAmount ?? 0));
    const exCredit = Math.max(0, Math.round((exNet - exPaid - exDue) * 100) / 100);
    const exCost = Math.max(0, ex.quantity) * Math.max(0, ex.purchasePrice ?? 0);

    exchangeNetTotal += exNet;
    exchangePaidTotal += exPaid;
    exchangeCogsTotal += exCost;

    if (exCredit > 0.004) {
      rows.push({
        date: dateKey(ex.date),
        description: `এক্সচেঞ্জ বিক্রয় সমন্বয় • ${ex.productName} × ${ex.quantity}`,
        debitAccount: 'এক্সচেঞ্জ সমন্বয়',
        creditAccount: 'বিক্রয় আয়',
        debitAmount: exCredit,
        creditAmount: exCredit,
        note: `এক্সচেঞ্জ: ${ex.exchangeId}`,
      });
    }
    if (exPaid > 0.004) {
      rows.push({
        date: dateKey(ex.date),
        description: `এক্সচেঞ্জ আদায় • ${ex.productName} × ${ex.quantity}`,
        debitAccount: 'নগদ',
        creditAccount: 'বিক্রয় আয়',
        debitAmount: exPaid,
        creditAmount: exPaid,
        note: `এক্সচেঞ্জ: ${ex.exchangeId}`,
      });
    }
    if (exDue > 0.004) {
      rows.push({
        date: dateKey(ex.date),
        description: `এক্সচেঞ্জ বাকি • ${ex.productName} × ${ex.quantity}`,
        debitAccount: 'ক্রেতার পাওনা',
        creditAccount: 'বিক্রয় আয়',
        debitAmount: exDue,
        creditAmount: exDue,
        note: `এক্সচেঞ্জ: ${ex.exchangeId}`,
      });
    }
    if (exCost > 0.004) {
      rows.push({
        date: dateKey(ex.date),
        description: `বিক্রিত পণ্যের ক্রয়মূল্য • এক্সচেঞ্জ ${ex.productName} × ${ex.quantity}`,
        debitAccount: 'বিক্রিত পণ্যের খরচ',
        creditAccount: 'স্টক/ইনভেন্টরি',
        debitAmount: exCost,
        creditAmount: exCost,
        note: `এক্সচেঞ্জ: ${ex.exchangeId}`,
      });
    }
  });

  expenses.forEach((expense) => {
    if (expense.amount > 0) {
      rows.push({
        date: dateKey(expense.date),
        description: `প্রতিষ্ঠানের খরচ • ${expense.category}`,
        debitAccount: 'প্রতিষ্ঠানের খরচ',
        creditAccount: 'নগদ',
        debitAmount: expense.amount,
        creditAmount: expense.amount,
        note: expense.note,
      });
    }
  });

  damages.forEach((damage) => {
    const loss = damage.quantity * damage.unitCost;
    if (loss > 0) {
      rows.push({
        date: dateKey(damage.date),
        description: `ড্যামেজ/নষ্ট • ${damage.productName}`,
        debitAccount: 'ড্যামেজ ক্ষতি',
        creditAccount: 'স্টক/ইনভেন্টরি',
        debitAmount: loss,
        creditAmount: loss,
        note: damage.reason,
      });
    }
  });

  supplierEntries
    .filter((e) => e.type === 'PAYMENT' && e.amount < 0)
    .forEach((entry) => {
      rows.push({
        date: dateKey(entry.date),
        description: `সরবরাহকারীকে পরিশোধ • ${entry.supplier}`,
        debitAccount: 'সরবরাহকারীর দেনা',
        creditAccount: 'নগদ',
        debitAmount: -entry.amount,
        creditAmount: -entry.amount,
        note: entry.note,
      });
    });

  withdrawals.forEach((w) => {
    const partner = allPartners.find((p) => p.id === w.partnerId)?.name ?? 'পার্টনার';
    rows.push({
      date: dateKey(w.date),
      description: `পার্টনার লভ্যাংশ উত্তোলন • ${partner}`,
      debitAccount: 'লভ্যাংশ উত্তোলন',
      creditAccount: 'নগদ',
      debitAmount: w.amount,
      creditAmount: w.amount,
      note: w.note,
    });
  });

  const purchaseTotal = purchases.reduce((s, p) => s + p.quantity * p.purchasePrice, 0);
  const salesTotal =
    transactions.reduce((s, t) => s + Math.max(0, t.total), 0) -
    returns.reduce((s, r) => s + Math.max(0, r.amount), 0) +
    exchangeNetTotal;
  const collectionTotal =
    transactions.reduce((s, t) => s + Math.max(0, t.paid), 0) +
    exchangePaidTotal +
    actualCustomerCollections.reduce((s, c) => s + c.amount, 0);
  const allCustomerEntries = CustomerDueStorage.getEntries();
  const receivable = Math.max(
    0,
    allCustomerEntries.reduce((s, e) => s + (e.type === 'SALE' ? e.amount : -e.amount), 0)
  );
  const allSupplierEntries = SupplierDueStorage.getEntries();
  const supplierPayable = Math.max(
    0,
    allSupplierEntries.reduce((s, e) => s + e.amount, 0)
  );
  const expenseTotal = allExpenses.reduce((s, e) => s + Math.max(0, e.amount), 0);
  const periodExpenseTotal = expenses.reduce((s, e) => s + Math.max(0, e.amount), 0);
  const damageTotal = allDamages.reduce((s, d) => s + Math.max(0, d.quantity * d.unitCost), 0);
  const periodDamageTotal = damages.reduce((s, d) => s + Math.max(0, d.quantity * d.unitCost), 0);
  const withdrawalTotal = withdrawals.reduce((s, w) => s + Math.max(0, w.amount), 0);
  const totalCapital = allPartners.reduce((s, p) => s + Math.max(0, p.shareAmount), 0);
  const grossCogs =
    sales.reduce((s, sale) => s + Math.max(0, sale.quantity) * Math.max(0, sale.purchasePrice), 0) +
    exchangeCogsTotal;
  const returnedCost = returnLines.reduce((s, l) => s + historicalReturnCost(l), 0);

  const checkSource = (label: string, source: number, journal: number) => {
    if (Math.abs(source - journal) > 0.01) {
      warnings.push(
        `${label}: মূল রেকর্ড ${formatMoney(source)}, ডেবিট-ক্রেডিট জার্নাল ${formatMoney(
          journal
        )}; অমিল যাচাই করুন।`
      );
    }
  };

  checkSource(
    'প্রতিষ্ঠানের খরচ',
    periodExpenseTotal,
    rows.filter((r) => r.description.startsWith('প্রতিষ্ঠানের খরচ •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'ড্যামেজ ক্ষতি',
    periodDamageTotal,
    rows.filter((r) => r.description.startsWith('ড্যামেজ/নষ্ট •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'পণ্য ক্রয়',
    purchaseTotal,
    rows.filter((r) => r.description.startsWith('পণ্য ক্রয়')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'বিক্রয় ইনভয়েস ও এক্সচেঞ্জ',
    transactions.reduce((s, t) => s + Math.max(0, t.total), 0) + exchangeNetTotal,
    rows.filter((r) => r.creditAccount === 'বিক্রয় আয়').reduce((s, r) => s + r.creditAmount, 0)
  );
  checkSource(
    'বিক্রয় ফেরত',
    returns.reduce((s, r) => s + Math.max(0, r.amount), 0),
    rows.filter((r) => r.debitAccount === 'বিক্রয় ফেরত').reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'বিক্রিত পণ্যের ক্রয়মূল্য',
    grossCogs,
    rows.filter((r) => r.description.startsWith('বিক্রিত পণ্যের ক্রয়মূল্য •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'ফেরত পণ্যের ক্রয়মূল্য',
    returnedCost,
    rows.filter((r) => r.description.startsWith('ফেরত পণ্য স্টকে •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'সরবরাহকারীর কাছে ক্রয়-বাকি',
    supplierEntries.filter((e) => e.type === 'PURCHASE').reduce((s, e) => s + Math.max(0, e.amount), 0),
    rows.filter((r) => r.description.startsWith('পণ্য ক্রয় বাকি •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'সরবরাহকারীকে পরিশোধ',
    supplierEntries.filter((e) => e.type === 'PAYMENT').reduce((s, e) => s + -Math.min(0, e.amount), 0),
    rows.filter((r) => r.description.startsWith('সরবরাহকারীকে পরিশোধ •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'ক্রেতার বাকি আদায়',
    actualCustomerCollections.reduce((s, e) => s + e.amount, 0),
    rows.filter((r) => r.description.startsWith('ক্রেতার বাকি আদায় •')).reduce((s, r) => s + r.debitAmount, 0)
  );

  const capitalMovements: PartnerCapitalMovement[] = [
    ...capitalContributions.map((c) => ({
      kind: c.note.startsWith('অবণ্টিত লভ্যাংশ মূলধনে যোগ')
        ? PartnerCapitalKind.PROFIT_CAPITALIZATION
        : c.amount < -0.004
        ? PartnerCapitalKind.CAPITAL_RETURN
        : PartnerCapitalKind.CONTRIBUTION,
      amount: Math.abs(c.amount),
    })),
    ...withdrawals.map((w) => ({
      kind: PartnerCapitalKind.DIVIDEND_WITHDRAWAL,
      amount: w.amount,
    })),
  ];
  const partnerCapitalAudit = DoubleEntryAccounting.auditPartnerCapital(capitalMovements);

  checkSource(
    'পার্টনারের মূলধন জমা',
    partnerCapitalAudit.contribution,
    rows.filter((r) => r.description.startsWith('পার্টনার মূলধন জমা •')).reduce((s, r) => s + r.creditAmount, 0)
  );
  checkSource(
    'পার্টনারের মূলধন ফেরত',
    partnerCapitalAudit.capitalReturn,
    rows.filter((r) => r.description.startsWith('পার্টনার মূলধন ফেরত •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'লভ্যাংশ উত্তোলন',
    partnerCapitalAudit.dividendWithdrawal,
    rows.filter((r) => r.description.startsWith('পার্টনার লভ্যাংশ উত্তোলন •')).reduce((s, r) => s + r.debitAmount, 0)
  );
  checkSource(
    'লভ্যাংশ মূলধনে যোগ',
    partnerCapitalAudit.profitCapitalization,
    rows.filter((r) => r.description.startsWith('অবণ্টিত মুনাফা মূলধনে স্থানান্তর •')).reduce((s, r) => s + r.creditAmount, 0)
  );

  const externalCapitalIn = capitalContributions
    .filter((c) => c.amount > 0.004 && !c.note.startsWith('অবণ্টিত লভ্যাংশ মূলধনে যোগ'))
    .reduce((s, c) => s + c.amount, 0);
  const capitalReturned = capitalContributions
    .filter((c) => c.amount < -0.004)
    .reduce((s, c) => s + -c.amount, 0);

  const netProfit = sharedProfitReport.netProfit;
  const distributableProfit = Object.values(sharedProfitReport.partnerDistributionByDay).reduce(
    (s, v) => s + v,
    0
  );

  const stockWarnings: string[] = [];
  const currentProducts = ProductStorage.getProducts();
  const stockValue = currentProducts.reduce(
    (s, p) => s + Math.max(0, p.stockQuantity) * Math.max(0, p.purchasePrice),
    0
  );
  const stockUnits = currentProducts.reduce((s, p) => s + Math.max(0, p.stockQuantity), 0);

  const purchaseDue = supplierEntries
    .filter((e) => e.type === 'PURCHASE')
    .reduce((s, e) => s + Math.max(0, e.amount), 0);
  const cashIn =
    transactions.reduce((s, t) => s + Math.max(0, t.paid), 0) +
    exchangePaidTotal +
    actualCustomerCollections.reduce((s, c) => s + c.amount, 0) +
    externalCapitalIn;
  const cashOut =
    Math.max(0, purchaseTotal - purchaseDue) +
    supplierEntries.filter((e) => e.type === 'PAYMENT').reduce((s, e) => s + -Math.min(0, e.amount), 0) +
    periodExpenseTotal +
    withdrawalTotal +
    returns.reduce((s, r) => s + Math.max(0, r.refundAmount), 0) +
    capitalReturned;

  const openingRows = rows.filter((r) => r.description.startsWith('প্রারম্ভিক '));
  const openingLines: JournalLine[] = [];
  openingRows.forEach((row) => {
    if (row.debitAccount.trim() && row.debitAmount > 0.004) {
      openingLines.push({ account: row.debitAccount, debit: row.debitAmount, credit: 0 });
    }
    if (row.creditAccount.trim() && row.creditAmount > 0.004) {
      openingLines.push({ account: row.creditAccount, debit: 0, credit: row.creditAmount });
    }
  });
  const openingJournal: JournalEntry[] =
    openingLines.length === 0
      ? []
      : [{ id: `OPENING-${fromDate}`, date: fromDate, lines: openingLines, source: 'Verified opening balances' }];

  const periodJournal: JournalEntry[] = rows
    .filter((r) => !r.description.startsWith('প্রারম্ভিক '))
    .map((row, index) => {
      const lines: JournalLine[] = [];
      if (row.debitAccount.trim() && row.debitAmount > 0.004) {
        lines.push({ account: row.debitAccount, debit: row.debitAmount, credit: 0 });
      }
      if (row.creditAccount.trim() && row.creditAmount > 0.004) {
        lines.push({ account: row.creditAccount, debit: 0, credit: row.creditAmount });
      }
      return {
        id: `PERIOD-${fromDate}-${toDate}-${index}`,
        date: row.date,
        lines,
        source: `${row.description} ${row.note}`.trim(),
      };
    })
    .filter((j) => j.lines.length > 0);

  const trialBalance = DoubleEntryAccounting.trialBalance(openingJournal, periodJournal);
  warnings.push(...trialBalance.errors);

  rows.sort((a, b) => a.date.localeCompare(b.date) || a.description.localeCompare(b.description));

  return {
    rows,
    purchaseTotal,
    stockValue,
    stockUnits,
    salesTotal,
    collectionTotal,
    receivable,
    supplierPayable,
    expenseTotal,
    periodExpenseTotal,
    damageTotal,
    periodDamageTotal,
    withdrawalTotal,
    totalCapital,
    netProfit,
    distributableProfit,
    cashIn,
    cashOut,
    stockWarnings,
    dataWarnings: Array.from(new Set(warnings)),
    trialBalance,
  };
}
