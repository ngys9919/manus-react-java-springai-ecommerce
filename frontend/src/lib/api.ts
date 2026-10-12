import type {
  AnalyticsSummary,
  AssistantOnboarding,
  AssistantStatus,
  AuthResponse,
  CartView,
  CategoryView,
  ChatResponse,
  ConversationView,
  EscalationView,
  OrderView,
  ProductDetail,
  ProductPage,
  ProblemDetail,
  UserView,
} from '../types'

const API_ROOT = '/api/v1'
const GUEST_TOKEN_KEY = 'shopwise.guestToken'
const TOKEN_KEY = 'shopwise.token'

/** Stable anonymous identity so carts and conversations survive a reload. */
export function guestToken(): string {
  let token = localStorage.getItem(GUEST_TOKEN_KEY)
  if (!token) {
    token = `guest-${crypto.randomUUID()}`
    localStorage.setItem(GUEST_TOKEN_KEY, token)
  }
  return token
}

export function authToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuthToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

/**
 * Error carrying the parsed problem document so screens can render the server's own
 * wording instead of a generic message.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code ?? 'UNKNOWN'
    this.problem = problem
  }

  get isOffline(): boolean {
    return this.status === 0
  }

  get isRateLimited(): boolean {
    return this.status === 429
  }

  get isAuthIssue(): boolean {
    return this.status === 401 || this.status === 403
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  signal?: AbortSignal
  /** Skip the Authorization header even when a token is stored. */
  anonymous?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Guest-Token': guestToken(),
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = options.anonymous ? null : authToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(`${API_ROOT}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    throw new ApiError(0, {
      title: 'Network unavailable',
      status: 0,
      detail: 'We could not reach the Shopwise service. Check your connection and try again.',
      code: 'NETWORK_ERROR',
    })
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const payload = text ? safeParse(text) : null

  if (!response.ok) {
    throw new ApiError(response.status, (payload as ProblemDetail) ?? {
      title: response.statusText,
      status: response.status,
      detail: 'The request could not be completed.',
    })
  }
  return payload as T
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

// ---------------------------------------------------------------- catalogue

export interface ProductQuery {
  q?: string
  category?: string
  minPrice?: number | null
  maxPrice?: number | null
  inStock?: boolean
  sort?: string
  page?: number
  size?: number
}

export function fetchProducts(query: ProductQuery, signal?: AbortSignal) {
  const params = new URLSearchParams()
  if (query.q) params.set('q', query.q)
  if (query.category && query.category !== 'all') params.set('category', query.category)
  if (query.minPrice != null) params.set('minPrice', String(query.minPrice))
  if (query.maxPrice != null) params.set('maxPrice', String(query.maxPrice))
  if (query.inStock) params.set('inStock', 'true')
  params.set('sort', query.sort ?? 'relevance')
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 12))
  return request<ProductPage>(`/catalog/products?${params.toString()}`, { signal })
}

export function fetchProduct(slugOrSku: string, signal?: AbortSignal) {
  return request<ProductDetail>(`/catalog/products/${encodeURIComponent(slugOrSku)}`, { signal })
}

export function fetchCategories(signal?: AbortSignal) {
  return request<CategoryView[]>('/catalog/categories', { signal })
}

// --------------------------------------------------------------------- cart

export function fetchCart(signal?: AbortSignal) {
  return request<CartView>('/cart', { signal })
}

export function addToCart(body: { productId?: number; sku?: string; quantity?: number }) {
  return request<CartView>('/cart/items', { method: 'POST', body })
}

export function updateCartItem(productId: number, quantity: number) {
  return request<CartView>(`/cart/items/${productId}`, { method: 'PATCH', body: { quantity } })
}

export function removeCartItem(productId: number) {
  return request<CartView>(`/cart/items/${productId}`, { method: 'DELETE' })
}

export function clearCart() {
  return request<CartView>('/cart', { method: 'DELETE' })
}

// ------------------------------------------------------------------- orders

export interface CheckoutPayload {
  fullName: string
  email: string
  line1: string
  line2?: string
  city: string
  postcode: string
  country: string
  note?: string
}

export function checkout(payload: CheckoutPayload) {
  return request<OrderView>('/orders', { method: 'POST', body: payload })
}

export function fetchMyOrders() {
  return request<OrderView[]>('/orders/mine')
}

export function trackOrder(reference: string, email?: string) {
  const suffix = email ? `?email=${encodeURIComponent(email)}` : ''
  return request<OrderView>(`/orders/${encodeURIComponent(reference)}/track${suffix}`)
}

// --------------------------------------------------------------------- auth

export function login(email: string, password: string) {
  return request<AuthResponse>('/auth/login', { method: 'POST', body: { email, password }, anonymous: true })
}

export function register(fullName: string, email: string, password: string) {
  return request<AuthResponse>('/auth/register', {
    method: 'POST',
    body: { fullName, email, password },
    anonymous: true,
  })
}

export function fetchMe() {
  return request<UserView>('/me')
}

// ---------------------------------------------------------------- assistant

export interface ChatRequestPayload {
  message: string
  conversationId?: string | null
  pageContext?: string | null
}

export function fetchOnboarding(signal?: AbortSignal) {
  return request<AssistantOnboarding>('/assistant/onboarding', { signal })
}

export function fetchAssistantStatus(signal?: AbortSignal) {
  return request<AssistantStatus>('/assistant/status', { signal })
}

export function sendChatMessage(payload: ChatRequestPayload, signal?: AbortSignal) {
  return request<ChatResponse>('/assistant/chat', { method: 'POST', body: payload, signal })
}

export function fetchConversation(conversationId: string, signal?: AbortSignal) {
  return request<ConversationView>(`/assistant/conversations/${conversationId}`, { signal })
}

export function fetchConversations(signal?: AbortSignal) {
  return request<ConversationView[]>('/assistant/conversations', { signal })
}

export function escalateConversation(
  conversationId: string,
  payload: { reason?: string; note?: string; email?: string; urgent?: boolean },
) {
  return request<EscalationView>(`/assistant/conversations/${conversationId}/escalate`, {
    method: 'POST',
    body: payload,
  })
}

export function sendFeedback(conversationId: string, payload: { rating: string; comment?: string; messageId?: number }) {
  return request<{ recorded: string; acknowledgement: string }>(
    `/assistant/conversations/${conversationId}/feedback`,
    { method: 'POST', body: payload },
  )
}

// ----------------------------------------------------------------- insights

export function fetchAnalytics(days = 14, signal?: AbortSignal) {
  return request<AnalyticsSummary>(`/insights/assistant/analytics?days=${days}`, { signal })
}

export function fetchEscalations(signal?: AbortSignal) {
  return request<EscalationView[]>('/insights/assistant/escalations', { signal })
}

export function resolveEscalation(reference: string) {
  return request<EscalationView>(`/insights/assistant/escalations/${encodeURIComponent(reference)}/resolve`, {
    method: 'POST',
  })
}

export type { ChatRequestPayload as ChatPayload }
