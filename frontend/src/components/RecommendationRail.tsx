import { useCallback, useEffect, useRef, useState } from 'react'
import type { RecommendationEvent, RecommendationRail as Rail } from '../types'
import { trackRecommendationEvents } from '../lib/api'
import { ProductCard } from './ProductCard'
import { ErrorState } from './States'
import { SparkIcon } from './Icons'

interface RecommendationRailProps {
  /** Placement name sent with impressions and clicks, so click-through is measurable per slot. */
  surface: string
  loader: (signal: AbortSignal) => Promise<Rail>
  layout?: 'rail' | 'grid'
  /** Shown when the rail has nothing to offer. Omit to render nothing at all. */
  emptyMessage?: string
  /** Re-runs the loader when this changes, e.g. when the basket contents change. */
  refreshKey?: unknown
  heading?: string
}

/**
 * A rail of recommended products.
 *
 * <p>Three behaviours are deliberate:</p>
 *
 * <ul>
 *   <li><b>A failing rail disappears quietly.</b> Recommendations are an enhancement, so a problem
 *       fetching them must not take the page down with it. The error is shown in place, small, with
 *       a retry, and the rest of the page is unaffected.</li>
 *   <li><b>Impressions are counted only when the rail is actually seen.</b> Reporting every item on
 *       render would count products scrolled past and never looked at, which makes click-through
 *       meaningless.</li>
 *   <li><b>Fallbacks are labelled.</b> When the server could not personalise, it says so, and the
 *       rail passes that on rather than passing off a generic list as tailored.</li>
 * </ul>
 */
export function RecommendationRail({
  surface,
  loader,
  layout = 'rail',
  emptyMessage,
  refreshKey,
  heading,
}: RecommendationRailProps) {
  const [rail, setRail] = useState<Rail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)

  const containerRef = useRef<HTMLDivElement | null>(null)
  const reportedRef = useRef<string | null>(null)
  const loaderRef = useRef(loader)
  loaderRef.current = loader

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    setLoading(true)
    setError(null)

    loaderRef
      .current(controller.signal)
      .then((result) => {
        if (!active) return
        setRail(result)
        setLoading(false)
      })
      .catch((cause: unknown) => {
        if (!active) return
        if (cause instanceof DOMException && cause.name === 'AbortError') return
        setError(cause instanceof Error ? cause.message : 'Recommendations are unavailable right now.')
        setLoading(false)
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [attempt, refreshKey])

  const report = useCallback(
    (events: RecommendationEvent[]) => trackRecommendationEvents(events),
    [],
  )

  // Count an impression only once the rail has genuinely been on screen.
  useEffect(() => {
    const node = containerRef.current
    if (!node || !rail || rail.items.length === 0) return
    if (reportedRef.current === rail.recommendationId) return

    const send = () => {
      if (reportedRef.current === rail.recommendationId) return
      reportedRef.current = rail.recommendationId
      report(
        rail.items.map((item) => ({
          type: 'RECO_IMPRESSION' as const,
          productId: item.product.id,
          recommendationId: rail.recommendationId,
          surface,
        })),
      )
    }

    if (typeof IntersectionObserver === 'undefined') {
      send()
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          send()
          observer.disconnect()
        }
      },
      { threshold: 0.35 },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [rail, report, surface])

  const onActivate = (productId: number) => {
    if (!rail) return
    report([
      {
        type: 'RECO_CLICK',
        productId,
        recommendationId: rail.recommendationId,
        surface,
      },
    ])
  }

  if (loading) {
    return (
      <section className="reco" aria-busy="true" aria-live="polite">
        {heading ? <h2 className="section-title">{heading}</h2> : null}
        <div className={`reco__track reco__track--${layout}`}>
          {Array.from({ length: layout === 'rail' ? 4 : 3 }, (_, index) => (
            <div className="skeleton-card reco__skeleton" key={index}>
              <div className="skeleton skeleton-card__media" />
              <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div className="skeleton" style={{ height: 10, width: '40%' }} />
                <div className="skeleton" style={{ height: 13, width: '80%' }} />
                <div className="skeleton" style={{ height: 10, width: '58%' }} />
              </div>
            </div>
          ))}
        </div>
      </section>
    )
  }

  if (error) {
    return (
      <section className="reco" ref={containerRef}>
        <ErrorState
          title="Recommendations unavailable"
          message={error}
          onRetry={() => setAttempt((value) => value + 1)}
          retryLabel="Reload recommendations"
        />
      </section>
    )
  }

  if (!rail || rail.items.length === 0) {
    if (!emptyMessage) return null
    return (
      <section className="reco">
        <div className="reco__empty" role="status">
          <SparkIcon size={16} />
          <span>{emptyMessage}</span>
        </div>
      </section>
    )
  }

  return (
    <section className="reco" ref={containerRef}>
      <div className="reco__head">
        <div>
          <h2 className="section-title reco__title">
            <SparkIcon size={17} />
            {rail.title}
          </h2>
          {rail.subtitle ? <p className="reco__subtitle">{rail.subtitle}</p> : null}
        </div>
        {/* Only the personalised rail can be "not personalised yet". For a cross-sell or a
            similar-products rail the same flag just means the evidence is thin, and the note
            below already says so in plainer words. */}
        {rail.fallback && rail.strategy === 'for_you' ? (
          <span className="pill pill--info">Not personalised yet</span>
        ) : null}
      </div>

      {rail.fallback && rail.note ? (
        <p className="reco__note" role="status">
          {rail.note}
        </p>
      ) : null}

      <div className={`reco__track reco__track--${layout}`}>
        {rail.items.map((item) => (
          <div className="reco__item" key={item.product.id}>
            <ProductCard
              product={item.product}
              reason={item.reason}
              onActivate={() => onActivate(item.product.id)}
            />
          </div>
        ))}
      </div>
    </section>
  )
}
