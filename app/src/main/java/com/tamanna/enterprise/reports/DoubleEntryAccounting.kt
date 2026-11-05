package com.tamanna.enterprise.reports

/**
 * Pure accounting core. Every posted journal entry must balance independently;
 * opening balances and period movements are accumulated separately.
 */
data class JournalLine(
    val account: String,
    val debit: Double = 0.0,
    val credit: Double = 0.0
)

data class JournalEntry(
    val id: String,
    val date: String,
    val lines: List<JournalLine>,
    val source: String
)

data class EntryAudit(
    val entryId: String,
    val debitTotal: Double,
    val creditTotal: Double,
    val balanced: Boolean,
    val errors: List<String>
)

data class AccountBalance(
    val account: String,
    val openingDebit: Double,
    val openingCredit: Double,
    val periodDebit: Double,
    val periodCredit: Double,
    val closingDebit: Double,
    val closingCredit: Double
)

data class TrialBalanceReport(
    val entryAudits: List<EntryAudit>,
    val accounts: List<AccountBalance>,
    val openingDebit: Double,
    val openingCredit: Double,
    val periodDebit: Double,
    val periodCredit: Double,
    val closingDebit: Double,
    val closingCredit: Double,
    val errors: List<String>
) {
    val allEntriesBalanced: Boolean get() = entryAudits.all { it.balanced }
    val openingBalanced: Boolean get() = kotlin.math.abs(openingDebit - openingCredit) <= 0.01
    val periodBalanced: Boolean get() = kotlin.math.abs(periodDebit - periodCredit) <= 0.01
    val closingBalanced: Boolean get() = kotlin.math.abs(closingDebit - closingCredit) <= 0.01
}

enum class PartnerCapitalKind {
    CONTRIBUTION,
    CAPITAL_RETURN,
    DIVIDEND_WITHDRAWAL,
    PROFIT_CAPITALIZATION
}

data class PartnerCapitalMovement(val kind: PartnerCapitalKind, val amount: Double)

data class PartnerCapitalAudit(
    val contribution: Double,
    val capitalReturn: Double,
    val dividendWithdrawal: Double,
    val profitCapitalization: Double,
    val errors: List<String>
)

object DoubleEntryAccounting {
    private const val TOLERANCE = 0.01

    fun auditEntry(entry: JournalEntry): EntryAudit {
        val errors = mutableListOf<String>()
        if (entry.id.isBlank()) errors += "Journal ID is blank"
        if (entry.date.isBlank()) errors += "Journal date is blank"
        if (entry.source.isBlank()) errors += "Journal source is blank"
        if (entry.lines.isEmpty()) errors += "Journal has no lines"
        entry.lines.forEachIndexed { index, line ->
            if (line.account.isBlank()) errors += "Line ${index + 1}: account is blank"
            if (!line.debit.isFinite() || !line.credit.isFinite() || line.debit < 0.0 || line.credit < 0.0) {
                errors += "Line ${index + 1}: invalid debit/credit amount"
            }
            if (line.debit > TOLERANCE && line.credit > TOLERANCE) {
                errors += "Line ${index + 1}: both debit and credit are populated"
            }
            if (line.debit <= TOLERANCE && line.credit <= TOLERANCE) {
                errors += "Line ${index + 1}: zero-value line"
            }
        }
        val debit = entry.lines.sumOf { if (it.debit.isFinite() && it.debit > 0.0) it.debit else 0.0 }
        val credit = entry.lines.sumOf { if (it.credit.isFinite() && it.credit > 0.0) it.credit else 0.0 }
        if (kotlin.math.abs(debit - credit) > TOLERANCE) errors += "Entry is out of balance by ${kotlin.math.abs(debit - credit)}"
        return EntryAudit(entry.id, debit, credit, errors.isEmpty(), errors)
    }

    fun trialBalance(opening: List<JournalEntry>, period: List<JournalEntry>): TrialBalanceReport {
        val all = opening + period
        val audits = all.map(::auditEntry)
        val errors = audits.flatMap { audit -> audit.errors.map { "${audit.entryId}: $it" } }.toMutableList()
        val accounts = (all.flatMap { it.lines }.map { it.account }.filter { it.isNotBlank() }.toSet()).sorted()
        val rows = accounts.map { account ->
            val openingLines = opening.flatMap { it.lines }.filter { it.account == account }
            val periodLines = period.flatMap { it.lines }.filter { it.account == account }
            val od = openingLines.sumOf { it.debit.coerceFiniteNonNegative() }
            val oc = openingLines.sumOf { it.credit.coerceFiniteNonNegative() }
            val pd = periodLines.sumOf { it.debit.coerceFiniteNonNegative() }
            val pc = periodLines.sumOf { it.credit.coerceFiniteNonNegative() }
            val closingNet = (od - oc) + (pd - pc)
            AccountBalance(account, od, oc, pd, pc, closingNet.coerceAtLeast(0.0), (-closingNet).coerceAtLeast(0.0))
        }
        fun sum(selector: (AccountBalance) -> Double) = rows.sumOf(selector)
        val report = TrialBalanceReport(
            audits, rows,
            sum { it.openingDebit }, sum { it.openingCredit },
            sum { it.periodDebit }, sum { it.periodCredit },
            sum { it.closingDebit }, sum { it.closingCredit }, errors
        )
        if (!report.openingBalanced) errors += "Opening balances are out of balance"
        if (!report.periodBalanced) errors += "Period movements are out of balance"
        if (!report.closingBalanced) errors += "Closing trial balance is out of balance"
        return report.copy(errors = errors)
    }

    fun auditPartnerCapital(movements: List<PartnerCapitalMovement>): PartnerCapitalAudit {
        val errors = movements.filter { !it.amount.isFinite() || it.amount < 0.0 }
            .map { "Invalid partner movement amount" }
        return PartnerCapitalAudit(
            movements.filter { it.kind == PartnerCapitalKind.CONTRIBUTION }.sumOf { it.amount.coerceFiniteNonNegative() },
            movements.filter { it.kind == PartnerCapitalKind.CAPITAL_RETURN }.sumOf { it.amount.coerceFiniteNonNegative() },
            movements.filter { it.kind == PartnerCapitalKind.DIVIDEND_WITHDRAWAL }.sumOf { it.amount.coerceFiniteNonNegative() },
            movements.filter { it.kind == PartnerCapitalKind.PROFIT_CAPITALIZATION }.sumOf { it.amount.coerceFiniteNonNegative() },
            errors
        )
    }

    private fun Double.coerceFiniteNonNegative(): Double = if (isFinite() && this > 0.0) this else 0.0
}
