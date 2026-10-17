// SpendWise Design Lab — shared domain types
// Mirrors the real Android app's data model (simplified for design exploration)

export type TransactionType = 'INCOME' | 'EXPENSE';
export type AccountType = 'CASH' | 'BANK';
export type Provenance = 'Manual' | 'Imported' | 'Recurring' | 'Transfer';

export type CategoryCode =
  | 'FOOD'
  | 'TRANSPORT'
  | 'HOUSING'
  | 'ENTERTAINMENT'
  | 'SALARY'
  | 'UNCATEGORIZED';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  balance: number;
  currency: string;
  openingBalance: number;
}

export interface Transaction {
  id: string;
  type: TransactionType;
  amount: number;
  currency: string;
  category: CategoryCode;
  description: string;
  accountId: string;
  accountName: string;
  provenance: Provenance;
  occurredOn: string; // ISO date
  note?: string;
}

export interface Budget {
  id: string;
  category: CategoryCode;
  month: string;
  limit: number;
  spent: number;
  currency: string;
}

export interface CategoryBreakdownItem {
  category: CategoryCode;
  amount: number;
  percentage: number;
}

export interface DashboardMonth {
  month: string;
  totalIncome: number;
  totalExpense: number;
  currency: string;
  breakdown: CategoryBreakdownItem[];
}

export type ScreenId = 'home' | 'transactions';
export type ConceptId = 'A' | 'B' | 'C';

export interface OverlayState {
  type: 'none' | 'addTransaction' | 'transactionDetail';
  transactionId?: string;
  defaultType?: TransactionType;
}
