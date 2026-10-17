// Synthetic Vietnamese personal-finance data for SpendWise design exploration
// All amounts in VND. Realistic account names, descriptions, and categories.

import type {
  Account,
  Budget,
  DashboardMonth,
  Transaction,
} from './types';

export const accounts: Account[] = [
  {
    id: 'acc-1',
    name: 'TPBank Năng động',
    type: 'BANK',
    balance: 48_650_000,
    currency: 'VND',
    openingBalance: 30_000_000,
  },
  {
    id: 'acc-2',
    name: 'VCB Tiết kiệm',
    type: 'BANK',
    balance: 125_000_000,
    currency: 'VND',
    openingBalance: 100_000_000,
  },
  {
    id: 'acc-3',
    name: 'Tiền mặt',
    type: 'CASH',
    balance: 2_350_000,
    currency: 'VND',
    openingBalance: 5_000_000,
  },
];

export const dashboard: DashboardMonth = {
  month: '2026-10',
  currency: 'VND',
  totalIncome: 32_500_000,
  totalExpense: 18_420_000,
  breakdown: [
    { category: 'FOOD', amount: 5_840_000, percentage: 31.7 },
    { category: 'HOUSING', amount: 5_500_000, percentage: 29.9 },
    { category: 'TRANSPORT', amount: 2_680_000, percentage: 14.6 },
    { category: 'ENTERTAINMENT', amount: 2_100_000, percentage: 11.4 },
    { category: 'UNCATEGORIZED', amount: 2_300_000, percentage: 12.5 },
  ],
};

export const budgets: Budget[] = [
  {
    id: 'bud-1',
    category: 'FOOD',
    month: '2026-10',
    limit: 7_000_000,
    spent: 5_840_000,
    currency: 'VND',
  },
  {
    id: 'bud-2',
    category: 'TRANSPORT',
    month: '2026-10',
    limit: 3_000_000,
    spent: 2_680_000,
    currency: 'VND',
  },
  {
    id: 'bud-3',
    category: 'ENTERTAINMENT',
    month: '2026-10',
    limit: 1_500_000,
    spent: 2_100_000,
    currency: 'VND',
  },
];

export const transactions: Transaction[] = [
  {
    id: 'txn-1',
    type: 'EXPENSE',
    amount: 145_000,
    currency: 'VND',
    category: 'FOOD',
    description: 'Phở Thìn — bữa trưa',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Manual',
    occurredOn: '2026-10-17',
    note: 'Ăn cùng đồng nghiệp',
  },
  {
    id: 'txn-2',
    type: 'EXPENSE',
    amount: 35_000,
    currency: 'VND',
    category: 'TRANSPORT',
    description: 'Grab Bike — về nhà',
    accountId: 'acc-3',
    accountName: 'Tiền mặt',
    provenance: 'Manual',
    occurredOn: '2026-10-17',
  },
  {
    id: 'txn-3',
    type: 'EXPENSE',
    amount: 1_250_000,
    currency: 'VND',
    category: 'HOUSING',
    description: 'Tiền điện THÁNG 10',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Imported',
    occurredOn: '2026-10-16',
  },
  {
    id: 'txn-4',
    type: 'INCOME',
    amount: 25_000_000,
    currency: 'VND',
    category: 'SALARY',
    description: 'Lương tháng 10 — Công ty ABC',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Recurring',
    occurredOn: '2026-10-15',
  },
  {
    id: 'txn-5',
    type: 'EXPENSE',
    amount: 320_000,
    currency: 'VND',
    category: 'FOOD',
    description: 'Highlands Coffee — cà phê sáng',
    accountId: 'acc-3',
    accountName: 'Tiền mặt',
    provenance: 'Manual',
    occurredOn: '2026-10-15',
  },
  {
    id: 'txn-6',
    type: 'EXPENSE',
    amount: 890_000,
    currency: 'VND',
    category: 'ENTERTAINMENT',
    description: 'Vé xem phim CGV Vincom',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Manual',
    occurredOn: '2026-10-14',
    note: 'Phim Đất Rừng Phương Nam',
  },
  {
    id: 'txn-7',
    type: 'EXPENSE',
    amount: 550_000,
    currency: 'VND',
    category: 'TRANSPORT',
    description: 'Bơm xăng — Xăng dầu Petrolimex',
    accountId: 'acc-3',
    accountName: 'Tiền mặt',
    provenance: 'Manual',
    occurredOn: '2026-10-13',
  },
  {
    id: 'txn-8',
    type: 'EXPENSE',
    amount: 4_250_000,
    currency: 'VND',
    category: 'HOUSING',
    description: 'Tiền thuê nhà THÁNG 10',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Recurring',
    occurredOn: '2026-10-10',
  },
  {
    id: 'txn-9',
    type: 'INCOME',
    amount: 7_500_000,
    currency: 'VND',
    category: 'SALARY',
    description: 'Freelance — thiết kế logo',
    accountId: 'acc-2',
    accountName: 'VCB Tiết kiệm',
    provenance: 'Manual',
    occurredOn: '2026-10-08',
  },
  {
    id: 'txn-10',
    type: 'EXPENSE',
    amount: 185_000,
    currency: 'VND',
    category: 'FOOD',
    description: 'ShopeeFood — cơm văn phòng',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Manual',
    occurredOn: '2026-10-12',
  },
  {
    id: 'txn-11',
    type: 'EXPENSE',
    amount: 1_990_000,
    currency: 'VND',
    category: 'UNCATEGORIZED',
    description: 'Shopee — áo thun unisex',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Imported',
    occurredOn: '2026-10-11',
  },
  {
    id: 'txn-12',
    type: 'EXPENSE',
    amount: 99_000,
    currency: 'VND',
    category: 'ENTERTAINMENT',
    description: 'Netflix Premium — gói tháng',
    accountId: 'acc-1',
    accountName: 'TPBank Năng động',
    provenance: 'Recurring',
    occurredOn: '2026-10-05',
  },
];

export const categoryLabels: Record<string, string> = {
  FOOD: 'Ăn uống',
  TRANSPORT: 'Di chuyển',
  HOUSING: 'Nhà cửa',
  ENTERTAINMENT: 'Giải trí',
  SALARY: 'Lương',
  UNCATEGORIZED: 'Chưa phân loại',
};

export function formatVND(amount: number): string {
  return new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits: 0,
  }).format(Math.abs(amount));
}

export function formatVNDShort(amount: number): string {
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toFixed(1)} tỷ`;
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(1)} tr`;
  if (amount >= 1_000) return `${Math.round(amount / 1_000)}k`;
  return String(amount);
}

export function formatDateVi(iso: string): string {
  const d = new Date(iso);
  const days = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
  const dayName = days[d.getDay()];
  return `${dayName}, ${d.getDate()}/${d.getMonth() + 1}`;
}

export function formatDateLongVi(iso: string): string {
  const d = new Date(iso);
  return `${d.getDate()} tháng ${d.getMonth() + 1}, ${d.getFullYear()}`;
}
