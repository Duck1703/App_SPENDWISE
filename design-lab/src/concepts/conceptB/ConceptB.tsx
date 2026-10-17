// Concept B — Next-Generation Fintech
// Expressive, bold, confident. Deep blue-violet surfaces + electric lime accent.
// Modern grotesk typography (Space Grotesk) with bold numeric display.

import { useState } from 'react';
import {
  ArrowUpRight,
  ArrowDownLeft,
  Plus,
  X,
  ChevronRight,
  Wallet,
  Landmark,
  Calendar,
  TrendingUp,
  TrendingDown,
  Sparkles,
  Upload,
} from 'lucide-react';
import type { Transaction, Account, DashboardMonth } from '../../types';
import {
  formatVND,
  formatVNDShort,
  formatDateVi,
  categoryLabels,
} from '../../data';
import { StatusBar, HomeIndicator } from '../../components/PhoneFrame';

const B = {
  bg: '#0B0F1A',
  surface: '#151B2E',
  surface2: '#1E2640',
  card: '#222B48',
  ink: '#FFFFFF',
  muted: '#7C87A8',
  mutedDim: '#4A5378',
  line: 'rgba(255,255,255,0.08)',
  lime: '#C6F432',
  limeDim: '#9FCC1F',
  blue: '#4D7CFF',
  blueDim: '#3A61D9',
  income: '#4ADE80',
  incomeBg: 'rgba(74,222,128,0.12)',
  expense: '#FB7185',
  expenseBg: 'rgba(251,113,133,0.12)',
  violet: '#8B5CF6',
};

// ── Home ─────────────────────────────────────────────────────────────────
export function ConceptBHome({
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
  const totalBalance = accounts.reduce((s, a) => s + a.balance, 0);

  return (
    <div className="h-full overflow-y-auto no-scrollbar" style={{ background: B.bg, color: B.ink }}>
      <StatusBar textColor={B.ink} />

      {/* Header */}
      <div className="px-6 pt-4 pb-2">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-[11px] uppercase tracking-widest" style={{ color: B.muted, letterSpacing: '0.15em' }}>Xin chào</p>
            <h1 className="mt-0.5 text-[24px] font-bold" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700 }}>
              Minh 👋
            </h1>
          </div>
          <div className="flex h-10 w-10 items-center justify-center rounded-full text-[14px] font-bold" style={{ background: B.lime, color: B.bg }}>
            M
          </div>
        </div>
      </div>

      {/* Hero Balance Card */}
      <div className="mx-6 mt-4 rounded-[24px] p-6 animate-fade-up relative overflow-hidden" style={{ background: B.surface }}>
        {/* Decorative glow */}
        <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full" style={{ background: `radial-gradient(circle, ${B.lime}22 0%, transparent 70%)` }} />
        <div className="absolute -left-5 -bottom-10 h-32 w-32 rounded-full" style={{ background: `radial-gradient(circle, ${B.blue}22 0%, transparent 70%)` }} />

        <div className="relative">
          <p className="text-[11px] uppercase tracking-widest" style={{ color: B.muted, letterSpacing: '0.15em' }}>Tổng số dư</p>
          <div className="mt-2 flex items-baseline gap-1.5">
            <span className="text-[38px] font-bold tnum leading-none" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700 }}>
              {formatVND(totalBalance)}
            </span>
            <span className="text-[14px] font-medium" style={{ color: B.muted }}>₫</span>
          </div>

          {/* Net flow pill */}
          <div className="mt-3 inline-flex items-center gap-1.5 rounded-full px-3 py-1" style={{ background: netFlow >= 0 ? B.incomeBg : B.expenseBg }}>
            {netFlow >= 0 ? <TrendingUp size={13} style={{ color: B.income }} /> : <TrendingDown size={13} style={{ color: B.expense }} />}
            <span className="text-[12px] font-semibold tnum" style={{ color: netFlow >= 0 ? B.income : B.expense }}>
              {netFlow >= 0 ? '+' : '−'}{formatVNDShort(netFlow)} ₫ tháng này
            </span>
          </div>
        </div>
      </div>

      {/* Quick Actions */}
      <div className="mx-6 mt-3 grid grid-cols-3 gap-2.5">
        <QuickActionB icon={<Plus size={18} />} label="Ghi" onClick={onAddTransaction} primary />
        <QuickActionB icon={<Upload size={18} />} label="Nhập" onClick={() => {}} />
        <QuickActionB icon={<Sparkles size={18} />} label="Phân tích" onClick={() => {}} />
      </div>

      {/* Income/Expense Cards */}
      <div className="mx-6 mt-4 grid grid-cols-2 gap-2.5">
        <FlowCardB
          label="Thu nhập"
          amount={dashboard.totalIncome}
          icon={<ArrowDownLeft size={16} />}
          color={B.income}
          bg={B.incomeBg}
        />
        <FlowCardB
          label="Chi tiêu"
          amount={dashboard.totalExpense}
          icon={<ArrowUpRight size={16} />}
          color={B.expense}
          bg={B.expenseBg}
        />
      </div>

      {/* Accounts carousel */}
      <div className="mt-6 px-6">
        <div className="flex items-center justify-between">
          <h2 className="text-[16px] font-bold" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700 }}>
            Tài khoản
          </h2>
          <span className="text-[12px]" style={{ color: B.muted }}>{accounts.length}</span>
        </div>
      </div>
      <div className="mt-3 flex gap-3 overflow-x-auto no-scrollbar px-6 pb-2">
        {accounts.map((acc, i) => (
          <AccountCardB key={acc.id} account={acc} delay={i} />
        ))}
      </div>

      {/* Spending Donut + Breakdown */}
      <div className="mx-6 mt-5 rounded-[24px] p-5 animate-fade-up stagger-3" style={{ background: B.surface }}>
        <h3 className="text-[15px] font-bold" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700 }}>
          Chi tiêu theo danh mục
        </h3>
        <div className="mt-4 space-y-3">
          {dashboard.breakdown.map((item, i) => (
            <div key={item.category} className="flex items-center gap-3">
              <div
                className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg text-[10px] font-bold"
                style={{
                  background: [B.lime, B.blue, B.violet, B.income, B.mutedDim][i % 5] + '22',
                  color: [B.lime, B.blue, B.violet, B.income, B.muted][i % 5],
                }}
              >
                {(categoryLabels[item.category] || '?').charAt(0)}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-medium truncate">{categoryLabels[item.category]}</span>
                  <span className="text-[13px] font-semibold tnum" style={{ color: B.muted }}>
                    {formatVNDShort(item.amount)} ₫
                  </span>
                </div>
                <div className="mt-1.5 h-1.5 overflow-hidden rounded-full" style={{ background: B.surface2 }}>
                  <div
                    className="h-full rounded-full transition-all duration-700"
                    style={{
                      width: `${item.percentage}%`,
                      background: [B.lime, B.blue, B.violet, B.income, B.muted][i % 5],
                    }}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Recent activity */}
      <div className="mt-6 px-6">
        <div className="flex items-center justify-between">
          <h2 className="text-[16px] font-bold" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700 }}>
            Giao dịch gần đây
          </h2>
          <button className="text-[12px]" style={{ color: B.lime }}>Tất cả</button>
        </div>
        <div className="mt-3 space-y-2">
          {transactions.slice(0, 4).map((t) => (
            <TxnRowB key={t.id} txn={t} onClick={() => onOpenTransaction(t.id)} />
          ))}
        </div>
      </div>

      <div className="h-28" />
      <HomeIndicator color={B.ink} />
    </div>
  );
}

function QuickActionB({ icon, label, onClick, primary }: { icon: React.ReactNode; label: string; onClick: () => void; primary?: boolean }) {
  return (
    <button
      onClick={onClick}
      className="flex flex-col items-center gap-2 rounded-2xl py-3.5 transition-transform active:scale-95"
      style={{
        background: primary ? B.lime : B.surface,
        color: primary ? B.bg : B.ink,
      }}
    >
      {icon}
      <span className="text-[12px] font-semibold">{label}</span>
    </button>
  );
}

function FlowCardB({ label, amount, icon, color, bg }: { label: string; amount: number; icon: React.ReactNode; color: string; bg: string }) {
  return (
    <div className="rounded-[16px] p-4" style={{ background: B.surface }}>
      <div className="flex items-center gap-1.5">
        <div className="flex h-6 w-6 items-center justify-center rounded-full" style={{ background: bg }}>
          <span style={{ color }}>{icon}</span>
        </div>
        <span className="text-[11px] uppercase tracking-wider" style={{ color: B.muted }}>{label}</span>
      </div>
      <p className="mt-2 text-[18px] font-bold tnum" style={{ color }}>
        {formatVND(amount)} ₫
      </p>
    </div>
  );
}

function AccountCardB({ account, delay }: { account: Account; delay: number }) {
  const isBank = account.type === 'BANK';
  return (
    <div
      className={`flex-shrink-0 w-[170px] rounded-[20px] p-4 animate-fade-up stagger-${delay + 1} relative overflow-hidden`}
      style={{ background: isBank ? B.card : B.surface2, border: `1px solid ${B.line}` }}
    >
      <div className="absolute top-0 right-0 h-16 w-16 rounded-full" style={{ background: `radial-gradient(circle, ${isBank ? B.blue : B.lime}15 0%, transparent 70%)` }} />
      <div className="relative">
        <div className="flex items-center gap-2">
          {isBank ? <Landmark size={15} style={{ color: B.blue }} /> : <Wallet size={15} style={{ color: B.lime }} />}
          <span className="text-[10px] uppercase tracking-wider" style={{ color: B.muted }}>
            {isBank ? 'Bank' : 'Cash'}
          </span>
        </div>
        <p className="mt-2 text-[13px] font-medium leading-tight">{account.name}</p>
        <p className="mt-3 text-[20px] font-bold tnum">
          {formatVNDShort(account.balance)} <span className="text-[11px] font-normal" style={{ color: B.muted }}>₫</span>
        </p>
      </div>
    </div>
  );
}

export function TxnRowB({ txn, onClick }: { txn: Transaction; onClick?: () => void }) {
  const isIncome = txn.type === 'INCOME';
  return (
    <button
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition-colors"
      style={{ background: B.surface }}
    >
      <div
        className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl"
        style={{ background: isIncome ? B.incomeBg : B.expenseBg }}
      >
        {isIncome ? <ArrowDownLeft size={18} style={{ color: B.income }} /> : <ArrowUpRight size={18} style={{ color: B.expense }} />}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[14px] font-semibold">{txn.description}</p>
        <p className="text-[11px]" style={{ color: B.muted }}>
          {categoryLabels[txn.category]} · {txn.accountName}
        </p>
      </div>
      <div className="text-right">
        <p className="text-[15px] font-bold tnum" style={{ color: isIncome ? B.income : B.expense }}>
          {isIncome ? '+' : '−'}{formatVNDShort(txn.amount)}
        </p>
        <p className="text-[9px] uppercase tracking-wider" style={{ color: B.mutedDim }}>{txn.provenance}</p>
      </div>
    </button>
  );
}

// ── Transactions ─────────────────────────────────────────────────────────
export function ConceptBTransactions({
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
    <div className="h-full overflow-y-auto no-scrollbar" style={{ background: B.bg, color: B.ink }}>
      <StatusBar textColor={B.ink} />

      <div className="px-6 pt-3 pb-4">
        <div className="flex items-center justify-between">
          <h1 className="text-[24px] font-bold" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700 }}>
            Sổ cái
          </h1>
          <button
            onClick={onAddTransaction}
            className="flex h-10 w-10 items-center justify-center rounded-xl transition-transform active:scale-90"
            style={{ background: B.lime, color: B.bg }}
          >
            <Plus size={18} strokeWidth={2.5} />
          </button>
        </div>

        {/* Filter chips */}
        <div className="mt-4 flex gap-2">
          {(['ALL', 'INCOME', 'EXPENSE'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className="rounded-full px-4 py-2 text-[13px] font-semibold transition-all"
              style={{
                background: filter === f ? B.lime : B.surface,
                color: filter === f ? B.bg : B.muted,
              }}
            >
              {f === 'ALL' ? 'Tất cả' : f === 'INCOME' ? 'Thu nhập' : 'Chi tiêu'}
            </button>
          ))}
        </div>
      </div>

      <div className="px-6 pb-24">
        <p className="py-2 text-[11px] uppercase tracking-widest" style={{ color: B.muted, letterSpacing: '0.1em' }}>
          {filtered.length} giao dịch
        </p>
        <div className="space-y-2">
          {filtered.map((t) => (
            <TxnRowB key={t.id} txn={t} onClick={() => onOpenTransaction(t.id)} />
          ))}
        </div>
        <p className="mt-6 text-center text-[11px]" style={{ color: B.mutedDim }}>
          Thứ tự do hệ thống quyết định
        </p>
      </div>

      <HomeIndicator color={B.ink} />
    </div>
  );
}

// ── Transaction Detail ───────────────────────────────────────────────────
export function ConceptBDetail({ txn, onClose }: { txn: Transaction; onClose: () => void }) {
  const isIncome = txn.type === 'INCOME';
  return (
    <div className="absolute inset-0 z-40 flex flex-col animate-slide-up" style={{ background: B.bg, color: B.ink }}>
      <StatusBar textColor={B.ink} />
      <div className="flex items-center justify-between px-6 pt-3 pb-4">
        <button onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-xl" style={{ background: B.surface }}>
          <X size={18} style={{ color: B.ink }} />
        </button>
        <span className="text-[13px] font-semibold uppercase tracking-wider" style={{ color: B.muted }}>Chi tiết</span>
        <div className="w-9" />
      </div>

      <div className="flex-1 overflow-y-auto no-scrollbar px-6">
        <div className="mt-4 rounded-[24px] p-6 text-center animate-scale-in" style={{ background: B.surface }}>
          <div
            className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl"
            style={{ background: isIncome ? B.incomeBg : B.expenseBg }}
          >
            {isIncome ? <ArrowDownLeft size={28} style={{ color: B.income }} /> : <ArrowUpRight size={28} style={{ color: B.expense }} />}
          </div>
          <p className="mt-3 text-[11px] uppercase tracking-widest" style={{ color: B.muted }}>
            {isIncome ? 'Thu nhập' : 'Chi tiêu'}
          </p>
          <p className="mt-2 text-[36px] font-bold tnum leading-none" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700, color: isIncome ? B.income : B.expense }}>
            {isIncome ? '+' : '−'}{formatVND(txn.amount)} <span className="text-[14px]" style={{ color: B.muted }}>₫</span>
          </p>
        </div>

        <div className="mt-4 overflow-hidden rounded-[20px]" style={{ background: B.surface }}>
          <DetailRowB label="Mô tả" value={txn.description} />
          <DividerB />
          <DetailRowB label="Danh mục" value={categoryLabels[txn.category]} />
          <DividerB />
          <DetailRowB label="Tài khoản" value={txn.accountName} />
          <DividerB />
          <DetailRowB label="Nguồn" value={txn.provenance} />
          {txn.note && (
            <>
              <DividerB />
              <DetailRowB label="Ghi chú" value={txn.note} />
            </>
          )}
        </div>

        <p className="mt-6 text-center text-[12px]" style={{ color: B.mutedDim }}>
          Giao dịch đã ghi không thể chỉnh sửa
        </p>
      </div>

      <HomeIndicator color={B.ink} />
    </div>
  );
}

function DetailRowB({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-5 py-4">
      <span className="text-[13px]" style={{ color: B.muted }}>{label}</span>
      <span className="max-w-[60%] text-right text-[14px] font-semibold">{value}</span>
    </div>
  );
}

function DividerB() {
  return <div className="mx-5" style={{ height: 1, background: B.line }} />;
}

// ── Add Transaction ──────────────────────────────────────────────────────
export function ConceptBAdd({ onClose, accounts }: { onClose: () => void; accounts: Account[] }) {
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('FOOD');
  const [accountId, setAccountId] = useState(accounts[0]?.id || '');

  return (
    <div className="absolute inset-0 z-40 flex flex-col animate-slide-up" style={{ background: B.bg, color: B.ink }}>
      <StatusBar textColor={B.ink} />
      <div className="flex items-center justify-between px-6 pt-3 pb-4">
        <button onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-xl" style={{ background: B.surface }}>
          <X size={18} />
        </button>
        <span className="text-[18px] font-bold" style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700 }}>Ghi giao dịch</span>
        <div className="w-9" />
      </div>

      <div className="flex-1 overflow-y-auto no-scrollbar px-6">
        {/* Type toggle */}
        <div className="mt-2 flex gap-2">
          {(['EXPENSE', 'INCOME'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setType(t)}
              className="flex-1 rounded-2xl py-3.5 text-[14px] font-bold transition-all"
              style={{
                background: type === t ? (t === 'INCOME' ? B.income : B.expense) : B.surface,
                color: type === t ? B.bg : B.muted,
              }}
            >
              {t === 'EXPENSE' ? 'Chi tiêu' : 'Thu nhập'}
            </button>
          ))}
        </div>

        {/* Amount */}
        <div className="mt-8 text-center">
          <p className="text-[11px] uppercase tracking-widest" style={{ color: B.muted, letterSpacing: '0.15em' }}>Số tiền (VND)</p>
          <div className="mt-3 flex items-baseline justify-center gap-1">
            <input
              autoFocus
              type="text"
              inputMode="numeric"
              value={amount}
              onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, ''))}
              placeholder="0"
              className="w-56 bg-transparent text-center text-[44px] font-bold tnum outline-none"
              style={{ fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700, color: B.ink }}
            />
            <span className="text-[18px]" style={{ color: B.muted }}>₫</span>
          </div>
        </div>

        {/* Account */}
        <div className="mt-6">
          <p className="mb-2 text-[12px] uppercase tracking-wider" style={{ color: B.muted }}>Tài khoản</p>
          <div className="flex gap-2 overflow-x-auto no-scrollbar">
            {accounts.map((acc) => (
              <button
                key={acc.id}
                onClick={() => setAccountId(acc.id)}
                className="flex-shrink-0 rounded-2xl px-4 py-3 text-left transition-all"
                style={{
                  background: accountId === acc.id ? B.lime : B.surface,
                  color: accountId === acc.id ? B.bg : B.ink,
                  border: `1px solid ${B.line}`,
                }}
              >
                <p className="text-[13px] font-bold">{acc.name}</p>
                <p className="text-[10px] opacity-60">{acc.type === 'BANK' ? 'Bank' : 'Cash'} · VND</p>
              </button>
            ))}
          </div>
        </div>

        {/* Category */}
        <div className="mt-6">
          <p className="mb-2 text-[12px] uppercase tracking-wider" style={{ color: B.muted }}>Danh mục</p>
          <div className="flex flex-wrap gap-2">
            {Object.entries(categoryLabels).map(([code, label]) => (
              <button
                key={code}
                onClick={() => setCategory(code)}
                className="rounded-full px-4 py-2 text-[13px] font-semibold transition-all"
                style={{
                  background: category === code ? B.blue : B.surface,
                  color: category === code ? '#fff' : B.ink,
                }}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 flex items-center gap-2 rounded-2xl px-4 py-3" style={{ background: B.surface }}>
          <Calendar size={16} style={{ color: B.muted }} />
          <span className="text-[13px]" style={{ color: B.muted }}>Ghi nhận là hôm nay</span>
        </div>
      </div>

      <div className="px-6 pb-8 pt-4">
        <button
          onClick={onClose}
          className="w-full rounded-2xl py-4 text-[15px] font-bold transition-transform active:scale-95"
          style={{ background: B.lime, color: B.bg }}
        >
          Ghi giao dịch
        </button>
      </div>

      <HomeIndicator color={B.ink} />
    </div>
  );
}

// ── Bottom Nav ───────────────────────────────────────────────────────────
export function ConceptBNav({
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
    <div className="absolute bottom-0 left-0 right-0 z-30 px-4 pb-4 pt-2">
      <div className="flex items-center justify-around rounded-[20px] px-2 py-2" style={{ background: B.surface, border: `1px solid ${B.line}` }}>
        {items.map((item) => (
          <button
            key={item.id}
            onClick={() => onNavigate(item.id)}
            className="relative flex flex-col items-center gap-1 rounded-xl px-4 py-1.5 transition-all"
            style={{ background: active === item.id ? B.lime + '22' : 'transparent' }}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={active === item.id ? B.lime : B.muted} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d={item.icon} />
            </svg>
            <span className="text-[10px] font-semibold" style={{ color: active === item.id ? B.lime : B.muted }}>
              {item.label}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
