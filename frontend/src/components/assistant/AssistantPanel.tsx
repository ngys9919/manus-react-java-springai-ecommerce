import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAssistant } from '../../state/AssistantContext'
import { useCart } from '../../state/CartContext'
import { useAuth } from '../../state/AuthContext'
import type { QuickReply } from '../../types'
import { AlertIcon, CloseIcon, HeadsetIcon, RefreshIcon, SparkIcon } from '../Icons'
import { OnboardingView } from './OnboardingView'
import { MessageList } from './MessageList'
import { Composer } from './Composer'

const PAGE_LABELS: Record<string, string> = {
  home: 'the home page',
  shop: 'the catalogue',
  product: 'a product page',
  cart: 'the cart',
  checkout: 'checkout',
  orders: 'order history',
}

interface Props {
  pageContext: string
  /** Optional prompt pushed in from a product page ("Ask about this product"). */
  seedPrompt?: string | null
  onSeedConsumed?: () => void
}

export function AssistantPanel({ pageContext, seedPrompt, onSeedConsumed }: Props) {
  const {
    open,
    setOpen,
    messages,
    onboarding,
    onboardingError,
    onboardingLoading,
    status,
    sending,
    failure,
    degradedNotice,
    conversationStatus,
    quickReplies,
    loadingHistory,
    send,
    retry,
    dismissFailure,
    reset,
    rate,
    escalate,
    reloadOnboarding,
  } = useAssistant()
  const { refresh: refreshCart } = useCart()
  const { user } = useAuth()
  const navigate = useNavigate()
  const bodyRef = useRef<HTMLDivElement>(null)
  const [escalateOpen, setEscalateOpen] = useState(false)
  const [escalateNote, setEscalateNote] = useState('')
  const [escalateEmail, setEscalateEmail] = useState('')
  const [escalateReason, setEscalateReason] = useState('order_issue')
  const [escalating, setEscalating] = useState(false)

  // Keep the newest turn in view.
  useEffect(() => {
    const body = bodyRef.current
    if (!body) return
    body.scrollTo({ top: body.scrollHeight, behavior: 'smooth' })
  }, [messages.length, sending, failure])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, setOpen])

  useEffect(() => {
    if (!seedPrompt) return
    void send(seedPrompt, { pageContext })
    onSeedConsumed?.()
  }, [seedPrompt, send, pageContext, onSeedConsumed])

  if (!open) return null

  const onQuickReply = (reply: QuickReply) => {
    if (reply.kind === 'NAVIGATE') {
      navigate(reply.value)
      setOpen(false)
      return
    }
    if (reply.kind === 'ACTION') {
      // Client-side action: refresh the cart and confirm to the assistant.
      void refreshCart()
      void send(`I completed the action: ${reply.label}`, { pageContext })
      return
    }
    void send(reply.value, { pageContext })
  }

  const submitEscalation = async () => {
    setEscalating(true)
    const ticket = await escalate({
      reason: escalateReason,
      note: escalateNote.trim() || undefined,
      email: escalateEmail.trim() || user?.email || undefined,
      urgent: escalateReason === 'order_issue',
    })
    setEscalating(false)
    if (ticket) {
      setEscalateOpen(false)
      setEscalateNote('')
    }
  }

  const available = status?.available !== false
  const hasConversation = messages.length > 0

  return (
    <>
      <div
        className="drawer-backdrop"
        style={{ zIndex: 70 }}
        onClick={() => setOpen(false)}
        aria-hidden
      />
      <aside className="assistant-panel" role="dialog" aria-label="Shopping assistant" aria-modal="true">
        <header className="assistant-panel__head">
          <div className="assistant-panel__avatar" aria-hidden>
            <SparkIcon size={18} />
          </div>
          <div className="grow">
            <div className="assistant-panel__title">Shopping assistant</div>
            <div className="assistant-panel__subtitle">
              <span className={`dot ${available ? 'dot--pulse' : ''}`} />
              {available ? `Online · ${status?.model ?? 'model'}` : 'Offline mode'}
              <span className="muted">·</span>
              <span className="muted">looking at {PAGE_LABELS[pageContext] ?? 'the shop'}</span>
            </div>
          </div>
          <div className="assistant-panel__actions">
            {hasConversation ? (
              <button
                type="button"
                className="icon-btn"
                onClick={reset}
                title="Start a new conversation"
                aria-label="Start a new conversation"
              >
                <RefreshIcon size={16} />
              </button>
            ) : null}
            <button type="button" className="icon-btn" onClick={() => setOpen(false)} aria-label="Close assistant">
              <CloseIcon size={17} />
            </button>
          </div>
        </header>

        <div className="assistant-panel__body" ref={bodyRef}>
          {degradedNotice ? (
            <div className="assistant-banner assistant-banner--warn">
              <AlertIcon size={16} />
              <div>
                <strong>Limited mode.</strong> {degradedNotice} Answers come from the catalogue rules, not the model.
              </div>
            </div>
          ) : null}

          {conversationStatus === 'ESCALATED' ? (
            <div className="assistant-banner assistant-banner--info">
              <HeadsetIcon size={16} />
              <div>
                A human agent has this conversation. Say <strong>/resume</strong> to hand it back to me.
              </div>
            </div>
          ) : null}

          {loadingHistory ? (
            <div className="stack">
              <div className="skeleton" style={{ height: 54, width: '72%' }} />
              <div className="skeleton" style={{ height: 72, width: '88%', alignSelf: 'flex-end' }} />
            </div>
          ) : null}

          {!hasConversation && !loadingHistory ? (
            <OnboardingView
              onboarding={onboarding}
              loading={onboardingLoading}
              error={onboardingError}
              onRetry={reloadOnboarding}
              onPick={(text) => void send(text, { pageContext })}
            />
          ) : (
            <MessageList
              messages={messages}
              sending={sending}
              quickReplies={quickReplies}
              onQuickReply={onQuickReply}
              onRate={(message, rating) => void rate(message, rating)}
            />
          )}

          {failure ? (
            <div className="assistant-banner assistant-banner--danger" style={{ flexDirection: 'column', gap: 9 }}>
              <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
                <AlertIcon size={16} />
                <div>
                  <strong>That did not go through.</strong>
                  <div>{failure.message}</div>
                </div>
              </div>
              <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                {failure.retryable && failure.retryText ? (
                  <button type="button" className="btn btn--primary btn--sm" onClick={() => void retry()} disabled={sending}>
                    <RefreshIcon size={14} />
                    Retry
                  </button>
                ) : null}
                <button type="button" className="btn btn--ghost btn--sm" onClick={dismissFailure}>
                  Dismiss
                </button>
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEscalateOpen(true)}>
                  <HeadsetIcon size={14} />
                  Get a human
                </button>
              </div>
            </div>
          ) : null}

          {escalateOpen ? (
            <div className="card card--pad stack" style={{ gap: 12 }}>
              <div className="row" style={{ gap: 8 }}>
                <HeadsetIcon size={16} />
                <strong style={{ fontSize: '0.88rem' }}>Hand this conversation to a person</strong>
              </div>
              <div className="field">
                <label className="field__label" htmlFor="escalate-reason">
                  What is this about?
                </label>
                <select
                  id="escalate-reason"
                  className="select"
                  style={{ width: '100%' }}
                  value={escalateReason}
                  onChange={(event) => setEscalateReason(event.target.value)}
                >
                  <option value="order_issue">An order problem</option>
                  <option value="payment">Payment or refund</option>
                  <option value="product_question">A product question I could not answer</option>
                  <option value="other">Something else</option>
                </select>
              </div>
              <div className="field">
                <label className="field__label" htmlFor="escalate-email">
                  Email for the reply
                </label>
                <input
                  id="escalate-email"
                  className="input"
                  type="email"
                  placeholder={user?.email ?? 'you@example.com'}
                  value={escalateEmail}
                  onChange={(event) => setEscalateEmail(event.target.value)}
                />
                <span className="field__hint">We attach this transcript so you do not repeat yourself.</span>
              </div>
              <div className="field">
                <label className="field__label" htmlFor="escalate-note">
                  Anything to add? (optional)
                </label>
                <textarea
                  id="escalate-note"
                  className="textarea"
                  rows={3}
                  value={escalateNote}
                  onChange={(event) => setEscalateNote(event.target.value)}
                />
              </div>
              <div className="row" style={{ gap: 8 }}>
                <button type="button" className="btn btn--primary btn--sm" onClick={() => void submitEscalation()} disabled={escalating}>
                  {escalating ? <span className="spinner" /> : 'Raise the ticket'}
                </button>
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEscalateOpen(false)}>
                  Cancel
                </button>
              </div>
            </div>
          ) : null}
        </div>

        <div className="assistant-panel__foot">
          <Composer
            disabled={!available && messages.length === 0 && Boolean(onboardingError)}
            sending={sending}
            placeholder={
              conversationStatus === 'ESCALATED'
                ? 'A human has this conversation — type /resume to take it back'
                : 'Ask about a product, a comparison or an order…'
            }
            hint={
              available
                ? 'I can search, compare and act on your cart.'
                : 'Offline mode: answers come from catalogue rules.'
            }
            onSend={(text) => void send(text, { pageContext })}
          />
          <div className="row row--between" style={{ marginTop: 10 }}>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => setEscalateOpen(true)}
              disabled={!hasConversation}
              title={hasConversation ? 'Hand to a human' : 'Say something first'}
            >
              <HeadsetIcon size={14} />
              Human handoff
            </button>
            <span className="tiny muted">
              {onboarding?.intents.length ?? 0} supported intents
            </span>
          </div>
        </div>
      </aside>
    </>
  )
}
