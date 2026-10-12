import { useState } from 'react'
import type { AssistantOnboarding, IntentCard } from '../../types'
import { AlertIcon, ChevronDownIcon, HeadsetIcon, InfoIcon, RefreshIcon, SparkIcon } from '../Icons'

interface Props {
  onboarding: AssistantOnboarding | null
  loading: boolean
  error: string | null
  onRetry: () => void
  onPick: (text: string) => void
}

/**
 * The first thing a shopper sees: a welcome, the intents the assistant actually
 * supports with a sample answer for each, and an honest statement of its limits.
 */
export function OnboardingView({ onboarding, loading, error, onRetry, onPick }: Props) {
  const [openIntent, setOpenIntent] = useState<string | null>(null)

  if (loading) {
    return (
      <div className="onboarding" aria-busy="true">
        <div className="skeleton" style={{ height: 116, borderRadius: 18 }} />
        <div className="skeleton" style={{ height: 44 }} />
        <div className="skeleton" style={{ height: 44 }} />
        <div className="skeleton" style={{ height: 44 }} />
      </div>
    )
  }

  if (error || !onboarding) {
    return (
      <div className="state" role="alert">
        <div className="state__icon" style={{ background: 'var(--danger-soft)', color: 'var(--danger)' }}>
          <AlertIcon size={22} />
        </div>
        <div className="state__title">The assistant did not load</div>
        <p className="state__text">{error ?? 'We could not fetch the assistant introduction.'}</p>
        <div className="state__actions">
          <button type="button" className="btn btn--primary" onClick={onRetry}>
            <RefreshIcon size={15} />
            Try again
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="onboarding">
      <div className="onboarding__hero">
        <div className="onboarding__greeting">{onboarding.greeting}</div>
        <div className="onboarding__text">{onboarding.subGreeting}</div>
        <div className="onboarding__availability">
          <span className={`dot ${onboarding.degraded ? '' : 'dot--pulse'}`} />
          {onboarding.availability}
        </div>
      </div>

      {onboarding.starterPrompts.length > 0 ? (
        <div>
          <div className="intent__section-title" style={{ marginBottom: 8 }}>
            Try one of these
          </div>
          <div className="starter-list">
            {onboarding.starterPrompts.map((prompt) => (
              <button type="button" className="starter" key={prompt} onClick={() => onPick(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        </div>
      ) : null}

      <div>
        <div className="intent__section-title" style={{ marginBottom: 8 }}>
          What I can help with ({onboarding.intents.length})
        </div>
        <div className="intent-list">
          {onboarding.intents.map((intent) => (
            <IntentRow
              key={intent.id}
              intent={intent}
              open={openIntent === intent.id}
              onToggle={() => setOpenIntent(openIntent === intent.id ? null : intent.id)}
              onPick={onPick}
            />
          ))}
        </div>
      </div>

      <div className="card card--pad stack" style={{ gap: 10 }}>
        <div className="row" style={{ gap: 8 }}>
          <InfoIcon size={16} />
          <strong style={{ fontSize: '0.86rem' }}>What I will not do</strong>
        </div>
        <ul className="limit-list">
          {onboarding.limitations.map((limit) => (
            <li key={limit}>
              <span aria-hidden>•</span>
              <span>{limit}</span>
            </li>
          ))}
        </ul>
        <hr className="divider" />
        <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
          <HeadsetIcon size={16} />
          <div className="small muted">{onboarding.handoffNote}</div>
        </div>
        <div className="tiny muted">
          Prefer email? <a href={`mailto:${onboarding.supportEmail}`}>{onboarding.supportEmail}</a>
        </div>
      </div>
    </div>
  )
}

function IntentRow({
  intent,
  open,
  onToggle,
  onPick,
}: {
  intent: IntentCard
  open: boolean
  onToggle: () => void
  onPick: (text: string) => void
}) {
  return (
    <div className="intent">
      <button type="button" className="intent__head" onClick={onToggle} aria-expanded={open}>
        <span className="intent__icon" aria-hidden>
          {intent.icon ?? '•'}
        </span>
        <span>
          <span className="intent__label" style={{ display: 'block' }}>
            {intent.label}
          </span>
          <span className="intent__desc">{intent.description}</span>
        </span>
        <ChevronDownIcon size={15} className={`intent__chevron${open ? ' intent__chevron--open' : ''}`} />
      </button>

      {open ? (
        <div className="intent__body">
          <div>
            <div className="intent__section-title" style={{ marginBottom: 6 }}>
              Ask it like this
            </div>
            <div className="stack" style={{ gap: 6 }}>
              {intent.sampleUtterances.map((utterance) => (
                <button type="button" className="intent__sample" key={utterance} onClick={() => onPick(utterance)}>
                  “{utterance}”
                </button>
              ))}
            </div>
          </div>
          <div>
            <div className="intent__section-title" style={{ marginBottom: 6 }}>
              A typical answer
            </div>
            <div className="intent__example">{intent.exampleResponse}</div>
          </div>
          {intent.requiresSignIn ? (
            <div className="notice-inline">
              <SparkIcon size={14} />
              Works best when you are signed in.
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
