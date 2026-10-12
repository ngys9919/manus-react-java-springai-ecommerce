import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type { OrderView } from '../types'
import { dateOnly, money } from '../lib/format'
import { useAuth } from '../state/AuthContext'
import { useAssistant } from '../state/AssistantContext'
import { AlertIcon, BoxIcon, CheckIcon, HeadsetIcon, SparkIcon, TruckIcon } from '../components/Icons'
import { EmptyState, ErrorState, LineSkeleton } from '../components/States'

const STEPS = ['PLACED', 'PACKED', 'SHIPPED', 'DELIVERED']

export function OrdersPage() {
  const { reference } = useParams()
  const location = useLocation()
  const { user, ready } = useAuth()
  const { send, setOpen } = useAssistant()

  const [history, setHistory] = useState<OrderView[] | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [lookupRef, setLookupRef] = useState(reference ?? '')
  const [lookupEmail, setLookupEmail] = useState('')
  const [lookupResult, setLookupResult] = useState<OrderView | null>(
    (location.state as { order?: OrderView } | null)?.order ?? null,
  )
  const [lookupError, setLookupError] = useState<string | null>(null)
  const [looking, setLooking] = useState(false)

  const justPlaced = Boolean((location.state as { justPlaced?: boolean } | null)?.justPlaced)

  useEffect(() => {
    if (!ready || !user) {
      setHistory(user ? [] : null)
      return
    }
    let cancelled = false
    api
      .fetchMyOrders()
      .then((orders) => {
        if (!cancelled) setHistory(orders)
      })
      .catch((cause) => {
        if (!cancelled) setHistoryError(cause instanceof ApiError ? cause.message : 'Could not load your orders.')
      })
    return () => {
      cancelled = true
    }
  }, [ready, user])

  const onLookup = async (event: FormEvent) => {
    event.preventDefault()
    setLookupError(null)
    setLooking(true)
    try {
      const order = await api.trackOrder(lookupRef.trim(), lookupEmail.trim() || undefined)
      setLookupResult(order)
    } catch (cause) {
      setLookupResult(null)
      setLookupError(cause instanceof ApiError ? cause.message : 'Could not find that order.')
    } finally {
      setLooking(false)
    }
  }

  return (
    <div className="page page--wide">
      {justPlaced ? (
        <div className="assistant-banner assistant-banner--info" style={{ marginBottom: 18 }}>
          <CheckIcon size={16} />
          <div>
            <strong>Order placed.</strong> We emailed the confirmation. Keep the reference handy — the
            assistant can look it up any time.
          </div>
        </div>
      ) : null}

      <div className="section__head">
        <div>
          <h1 className="section__title">{reference ? 'Order detail' : 'Orders'}</h1>
          <p className="section__subtitle">
            Track by reference, or see everything on your account.
          </p>
        </div>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => {
            void send('Where is my order?')
            setOpen(true)
          }}
        >
          <SparkIcon size={14} />
          Ask about an order
        </button>
      </div>

      <div className="cart-layout">
        <div className="stack" style={{ gap: 20 }}>
          {lookupResult ? (
            <OrderDetail order={lookupResult} />
          ) : (
            <div className="card card--pad stack" style={{ gap: 16 }}>
              <div className="row" style={{ gap: 8 }}>
                <BoxIcon size={18} />
                <strong style={{ fontSize: '0.96rem' }}>Track an order</strong>
              </div>
              <form onSubmit={onLookup} className="stack" style={{ gap: 12 }}>
                <div className="field">
                  <label className="field__label" htmlFor="reference">
                    Order reference
                  </label>
                  <input
                    id="reference"
                    className="input"
                    placeholder="SW-241002-0001"
                    value={lookupRef}
                    onChange={(event) => setLookupRef(event.target.value)}
                    required
                  />
                </div>
                <div className="field">
                  <label className="field__label" htmlFor="trackEmail">
                    Email used at checkout
                  </label>
                  <input
                    id="trackEmail"
                    className="input"
                    type="email"
                    placeholder={user?.email ?? 'you@example.com'}
                    value={lookupEmail}
                    onChange={(event) => setLookupEmail(event.target.value)}
                  />
                  <span className="field__hint">
                    Only needed for guest orders. Signed-in orders open without it.
                  </span>
                </div>
                {lookupError ? (
                  <div className="assistant-banner assistant-banner--danger">
                    <AlertIcon size={15} />
                    <div>{lookupError}</div>
                  </div>
                ) : null}
                <button type="submit" className="btn btn--primary" disabled={looking}>
                  {looking ? <span className="spinner" /> : 'Track order'}
                </button>
              </form>
            </div>
          )}

          <div>
            <h2 className="section__title" style={{ fontSize: '1.1rem', marginBottom: 12 }}>
              Your order history
            </h2>

            {!ready || (user && history === null) ? (
              <div className="stack">
                <LineSkeleton height={92} />
                <LineSkeleton height={92} />
              </div>
            ) : historyError ? (
              <ErrorState message={historyError} />
            ) : !user ? (
              <EmptyState
                title="Sign in to see your history"
                text="Guest orders can still be tracked with the reference and the email used at checkout."
                icon={<BoxIcon size={22} />}
                actions={
                  <Link to="/login" className="btn btn--primary">
                    Sign in
                  </Link>
                }
              />
            ) : history && history.length === 0 ? (
              <EmptyState
                title="No orders yet"
                text="When you place an order it will appear here, ready to track."
                icon={<BoxIcon size={22} />}
                actions={
                  <Link to="/shop" className="btn btn--primary">
                    Start shopping
                  </Link>
                }
              />
            ) : (
              <div className="stack" style={{ gap: 14 }}>
                {history?.map((order) => (
                  <button
                    type="button"
                    key={order.reference}
                    className="card card--pad"
                    style={{ textAlign: 'left', cursor: 'pointer', display: 'block', width: '100%' }}
                    onClick={() => setLookupResult(order)}
                  >
                    <div className="row row--between">
                      <strong className="mono">{order.reference}</strong>
                      <span className="pill pill--info">{order.statusLabel}</span>
                    </div>
                    <div className="row row--between small muted" style={{ marginTop: 6 }}>
                      <span>
                        {order.itemCount} item{order.itemCount === 1 ? '' : 's'} · {dateOnly(order.placedAt)}
                      </span>
                      <span className="mono" style={{ color: 'var(--ink)', fontWeight: 620 }}>
                        {money(order.grandTotal, order.currency)}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        <aside className="card summary">
          <div className="card__title">Need a hand?</div>
          <p className="card__body" style={{ marginTop: 6 }}>
            The assistant can read the order status, explain a delay, or start a return. If it cannot
            resolve something, it will hand you to a person with the transcript attached.
          </p>
          <button
            type="button"
            className="btn btn--subtle btn--block btn--sm"
            style={{ marginTop: 10 }}
            onClick={() => {
              void send(lookupResult ? `What is the status of order ${lookupResult.reference}?` : 'Where is my order?')
              setOpen(true)
            }}
          >
            <SparkIcon size={14} />
            Ask the assistant
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--block btn--sm"
            onClick={() => {
              void send('I want to talk to a human about my order')
              setOpen(true)
            }}
          >
            <HeadsetIcon size={14} />
            Talk to a human
          </button>
        </aside>
      </div>
    </div>
  )
}

function OrderDetail({ order }: { order: OrderView }) {
  const stepIndex = Math.max(0, STEPS.indexOf(order.status))
  const cancelled = order.status === 'CANCELLED'

  return (
    <div className="card card--pad stack" style={{ gap: 16 }}>
      <div className="row row--between" style={{ flexWrap: 'wrap', gap: 10 }}>
        <div>
          <div className="tiny muted">Order</div>
          <div className="mono" style={{ fontWeight: 700, fontSize: '1.1rem' }}>
            {order.reference}
          </div>
        </div>
        <span className={`pill ${cancelled ? 'pill--danger' : 'pill--info'}`}>{order.statusLabel}</span>
      </div>

      {!cancelled ? (
        <div className="timeline">
          {STEPS.map((step, index) => (
            <div className="timeline__step" key={step}>
              <div className={`timeline__bar${index <= stepIndex ? ' timeline__bar--done' : ''}`} />
              {step.charAt(0) + step.slice(1).toLowerCase()}
            </div>
          ))}
        </div>
      ) : null}

      <div className="row" style={{ gap: 8 }}>
        <TruckIcon size={15} />
        <span className="small">{order.trackingNote}</span>
      </div>

      <div className="stack" style={{ gap: 4 }}>
        <div className="order-card__row">
          <span className="muted">Placed</span>
          <span>{dateOnly(order.placedAt)}</span>
        </div>
        <div className="order-card__row">
          <span className="muted">Estimated delivery</span>
          <span>{order.estimatedDelivery}</span>
        </div>
        <div className="order-card__row">
          <span className="muted">Payment</span>
          <span>{order.paymentMethod}</span>
        </div>
        <div className="order-card__row">
          <span className="muted">Ship to</span>
          <span>
            {order.shippingAddress.fullName}, {order.shippingAddress.line1}, {order.shippingAddress.city}{' '}
            {order.shippingAddress.postcode}, {order.shippingAddress.country}
          </span>
        </div>
      </div>

      <hr className="divider" />

      <div>
        {order.items.map((line) => (
          <div className="order-line" key={`${order.reference}-${line.sku}`}>
            <span>
              {line.quantity} × {line.productName}
            </span>
            <span className="mono">{money(line.lineTotal)}</span>
          </div>
        ))}
      </div>

      <div className="summary__row">
        <span>Subtotal</span>
        <span className="mono">{money(order.subtotal)}</span>
      </div>
      <div className="summary__row">
        <span>Shipping</span>
        <span className="mono">{order.shippingFee === 0 ? 'Free' : money(order.shippingFee)}</span>
      </div>
      <div className="summary__row">
        <span>Tax</span>
        <span className="mono">{money(order.taxTotal)}</span>
      </div>
      <div className="summary__row summary__row--total">
        <span>Total</span>
        <span className="mono">{money(order.grandTotal, order.currency)}</span>
      </div>
    </div>
  )
}
