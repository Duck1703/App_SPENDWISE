// Shared UI components for the design gallery shell

import type { ReactNode } from 'react';

interface PhoneFrameProps {
  children: ReactNode;
  width?: number;
  label?: string;
  className?: string;
}

export function PhoneFrame({ children, width = 390, label, className = '' }: PhoneFrameProps) {
  const height = Math.round(width * 844 / 390);
  return (
    <div className={`flex flex-col items-center ${className}`}>
      {label && (
        <span className="mb-3 text-xs font-medium uppercase tracking-widest text-white/40">
          {label}
        </span>
      )}
      <div
        className="phone-shadow relative rounded-[44px] bg-black p-[10px] transition-transform duration-300 hover:scale-[1.01]"
        style={{ width: width + 20, height: height + 20 }}
      >
        <div
          className="relative h-full w-full overflow-hidden rounded-[36px] bg-white"
          style={{ width, height }}
        >
          {/* Notch */}
          <div className="absolute left-1/2 top-0 z-50 h-[30px] w-[110px] -translate-x-1/2 rounded-b-[18px] bg-black" />
          {children}
        </div>
      </div>
    </div>
  );
}

interface StatusBarProps {
  textColor?: string;
  bgColor?: string;
}

export function StatusBar({ textColor = '#000', bgColor = 'transparent' }: StatusBarProps) {
  return (
    <div
      className="flex items-center justify-between px-7 pt-3 pb-1 text-[13px] font-semibold"
      style={{ color: textColor, background: bgColor }}
    >
      <span className="tnum">9:41</span>
      <div className="flex items-center gap-1.5">
        {/* Signal */}
        <svg width="17" height="11" viewBox="0 0 17 11" fill="none">
          <rect x="0" y="7" width="3" height="4" rx="0.5" fill={textColor} />
          <rect x="4.5" y="5" width="3" height="6" rx="0.5" fill={textColor} />
          <rect x="9" y="3" width="3" height="8" rx="0.5" fill={textColor} />
          <rect x="13.5" y="0" width="3" height="11" rx="0.5" fill={textColor} />
        </svg>
        {/* WiFi */}
        <svg width="15" height="11" viewBox="0 0 15 11" fill="none">
          <path d="M7.5 2C5.2 2 3.1 2.9 1.5 4.3L0 2.8C2 .9 4.7 0 7.5 0S13 .9 15 2.8L13.5 4.3C11.9 2.9 9.8 2 7.5 2Z" fill={textColor} />
          <path d="M7.5 5C6.1 5 4.8 5.5 3.8 6.4L2.3 4.9C3.7 3.6 5.5 3 7.5 3S11.3 3.6 12.7 4.9L11.2 6.4C10.2 5.5 8.9 5 7.5 5Z" fill={textColor} />
          <circle cx="7.5" cy="9" r="2" fill={textColor} />
        </svg>
        {/* Battery */}
        <svg width="25" height="11" viewBox="0 0 25 11" fill="none">
          <rect x="0.5" y="0.5" width="21" height="10" rx="2.5" stroke={textColor} strokeOpacity="0.4" />
          <rect x="2" y="2" width="18" height="7" rx="1.5" fill={textColor} />
          <rect x="22.5" y="3.5" width="1.5" height="4" rx="0.75" fill={textColor} fillOpacity="0.4" />
        </svg>
      </div>
    </div>
  );
}

interface HomeIndicatorProps {
  color?: string;
}

export function HomeIndicator({ color = '#000' }: HomeIndicatorProps) {
  return (
    <div className="absolute bottom-2 left-1/2 z-50 h-1 w-32 -translate-x-1/2 rounded-full" style={{ background: color, opacity: 0.3 }} />
  );
}
