import { useState } from 'react';
import {
  Sparkles,
  ArrowRight,
  Check,
  ChevronRight,
} from 'lucide-react';
import type { ConceptId, OverlayState, ScreenId } from './types';
import { accounts, dashboard, transactions } from './data';
import { PhoneFrame } from './components/PhoneFrame';

// Concept screens
import {
  ConceptAHome, ConceptATransactions, ConceptADetail, ConceptAAdd, ConceptANav,
} from './concepts/conceptA/ConceptA';
import {
  ConceptBHome, ConceptBTransactions, ConceptBDetail, ConceptBAdd, ConceptBNav,
} from './concepts/conceptB/ConceptB';
import {
  ConceptCHome, ConceptCTransactions, ConceptCDetail, ConceptCAdd, ConceptCNav,
} from './concepts/conceptC/ConceptC';

type View = 'gallery' | 'prototype';

interface ConceptMeta {
  id: ConceptId;
  name: string;
  subtitle: string;
  description: string;
  palette: { name: string; color: string }[];
  font: string;
  accent: string;
  bg: string;
  textColor: string;
}

const CONCEPTS: ConceptMeta[] = [
  {
    id: 'A',
    name: 'Modern Private Banking',
    subtitle: 'Editorial · Warm · High-trust',
    description:
      'A refined interface inspired by luxury financial products. Editorial serif typography, warm ivory surfaces, deep charcoal contrast, and a rich burgundy accent. Spacious, calm, and intentionally adult.',
    palette: [
      { name: 'Ivory', color: '#F5F1EA' },
      { name: 'Charcoal', color: '#1A1714' },
      { name: 'Burgundy', color: '#7A1E2B' },
      { name: 'Gold', color: '#9A7B4F' },
    ],
    font: 'Fraunces + Inter',
    accent: '#7A1E2B',
    bg: '#F5F1EA',
    textColor: '#1A1714',
  },
  {
    id: 'B',
    name: 'Next-Generation Fintech',
    subtitle: 'Bold · Dark · Expressive',
    description:
      'A confident dark-mode consumer fintech experience with electric lime accents, bold grotesk typography, and innovative card composition. Distinctive, modern, and highly competitive.',
    palette: [
      { name: 'Midnight', color: '#0B0F1A' },
      { name: 'Lime', color: '#C6F432' },
      { name: 'Blue', color: '#4D7CFF' },
      { name: 'Violet', color: '#8B5CF6' },
    ],
    font: 'Space Grotesk',
    accent: '#C6F432',
    bg: '#0B0F1A',
    textColor: '#FFFFFF',
  },
  {
    id: 'C',
    name: 'Sculpted Minimalism',
    subtitle: 'Clean · Data-first · Precise',
    description:
      'An exceptionally clean, data-first interface with striking typographic hierarchy, near-monochrome surfaces, and a single emerald accent. Elegant, scannable, and uniquely SpendWise.',
    palette: [
      { name: 'White', color: '#FAFAFA' },
      { name: 'Ink', color: '#0A0A0A' },
      { name: 'Emerald', color: '#00875A' },
      { name: 'Coral', color: '#CC3300' },
    ],
    font: 'Inter (display weights)',
    accent: '#00875A',
    bg: '#FAFAFA',
    textColor: '#0A0A0A',
  },
];

export default function App() {
  const [view, setView] = useState<View>('gallery');
  const [concept, setConcept] = useState<ConceptId>('A');
  const [screen, setScreen] = useState<ScreenId>('home');
  const [overlay, setOverlay] = useState<OverlayState>({ type: 'none' });

  // Reset state when switching concepts
  const switchConcept = (id: ConceptId) => {
    setConcept(id);
    setScreen('home');
    setOverlay({ type: 'none' });
  };

  const activeMeta = CONCEPTS.find((c) => c.id === concept)!;
  const activeTxn = overlay.transactionId
    ? transactions.find((t) => t.id === overlay.transactionId)
    : null;

  const renderScreen = () => {
    if (concept === 'A') {
      return screen === 'home' ? (
        <ConceptAHome
          accounts={accounts}
          dashboard={dashboard}
          transactions={transactions}
          onAddTransaction={() => setOverlay({ type: 'addTransaction', defaultType: 'EXPENSE' })}
          onOpenTransaction={(id) => setOverlay({ type: 'transactionDetail', transactionId: id })}
        />
      ) : (
        <ConceptATransactions
          transactions={transactions}
          onOpenTransaction={(id) => setOverlay({ type: 'transactionDetail', transactionId: id })}
          onAddTransaction={() => setOverlay({ type: 'addTransaction', defaultType: 'EXPENSE' })}
          onBack={() => setScreen('home')}
        />
      );
    }
    if (concept === 'B') {
      return screen === 'home' ? (
        <ConceptBHome
          accounts={accounts}
          dashboard={dashboard}
          transactions={transactions}
          onAddTransaction={() => setOverlay({ type: 'addTransaction', defaultType: 'EXPENSE' })}
          onOpenTransaction={(id) => setOverlay({ type: 'transactionDetail', transactionId: id })}
        />
      ) : (
        <ConceptBTransactions
          transactions={transactions}
          onOpenTransaction={(id) => setOverlay({ type: 'transactionDetail', transactionId: id })}
          onAddTransaction={() => setOverlay({ type: 'addTransaction', defaultType: 'EXPENSE' })}
          onBack={() => setScreen('home')}
        />
      );
    }
    return screen === 'home' ? (
      <ConceptCHome
        accounts={accounts}
        dashboard={dashboard}
        transactions={transactions}
        onAddTransaction={() => setOverlay({ type: 'addTransaction', defaultType: 'EXPENSE' })}
        onOpenTransaction={(id) => setOverlay({ type: 'transactionDetail', transactionId: id })}
      />
    ) : (
      <ConceptCTransactions
        transactions={transactions}
        onOpenTransaction={(id) => setOverlay({ type: 'transactionDetail', transactionId: id })}
        onAddTransaction={() => setOverlay({ type: 'addTransaction', defaultType: 'EXPENSE' })}
        onBack={() => setScreen('home')}
      />
    );
  };

  const renderOverlay = () => {
    if (overlay.type === 'transactionDetail' && activeTxn) {
      if (concept === 'A') return <ConceptADetail txn={activeTxn} onClose={() => setOverlay({ type: 'none' })} />;
      if (concept === 'B') return <ConceptBDetail txn={activeTxn} onClose={() => setOverlay({ type: 'none' })} />;
      return <ConceptCDetail txn={activeTxn} onClose={() => setOverlay({ type: 'none' })} />;
    }
    if (overlay.type === 'addTransaction') {
      if (concept === 'A') return <ConceptAAdd onClose={() => setOverlay({ type: 'none' })} accounts={accounts} />;
      if (concept === 'B') return <ConceptBAdd onClose={() => setOverlay({ type: 'none' })} accounts={accounts} />;
      return <ConceptCAdd onClose={() => setOverlay({ type: 'none' })} accounts={accounts} />;
    }
    return null;
  };

  const renderNav = () => {
    const handleNav = (tab: string) => {
      if (tab === 'home') setScreen('home');
      else if (tab === 'transactions') setScreen('transactions');
    };
    if (concept === 'A') return <ConceptANav active={screen} onNavigate={handleNav} />;
    if (concept === 'B') return <ConceptBNav active={screen} onNavigate={handleNav} />;
    return <ConceptCNav active={screen} onNavigate={handleNav} />;
  };

  // ── Gallery view ─────────────────────────────────────────────────────
  if (view === 'gallery') {
    return <GalleryView concepts={CONCEPTS} onSelect={(id) => { switchConcept(id); setView('prototype'); }} />;
  }

  // ── Prototype view ───────────────────────────────────────────────────
  return (
    <div className="min-h-screen w-full" style={{ background: '#0a0e0d' }}>
      {/* Top bar */}
      <div className="sticky top-0 z-50 border-b border-white/[0.06] backdrop-blur-xl" style={{ background: 'rgba(10,14,13,0.8)' }}>
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <button
            onClick={() => setView('gallery')}
            className="flex items-center gap-2 text-sm font-medium text-white/60 transition-colors hover:text-white"
          >
            <ChevronRight size={16} className="rotate-180" />
            Gallery
          </button>

          {/* Concept switcher */}
          <div className="flex gap-2">
            {CONCEPTS.map((c) => (
              <button
                key={c.id}
                onClick={() => switchConcept(c.id)}
                className="flex items-center gap-2 rounded-full px-4 py-2 text-[13px] font-semibold transition-all"
                style={{
                  background: concept === c.id ? c.accent : 'rgba(255,255,255,0.05)',
                  color: concept === c.id ? (c.id === 'B' ? '#0B0F1A' : '#fff') : 'rgba(255,255,255,0.5)',
                }}
              >
                <span className="flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold" style={{
                  background: concept === c.id ? 'rgba(255,255,255,0.2)' : c.accent + '30',
                  color: concept === c.id ? '#fff' : c.accent,
                }}>
                  {c.id}
                </span>
                {c.name.split(' ')[0]}
              </button>
            ))}
          </div>

          <div className="w-20" />
        </div>
      </div>

      {/* Prototype canvas */}
      <div className="flex flex-col items-center px-6 py-10">
        {/* Concept title */}
        <div className="mb-8 text-center">
          <p className="text-[11px] uppercase tracking-widest text-white/30">Concept {concept}</p>
          <h2 className="mt-1 text-2xl font-bold text-white">{activeMeta.name}</h2>
          <p className="mt-1 text-sm text-white/40">{activeMeta.subtitle}</p>
        </div>

        {/* Phone */}
        <div className="relative">
          <PhoneFrame width={390}>
            <div className="relative h-full w-full">
              {renderScreen()}
              {renderNav()}
              {renderOverlay()}
            </div>
          </PhoneFrame>
        </div>

        {/* Screen switcher */}
        <div className="mt-6 flex gap-2">
          {(['home', 'transactions'] as const).map((s) => (
            <button
              key={s}
              onClick={() => { setScreen(s); setOverlay({ type: 'none' }); }}
              className="rounded-full px-5 py-2 text-[13px] font-medium transition-all"
              style={{
                background: screen === s ? 'rgba(255,255,255,0.1)' : 'transparent',
                color: screen === s ? '#fff' : 'rgba(255,255,255,0.4)',
                border: '1px solid rgba(255,255,255,0.1)',
              }}
            >
              {s === 'home' ? 'Trang chủ' : 'Sổ cái'}
            </button>
          ))}
        </div>

        {/* Interaction hints */}
        <div className="mt-8 max-w-md text-center">
          <p className="text-[12px] leading-relaxed text-white/30">
            Tap the + button to add a transaction · Tap any transaction to see detail ·
            Use the bottom nav to switch between Home and Transactions
          </p>
        </div>
      </div>
    </div>
  );
}

// ── Gallery View ──────────────────────────────────────────────────────────
function GalleryView({
  concepts,
  onSelect,
}: {
  concepts: ConceptMeta[];
  onSelect: (id: ConceptId) => void;
}) {
  const [hovered, setHovered] = useState<ConceptId | null>(null);
  const [selectedWidth] = useState(300);

  return (
    <div className="min-h-screen w-full" style={{
      background: 'radial-gradient(ellipse at top, #121817 0%, #0a0e0d 60%)',
    }}>
      {/* Header */}
      <div className="mx-auto max-w-6xl px-6 pt-16 pb-8">
        <div className="flex items-center gap-2">
          <Sparkles size={16} style={{ color: '#C6F432' }} />
          <span className="text-[11px] uppercase tracking-widest text-white/30">Design Exploration Studio</span>
        </div>
        <h1 className="mt-3 text-4xl font-bold text-white sm:text-5xl" style={{ fontWeight: 800, letterSpacing: '-0.02em' }}>
          SpendWise<span className="text-white/30"> — </span>Visual Identity Lab
        </h1>
        <p className="mt-4 max-w-2xl text-[15px] leading-relaxed text-white/50">
          Three distinct design directions for a premium Vietnamese personal finance app.
          Each concept reimagines the Home dashboard and Transactions screen with a unique
          visual language. Select a concept to explore the interactive prototype.
        </p>
      </div>

      {/* Concept cards */}
      <div className="mx-auto max-w-6xl px-6 pb-16">
        <div className="grid gap-6 md:grid-cols-3">
          {concepts.map((c, i) => (
            <div
              key={c.id}
              className="group cursor-pointer rounded-3xl border border-white/[0.08] p-1 transition-all hover:border-white/20 hover:scale-[1.02]"
              style={{ background: 'rgba(255,255,255,0.02)' }}
              onClick={() => onSelect(c.id)}
              onMouseEnter={() => setHovered(c.id)}
              onMouseLeave={() => setHovered(null)}
            >
              {/* Mini preview */}
              <div className="overflow-hidden rounded-[20px]" style={{ background: c.bg }}>
                <MiniPreview conceptId={c.id} width={selectedWidth} />
              </div>

              {/* Info */}
              <div className="px-4 py-4">
                <div className="flex items-center gap-2">
                  <span
                    className="flex h-7 w-7 items-center justify-center rounded-full text-[13px] font-bold"
                    style={{ background: c.accent + '30', color: c.accent }}
                  >
                    {c.id}
                  </span>
                  <h3 className="text-[16px] font-bold text-white">{c.name}</h3>
                </div>
                <p className="mt-1 text-[12px] text-white/40">{c.subtitle}</p>
                <p className="mt-3 text-[13px] leading-relaxed text-white/50">{c.description}</p>

                {/* Palette */}
                <div className="mt-4 flex gap-1.5">
                  {c.palette.map((p) => (
                    <div key={p.name} className="group/swatch relative">
                      <div
                        className="h-7 w-7 rounded-lg"
                        style={{ background: p.color, border: '1px solid rgba(255,255,255,0.1)' }}
                      />
                      <span className="pointer-events-none absolute -top-7 left-1/2 -translate-x-1/2 whitespace-nowrap rounded bg-white px-1.5 py-0.5 text-[9px] font-medium text-black opacity-0 transition-opacity group-hover/swatch:opacity-80">
                        {p.name}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Font */}
                <p className="mt-3 text-[11px] text-white/30">
                  <span className="text-white/20">Typography: </span>
                  {c.font}
                </p>

                {/* CTA */}
                <div className="mt-4 flex items-center gap-1.5 text-[13px] font-semibold text-white/70 transition-colors group-hover:text-white">
                  Explore prototype
                  <ArrowRight size={14} className="transition-transform group-hover:translate-x-1" />
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Recommendation banner */}
        <div className="mt-10 rounded-2xl border border-white/[0.08] p-6" style={{ background: 'rgba(255,255,255,0.02)' }}>
          <div className="flex items-center gap-2">
            <Check size={16} style={{ color: '#C6F432' }} />
            <h3 className="text-[14px] font-bold text-white">Design Recommendation</h3>
          </div>
          <p className="mt-3 text-[14px] leading-relaxed text-white/50">
            <span className="font-semibold text-white/80">Concept B (Next-Generation Fintech)</span> is the
            strongest candidate for commercialization. Its dark-mode identity with electric lime accents
            creates a distinctive, memorable brand presence that stands apart from conventional banking apps
            while maintaining excellent financial readability. The bold typographic hierarchy and innovative
            card composition feel native to a modern consumer fintech product. Concepts A and C offer
            compelling alternatives — A for a trust-focused premium audience, C for users who prioritize
            clarity above all else.
          </p>
        </div>

        {/* Footer */}
        <div className="mt-12 text-center">
          <p className="text-[12px] text-white/20">
            SpendWise Design Exploration Studio · React + Vite prototype · Not production Android code
          </p>
        </div>
      </div>
    </div>
  );
}

// ── Mini Preview ──────────────────────────────────────────────────────────
function MiniPreview({ conceptId, width }: { conceptId: ConceptId; width: number }) {
  // Render a tiny static preview of the Home screen's hero section
  const meta = CONCEPTS.find((c) => c.id === conceptId)!;
  const scale = width / 390;
  const height = 220;

  if (conceptId === 'A') {
    return (
      <div className="relative overflow-hidden" style={{ height, background: '#F5F1EA' }}>
        <div className="px-5 pt-4">
          <p className="text-[9px] uppercase tracking-widest" style={{ color: '#7A7065' }}>Tháng 10 · 2026</p>
          <h3 className="mt-1 text-[18px]" style={{ color: '#1A1714', fontFamily: 'Fraunces, serif', fontWeight: 500 }}>
            Xin chào, Minh
          </h3>
        </div>
        <div className="mx-5 mt-3 rounded-[16px] p-4" style={{ background: '#1A1714', color: '#F5F1EA' }}>
          <p className="text-[8px] uppercase tracking-widest opacity-50">Tổng quan tháng</p>
          <p className="mt-1.5 text-[22px] tnum" style={{ fontFamily: 'Fraunces, serif', fontWeight: 600 }}>
            14.080.000 ₫
          </p>
          <div className="mt-3 flex gap-3">
            <div>
              <span className="text-[7px] uppercase opacity-50">Thu</span>
              <p className="text-[11px] tnum" style={{ color: '#A8D5BA' }}>32.5tr ₫</p>
            </div>
            <div>
              <span className="text-[7px] uppercase opacity-50">Chi</span>
              <p className="text-[11px] tnum" style={{ color: '#D49B9B' }}>18.4tr ₫</p>
            </div>
          </div>
        </div>
      </div>
    );
  }
  if (conceptId === 'B') {
    return (
      <div className="relative overflow-hidden" style={{ height, background: '#0B0F1A' }}>
        <div className="absolute -right-8 -top-8 h-24 w-24 rounded-full" style={{ background: 'radial-gradient(circle, #C6F43222 0%, transparent 70%)' }} />
        <div className="relative px-5 pt-4">
          <p className="text-[9px] uppercase tracking-widest" style={{ color: '#7C87A8' }}>Xin chào</p>
          <h3 className="mt-0.5 text-[16px] font-bold text-white" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
            Minh 👋
          </h3>
        </div>
        <div className="relative mx-5 mt-3 rounded-[18px] p-4" style={{ background: '#151B2E' }}>
          <p className="text-[8px] uppercase tracking-widest" style={{ color: '#7C87A8' }}>Tổng số dư</p>
          <p className="mt-1.5 text-[22px] font-bold tnum text-white" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
            176.000.000 ₫
          </p>
          <div className="mt-2 inline-flex items-center gap-1 rounded-full px-2 py-0.5" style={{ background: 'rgba(74,222,128,0.12)' }}>
            <span className="text-[8px] font-semibold" style={{ color: '#4ADE80' }}>+14tr tháng này</span>
          </div>
        </div>
      </div>
    );
  }
  return (
    <div className="relative overflow-hidden" style={{ height, background: '#FAFAFA' }}>
      <div className="px-5 pt-4">
        <div className="flex items-baseline justify-between">
          <p className="text-[8px] uppercase tracking-[0.2em] font-semibold" style={{ color: '#6B6B6B' }}>Tháng 10</p>
          <p className="text-[8px] uppercase" style={{ color: '#9B9B9B' }}>VND</p>
        </div>
      </div>
      <div className="px-5 mt-2">
        <p className="text-[10px]" style={{ color: '#6B6B6B' }}>Dòng tiền ròng</p>
        <p className="mt-1 text-[26px] font-bold tnum tracking-tight" style={{ color: '#0A0A0A', fontWeight: 800, letterSpacing: '-0.02em' }}>
          +14.080.000 ₫
        </p>
      </div>
      <div className="mx-5 mt-4 grid grid-cols-2 border-t border-b py-3" style={{ borderColor: '#EAEAEA' }}>
        <div className="pr-3 border-r" style={{ borderColor: '#EAEAEA' }}>
          <p className="text-[7px] uppercase" style={{ color: '#9B9B9B' }}>Thu nhập</p>
          <p className="mt-0.5 text-[12px] font-bold tnum" style={{ color: '#0A0A0A' }}>32.500.000</p>
        </div>
        <div className="pl-3">
          <p className="text-[7px] uppercase" style={{ color: '#9B9B9B' }}>Chi tiêu</p>
          <p className="mt-0.5 text-[12px] font-bold tnum" style={{ color: '#CC3300' }}>18.420.000</p>
        </div>
      </div>
      <div className="mx-5 mt-3 space-y-2">
        <div className="flex justify-between text-[9px]">
          <span style={{ color: '#0A0A0A' }}>Ăn uống</span>
          <span className="tnum" style={{ color: '#0A0A0A' }}>5.840.000</span>
        </div>
        <div className="h-1 rounded-full" style={{ background: '#EAEAEA' }}>
          <div className="h-full rounded-full" style={{ width: '60%', background: '#00875A' }} />
        </div>
      </div>
    </div>
  );
}
