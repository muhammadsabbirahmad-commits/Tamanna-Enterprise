export type ThemeMode = 'green' | 'blue' | 'purple' | 'dark';
export type UserRole = 'ADMIN' | 'PARTNER';

export type NavRoute =
  | 'dashboard'
  | 'double-entry'
  | 'products'
  | 'purchases'
  | 'sales'
  | 'new-sale'
  | 'stock'
  | 'stock-alert'
  | 'customer-due'
  | 'supplier-due'
  | 'finance'
  | 'profit'
  | 'financial-dashboard'
  | 'financial-calendar'
  | 'partners'
  | 'reports'
  | 'search'
  | 'settings';

export interface AppUser {
  id: string;
  username: string;
  passwordHash: string;
  role: UserRole;
  approved: boolean;
  googleEmail: string;
}

export interface ActivityLogEntry {
  id: number;
  date: string;
  username: string;
  action: string;
  details: string;
}

export interface Product {
  code: string;
  name: string;
  purchasePrice: number;
  salePrice: number;
  stockQuantity: number;
  createdAt: number;
  updatedAt: number;
}

export type ProductSort = 'NEWEST' | 'OLDEST' | 'STOCK_OUT' | 'MOST_SOLD';

export interface InventoryMeta {
  code: string;
  batch: string;
  expiryDate: string;
  lowStockLimit: number;
  damagedQuantity: number;
}

export interface StockHistoryEntry {
  id: number;
  date: string;
  code: string;
  productName: string;
  type: string;
  quantity: number;
  before: number;
  after: number;
  note: string;
}

export interface Sale {
  id: number;
  transactionId: string;
  date: string;
  productCode: string;
  productName: string;
  quantity: number;
  salePrice: number;
  purchasePrice: number;
  customer: string;
}

export interface SaleTransaction {
  transactionId: string;
  date: string;
  customer: string;
  mobile: string;
  subtotal: number;
  discount: number;
  total: number;
  paid: number;
  due: number;
  paymentMethod: string;
}

export interface SaleReturn {
  returnId: string;
  transactionId: string;
  date: string;
  amount: number;
  dueReduction: number;
  refundAmount: number;
  refundMethod: string;
}

export interface SaleReturnLine {
  returnId: string;
  transactionId: string;
  productCode: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface SaleExchange {
  exchangeId: string;
  returnId: string;
  transactionId: string;
  date: string;
  productCode: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  purchasePrice?: number;
  discount?: number;
  netAmount?: number;
  returnedValue: number;
  difference: number;
  paidAmount?: number;
  dueAmount?: number;
  paymentMethod?: string;
}

export interface Purchase {
  id: number;
  date: string;
  productCode: string;
  productName: string;
  quantity: number;
  purchasePrice: number;
  supplier: string;
  memoNumber: string;
  paidAmount: number;
  dueAmount: number;
  settlementRecorded: boolean;
}

export interface Supplier {
  id: number;
  name: string;
  mobile: string;
  address: string;
}

export interface SupplierDueEntry {
  id: number;
  date: string;
  supplier: string;
  type: 'PURCHASE' | 'PAYMENT';
  amount: number;
  note: string;
  purchaseId: number;
}

export interface Customer {
  id: number;
  name: string;
  mobile: string;
}

export interface DueEntry {
  id: number;
  date: string;
  customer: string;
  mobile: string;
  type: 'SALE' | 'PAYMENT';
  amount: number;
  note: string;
  transactionId?: string;
}

export interface PartnerExpenseAllocation {
  partnerId: number;
  partnerName: string;
  percentage: number;
  amount: number;
}

export interface Expense {
  id: number;
  date: string;
  category: string;
  amount: number;
  note: string;
  allocations: PartnerExpenseAllocation[];
  allocationEstimated: boolean;
  updatedAt: number;
}

export interface DamageRecord {
  id: number;
  date: string;
  productCode: string;
  productName: string;
  quantity: number;
  unitCost: number;
  reason: string;
  allocations: PartnerExpenseAllocation[];
  allocationEstimated: boolean;
}

export interface Partner {
  id: number;
  name: string;
  shareAmount: number;
  profitPercentage: number;
  note: string;
  updatedAt: number;
}

export interface PartnerProfitWithdrawal {
  id: string;
  partnerId: number;
  amount: number;
  date: string;
  note: string;
  createdAt: number;
}

export interface PartnerCapitalContribution {
  id: string;
  partnerId: number;
  amount: number;
  date: string;
  note: string;
  createdAt: number;
  approximateDate: boolean;
}

export interface PartnerRateSnapshot {
  effectiveDate: string;
  percentages: Record<number, number>;
  updatedAt: number;
}

export interface CapitalizedProfitPeriod {
  id: string;
  amount: number;
  date: string;
  fromDate: string;
  toDate: string;
  createdAt: number;
  updatedAt: number;
}

export interface ManualCashAccount {
  id: string;
  name: string;
  balance: number;
  updatedAt: number;
}

export interface OpeningBalances {
  date: string;
  cash: number;
  bank: number;
  stock: number;
  customerReceivable: number;
  supplierPayable: number;
  otherLiabilities: number;
  verified: boolean;
  updatedAt: number;
}

export interface DailyBalanceEntry {
  id: string;
  date: string;
  account: 'CASH' | 'BANK';
  mode: 'SET' | 'ADD' | 'SUB';
  amount: number;
  note: string;
  updatedAt: number;
  deleted: boolean;
}

export interface JournalLine {
  account: string;
  debit: number;
  credit: number;
}

export interface JournalEntry {
  id: string;
  date: string;
  lines: JournalLine[];
  source: string;
}

export interface EntryAudit {
  entryId: string;
  debitTotal: number;
  creditTotal: number;
  balanced: boolean;
  errors: string[];
}

export interface AccountBalance {
  account: string;
  openingDebit: number;
  openingCredit: number;
  periodDebit: number;
  periodCredit: number;
  closingDebit: number;
  closingCredit: number;
}

export interface TrialBalanceReport {
  entryAudits: EntryAudit[];
  accounts: AccountBalance[];
  openingDebit: number;
  openingCredit: number;
  periodDebit: number;
  periodCredit: number;
  closingDebit: number;
  closingCredit: number;
  errors: string[];
  allEntriesBalanced: boolean;
  openingBalanced: boolean;
  periodBalanced: boolean;
  closingBalanced: boolean;
}

export enum PartnerCapitalKind {
  CONTRIBUTION = 'CONTRIBUTION',
  CAPITAL_RETURN = 'CAPITAL_RETURN',
  DIVIDEND_WITHDRAWAL = 'DIVIDEND_WITHDRAWAL',
  PROFIT_CAPITALIZATION = 'PROFIT_CAPITALIZATION',
}

export interface PartnerCapitalMovement {
  kind: PartnerCapitalKind;
  amount: number;
}

export interface PartnerCapitalAudit {
  contribution: number;
  capitalReturn: number;
  dividendWithdrawal: number;
  profitCapitalization: number;
  errors: string[];
}

export interface DailyBusinessProfit {
  date: string;
  grossSales: number;
  salesDiscount: number;
  costOfGoodsSold: number;
  returnRevenue: number;
  returnCost: number;
  expenses: number;
  damageLoss: number;
  netProfit: number;
}

export interface BusinessProfitReport {
  days: DailyBusinessProfit[];
  warnings: string[];
  partnerDistributionByDay: Record<string, number>;
  unrecoveredOperatingExpenses: number;
  grossSales: number;
  salesDiscount: number;
  costOfGoodsSold: number;
  returnRevenue: number;
  returnCost: number;
  expenses: number;
  damageLoss: number;
  netProfit: number;
}

export interface LedgerRow {
  date: string;
  description: string;
  debitAccount: string;
  creditAccount: string;
  debitAmount: number;
  creditAmount: number;
  note: string;
}

export interface LedgerSummary {
  rows: LedgerRow[];
  purchaseTotal: number;
  stockValue: number;
  stockUnits: number;
  salesTotal: number;
  collectionTotal: number;
  receivable: number;
  supplierPayable: number;
  expenseTotal: number;
  periodExpenseTotal: number;
  damageTotal: number;
  periodDamageTotal: number;
  withdrawalTotal: number;
  totalCapital: number;
  netProfit: number;
  distributableProfit: number;
  cashIn: number;
  cashOut: number;
  stockWarnings: string[];
  dataWarnings: string[];
  trialBalance: TrialBalanceReport;
}
