/**
 * Small hand-rolled icon set so the storefront ships no icon dependency.
 * Every icon inherits `currentColor` and accepts a size.
 */
interface IconProps {
  size?: number
  className?: string
}

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
})

export const SearchIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </svg>
)

export const CartIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.8a2 2 0 0 0 2-1.6L21 8H6" />
    <circle cx="10" cy="20" r="1.4" />
    <circle cx="18" cy="20" r="1.4" />
  </svg>
)

export const UserIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="12" cy="8" r="3.6" />
    <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
  </svg>
)

export const SparkIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M12 3v3M12 18v3M4.9 7.1l2.1 2.1M17 14.8l2.1 2.1M3 12h3M18 12h3M4.9 16.9l2.1-2.1M17 9.2l2.1-2.1" />
    <circle cx="12" cy="12" r="2.6" />
  </svg>
)

export const CloseIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M6 6l12 12M18 6 6 18" />
  </svg>
)

export const SendIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 12l16-8-6 8 6 8z" />
  </svg>
)

export const ChevronDownIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="m6 9 6 6 6-6" />
  </svg>
)

export const ChevronRightIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="m9 6 6 6-6 6" />
  </svg>
)

export const CheckIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="m5 13 4.5 4.5L19 7" />
  </svg>
)

export const AlertIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M12 3.6 21 19.4H3z" />
    <path d="M12 10v4M12 17h.01" />
  </svg>
)

export const InfoIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="12" cy="12" r="8.6" />
    <path d="M12 11v5M12 8h.01" />
  </svg>
)

export const RefreshIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M20 12a8 8 0 1 1-2.6-5.9" />
    <path d="M20 4v5h-5" />
  </svg>
)

export const StarIcon = ({ size = 14, className }: IconProps) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
    <path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.1 5.8-5.3-2.9-5.3 2.9 1.1-5.8L3.5 9.7l5.9-.8z" />
  </svg>
)

export const ThumbUpIcon = ({ size = 14, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M7 10.5V20H4.6a1 1 0 0 1-1-1v-7.5a1 1 0 0 1 1-1z" />
    <path d="M7 11 10.4 4a2 2 0 0 1 2.9 1.9L12.8 9h5.3a2 2 0 0 1 2 2.4l-1.2 6A2 2 0 0 1 17 19H7" />
  </svg>
)

export const ThumbDownIcon = ({ size = 14, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M17 13.5V4h2.4a1 1 0 0 1 1 1v7.5a1 1 0 0 1-1 1z" />
    <path d="M17 13 13.6 20a2 2 0 0 1-2.9-1.9L10.4 15H5.1a2 2 0 0 1-2-2.4l1.2-6A2 2 0 0 1 6.3 5H17" />
  </svg>
)

export const HeadsetIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 13v-1a8 8 0 0 1 16 0v1" />
    <rect x="2.6" y="13" width="4" height="6" rx="1.4" />
    <rect x="17.4" y="13" width="4" height="6" rx="1.4" />
    <path d="M20 19v.5a2.5 2.5 0 0 1-2.5 2.5H13" />
  </svg>
)

export const TrashIcon = ({ size = 15, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" />
  </svg>
)

export const LockIcon = ({ size = 15, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <rect x="4.5" y="10.5" width="15" height="9.5" rx="2" />
    <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
  </svg>
)

export const ChartIcon = ({ size = 17, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 20V4M4 20h16" />
    <path d="M8 20v-6M12.5 20V9M17 20v-8" />
  </svg>
)

export const BoxIcon = ({ size = 22, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5z" />
    <path d="M3.5 7.5 12 12l8.5-4.5M12 12v9" />
  </svg>
)

export const TruckIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M2.5 6.5h10v9h-10zM12.5 10h4l3 3v2.5h-7z" />
    <circle cx="6" cy="17.5" r="1.6" />
    <circle cx="16" cy="17.5" r="1.6" />
  </svg>
)
