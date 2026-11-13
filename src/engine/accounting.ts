import {
  AccountBalance,
  EntryAudit,
  JournalEntry,
  PartnerCapitalAudit,
  PartnerCapitalKind,
  PartnerCapitalMovement,
  TrialBalanceReport,
} from '../types';

const TOLERANCE = 0.01;

function coerceFiniteNonNegative(val: number): number {
  return Number.isFinite(val) && val > 0 ? val : 0;
}

export const DoubleEntryAccounting = {
  auditEntry(entry: JournalEntry): EntryAudit {
    const errors: string[] = [];
    if (!entry.id.trim()) errors.push('Journal ID is blank');
    if (!entry.date.trim()) errors.push('Journal date is blank');
    if (!entry.source.trim()) errors.push('Journal source is blank');
    if (entry.lines.length === 0) errors.push('Journal has no lines');

    entry.lines.forEach((line, index) => {
      if (!line.account.trim()) errors.push(`Line ${index + 1}: account is blank`);
      if (
        !Number.isFinite(line.debit) ||
        !Number.isFinite(line.credit) ||
        line.debit < 0 ||
        line.credit < 0
      ) {
        errors.push(`Line ${index + 1}: invalid debit/credit amount`);
      }
      if (line.debit > TOLERANCE && line.credit > TOLERANCE) {
        errors.push(`Line ${index + 1}: both debit and credit are populated`);
      }
      if (line.debit <= TOLERANCE && line.credit <= TOLERANCE) {
        errors.push(`Line ${index + 1}: zero-value line`);
      }
    });

    const debitTotal = entry.lines.reduce(
      (s, l) => s + (Number.isFinite(l.debit) && l.debit > 0 ? l.debit : 0),
      0
    );
    const creditTotal = entry.lines.reduce(
      (s, l) => s + (Number.isFinite(l.credit) && l.credit > 0 ? l.credit : 0),
      0
    );
    if (Math.abs(debitTotal - creditTotal) > TOLERANCE) {
      errors.push(`Entry is out of balance by ${Math.abs(debitTotal - creditTotal).toFixed(2)}`);
    }

    return {
      entryId: entry.id,
      debitTotal,
      creditTotal,
      balanced: errors.length === 0,
      errors,
    };
  },

  trialBalance(opening: JournalEntry[], period: JournalEntry[]): TrialBalanceReport {
    const all = [...opening, ...period];
    const entryAudits = all.map((e) => this.auditEntry(e));
    const errors: string[] = entryAudits.flatMap((audit) =>
      audit.errors.map((err) => `${audit.entryId}: ${err}`)
    );

    const accountSet = new Set<string>();
    all.forEach((entry) => {
      entry.lines.forEach((line) => {
        if (line.account.trim()) accountSet.add(line.account);
      });
    });
    const sortedAccounts = Array.from(accountSet).sort();

    const openingLinesAll = opening.flatMap((e) => e.lines);
    const periodLinesAll = period.flatMap((e) => e.lines);

    const accounts: AccountBalance[] = sortedAccounts.map((account) => {
      const oLines = openingLinesAll.filter((l) => l.account === account);
      const pLines = periodLinesAll.filter((l) => l.account === account);
      const openingDebit = oLines.reduce((s, l) => s + coerceFiniteNonNegative(l.debit), 0);
      const openingCredit = oLines.reduce((s, l) => s + coerceFiniteNonNegative(l.credit), 0);
      const periodDebit = pLines.reduce((s, l) => s + coerceFiniteNonNegative(l.debit), 0);
      const periodCredit = pLines.reduce((s, l) => s + coerceFiniteNonNegative(l.credit), 0);
      const closingNet = openingDebit - openingCredit + (periodDebit - periodCredit);
      return {
        account,
        openingDebit,
        openingCredit,
        periodDebit,
        periodCredit,
        closingDebit: Math.max(0, closingNet),
        closingCredit: Math.max(0, -closingNet),
      };
    });

    const openingDebit = accounts.reduce((s, a) => s + a.openingDebit, 0);
    const openingCredit = accounts.reduce((s, a) => s + a.openingCredit, 0);
    const periodDebit = accounts.reduce((s, a) => s + a.periodDebit, 0);
    const periodCredit = accounts.reduce((s, a) => s + a.periodCredit, 0);
    const closingDebit = accounts.reduce((s, a) => s + a.closingDebit, 0);
    const closingCredit = accounts.reduce((s, a) => s + a.closingCredit, 0);

    const allEntriesBalanced = entryAudits.every((a) => a.balanced);
    const openingBalanced = Math.abs(openingDebit - openingCredit) <= TOLERANCE;
    const periodBalanced = Math.abs(periodDebit - periodCredit) <= TOLERANCE;
    const closingBalanced = Math.abs(closingDebit - closingCredit) <= TOLERANCE;

    if (!openingBalanced) errors.push('Opening balances are out of balance');
    if (!periodBalanced) errors.push('Period movements are out of balance');
    if (!closingBalanced) errors.push('Closing trial balance is out of balance');

    return {
      entryAudits,
      accounts,
      openingDebit,
      openingCredit,
      periodDebit,
      periodCredit,
      closingDebit,
      closingCredit,
      errors,
      allEntriesBalanced,
      openingBalanced,
      periodBalanced,
      closingBalanced,
    };
  },

  auditPartnerCapital(movements: PartnerCapitalMovement[]): PartnerCapitalAudit {
    const errors = movements
      .filter((m) => !Number.isFinite(m.amount) || m.amount < 0)
      .map(() => 'Invalid partner movement amount');

    const sumKind = (kind: PartnerCapitalKind) =>
      movements
        .filter((m) => m.kind === kind)
        .reduce((s, m) => s + coerceFiniteNonNegative(m.amount), 0);

    return {
      contribution: sumKind(PartnerCapitalKind.CONTRIBUTION),
      capitalReturn: sumKind(PartnerCapitalKind.CAPITAL_RETURN),
      dividendWithdrawal: sumKind(PartnerCapitalKind.DIVIDEND_WITHDRAWAL),
      profitCapitalization: sumKind(PartnerCapitalKind.PROFIT_CAPITALIZATION),
      errors,
    };
  },
};
