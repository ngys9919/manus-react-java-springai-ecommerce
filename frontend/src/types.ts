/** Types mirroring the Spring Boot REST contract. */

export interface ProductSummary {
  id: number
  sku: string
  slug: string
  name: string
  brand: string
  categorySlug: string
  categoryName: string
  price: number
  compareAtPrice?: number | null
  discountPercent?: number | null
  currency: string
  stockQuantity: number
  inStock: boolean
  lowStock: boolean
  rating: number
  reviewCount: number
  imageUrl?: string | null
  accentColor?: string | null
  shortDescription?: string | null
  tags: string[]
}

export interface ProductDetail extends ProductSummary {
  description?: string | null
  highlights: string[]
  attributes: Record<string, string>
}

export interface CategoryView {
  id: number
  slug: string
  name: string
  description?: string | null
  icon?: string | null
  productCount: number
  priceFrom?: string | null
}

export interface ProductPage {
  items: ProductSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
  sort: string
  appliedQuery: string
  facets: {
    categories: { slug: string; name: string; icon?: string | null; count: number }[]
    minPrice?: number | null
    maxPrice?: number | null
  }
}

export interface CartLine {
  productId: number
  sku: string
  slug: string
  name: string
  brand: string
  imageUrl?: string | null
  accentColor?: string | null
  unitPrice: number
  quantity: number
  lineTotal: number
  stockQuantity: number
  inStock: boolean
  quantityAdjusted: boolean
}

export interface CartView {
  id: string
  guestToken?: string | null
  items: CartLine[]
  itemCount: number
  distinctItemCount: number
  subtotal: number
  shippingFee: number
  taxTotal: number
  grandTotal: number
  freeShippingThreshold: number
  freeShippingRemaining: number
  currency: string
  empty: boolean
  warnings: string[]
}

export interface OrderLine {
  productId?: number | null
  sku: string
  productName: string
  imageUrl?: string | null
  slug?: string | null
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface OrderView {
  reference: string
  status: string
  statusLabel: string
  placedAt: string
  subtotal: number
  shippingFee: number
  taxTotal: number
  grandTotal: number
  currency: string
  paymentMethod: string
  shippingAddress: {
    fullName: string
    line1: string
    line2?: string | null
    city: string
    postcode: string
    country: string
  }
  items: OrderLine[]
  itemCount: number
  estimatedDelivery: string
  trackingNote: string
}

export interface UserView {
  id: number
  email: string
  fullName: string
  role: 'CUSTOMER' | 'SUPPORT' | 'ADMIN'
  staff: boolean
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  user: UserView
}

export type QuickReplyKind = 'SEND' | 'NAVIGATE' | 'ACTION'

export interface QuickReply {
  label: string
  value: string
  kind: QuickReplyKind
}

export type ChatBlockType =
  | 'TEXT'
  | 'PRODUCT_CARDS'
  | 'COMPARISON'
  | 'CART_SUMMARY'
  | 'ORDER_CARD'
  | 'QUICK_REPLIES'
  | 'NOTICE'
  | 'ESCALATION'
  | 'RECOVERY'

export interface ChatBlock {
  type: ChatBlockType
  title?: string | null
  subtitle?: string | null
  body?: string | null
  data?: Record<string, unknown> | null
  actions?: QuickReply[] | null
}

export interface ToolCallView {
  name: string
  label?: string | null
  status?: string | null
  arguments?: Record<string, unknown> | null
}

export interface MessageView {
  id?: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  intent?: string | null
  confidence?: number | null
  createdAt?: string
  blocks: ChatBlock[]
  toolCalls: ToolCallView[]
  safety?: Record<string, unknown> | null
  model?: string | null
  latencyMs?: number | null
}

export interface ChatResponse {
  conversationId: string
  title: string
  status: string
  reply: MessageView
  quickReplies: QuickReply[]
  intent?: string | null
  confidence?: number | null
  degraded: boolean
  degradedReason?: string | null
  model?: string | null
  latencyMs: number
  remainingMessagesThisMinute: number
  escalated: boolean
}

export interface ConversationView {
  id: string
  title: string
  status: string
  lastIntent?: string | null
  messageCount: number
  createdAt: string
  lastMessageAt: string
  messages: MessageView[]
}

export interface IntentCard {
  id: string
  label: string
  description: string
  icon?: string | null
  sampleUtterances: string[]
  exampleResponse: string
  requiresSignIn: boolean
}

export interface AssistantOnboarding {
  greeting: string
  subGreeting: string
  availability: string
  degraded: boolean
  degradedReason?: string | null
  intents: IntentCard[]
  starterPrompts: string[]
  capabilities: string[]
  limitations: string[]
  handoffNote: string
  supportEmail: string
}

export interface AssistantStatus {
  mode: 'MODEL' | 'FALLBACK'
  model: string
  available: boolean
  reason: string
}

export interface EscalationView {
  reference: string
  conversationId?: string | null
  reason: string
  reasonLabel: string
  priority: string
  status: string
  summary: string
  transcriptExcerpt?: string | null
  contactEmail?: string | null
  createdAt: string
  expectedResponse: string
  nextStep: string
}

export interface AnalyticsSummary {
  windowStart: string
  windowEnd: string
  windowDays: number
  conversationsStarted: number
  activeConversations: number
  messagesSent: number
  messagesAnswered: number
  toolInvocations: number
  fallbacks: number
  moderationBlocks: number
  escalations: number
  escalationsOpen: number
  errors: number
  positiveFeedback: number
  negativeFeedback: number
  resolutionRate: number
  fallbackRate: number
  escalationRate: number
  moderationBlockRate: number
  errorRate: number
  averageLatencyMs?: number | null
  satisfactionScore: number
  topIntents: CountItem[]
  topTools: CountItem[]
  moderationCategories: CountItem[]
  dailyMessages: DailyPoint[]
  dailyFallbacks: DailyPoint[]
  recentEscalations: EscalationEntry[]
  recentModeration: ModerationEntry[]
}

export interface CountItem {
  key: string
  label: string
  count: number
}

export interface DailyPoint {
  day: string
  count: number
}

export interface EscalationEntry {
  reference: string
  reason: string
  priority: string
  status: string
  summary: string
  createdAt: string
}

export interface ModerationEntry {
  direction: string
  category: string
  severity: string
  action: string
  excerpt?: string | null
  createdAt: string
}

/** RFC 9457 problem document returned by the API on failure. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  path?: string
  errors?: Record<string, string>
}
