import type { ReactNode } from 'react'
import { AlertIcon, RefreshIcon, SearchIcon } from './Icons'

/** Loading placeholder used by grids while data is in flight. */
export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="product-grid" aria-busy="true" aria-live="polite">
      {Array.from({ length: count }, (_, index) => (
        <div className="skeleton-card" key={index}>
          <div className="skeleton skeleton-card__media" />
          <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div className="skeleton" style={{ height: 10, width: '38%' }} />
            <div className="skeleton" style={{ height: 14, width: '82%' }} />
            <div className="skeleton" style={{ height: 10, width: '64%' }} />
            <div className="skeleton" style={{ height: 20, width: '46%', marginTop: 6 }} />
          </div>
        </div>
      ))}
    </div>
  )
}

export function LineSkeleton({ width = '100%', height = 14 }: { width?: string; height?: number }) {
  return <div className="skeleton" style={{ width, height }} />
}

interface EmptyStateProps {
  title: string
  text: string
  icon?: ReactNode
  actions?: ReactNode
}

export function EmptyState({ title, text, icon, actions }: EmptyStateProps) {
  return (
    <div className="state" role="status">
      <div className="state__icon">{icon ?? <SearchIcon size={22} />}</div>
      <div className="state__title">{title}</div>
      <p className="state__text">{text}</p>
      {actions ? <div className="state__actions">{actions}</div> : null}
    </div>
  )
}

interface ErrorStateProps {
  title?: string
  message: string
  onRetry?: () => void
  retryLabel?: string
  secondary?: ReactNode
}

export function ErrorState({ title = 'Something went wrong', message, onRetry, retryLabel = 'Try again', secondary }: ErrorStateProps) {
  return (
    <div className="state" role="alert">
      <div className="state__icon" style={{ background: 'var(--danger-soft)', color: 'var(--danger)' }}>
        <AlertIcon size={22} />
      </div>
      <div className="state__title">{title}</div>
      <p className="state__text">{message}</p>
      <div className="state__actions">
        {onRetry ? (
          <button type="button" className="btn btn--primary" onClick={onRetry}>
            <RefreshIcon size={15} />
            {retryLabel}
          </button>
        ) : null}
        {secondary}
      </div>
    </div>
  )
}

export function InlineNotice({ children, tone = 'info' }: { children: ReactNode; tone?: 'info' | 'warn' | 'danger' }) {
  return <div className={`assistant-banner assistant-banner--${tone}`}>{children}</div>
}
