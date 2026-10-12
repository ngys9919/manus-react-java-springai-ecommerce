import { Link, useNavigate } from 'react-router-dom'
import type { CartView, ChatBlock, EscalationView, OrderView, ProductDetail, ProductSummary, QuickReply } from '../../types'
import { money, dateOnly, stockLabel } from '../../lib/format'
import { AlertIcon, CheckIcon, HeadsetIcon, InfoIcon, StarIcon, TruckIcon } from '../Icons'

interface BlockProps {
  block: ChatBlock
  onQuickReply: (reply: QuickReply) => void
}

export function BlockRenderer({ block, onQuickReply }: BlockProps) {
  switch (block.type) {
    case 'PRODUCT_CARDS':
      return <ProductCards block={block} onQuickReply={onQuickReply} />
    case 'COMPARISON':
      return <Comparison block={block} onQuickReply={onQuickReply} />
    case 'CART_SUMMARY':
      return <CartSummary block={block} onQuickReply={onQuickReply} />
    case 'ORDER_CARD':
      return <OrderCard block={block} onQuickReply={onQuickReply} />
    case 'ESCALATION':
      return <EscalationCard block={block} onQuickReply={onQuickReply} />
    case 'NOTICE':
      return <Notice block={block} onQuickReply={onQuickReply} />
    case 'QUICK_REPLIES':
      return <QuickReplies replies={block.actions ?? []} title={block.title} onQuickReply={onQuickReply} />
    default:
      return block.body ? <div className="bubble bubble--assistant">{block.body}</div> : null
  }
}

// --------------------------------------------------------------- products

function ProductCards({ block, onQuickReply }: BlockProps) {
  const products = (block.data?.products as ProductSummary[] | undefined) ?? []
  if (products.length === 0) {
    return (
      <div className="block">
        <div className="block__body">
          <div className="muted small">No products were attached to this answer.</div>
        </div>
      </div>
    )
  }
  return (
    <div className="block">
      {block.title ? (
        <div className="block__head">
          <span className="block__title">{block.title}</span>
          {block.subtitle ? <span className="block__subtitle">{block.subtitle}</span> : null}
        </div>
      ) : null}
      <div>
        {products.map((product) => (
          <Link className="mini-product" key={product.sku} to={`/product/${product.slug}`}>
            <span className="mini-product__media">
              <img src={product.imageUrl ?? '/media/products/placeholder.svg'} alt="" loading="lazy" />
            </span>
            <span>
              <span className="mini-product__name" style={{ display: 'block' }}>
                {product.name}
              </span>
              <span className="mini-product__meta">
                {product.brand} · <StarIcon size={11} className="rating__star" /> {product.rating.toFixed(1)} ·{' '}
                {stockLabel(product.stockQuantity, product.inStock, product.lowStock)}
              </span>
            </span>
            <span className="mini-product__price">{money(product.price, product.currency)}</span>
          </Link>
        ))}
      </div>
      {block.actions?.length ? (
        <div className="block__body" style={{ borderTop: '1px solid var(--line)' }}>
          <QuickReplies replies={block.actions} onQuickReply={onQuickReply} />
        </div>
      ) : null}
    </div>
  )
}

// -------------------------------------------------------------- comparison

interface ComparisonRow {
  attribute: string
  differs?: boolean
  [sku: string]: unknown
}

interface PriceRow {
  sku: string
  name: string
  price: number
  rating?: number
  inStock?: boolean
  cheapest?: boolean
  bestRated?: boolean
}

function Comparison({ block, onQuickReply }: BlockProps) {
  const products = (block.data?.products as ProductDetail[] | undefined) ?? []
  const rows = (block.data?.rows as ComparisonRow[] | undefined) ?? []
  const pricing = (block.data?.pricing as PriceRow[] | undefined) ?? []
  const differences = (block.data?.differentiatingAttributes as string[] | undefined) ?? []
  const navigate = useNavigate()

  if (products.length < 2) {
    return (
      <div className="block">
        <div className="block__head">
          <span className="block__title">{block.title ?? 'Comparison'}</span>
        </div>
        <div className="block__body muted small">A comparison needs two products.</div>
      </div>
    )
  }

  const cheapest = pricing.find((entry) => entry.cheapest)

  return (
    <div className="block">
      <div className="block__head">
        <span className="block__title">{block.title ?? 'Side by side'}</span>
        <span className="block__subtitle">
          {differences.length > 0 ? `${differences.length} specs differ` : 'Very close on specs'}
        </span>
      </div>

      <div className="compare-scroll">
        <table className="compare-table">
          <thead>
            <tr>
              <th scope="col">Spec</th>
              {products.map((product) => (
                <th scope="col" key={product.sku}>
                  {product.name}
                  <div className="muted tiny" style={{ fontWeight: 400 }}>
                    {money(product.price, product.currency)}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.attribute} className={row.differs ? 'differs' : undefined}>
                <td>
                  {row.attribute}
                  {row.differs ? <span className="compare-table__badge">differs</span> : null}
                </td>
                {products.map((product) => (
                  <td key={product.sku}>{String(row[product.sku] ?? '—')}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="block__body" style={{ borderTop: '1px solid var(--line)', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {pricing.length > 0 ? (
          <div className="stack" style={{ gap: 4 }}>
            {pricing.map((entry) => (
              <div className="row row--between small" key={entry.sku}>
                <span>
                  {entry.name}
                  {entry.cheapest ? <span className="pill pill--success" style={{ marginLeft: 8 }}>Cheapest</span> : null}
                  {entry.bestRated ? <span className="pill pill--brand" style={{ marginLeft: 6 }}>Best rated</span> : null}
                  {entry.inStock === false ? <span className="pill" style={{ marginLeft: 6 }}>Out of stock</span> : null}
                </span>
                <span className="mono">{money(entry.price)}</span>
              </div>
            ))}
          </div>
        ) : null}

        {cheapest ? (
          <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => navigate(`/product/${products.find((p) => p.sku === cheapest.sku)?.slug ?? ''}`)}
            >
              Open the cheapest
            </button>
          </div>
        ) : null}

        {block.actions?.length ? <QuickReplies replies={block.actions} onQuickReply={onQuickReply} /> : null}
      </div>
    </div>
  )
}

// ------------------------------------------------------------- cart block

function CartSummary({ block, onQuickReply }: BlockProps) {
  const cart = block.data?.cart as CartView | undefined
  if (!cart) return null

  return (
    <div className="block">
      <div className="block__head">
        <span className="block__title">{block.title ?? 'Your cart'}</span>
        {block.subtitle ? <span className="block__subtitle">{block.subtitle}</span> : null}
      </div>
      <div className="block__body">
        {cart.empty ? (
          <div className="muted small">Nothing in the cart yet.</div>
        ) : (
          <>
            {cart.items.map((line) => (
              <div className="cart-summary__line" key={line.productId}>
                <span>
                  {line.quantity} × {line.name}
                </span>
                <span className="mono">{money(line.lineTotal)}</span>
              </div>
            ))}
            <div className="cart-summary__line cart-summary__total">
              <span>Total</span>
              <span className="mono">{money(cart.grandTotal)}</span>
            </div>
            {cart.freeShippingRemaining > 0 ? (
              <div className="tiny muted" style={{ marginTop: 6 }}>
                <TruckIcon size={13} /> Add {money(cart.freeShippingRemaining)} for free shipping
              </div>
            ) : null}
          </>
        )}
        {block.actions?.length ? (
          <div style={{ marginTop: 12 }}>
            <QuickReplies replies={block.actions} onQuickReply={onQuickReply} />
          </div>
        ) : null}
      </div>
    </div>
  )
}

// ------------------------------------------------------------ order block

const ORDER_STEPS = ['PLACED', 'PACKED', 'SHIPPED', 'DELIVERED']

function OrderCard({ block, onQuickReply }: BlockProps) {
  const single = block.data?.order as OrderView | undefined
  const many = block.data?.orders as OrderView[] | undefined
  const orders = single ? [single] : many ?? []

  if (orders.length === 0) return null

  return (
    <div className="block">
      <div className="block__head">
        <span className="block__title">{block.title ?? 'Order'}</span>
        {block.subtitle ? <span className="block__subtitle">{block.subtitle}</span> : null}
      </div>
      <div className="block__body">
        {orders.map((order) => {
          const stepIndex = Math.max(0, ORDER_STEPS.indexOf(order.status))
          const cancelled = order.status === 'CANCELLED'
          return (
            <div key={order.reference} style={{ paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid var(--line)' }}>
              <div className="row row--between">
                <strong className="mono small">{order.reference}</strong>
                <span className={`pill ${cancelled ? 'pill--danger' : 'pill--info'}`}>{order.statusLabel}</span>
              </div>

              {!cancelled ? (
                <div className="timeline">
                  {ORDER_STEPS.map((step, index) => (
                    <div className="timeline__step" key={step}>
                      <div className={`timeline__bar${index <= stepIndex ? ' timeline__bar--done' : ''}`} />
                      {step.charAt(0) + step.slice(1).toLowerCase()}
                    </div>
                  ))}
                </div>
              ) : null}

              <div className="order-card__row">
                <span className="muted">Placed</span>
                <span>{dateOnly(order.placedAt)}</span>
              </div>
              <div className="order-card__row">
                <span className="muted">Estimated delivery</span>
                <span>{order.estimatedDelivery}</span>
              </div>

              <div style={{ marginTop: 8 }}>
                {order.items.map((line) => (
                  <div className="order-line" key={`${order.reference}-${line.sku}`}>
                    <span>
                      {line.quantity} × {line.productName}
                    </span>
                    <span className="mono">{money(line.lineTotal)}</span>
                  </div>
                ))}
              </div>

              <div className="cart-summary__line cart-summary__total">
                <span>Total</span>
                <span className="mono">{money(order.grandTotal, order.currency)}</span>
              </div>

              <div className="tiny muted" style={{ marginTop: 6 }}>
                {order.trackingNote}
              </div>
            </div>
          )
        })}

        {block.actions?.length ? <QuickReplies replies={block.actions} onQuickReply={onQuickReply} /> : null}
      </div>
    </div>
  )
}

// -------------------------------------------------------------- escalation

function EscalationCard({ block, onQuickReply }: BlockProps) {
  const ticket = block.data?.ticket as EscalationView | undefined
  return (
    <div className="block" style={{ borderColor: 'var(--brand)' }}>
      <div className="block__head" style={{ background: 'var(--brand-soft)' }}>
        <span className="block__title row" style={{ gap: 7 }}>
          <HeadsetIcon size={16} />
          {block.title ?? 'Handed to a human'}
        </span>
        <span className="block__subtitle mono">{ticket?.reference ?? block.subtitle}</span>
      </div>
      <div className="block__body stack" style={{ gap: 8 }}>
        <div className="small">{ticket?.expectedResponse ?? block.body}</div>
        {ticket ? (
          <>
            <div className="row row--between small">
              <span className="muted">Priority</span>
              <span className={`pill ${ticket.priority === 'URGENT' ? 'pill--danger' : ''}`}>{ticket.priority}</span>
            </div>
            <div className="row row--between small">
              <span className="muted">Reason</span>
              <span>{ticket.reasonLabel}</span>
            </div>
            <div className="row row--between small">
              <span className="muted">Next step</span>
              <span>{ticket.nextStep}</span>
            </div>
            {ticket.contactEmail ? (
              <div className="row row--between small">
                <span className="muted">We will email</span>
                <span>{ticket.contactEmail}</span>
              </div>
            ) : null}
          </>
        ) : null}
        {block.actions?.length ? <QuickReplies replies={block.actions} onQuickReply={onQuickReply} /> : null}
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ notice

function Notice({ block, onQuickReply }: BlockProps) {
  const tone = block.subtitle?.toUpperCase() ?? 'INFO'
  const toneClass =
    tone === 'WARNING' || tone === 'WARN' ? 'assistant-banner--warn' : tone === 'DANGER' || tone === 'ERROR' ? 'assistant-banner--danger' : 'assistant-banner--info'
  const Icon = tone === 'WARNING' || tone === 'WARN' || tone === 'DANGER' ? AlertIcon : tone === 'SUCCESS' ? CheckIcon : InfoIcon

  return (
    <div className={`assistant-banner ${toneClass}`} style={{ flexDirection: 'column', gap: 8 }}>
      <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
        <Icon size={16} />
        <div>
          {block.title ? <div style={{ fontWeight: 620 }}>{block.title}</div> : null}
          {block.body ? <div>{block.body}</div> : null}
        </div>
      </div>
      {block.actions?.length ? <QuickReplies replies={block.actions} onQuickReply={onQuickReply} /> : null}
    </div>
  )
}

// ------------------------------------------------------------ quick replies

export function QuickReplies({
  replies,
  title,
  onQuickReply,
}: {
  replies: QuickReply[]
  title?: string | null
  onQuickReply: (reply: QuickReply) => void
}) {
  if (replies.length === 0) return null
  return (
    <div className="stack" style={{ gap: 8 }}>
      {title ? <div className="tiny muted">{title}</div> : null}
      <div className="quick-replies">
        {replies.map((reply) => (
          <button
            type="button"
            key={`${reply.kind}-${reply.label}`}
            className={`quick-reply${reply.kind === 'NAVIGATE' ? ' quick-reply--navigate' : ''}`}
            onClick={() => onQuickReply(reply)}
          >
            {reply.label}
          </button>
        ))}
      </div>
    </div>
  )
}
