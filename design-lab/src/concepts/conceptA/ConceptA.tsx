// Concept A — Modern Private Banking
// Refined, elegant, high-trust. Warm ivory + deep charcoal + rich burgundy accent.
// Editorial serif headings (Fraunces) + clean sans body (Inter).

import { useState } from 'react';
import {
  ArrowUpRight,
  ArrowDownLeft,
  Plus,
  ArrowLeft,
  X,
  Search,
  ChevronRight,
  Wallet,
  Landmark,
  Banknote,
  Calendar,
  Sparkles,
  Filter,
} from 'lucide-react';
import type { Transaction, Account, DashboardMonth } from '../../types';
import {
  formatVND,
  formatVNDShort,
  formatDateVi,
  formatDateLongVi,
  categoryLabels,
} from '../../data';
import { StatusBar, HomeIndicator } from '../../components/PhoneFrame';

const A = {
  ivory: '#F5F1EA',
  cream: '#EDE7DB',
  charcoal: '#1A1714',
  ink: '#2B2722',
  muted: '#7A7065',
  line: '#D8D0C2',
  surface: '#FFFFFF',
  burgundy: '#7A1E2B',
  burgundySoft: '#A03545',
  gold: '#9A7B4F',
  income: '#2D6A4F',
  incomeBg: '#E8F0EA',
  expense: '#7A1E2B',
  expenseBg: '#F5E8E9',
};

// ── Home ─────────────────────────────────────────────────────────────────
export function ConceptAHome({
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
    <div className="h-full overflow-y-auto no-scrollbar" style={{ background: A.ivory }}>
      <StatusBar textColor={A.charcoal} />

      {/* Header */}
      <div className="px-7 pt-4 pb-2" style={{ background: A.ivory }}>
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs tracking-widest uppercase" style={{ color: A.muted, letterSpacing: '0.12em' }}>
              Tháng 10 · 2026
            </p>
            <h1 className="mt-1 font-serif text-[28px] leading-tight" style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 500 }}>
              Xin chào, Minh
            </h1>
          </div>
          <button
            onClick={onAddTransaction}
            className="flex h-11 w-11 items-center justify-center rounded-full transition-transform active:scale-90"
            style={{ background: A.charcoal, color: A.ivory }}
          >
            <Plus size={20} strokeWidth={2.2} />
          </button>
        </div>
      </div>

      {/* Net Position Card */}
      <div className="mx-7 mt-3 rounded-[20px] p-6 animate-fade-up" style={{ background: A.charcoal, color: A.ivory }}>
        <p className="text-xs uppercase tracking-widest opacity-50" style={{ letterSpacing: '0.12em' }}>
          Tổng quan tháng
        </p>
        <div className="mt-3 flex items-baseline gap-2">
          <span className="font-serif text-[36px] tnum leading-none" style={{ fontFamily: 'Fraunces, serif', fontWeight: 600 }}>
            {formatVND(netFlow)}
          </span>
          <span className="text-sm font-medium opacity-60">₫</span>
        </div>
        <p className="mt-1 text-xs opacity-50">Dòng tiền ròng · VND</p>

        {/* Income / Expense split */}
        <div className="mt-5 flex gap-4">
          <div className="flex-1">
            <div className="flex items-center gap-1.5">
              <ArrowDownLeft size={14} style={{ color: '#7BB89A' }} />
              <span className="text-[11px] uppercase tracking-wider opacity-50">Thu nhập</span>
            </div>
            <p className="mt-1 text-[17px] font-semibold tnum" style={{ color: '#A8D5BA' }}>
              {formatVND(dashboard.totalIncome)} ₫
            </p>
          </div>
          <div className="w-px" style={{ background: 'rgba(255,255,255,0.1)' }} />
          <div className="flex-1">
            <div className="flex items-center gap-1.5">
              <ArrowUpRight size={14} style={{ color: '#D49B9B' }} />
              <span className="text-[11px] uppercase tracking-wider opacity-50">Chi tiêu</span>
            </div>
            <p className="mt-1 text-[17px] font-semibold tnum" style={{ color: '#D49B9B' }}>
              {formatVND(dashboard.totalExpense)} ₫
            </p>
          </div>
        </div>
      </div>

      {/* Accounts */}
      <div className="mt-7 px-7">
        <div className="flex items-center justify-between">
          <h2 className="font-serif text-[20px]" style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 500 }}>
            Tài khoản
          </h2>
          <span className="text-xs" style={{ color: A.muted }}>{accounts.length} tài khoản</span>
        </div>
      </div>

      <div className="mt-3 flex gap-3 overflow-x-auto no-scrollbar px-7 pb-2">
        {accounts.map((acc, i) => (
          <AccountCardA key={acc.id} account={acc} delay={i} />
        ))}
      </div>

      {/* Spending breakdown */}
      <div className="mx-7 mt-6 rounded-[20px] p-5 animate-fade-up stagger-3" style={{ background: A.surface, border: `1px solid ${A.line}` }}>
        <div className="flex items-center justify-between">
          <h3 className="font-serif text-[17px]" style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 500 }}>
            Cơ cấu chi tiêu
          </h3>
          <span className="text-[11px] uppercase tracking-wider" style={{ color: A.muted }}>Tháng 10</span>
        </div>

        {/* Bar chart */}
        <div className="mt-4 space-y-3">
          {dashboard.breakdown.map((item) => (
            <div key={item.category}>
              <div className="flex items-center justify-between text-[13px]">
                <span style={{ color: A.ink }}>{categoryLabels[item.category] || item.category}</span>
                <span className="tnum font-medium" style={{ color: A.ink }}>
                  {formatVNDShort(item.amount)} ₫
                </span>
              </div>
              <div className="mt-1.5 h-1.5 overflow-hidden rounded-full" style={{ background: A.cream }}>
                <div
                  className="h-full rounded-full transition-all duration-700"
                  style={{
                    width: `${item.percentage}%`,
                    background: item.category === 'UNCATEGORIZED' ? A.gold : A.burgundy,
                  }}
                />
              </div>
            </div>
          ))}
        </div>
        <p className="mt-4 text-[11px] leading-relaxed" style={{ color: A.muted }}>
          * Chưa bao gồm chi tiêu chưa phân loại trong tổng chi tiêu
        </p>
      </div>

      {/* Recent activity */}
      <RecentActivityA transactions={transactions} onOpenTransaction={onOpenTransaction} />

      <div className="h-24" />
      <HomeIndicator color={A.charcoal} />
    </div>
  );
}

function AccountCardA({ account, delay }: { account: Account; delay: number }) {
  const isBank = account.type === 'BANK';
  return (
    <div
      className={`flex-shrink-0 w-[160px] rounded-[16px] p-4 animate-fade-up stagger-${delay + 1}`}
      style={{ background: isBank ? A.surface : A.charcoal, color: isBank ? A.charcoal : A.ivory, border: isBank ? `1px solid ${A.line}` : 'none' }}
    >
      <div className="flex items-center gap-2">
        {isBank ? (
          <Landmark size={16} style={{ color: A.gold }} />
        ) : (
          <Wallet size={16} style={{ color: A.ivory, opacity: 0.7 }} />
        )}
        <span className="text-[11px] uppercase tracking-wider opacity-60">{isBank ? 'Ngân hàng' : 'Tiền mặt'}</span>
      </div>
      <p className="mt-2 text-[13px] leading-tight font-medium" style={{ color: isBank ? A.ink : A.ivory }}>
        {account.name}
      </p>
      <p className="mt-3 text-[18px] font-semibold tnum">
        {formatVND(account.balance)} <span className="text-[10px] font-normal opacity-50">₫</span>
      </p>
    </div>
  );
}

// Recent activity list
function RecentActivityA({
  transactions,
  onOpenTransaction,
}: {
  transactions: Transaction[];
  onOpenTransaction: (id: string) => void;
}) {
  const txns = transactions;
  return (
    <div className="mt-7 px-7">
      <div className="flex items-center justify-between">
        <h2 className="font-serif text-[20px]" style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 500 }}>
          Hoạt động gần đây
        </h2>
        <button className="text-xs underline" style={{ color: A.muted }}>Xem tất cả</button>
      </div>
      <div className="mt-3 space-y-1">
        {txns.slice(0, 5).map((t) => (
          <TxnRowA key={t.id} txn={t} onClick={() => onOpenTransaction(t.id)} />
        ))}
      </div>
    </div>
  );
}

export function TxnRowA({ txn, onClick }: { txn: Transaction; onClick?: () => void }) {
  const isIncome = txn.type === 'INCOME';
  return (
    <button
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-xl px-2 py-3 text-left transition-colors hover:bg-black/[0.02]"
    >
      <div
        className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full"
        style={{ background: isIncome ? A.incomeBg : A.expenseBg }}
      >
        {isIncome ? (
          <ArrowDownLeft size={16} style={{ color: A.income }} />
        ) : (
          <ArrowUpRight size={16} style={{ color: A.expense }} />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[14px] font-medium" style={{ color: A.ink }}>
          {txn.description}
        </p>
        <p className="text-[11px]" style={{ color: A.muted }}>
          {categoryLabels[txn.category]} · {txn.accountName}
        </p>
      </div>
      <div className="text-right">
        <p className="text-[15px] font-semibold tnum" style={{ color: isIncome ? A.income : A.expense }}>
          {isIncome ? '+' : '−'}{formatVND(txn.amount)}
        </p>
        <p className="text-[10px] uppercase tracking-wider" style={{ color: A.muted }}>{txn.provenance}</p>
      </div>
    </button>
  );
}

// ── Transactions ─────────────────────────────────────────────────────────
export function ConceptATransactions({
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
    <div className="h-full overflow-y-auto no-scrollbar" style={{ background: A.ivory }}>
      <StatusBar textColor={A.charcoal} />

      {/* Header */}
      <div className="px-7 pt-3 pb-4" style={{ background: A.ivory }}>
        <div className="flex items-center justify-between">
          <h1 className="font-serif text-[26px]" style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 500 }}>
            Sổ cái
          </h1>
          <button
            onClick={onAddTransaction}
            className="flex h-10 w-10 items-center justify-center rounded-full transition-transform active:scale-90"
            style={{ background: A.charcoal, color: A.ivory }}
          >
            <Plus size={18} strokeWidth={2.2} />
          </button>
        </div>

        {/* Filter row */}
        <div className="mt-4 flex gap-2">
          {(['ALL', 'INCOME', 'EXPENSE'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className="rounded-full px-4 py-2 text-[13px] font-medium transition-all"
              style={{
                background: filter === f ? A.charcoal : 'transparent',
                color: filter === f ? A.ivory : A.muted,
                border: `1px solid ${filter === f ? A.charcoal : A.line}`,
              }}
            >
              {f === 'ALL' ? 'Tất cả' : f === 'INCOME' ? 'Thu nhập' : 'Chi tiêu'}
            </button>
          ))}
        </div>
      </div>

      {/* List */}
      <div className="px-7 pb-24">
        <p className="py-2 text-[11px] uppercase tracking-widest" style={{ color: A.muted, letterSpacing: '0.1em' }}>
          {filtered.length} giao dịch
        </p>
        <div className="space-y-1">
          {filtered.map((t) => (
            <TxnRowA key={t.id} txn={t} onClick={() => onOpenTransaction(t.id)} />
          ))}
        </div>
        <p className="mt-6 text-center text-[11px] leading-relaxed" style={{ color: A.muted }}>
          Thứ tự giao dịch do hệ thống quyết định · Ngày không hiển thị
        </p>
      </div>

      <HomeIndicator color={A.charcoal} />
    </div>
  );
}

// ── Transaction Detail ────────────────────────────────────────────────────
export function ConceptADetail({
  txn,
  onClose,
}: {
  txn: Transaction;
  onClose: () => void;
}) {
  const isIncome = txn.type === 'INCOME';
  return (
    <div className="absolute inset-0 z-40 flex flex-col animate-slide-up" style={{ background: A.ivory }}>
      <StatusBar textColor={A.charcoal} />
      <div className="flex items-center justify-between px-7 pt-3 pb-4">
        <button onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-full" style={{ background: A.cream }}>
          <X size={18} style={{ color: A.charcoal }} />
        </button>
        <span className="text-[13px] font-medium uppercase tracking-wider" style={{ color: A.muted }}>Chi tiết</span>
        <div className="w-9" />
      </div>

      <div className="flex-1 overflow-y-auto no-scrollbar px-7">
        {/* Amount hero */}
        <div className="mt-4 rounded-[20px] p-6 text-center animate-scale-in" style={{ background: A.surface, border: `1px solid ${A.line}` }}>
          <div
            className="mx-auto flex h-14 w-14 items-center justify-center rounded-full"
            style={{ background: isIncome ? A.incomeBg : A.expenseBg }}
          >
            {isIncome ? <ArrowDownLeft size={24} style={{ color: A.income }} /> : <ArrowUpRight size={24} style={{ color: A.expense }} />}
          </div>
          <p className="mt-3 text-[11px] uppercase tracking-widest" style={{ color: A.muted }}>
            {isIncome ? 'Thu nhập' : 'Chi tiêu'}
          </p>
          <p className="mt-2 font-serif text-[34px] tnum leading-none" style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 600 }}>
            {isIncome ? '+' : '−'}{formatVND(txn.amount)} <span className="text-[14px] opacity-50">₫</span>
          </p>
        </div>

        {/* Detail rows */}
        <div className="mt-4 overflow-hidden rounded-[16px]" style={{ background: A.surface, border: `1px solid ${A.line}` }}>
          <DetailRowA label="Mô tả" value={txn.description} />
          <DividerA />
          <DetailRowA label="Danh mục" value={categoryLabels[txn.category]} />
          <DividerA />
          <DetailRowA label="Tài khoản" value={txn.accountName} />
          <DividerA />
          <DetailRowA label="Nguồn" value={txn.provenance} />
          {txn.note && (
            <>
              <DividerA />
              <DetailRowA label="Ghi chú" value={txn.note} />
            </>
          )}
        </div>

        <p className="mt-6 text-center text-[12px] italic" style={{ color: A.muted }}>
          Các giao dịch đã ghi không thể chỉnh sửa.
        </p>
      </div>

      <HomeIndicator color={A.charcoal} />
    </div>
  );
}

function DetailRowA({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-5 py-4">
      <span className="text-[13px]" style={{ color: A.muted }}>{label}</span>
      <span className="max-w-[60%] text-right text-[14px] font-medium" style={{ color: A.ink }}>{value}</span>
    </div>
  );
}

function DividerA() {
  return <div className="mx-5" style={{ height: 1, background: A.line }} />;
}

// ── Add Transaction ──────────────────────────────────────────────────────
export function ConceptAAdd({
  onClose,
  accounts,
}: {
  onClose: () => void;
  accounts: Account[];
}) {
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('FOOD');
  const [accountId, setAccountId] = useState(accounts[0]?.id || '');

  return (
    <div className="absolute inset-0 z-40 flex flex-col animate-slide-up" style={{ background: A.ivory }}>
      <StatusBar textColor={A.charcoal} />
      <div className="flex items-center justify-between px-7 pt-3 pb-4">
        <button onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-full" style={{ background: A.cream }}>
          <X size={18} style={{ color: A.charcoal }} />
        </button>
        <span className="font-serif text-[18px]" style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 500 }}>
          Ghi giao dịch
        </span>
        <div className="w-9" />
      </div>

      <div className="flex-1 overflow-y-auto no-scrollbar px-7">
        {/* Type switch */}
        <div className="mt-2 flex gap-2 rounded-full p-1" style={{ background: A.cream }}>
          {(['EXPENSE', 'INCOME'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setType(t)}
              className="flex-1 rounded-full py-2.5 text-[14px] font-medium transition-all"
              style={{
                background: type === t ? (t === 'INCOME' ? A.income : A.expense) : 'transparent',
                color: type === t ? '#fff' : A.muted,
              }}
            >
              {t === 'EXPENSE' ? 'Chi tiêu' : 'Thu nhập'}
            </button>
          ))}
        </div>

        {/* Amount */}
        <div className="mt-6 text-center">
          <p className="text-[11px] uppercase tracking-widest" style={{ color: A.muted, letterSpacing: '0.12em' }}>Số tiền</p>
          <div className="mt-2 flex items-baseline justify-center gap-1">
            <input
              autoFocus
              type="text"
              inputMode="numeric"
              value={amount}
              onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, ''))}
              placeholder="0"
              className="w-48 bg-transparent text-center font-serif text-[40px] tnum outline-none"
              style={{ color: A.charcoal, fontFamily: 'Fraunces, serif', fontWeight: 600 }}
            />
            <span className="text-[16px]" style={{ color: A.muted }}>₫</span>
          </div>
        </div>

        {/* Account picker */}
        <div className="mt-6">
          <p className="mb-2 text-[12px] uppercase tracking-wider" style={{ color: A.muted }}>Tài khoản</p>
          <div className="flex gap-2 overflow-x-auto no-scrollbar">
            {accounts.map((acc) => (
              <button
                key={acc.id}
                onClick={() => setAccountId(acc.id)}
                className="flex-shrink-0 rounded-xl px-4 py-3 text-left transition-all"
                style={{
                  background: accountId === acc.id ? A.charcoal : A.surface,
                  color: accountId === acc.id ? A.ivory : A.ink,
                  border: `1px solid ${accountId === acc.id ? A.charcoal : A.line}`,
                }}
              >
                <p className="text-[13px] font-medium">{acc.name}</p>
                <p className="text-[10px] opacity-60">{acc.type === 'BANK' ? 'Ngân hàng' : 'Tiền mặt'} · VND</p>
              </button>
            ))}
          </div>
        </div>

        {/* Category picker */}
        <div className="mt-6">
          <p className="mb-2 text-[12px] uppercase tracking-wider" style={{ color: A.muted }}>Danh mục</p>
          <div className="flex flex-wrap gap-2">
            {Object.entries(categoryLabels).map(([code, label]) => (
              <button
                key={code}
                onClick={() => setCategory(code)}
                className="rounded-full px-4 py-2 text-[13px] font-medium transition-all"
                style={{
                  background: category === code ? A.burgundy : A.surface,
                  color: category === code ? '#fff' : A.ink,
                  border: `1px solid ${category === code ? A.burgundy : A.line}`,
                }}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {/* Date note */}
        <div className="mt-6 flex items-center gap-2 rounded-xl px-4 py-3" style={{ background: A.cream }}>
          <Calendar size={16} style={{ color: A.muted }} />
          <span className="text-[13px]" style={{ color: A.muted }}>Ghi nhận là hôm nay</span>
        </div>
      </div>

      {/* Save button */}
      <div className="px-7 pb-8 pt-4">
        <button
          onClick={onClose}
          className="w-full rounded-full py-4 text-[15px] font-semibold transition-transform active:scale-95"
          style={{ background: A.charcoal, color: A.ivory }}
        >
          Ghi giao dịch
        </button>
      </div>

      <HomeIndicator color={A.charcoal} />
    </div>
  );
}

// ── Bottom Nav ───────────────────────────────────────────────────────────
export function ConceptANav({
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
    <div className="absolute bottom-0 left-0 right-0 z-30 px-6 pb-5 pt-2" style={{ background: 'transparent' }}>
      <div className="flex items-center justify-around rounded-full px-3 py-2.5" style={{ background: A.surface, border: `1px solid ${A.line}`, boxShadow: '0 8px 24px -8px rgba(0,0,0,0.12)' }}>
        {items.map((item) => (
          <button
            key={item.id}
            onClick={() => onNavigate(item.id)}
            className="flex flex-col items-center gap-1 px-3 py-1"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={active === item.id ? A.charcoal : A.muted} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: active === item.id ? 1 : 0.5 }}>
              <path d={item.icon} />
            </svg>
            <span className="text-[10px] font-medium" style={{ color: active === item.id ? A.charcoal : A.muted, opacity: active === item.id ? 1 : 0.5 }}>
              {item.label}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
