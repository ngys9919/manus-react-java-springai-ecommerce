import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type {
  AssistantOnboarding,
  AssistantStatus,
  ChatResponse,
  EscalationView,
  MessageView,
  QuickReply,
} from '../types'

const CONVERSATION_KEY = 'shopwise.conversationId'

export interface UiMessage extends MessageView {
  /** Client-side status used to render pending, failed and retry affordances. */
  state?: 'sent' | 'failed'
  /** The user text that produced a failed turn, kept so retry is a single tap. */
  retryText?: string
  feedback?: 'up' | 'down'
}

interface AssistantContextValue {
  open: boolean
  setOpen: (open: boolean) => void
  toggle: () => void
  messages: UiMessage[]
  onboarding: AssistantOnboarding | null
  onboardingError: string | null
  onboardingLoading: boolean
  status: AssistantStatus | null
  sending: boolean
  /** Non-null when the last turn failed outright, with the text needed to retry. */
  failure: { message: string; retryText: string; retryable: boolean } | null
  degradedNotice: string | null
  conversationId: string | null
  conversationStatus: string
  quickReplies: QuickReply[]
  escalatedTicket: EscalationView | null
  loadingHistory: boolean
  send: (text: string, options?: { pageContext?: string | null; silent?: boolean }) => Promise<void>
  retry: () => Promise<void>
  dismissFailure: () => void
  reset: () => void
  rate: (message: UiMessage, rating: 'up' | 'down') => Promise<void>
  escalate: (input: { reason?: string; note?: string; email?: string; urgent?: boolean }) => Promise<EscalationView | null>
  reloadOnboarding: () => void
}

const AssistantContext = createContext<AssistantContextValue | null>(null)

export function AssistantProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<UiMessage[]>([])
  const [onboarding, setOnboarding] = useState<AssistantOnboarding | null>(null)
  const [onboardingError, setOnboardingError] = useState<string | null>(null)
  const [onboardingLoading, setOnboardingLoading] = useState(true)
  const [status, setStatus] = useState<AssistantStatus | null>(null)
  const [sending, setSending] = useState(false)
  const [failure, setFailure] = useState<AssistantContextValue['failure']>(null)
  const [degradedNotice, setDegradedNotice] = useState<string | null>(null)
  const [conversationId, setConversationId] = useState<string | null>(() => localStorage.getItem(CONVERSATION_KEY))
  const [conversationStatus, setConversationStatus] = useState('ACTIVE')
  const [quickReplies, setQuickReplies] = useState<QuickReply[]>([])
  const [escalatedTicket, setEscalatedTicket] = useState<EscalationView | null>(null)
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [onboardingNonce, setOnboardingNonce] = useState(0)

  const conversationRef = useRef(conversationId)
  conversationRef.current = conversationId

  // Onboarding doubles as the empty state, so it is fetched eagerly.
  useEffect(() => {
    let cancelled = false
    setOnboardingLoading(true)
    setOnboardingError(null)
    Promise.all([api.fetchOnboarding(), api.fetchAssistantStatus().catch(() => null)])
      .then(([onboardingPayload, statusPayload]) => {
        if (cancelled) return
        setOnboarding(onboardingPayload)
        setStatus(statusPayload)
      })
      .catch((cause) => {
        if (cancelled) return
        setOnboardingError(
          cause instanceof ApiError ? cause.message : 'The assistant is unavailable right now.',
        )
      })
      .finally(() => {
        if (!cancelled) setOnboardingLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [onboardingNonce])

  // Restore a previous conversation so the panel is not empty after a reload.
  useEffect(() => {
    const existing = localStorage.getItem(CONVERSATION_KEY)
    if (!existing) return
    let cancelled = false
    setLoadingHistory(true)
    api
      .fetchConversation(existing)
      .then((conversation) => {
        if (cancelled) return
        setMessages(conversation.messages.map((message) => ({ ...message, state: 'sent' as const })))
        setConversationStatus(conversation.status)
        setConversationId(conversation.id)
      })
      .catch(() => {
        localStorage.removeItem(CONVERSATION_KEY)
        setConversationId(null)
      })
      .finally(() => {
        if (!cancelled) setLoadingHistory(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const rememberConversation = useCallback((id: string) => {
    setConversationId(id)
    localStorage.setItem(CONVERSATION_KEY, id)
  }, [])

  const send = useCallback<AssistantContextValue['send']>(
    async (text, options) => {
      const trimmed = text.trim()
      if (!trimmed || sending) return

      const userMessage: UiMessage = {
        role: 'USER',
        content: trimmed,
        blocks: [],
        toolCalls: [],
        createdAt: new Date().toISOString(),
        state: 'sent',
      }
      setMessages((current) => [...current, userMessage])
      setSending(true)
      setFailure(null)
      setQuickReplies([])

      try {
        const response = await api.sendChatMessage({
          message: trimmed,
          conversationId: conversationRef.current,
          pageContext: options?.pageContext ?? null,
        })
        applyResponse(response)
      } catch (cause) {
        const apiError = cause instanceof ApiError ? cause : null
        const message =
          apiError?.message ?? 'Something went wrong while contacting the assistant. Please try again.'
        setMessages((current) => {
          const next = [...current]
          for (let index = next.length - 1; index >= 0; index -= 1) {
            if (next[index].role === 'USER') {
              next[index] = { ...next[index], state: 'failed', retryText: trimmed }
              break
            }
          }
          return next
        })
        setFailure({
          message,
          retryText: trimmed,
          retryable: apiError ? !apiError.isAuthIssue || true : true,
        })
      } finally {
        setSending(false)
      }

      function applyResponse(response: ChatResponse) {
        rememberConversation(response.conversationId)
        setConversationStatus(response.status)
        setDegradedNotice(response.degraded ? response.degradedReason ?? 'Running in offline mode.' : null)
        setMessages((current) => [...current, { ...response.reply, state: 'sent' }])
        setQuickReplies(response.quickReplies ?? [])
        if (response.escalated) {
          setConversationStatus('ESCALATED')
        }
      }
    },
    [rememberConversation, sending],
  )

  const retry = useCallback(async () => {
    if (!failure) return
    const text = failure.retryText
    setFailure(null)
    setMessages((current) => {
      const next = [...current]
      for (let index = next.length - 1; index >= 0; index -= 1) {
        if (next[index].role === 'USER' && next[index].state === 'failed') {
          next.splice(index, 1)
          break
        }
      }
      return next
    })
    await send(text)
  }, [failure, send])

  const reset = useCallback(() => {
    localStorage.removeItem(CONVERSATION_KEY)
    setConversationId(null)
    setConversationStatus('ACTIVE')
    setMessages([])
    setQuickReplies([])
    setFailure(null)
    setDegradedNotice(null)
    setEscalatedTicket(null)
  }, [])

  const rate = useCallback(
    async (message: UiMessage, rating: 'up' | 'down') => {
      const id = conversationRef.current
      if (!id) return
      setMessages((current) =>
        current.map((entry) => (entry === message ? { ...entry, feedback: rating } : entry)),
      )
      try {
        await api.sendFeedback(id, { rating, messageId: message.id })
      } catch {
        // Feedback is best effort; the thumb stays optimistic.
      }
    },
    [],
  )

  const escalate = useCallback<AssistantContextValue['escalate']>(
    async (input) => {
      const id = conversationRef.current
      if (!id) {
        setFailure({
          message: 'Start a conversation first — say hello and I will raise the ticket.',
          retryText: '',
          retryable: false,
        })
        return null
      }
      try {
        const ticket = await api.escalateConversation(id, input)
        setEscalatedTicket(ticket)
        setConversationStatus('ESCALATED')
        setMessages((current) => [
          ...current,
          {
            role: 'SYSTEM',
            content: `Conversation handed to a human agent as ${ticket.reference}. ${ticket.expectedResponse}`,
            blocks: [
              {
                type: 'ESCALATION',
                title: 'Handed to a human',
                subtitle: ticket.reference,
                body: ticket.expectedResponse,
                data: { ticket },
              },
            ],
            toolCalls: [],
            createdAt: new Date().toISOString(),
            state: 'sent',
          },
        ])
        return ticket
      } catch (cause) {
        setFailure({
          message: cause instanceof ApiError ? cause.message : 'Could not raise the ticket.',
          retryText: '',
          retryable: false,
        })
        return null
      }
    },
    [],
  )

  const value = useMemo<AssistantContextValue>(
    () => ({
      open,
      setOpen,
      toggle: () => setOpen((current) => !current),
      messages,
      onboarding,
      onboardingError,
      onboardingLoading,
      status,
      sending,
      failure,
      degradedNotice,
      conversationId,
      conversationStatus,
      quickReplies,
      escalatedTicket,
      loadingHistory,
      send,
      retry,
      dismissFailure: () => setFailure(null),
      reset,
      rate,
      escalate,
      reloadOnboarding: () => setOnboardingNonce((current) => current + 1),
    }),
    [
      open,
      messages,
      onboarding,
      onboardingError,
      onboardingLoading,
      status,
      sending,
      failure,
      degradedNotice,
      conversationId,
      conversationStatus,
      quickReplies,
      escalatedTicket,
      loadingHistory,
      send,
      retry,
      reset,
      rate,
      escalate,
    ],
  )

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>
}

export function useAssistant(): AssistantContextValue {
  const context = useContext(AssistantContext)
  if (!context) {
    throw new Error('useAssistant must be used inside AssistantProvider')
  }
  return context
}
