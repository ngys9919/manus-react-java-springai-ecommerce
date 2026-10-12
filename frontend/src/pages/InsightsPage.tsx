import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type { AnalyticsSummary, EscalationView } from '../types'
import { dateTime, percent, relativeTime } from '../lib/format'
import { useAuth } from '../state/AuthContext'
import { useToast } from '../state/ToastContext'
import { AlertIcon, ChartIcon, CheckIcon, HeadsetIcon, LockIcon, RefreshIcon } from '../components/Icons'
import { EmptyState, ErrorState, LineSkeleton } from '../components/States'

export function InsightsPage() {
  const { user, ready } = useAuth()
  const { push } = useToast()

  const [days, setDays] = useState(14)
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [tickets, setTickets] = useState<EscalationView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [denied, setDenied] = useState(false)
  const [nonce, setNonce] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setDenied(false)
    try {
      const [analytics, escalations] = await Promise.all([api.fetchAnalytics(days), api.fetchEscalations()])
      setSummary(analytics)
      setTickets(escalations)
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
        setDenied(true)
      } else {
        setError(cause instanceof ApiError ? cause.message : 'Could not load assistant analytics.')
      }
    } finally {
      setLoading(false)
    }
  }, [days])

  useEffect(() => {
    if (!ready || !user) return
    void load()
  }, [ready, user, load, nonce])

  const resolve = async (reference: string) => {
    try {
      await api.resolveEscalation(reference)
      push({ tone: 'success', title: `${reference} resolved` })
      void load()
    } catch (cause) {
      push({ tone: 'error', title: 'Could not resolve', body: cause instanceof ApiError ? cause.message : undefined })
    }
  }

  // -------------------------------------------------- permission gates

  if (!ready) {
    return (
      <div className="page">
        <div className="stack">
          <LineSkeleton height={110} />
          <LineSkeleton height={260} />
        </div>
      </div>
    )
  }

  if (!user) {
    return (
      <div className="page">
        <EmptyState
          title="Staff area"
          text="Assistant analytics and the escalation queue are limited to support agents and admins."
          icon={<LockIcon size={22} />}
          actions={
            <Link to="/login" className="btn btn--primary" state={{ from: '/insights' }}>
              Sign in as staff
            </Link>
          }
        />
      </div>
    )
  }

  if (denied || !user.staff) {
    return (
      <div className="page">
        <EmptyState
          title="You do not have access to this page"
          text={`Your role is ${user.role}. Assistant analytics need the SUPPORT or ADMIN role. The storefront and the assistant still work for you.`}
          icon={<LockIcon size={22} />}
          actions={
            <>
              <Link to="/shop" className="btn btn--primary">
                Back to shopping
              </Link>
              <Link to="/" className="btn btn--ghost">
                Home
              </Link>
            </>
          }
        />
      </div>
    )
  }

  return (
    <div className="page page--wide">
      <div className="section__head">
        <div>
          <h1 className="section__title">Assistant insights</h1>
          <p className="section__subtitle">
            Usage, quality and safety signals for the shopping assistant.
          </p>
        </div>
        <div className="row" style={{ gap: 8 }}>
          <select className="select" value={days} onChange={(event) => setDays(Number(event.target.value))}>
            <option value={1}>Last 24 hours</option>
            <option value={7}>Last 7 days</option>
            <option value={14}>Last 14 days</option>
            <option value={30}>Last 30 days</option>
            <option value={90}>Last 90 days</option>
          </select>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setNonce((value) => value + 1)}>
            <RefreshIcon size={14} />
            Refresh
          </button>
        </div>
      </div>

      {loading && !summary ? (
        <div className="stack">
          <div className="stat-grid">
            {Array.from({ length: 8 }, (_, index) => (
              <div className="skeleton" key={index} style={{ height: 104, borderRadius: 18 }} />
            ))}
          </div>
          <LineSkeleton height={220} />
        </div>
      ) : error ? (
        <ErrorState message={error} onRetry={() => setNonce((value) => value + 1)} />
      ) : !summary ? (
        <EmptyState title="No analytics yet" text="Once shoppers talk to the assistant, metrics appear here." />
      ) : (
        <>
          <section className="section">
            <div className="stat-grid">
              <Stat label="Conversations" value={summary.conversationsStarted} hint={`${summary.activeConversations} still active`} />
              <Stat label="Messages sent" value={summary.messagesSent} hint={`${summary.messagesAnswered} answered`} />
              <Stat label="Resolution rate" value={percent(summary.resolutionRate)} hint="Answered without a fallback" />
              <Stat label="Fallback rate" value={percent(summary.fallbackRate)} hint={`${summary.fallbacks} fallbacks`} />
              <Stat label="Escalation rate" value={percent(summary.escalationRate)} hint={`${summary.escalationsOpen} still open`} />
              <Stat label="Moderation blocks" value={summary.moderationBlocks} hint={`${percent(summary.moderationBlockRate)} of messages`} />
              <Stat
                label="Satisfaction"
                value={summary.satisfactionScore > 0 ? `${summary.satisfactionScore}%` : '—'}
                hint={`${summary.positiveFeedback} up · ${summary.negativeFeedback} down`}
              />
              <Stat
                label="Median latency"
                value={summary.averageLatencyMs ? `${Math.round(summary.averageLatencyMs / 100) / 10}s` : '—'}
                hint={`${summary.toolInvocations} tool calls`}
              />
            </div>
          </section>

          <section className="section">
            <div className="card card--pad">
              <div className="section__head" style={{ marginBottom: 8 }}>
                <div>
                  <h2 className="card__title">Daily volume</h2>
                  <p className="small muted">
                    Messages per day, with fallbacks shown in amber.
                  </p>
                </div>
              </div>
              <DailyChart summary={summary} />
            </div>
          </section>

          <section className="section" style={{ display: 'grid', gap: 20, gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' }}>
            <div className="card card--pad stack" style={{ gap: 14 }}>
              <div className="row" style={{ gap: 8 }}>
                <ChartIcon size={16} />
                <h2 className="card__title">Top intents</h2>
              </div>
              {summary.topIntents.length === 0 ? (
                <p className="small muted">No intents recorded in this window.</p>
              ) : (
                <div className="stack" style={{ gap: 10 }}>
                  {summary.topIntents.map((item) => (
                    <Meter
                      key={item.key}
                      label={item.label}
                      value={item.count}
                      max={summary.topIntents[0].count}
                    />
                  ))}
                </div>
              )}
            </div>

            <div className="card card--pad stack" style={{ gap: 14 }}>
              <div className="row" style={{ gap: 8 }}>
                <ChartIcon size={16} />
                <h2 className="card__title">Tools the model used</h2>
              </div>
              {summary.topTools.length === 0 ? (
                <p className="small muted">No tool calls in this window.</p>
              ) : (
                <div className="stack" style={{ gap: 10 }}>
                  {summary.topTools.map((item) => (
                    <Meter key={item.key} label={item.label} value={item.count} max={summary.topTools[0].count} />
                  ))}
                </div>
              )}
            </div>

            <div className="card card--pad stack" style={{ gap: 14 }}>
              <div className="row" style={{ gap: 8 }}>
                <AlertIcon size={16} />
                <h2 className="card__title">Moderation categories</h2>
              </div>
              {summary.moderationCategories.length === 0 ? (
                <p className="small muted">Nothing was blocked or redacted.</p>
              ) : (
                <div className="stack" style={{ gap: 10 }}>
                  {summary.moderationCategories.map((item) => (
                    <Meter
                      key={item.key}
                      label={item.label}
                      value={item.count}
                      max={summary.moderationCategories[0].count}
                    />
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="section">
            <div className="card card--pad">
              <div className="section__head" style={{ marginBottom: 12 }}>
                <div>
                  <h2 className="card__title row" style={{ gap: 8 }}>
                    <HeadsetIcon size={16} />
                    Escalation queue
                  </h2>
                  <p className="small muted">
                    Conversations handed to a person. Resolving one updates the escalation rate.
                  </p>
                </div>
                <span className="pill">{summary.escalationsOpen} open</span>
              </div>

              {tickets.length === 0 ? (
                <EmptyState
                  title="No escalations"
                  text="Nothing has needed a human yet in this environment."
                  icon={<HeadsetIcon size={22} />}
                />
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="table">
                    <thead>
                      <tr>
                        <th scope="col">Reference</th>
                        <th scope="col">Reason</th>
                        <th scope="col">Priority</th>
                        <th scope="col">Summary</th>
                        <th scope="col">Raised</th>
                        <th scope="col">Status</th>
                        <th scope="col" />
                      </tr>
                    </thead>
                    <tbody>
                      {tickets.map((ticket) => (
                        <tr key={ticket.reference}>
                          <td className="mono">{ticket.reference}</td>
                          <td>{ticket.reasonLabel}</td>
                          <td>
                            <span className={`pill ${ticket.priority === 'URGENT' ? 'pill--danger' : ''}`}>
                              {ticket.priority}
                            </span>
                          </td>
                          <td style={{ maxWidth: 320 }}>
                            <div className="small">{ticket.summary}</div>
                            {ticket.transcriptExcerpt ? (
                              <details className="transcript">
                                <summary>Transcript attached</summary>
                                <pre>{ticket.transcriptExcerpt}</pre>
                              </details>
                            ) : null}
                          </td>
                          <td className="small muted">{relativeTime(ticket.createdAt)}</td>
                          <td>
                            <span className={`pill ${ticket.status === 'RESOLVED' ? 'pill--success' : 'pill--warn'}`}>
                              {ticket.status}
                            </span>
                          </td>
                          <td>
                            {ticket.status !== 'RESOLVED' ? (
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                onClick={() => void resolve(ticket.reference)}
                              >
                                <CheckIcon size={13} />
                                Resolve
                              </button>
                            ) : null}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>

          <section className="section" style={{ display: 'grid', gap: 20, gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' }}>
            <div className="card card--pad stack" style={{ gap: 12 }}>
              <h2 className="card__title">Recent escalations</h2>
              {summary.recentEscalations.length === 0 ? (
                <p className="small muted">None yet.</p>
              ) : (
                summary.recentEscalations.map((entry) => (
                  <div key={`${entry.reference}-${entry.createdAt}`} className="stack" style={{ gap: 2 }}>
                    <div className="row row--between">
                      <span className="mono small">{entry.reference}</span>
                      <span className="tiny muted">{dateTime(entry.createdAt)}</span>
                    </div>
                    <div className="tiny muted clamp-3">{entry.summary}</div>
                  </div>
                ))
              )}
            </div>

            <div className="card card--pad stack" style={{ gap: 12 }}>
              <h2 className="card__title">Recent moderation events</h2>
              {summary.recentModeration.length === 0 ? (
                <p className="small muted">Nothing blocked or redacted in this window.</p>
              ) : (
                summary.recentModeration.map((entry, index) => (
                  <div key={`${entry.category}-${entry.createdAt}-${index}`} className="stack" style={{ gap: 2 }}>
                    <div className="row row--between">
                      <span className="small">
                        <span className="pill pill--warn">{entry.category}</span>{' '}
                        <span className="tiny muted">
                          {entry.direction} · {entry.action}
                        </span>
                      </span>
                      <span className="tiny muted">{relativeTime(entry.createdAt)}</span>
                    </div>
                    {entry.excerpt ? (
                      <div className="tiny muted" style={{ fontStyle: 'italic' }}>
                        “{entry.excerpt.slice(0, 120)}”
                      </div>
                    ) : null}
                  </div>
                ))
              )}
            </div>
          </section>
        </>
      )}
    </div>
  )
}

function Stat({ label, value, hint }: { label: string; value: number | string; hint?: string }) {
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value mono">{value}</div>
      {hint ? <div className="stat__hint">{hint}</div> : null}
    </div>
  )
}

function Meter({ label, value, max }: { label: string; value: number; max: number }) {
  const width = max > 0 ? Math.max(4, Math.round((value / max) * 100)) : 0
  return (
    <div className="meter-row">
      <span className="meter-row__label">{label}</span>
      <span className="meter-row__track">
        <span className="meter-row__fill" style={{ width: `${width}%`, display: 'block' }} />
      </span>
      <span className="meter-row__value mono">{value}</span>
    </div>
  )
}

function DailyChart({ summary }: { summary: AnalyticsSummary }) {
  const fallbackByDay = new Map(summary.dailyFallbacks.map((point) => [point.day, point.count]))
  const max = Math.max(1, ...summary.dailyMessages.map((point) => point.count))

  if (summary.dailyMessages.length === 0) {
    return <p className="small muted">No messages in this window.</p>
  }

  return (
    <div className="bar-chart">
      {summary.dailyMessages.map((point) => {
        const height = Math.max(3, Math.round((point.count / max) * 100))
        const fallbacks = fallbackByDay.get(point.day) ?? 0
        const fallbackShare = point.count > 0 ? Math.min(100, (fallbacks / point.count) * 100) : 0
        return (
          <div className="bar-chart__col" key={point.day} title={`${point.day}: ${point.count} messages, ${fallbacks} fallbacks`}>
            <span className="tiny mono muted">{point.count}</span>
            <div className="bar-chart__track">
              <div className="bar-chart__bar" style={{ height: `${height}%` }}>
                {fallbacks > 0 ? (
                  <span className="bar-chart__bar-fallback" style={{ height: `${fallbackShare}%` }} />
                ) : null}
              </div>
            </div>
            <span className="bar-chart__label">{point.day.slice(5)}</span>
          </div>
        )
      })}
    </div>
  )
}
