// Concept C — Sculpted Minimalism
// Ultra-clean, data-first. Near-monochrome with a single emerald accent.
// Strong typographic hierarchy (Inter), precise alignment, generous whitespace.

import { useState } from 'react';
import {
  Plus,
  X,
  ArrowLeft,
  Calendar,
  ChevronRight,
} from 'lucide-react';
import type { Transaction, Account, DashboardMonth } from '../../types';
import {
  formatVND,
  formatVNDShort,
  categoryLabels,
} from '../../data';
import { StatusBar, HomeIndicator } from '../../components/PhoneFrame';

const C = {
  bg: '#FAFAFA',
  surface: '#FFFFFF',
  ink: '#0A0A0A',
  gray1: '#6B6B6B',
  gray2: '#9B9B9B',
  gray3: '#D0D0D0',
  gray4: '#EAEAEA',
  line: '#F0F0F0',
  accent: '#00875A',
  accentDim: '#006B47',
  accentBg: '#E6F4EF',
  income: '#00875A',
  incomeBg: '#E6F4EF',
  expense: '#CC3300',
  expenseBg: '#FCEEEA',
};

// ── Home ─────────────────────────────────────────────────────────────────
export function ConceptCHome({
  accounts,
  dashboard,
  transactions,
  onAddTransaction,
  onOpenTransaction,
}: {
  accounts: Account[];
  dashboard: DashboardMonth;
  transactions: Transaction[];
  onAddTransaction: () => void;
  onOpenTransaction: (id: string) => void;
}) {
  const netFlow = dashboard.totalIncome - dashboard.totalExpense;

  return (
    <div className="h-full overflow-y-auto no-scrollbar" style={{ background: C.bg, color: C.ink }}>
      <StatusBar textColor={C.ink} />

      {/* Header — minimal */}
      <div className="px-6 pt-5 pb-1">
        <div className="flex items-baseline justify-between">
          <p className="text-[11px] uppercase tracking-[0.2em] font-semibold" style={{ color: C.gray1 }}>
            Tháng 10
          </p>
          <p className="text-[11px] uppercase tracking-[0.15em]" style={{ color: C.gray2 }}>VND</p>
        </div>
      </div>

      {/* Net flow — typographic hero */}
      <div className="px-6 pt-3 animate-fade-up">
        <p className="text-[13px]" style={{ color: C.gray1 }}>Dòng tiền ròng</p>
        <div className="mt-1 flex items-baseline gap-1">
          <span className="text-[44px] font-bold tnum leading-[1] tracking-tight" style={{ fontWeight: 800, letterSpacing: '-0.02em' }}>
            {netFlow >= 0 ? '+' : '−'}{formatVND(netFlow)}
          </span>
          <span className="text-[16px] font-medium" style={{ color: C.gray1 }}>₫</span>
        </div>
      </div>

      {/* Income / Expense — minimal split */}
      <div className="mx-6 mt-5 grid grid-cols-2 border-t border-b py-4 animate-fade-up stagger-1" style={{ borderColor: C.gray4 }}>
        <div className="pr-4 border-r" style={{ borderColor: C.gray4 }}>
          <p className="text-[11px] uppercase tracking-wider" style={{ color: C.gray2 }}>Thu nhập</p>
          <p className="mt-1 text-[17px] font-bold tnum" style={{ fontWeight: 700 }}>
            {formatVND(dashboard.totalIncome)}
          </p>
        </div>
        <div className="pl-4">
          <p className="text-[11px] uppercase tracking-wider" style={{ color: C.gray2 }}>Chi tiêu</p>
          <p className="mt-1 text-[17px] font-bold tnum" style={{ fontWeight: 700, color: C.expense }}>
            {formatVND(dashboard.totalExpense)}
          </p>
        </div>
      </div>

      {/* Add button — inline, minimal */}
      <div className="mx-6 mt-4">
        <button
          onClick={onAddTransaction}
          className="flex w-full items-center justify-center gap-2 rounded-xl py-3.5 text-[14px] font-semibold transition-all active:scale-[0.98]"
          style={{ background: C.ink, color: C.bg }}
        >
          <Plus size={16} strokeWidth={2.5} />
          Ghi giao dịch
        </button>
      </div>

      {/* Accounts — list style */}
      <div className="mt-8 px-6">
        <div className="flex items-baseline justify-between">
          <h2 className="text-[13px] uppercase tracking-wider font-semibold" style={{ color: C.gray1 }}>
            Tài khoản
          </h2>
          <span className="text-[11px]" style={{ color: C.gray2 }}>{accounts.length}</span>
        </div>
        <div className="mt-2 divide-y" style={{ borderColor: C.line }}>
          {accounts.map((acc, i) => (
            <AccountRowC key={acc.id} account={acc} delay={i} />
          ))}
        </div>
      </div>

      {/* Spending — horizontal bars, minimal */}
      <div className="mt-8 px-6">
        <h2 className="text-[13px] uppercase tracking-wider font-semibold" style={{ color: C.gray1 }}>
          Chi tiêu theo danh mục
        </h2>
        <div className="mt-4 space-y-4">
          {dashboard.breakdown.map((item, i) => (
            <div key={item.category} className={`animate-fade-up stagger-${i + 1}`}>
              <div className="flex items-baseline justify-between">
                <span className="text-[14px] font-medium">{categoryLabels[item.category]}</span>
                <span className="text-[14px] font-semibold tnum">
                  {formatVND(item.amount)} ₫
                </span>
              </div>
              <div className="mt-1.5 h-1 overflow-hidden rounded-full" style={{ background: C.gray4 }}>
                <div
                  className="h-full rounded-full transition-all duration-700"
                  style={{
                    width: `${item.percentage}%`,
                    background: item.category === 'UNCATEGORIZED' ? C.gray2 : C.accent,
                  }}
                />
              </div>
              <span className="mt-0.5 block text-[11px] tnum" style={{ color: C.gray2 }}>{item.percentage}%</span>
            </div>
          ))}
        </div>
      </div>

      {/* Recent — minimal list */}
      <div className="mt-8 px-6">
        <div className="flex items-baseline justify-between">
          <h2 className="text-[13px] uppercase tracking-wider font-semibold" style={{ color: C.gray1 }}>
            Giao dịch gần đây
          </h2>
          <button className="text-[12px] font-medium underline" style={{ color: C.accent }}>Tất cả</button>
        </div>
        <div className="mt-2 divide-y" style={{ borderColor: C.line }}>
          {transactions.slice(0, 5).map((t) => (
            <TxnRowC key={t.id} txn={t} onClick={() => onOpenTransaction(t.id)} />
          ))}
        </div>
      </div>

      <div className="h-24" />
      <HomeIndicator color={C.ink} />
    </div>
  );
}

function AccountRowC({ account, delay }: { account: Account; delay: number }) {
  return (
    <div className={`flex items-center justify-between py-3.5 animate-fade-up stagger-${delay + 1}`} style={{ borderColor: C.line }}>
      <div className="flex items-center gap-3">
        <div
          className="flex h-8 w-8 items-center justify-center rounded-lg text-[10px] font-bold"
          style={{
            background: account.type === 'BANK' ? C.gray4 : C.accentBg,
            color: account.type === 'BANK' ? C.gray1 : C.accent,
          }}
        >
          {account.type === 'BANK' ? 'B' : 'C'}
        </div>
        <div>
          <p className="text-[14px] font-medium leading-tight">{account.name}</p>
          <p className="text-[11px]" style={{ color: C.gray2 }}>
            {account.type === 'BANK' ? 'Ngân hàng' : 'Tiền mặt'}
          </p>
        </div>
      </div>
      <p className="text-[16px] font-bold tnum">{formatVND(account.balance)} ₫</p>
    </div>
  );
}

export function TxnRowC({ txn, onClick }: { txn: Transaction; onClick?: () => void }) {
  const isIncome = txn.type === 'INCOME';
  return (
    <button
      onClick={onClick}
      className="flex w-full items-center justify-between py-3.5 text-left transition-colors hover:bg-black/[0.01]"
    >
      <div className="min-w-0 flex-1">
        <p className="truncate text-[14px] font-medium leading-tight">{txn.description}</p>
        <p className="mt-0.5 text-[11px]" style={{ color: C.gray2 }}>
          {categoryLabels[txn.category]} · {txn.accountName}
        </p>
      </div>
      <div className="ml-3 text-right">
        <p
          className="text-[15px] font-bold tnum leading-tight"
          style={{ color: isIncome ? C.income : C.expense }}
        >
          {isIncome ? '+' : '−'}{formatVND(txn.amount)}
        </p>
        <p className="text-[10px] uppercase tracking-wider" style={{ color: C.gray2 }}>{txn.provenance}</p>
      </div>
    </button>
  );
}

// ── Transactions ─────────────────────────────────────────────────────────
export function ConceptCTransactions({
  transactions,
  onOpenTransaction,
  onAddTransaction,
  onBack,
}: {
  transactions: Transaction[];
  onOpenTransaction: (id: string) => void;
  onAddTransaction: () => void;
  onBack: () => void;
}) {
  const [filter, setFilter] = useState<'ALL' | 'INCOME' | 'EXPENSE'>('ALL');
  const filtered = transactions.filter((t) => filter === 'ALL' || t.type === filter);

  return (
    <div className="h-full overflow-y-auto no-scrollbar" style={{ background: C.bg, color: C.ink }}>
      <StatusBar textColor={C.ink} />

      <div className="px-6 pt-4 pb-4">
        <div className="flex items-center justify-between">
          <h1 className="text-[28px] font-bold tracking-tight" style={{ fontWeight: 800, letterSpacing: '-0.02em' }}>
            Sổ cái
          </h1>
          <button
            onClick={onAddTransaction}
            className="flex h-10 w-10 items-center justify-center rounded-lg transition-transform active:scale-90"
            style={{ background: C.ink, color: C.bg }}
          >
            <Plus size={18} strokeWidth={2.5} />
          </button>
        </div>

        {/* Filters — text-based, minimal */}
        <div className="mt-4 flex gap-5 border-b pb-2" style={{ borderColor: C.gray4 }}>
          {(['ALL', 'INCOME', 'EXPENSE'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className="relative text-[14px] font-semibold transition-colors"
              style={{ color: filter === f ? C.ink : C.gray2 }}
            >
              {f === 'ALL' ? 'Tất cả' : f === 'INCOME' ? 'Thu nhập' : 'Chi tiêu'}
              {filter === f && (
                <div className="absolute -bottom-2 left-0 right-0 h-0.5 rounded-full" style={{ background: C.accent }} />
              )}
            </button>
          ))}
        </div>
      </div>

      <div className="px-6 pb-24">
        <p className="py-3 text-[11px] uppercase tracking-wider" style={{ color: C.gray2 }}>
          {filtered.length} giao dịch
        </p>
        <div className="divide-y" style={{ borderColor: C.line }}>
          {filtered.map((t) => (
            <TxnRowC key={t.id} txn={t} onClick={() => onOpenTransaction(t.id)} />
          ))}
        </div>
        <p className="mt-6 text-center text-[11px]" style={{ color: C.gray2 }}>
          Thứ tự do hệ thống quyết định · Ngày không hiển thị
        </p>
      </div>

      <HomeIndicator color={C.ink} />
    </div>
  );
}

// ── Transaction Detail ────────────────────────────────────────────────────
export function ConceptCDetail({ txn, onClose }: { txn: Transaction; onClose: () => void }) {
  const isIncome = txn.type === 'INCOME';
  return (
    <div className="absolute inset-0 z-40 flex flex-col animate-slide-up" style={{ background: C.bg, color: C.ink }}>
      <StatusBar textColor={C.ink} />
      <div className="flex items-center justify-between px-6 pt-3 pb-4">
        <button onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-lg" style={{ background: C.gray4 }}>
          <X size={18} />
        </button>
        <span className="text-[13px] font-semibold uppercase tracking-wider" style={{ color: C.gray1 }}>Chi tiết</span>
        <div className="w-9" />
      </div>

      <div className="flex-1 overflow-y-auto no-scrollbar px-6">
        {/* Amount — typographic */}
        <div className="mt-6 animate-scale-in">
          <p className="text-[11px] uppercase tracking-widest" style={{ color: C.gray2, letterSpacing: '0.15em' }}>
            {isIncome ? 'Thu nhập' : 'Chi tiêu'}
          </p>
          <div className="mt-2 flex items-baseline gap-1">
            <span className="text-[40px] font-bold tnum leading-none tracking-tight" style={{ fontWeight: 800, letterSpacing: '-0.02em', color: isIncome ? C.income : C.expense }}>
              {isIncome ? '+' : '−'}{formatVND(txn.amount)}
            </span>
            <span className="text-[16px] font-medium" style={{ color: C.gray1 }}>₫</span>
          </div>
        </div>

        {/* Detail rows — bordered list */}
        <div className="mt-6 divide-y border-y" style={{ borderColor: C.gray4 }}>
          <DetailRowC label="Mô tả" value={txn.description} />
          <DetailRowC label="Danh mục" value={categoryLabels[txn.category]} />
          <DetailRowC label="Tài khoản" value={txn.accountName} />
          <DetailRowC label="Nguồn" value={txn.provenance} />
          {txn.note && <DetailRowC label="Ghi chú" value={txn.note} />}
        </div>

        <p className="mt-6 text-center text-[12px]" style={{ color: C.gray2 }}>
          Giao dịch đã ghi không thể chỉnh sửa
        </p>
      </div>

      <HomeIndicator color={C.ink} />
    </div>
  );
}

function DetailRowC({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-3.5">
      <span className="text-[13px]" style={{ color: C.gray1 }}>{label}</span>
      <span className="max-w-[60%] text-right text-[14px] font-semibold">{value}</span>
    </div>
  );
}

// ── Add Transaction ──────────────────────────────────────────────────────
export function ConceptCAdd({ onClose, accounts }: { onClose: () => void; accounts: Account[] }) {
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('FOOD');
  const [accountId, setAccountId] = useState(accounts[0]?.id || '');

  return (
    <div className="absolute inset-0 z-40 flex flex-col animate-slide-up" style={{ background: C.bg, color: C.ink }}>
      <StatusBar textColor={C.ink} />
      <div className="flex items-center justify-between px-6 pt-3 pb-4">
        <button onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-lg" style={{ background: C.gray4 }}>
          <X size={18} />
        </button>
        <span className="text-[18px] font-bold" style={{ fontWeight: 700 }}>Ghi giao dịch</span>
        <div className="w-9" />
      </div>

      <div className="flex-1 overflow-y-auto no-scrollbar px-6">
        {/* Type toggle — underline style */}
        <div className="mt-2 flex border-b" style={{ borderColor: C.gray4 }}>
          {(['EXPENSE', 'INCOME'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setType(t)}
              className="relative flex-1 py-3 text-[14px] font-bold transition-colors"
              style={{ color: type === t ? C.ink : C.gray2 }}
            >
              {t === 'EXPENSE' ? 'Chi tiêu' : 'Thu nhập'}
              {type === t && (
                <div className="absolute -bottom-px left-0 right-0 h-0.5" style={{ background: C.accent }} />
              )}
            </button>
          ))}
        </div>

        {/* Amount — large typographic */}
        <div className="mt-8 text-center">
          <p className="text-[11px] uppercase tracking-widest" style={{ color: C.gray2, letterSpacing: '0.15em' }}>Số tiền</p>
          <div className="mt-3 flex items-baseline justify-center gap-1">
            <input
              autoFocus
              type="text"
              inputMode="numeric"
              value={amount}
              onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, ''))}
              placeholder="0"
              className="w-52 bg-transparent text-center text-[44px] font-bold tnum outline-none"
              style={{ fontWeight: 800, letterSpacing: '-0.02em', color: C.ink }}
            />
            <span className="text-[18px]" style={{ color: C.gray1 }}>₫</span>
          </div>
        </div>

        {/* Account — list style */}
        <div className="mt-6">
          <p className="mb-1 text-[12px] uppercase tracking-wider" style={{ color: C.gray1 }}>Tài khoản</p>
          <div className="divide-y border-y" style={{ borderColor: C.gray4 }}>
            {accounts.map((acc) => (
              <button
                key={acc.id}
                onClick={() => setAccountId(acc.id)}
                className="flex w-full items-center justify-between py-3 text-left"
              >
                <div>
                  <p className="text-[14px] font-medium">{acc.name}</p>
                  <p className="text-[11px]" style={{ color: C.gray2 }}>{acc.type === 'BANK' ? 'Ngân hàng' : 'Tiền mặt'} · VND</p>
                </div>
                <div className="h-4 w-4 rounded-full border-2 transition-all" style={{
                  borderColor: accountId === acc.id ? C.accent : C.gray3,
                  background: accountId === acc.id ? C.accent : 'transparent',
                }} />
              </button>
            ))}
          </div>
        </div>

        {/* Category — text pills */}
        <div className="mt-6">
          <p className="mb-2 text-[12px] uppercase tracking-wider" style={{ color: C.gray1 }}>Danh mục</p>
          <div className="flex flex-wrap gap-2">
            {Object.entries(categoryLabels).map(([code, label]) => (
              <button
                key={code}
                onClick={() => setCategory(code)}
                className="rounded-full px-3.5 py-1.5 text-[13px] font-medium transition-all"
                style={{
                  background: category === code ? C.ink : 'transparent',
                  color: category === code ? C.bg : C.gray1,
                  border: `1px solid ${category === code ? C.ink : C.gray3}`,
                }}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 flex items-center gap-2 py-3 border-t border-b" style={{ borderColor: C.gray4 }}>
          <Calendar size={15} style={{ color: C.gray1 }} />
          <span className="text-[13px]" style={{ color: C.gray1 }}>Ghi nhận là hôm nay</span>
        </div>
      </div>

      <div className="px-6 pb-8 pt-4">
        <button
          onClick={onClose}
          className="w-full rounded-xl py-4 text-[15px] font-bold transition-transform active:scale-[0.98]"
          style={{ background: C.ink, color: C.bg }}
        >
          Ghi giao dịch
        </button>
      </div>

      <HomeIndicator color={C.ink} />
    </div>
  );
}

// ── Bottom Nav ───────────────────────────────────────────────────────────
export function ConceptCNav({
  active,
  onNavigate,
}: {
  active: 'home' | 'transactions' | 'accounts' | 'more';
  onNavigate: (tab: string) => void;
}) {
  const items = [
    { id: 'home', label: 'Trang chủ', icon: 'M3 12l9-9 9 9M5 10v10h14V10' },
    { id: 'transactions', label: 'Sổ cái', icon: 'M4 6h16M4 12h16M4 18h10' },
    { id: 'accounts', label: 'Tài khoản', icon: 'M3 10h18M5 6h14a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2z' },
    { id: 'more', label: 'Khác', icon: 'M12 5v.01M12 12v.01M12 19v.01' },
  ];
  return (
    <div className="absolute bottom-0 left-0 right-0 z-30 border-t" style={{ background: C.surface, borderColor: C.gray4 }}>
      <div className="flex items-center justify-around py-2">
        {items.map((item) => (
          <button
            key={item.id}
            onClick={() => onNavigate(item.id)}
            className="flex flex-col items-center gap-0.5 px-3 py-1"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={active === item.id ? C.accent : C.gray2} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d={item.icon} />
            </svg>
            <span className="text-[9px] font-semibold" style={{ color: active === item.id ? C.accent : C.gray2 }}>
              {item.label}
            </span>
          </button>
        ))}
      </div>
      <HomeIndicator color={C.ink} />
    </div>
  );
}
