import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type { OrderView, ProblemDetail } from '../types'
import { money } from '../lib/format'
import { useCart } from '../state/CartContext'
import { useAuth } from '../state/AuthContext'
import { useToast } from '../state/ToastContext'
import { useAssistant } from '../state/AssistantContext'
import { AlertIcon, LockIcon, SparkIcon } from '../components/Icons'
import { EmptyState } from '../components/States'

interface FormState {
  fullName: string
  email: string
  line1: string
  line2: string
  city: string
  postcode: string
  country: string
  note: string
}

const EMPTY: FormState = {
  fullName: '',
  email: '',
  line1: '',
  line2: '',
  city: '',
  postcode: '',
  country: 'Singapore',
  note: '',
}

export function CheckoutPage() {
  const { cart, loading, refresh } = useCart()
  const { user } = useAuth()
  const { push } = useToast()
  const { setOpen, send } = useAssistant()
  const navigate = useNavigate()

  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  useEffect(() => {
    setForm((current) => ({
      ...current,
      fullName: current.fullName || user?.fullName || '',
      email: current.email || user?.email || '',
    }))
  }, [user])

  const set = (key: keyof FormState, value: string) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => {
      if (!current[key]) return current
      const next = { ...current }
      delete next[key]
      return next
    })
  }

  const validate = (): boolean => {
    const next: Record<string, string> = {}
    if (!form.fullName.trim()) next.fullName = 'We need a name for the delivery.'
    if (!form.email.trim()) {
      next.email = 'An email is required so we can send the confirmation.'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(form.email.trim())) {
      next.email = 'That email address does not look right.'
    }
    if (!form.line1.trim()) next.line1 = 'Street address is required.'
    if (!form.city.trim()) next.city = 'City is required.'
    if (!form.postcode.trim()) next.postcode = 'Postcode is required.'
    if (!form.country.trim()) next.country = 'Country is required.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setFailure(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      const order: OrderView = await api.checkout({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        line1: form.line1.trim(),
        line2: form.line2.trim() || undefined,
        city: form.city.trim(),
        postcode: form.postcode.trim(),
        country: form.country.trim(),
        note: form.note.trim() || undefined,
      })
      await refresh()
      push({ tone: 'success', title: `Order ${order.reference} placed`, body: order.estimatedDelivery })
      navigate(`/orders/${order.reference}`, { state: { order, justPlaced: true } })
    } catch (cause) {
      if (cause instanceof ApiError) {
        const problem: ProblemDetail = cause.problem
        if (problem.errors) {
          setErrors(problem.errors)
        }
        if (cause.status === 409) {
          setFailure('Something in your cart changed while you were checking out. Review the cart and try again.')
          void refresh()
        } else if (cause.status === 401 || cause.status === 403) {
          setFailure('That email is already used by a different account. Sign in or use another address.')
        } else {
          setFailure(cause.message)
        }
      } else {
        setFailure('We could not place the order. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading && !cart) {
    return (
      <div className="page">
        <div className="skeleton" style={{ height: 320, borderRadius: 18 }} />
      </div>
    )
  }

  if (!cart || cart.empty) {
    return (
      <div className="page">
        <EmptyState
          title="Nothing to check out"
          text="Your cart is empty. Add a product first, then come back."
          actions={
            <Link to="/shop" className="btn btn--primary">
              Browse products
            </Link>
          }
        />
      </div>
    )
  }

  return (
    <div className="page page--wide">
      <div className="breadcrumbs">
        <Link to="/cart">Cart</Link>
        <span>/</span>
        <span>Checkout</span>
      </div>

      <h1 className="section__title" style={{ marginBottom: 6 }}>
        Checkout
      </h1>
      <p className="section__subtitle" style={{ marginBottom: 24 }}>
        {user ? `Signed in as ${user.email}` : 'Checking out as a guest — we just need an email for the confirmation.'}
      </p>

      {failure ? (
        <div className="assistant-banner assistant-banner--danger" style={{ marginBottom: 18 }}>
          <AlertIcon size={16} />
          <div>{failure}</div>
        </div>
      ) : null}

      <form onSubmit={onSubmit} className="cart-layout">
        <div className="card card--pad stack" style={{ gap: 18 }}>
          <div className="form-grid">
            <div className="field form-grid--full">
              <label className="field__label" htmlFor="fullName">
                Full name
              </label>
              <input
                id="fullName"
                className="input"
                value={form.fullName}
                onChange={(event) => set('fullName', event.target.value)}
                aria-invalid={Boolean(errors.fullName)}
                autoComplete="name"
              />
              {errors.fullName ? <span className="field__error">{errors.fullName}</span> : null}
            </div>

            <div className="field form-grid--full">
              <label className="field__label" htmlFor="email">
                Email
              </label>
              <input
                id="email"
                className="input"
                type="email"
                value={form.email}
                onChange={(event) => set('email', event.target.value)}
                aria-invalid={Boolean(errors.email)}
                autoComplete="email"
              />
              {errors.email ? (
                <span className="field__error">{errors.email}</span>
              ) : (
                <span className="field__hint">Used to track the order and to reach you if needed.</span>
              )}
            </div>

            <div className="field form-grid--full">
              <label className="field__label" htmlFor="line1">
                Street address
              </label>
              <input
                id="line1"
                className="input"
                value={form.line1}
                onChange={(event) => set('line1', event.target.value)}
                aria-invalid={Boolean(errors.line1)}
                autoComplete="address-line1"
              />
              {errors.line1 ? <span className="field__error">{errors.line1}</span> : null}
            </div>

            <div className="field form-grid--full">
              <label className="field__label" htmlFor="line2">
                Apartment, suite (optional)
              </label>
              <input
                id="line2"
                className="input"
                value={form.line2}
                onChange={(event) => set('line2', event.target.value)}
                autoComplete="address-line2"
              />
            </div>

            <div className="field">
              <label className="field__label" htmlFor="city">
                City
              </label>
              <input
                id="city"
                className="input"
                value={form.city}
                onChange={(event) => set('city', event.target.value)}
                aria-invalid={Boolean(errors.city)}
                autoComplete="address-level2"
              />
              {errors.city ? <span className="field__error">{errors.city}</span> : null}
            </div>

            <div className="field">
              <label className="field__label" htmlFor="postcode">
                Postcode
              </label>
              <input
                id="postcode"
                className="input"
                value={form.postcode}
                onChange={(event) => set('postcode', event.target.value)}
                aria-invalid={Boolean(errors.postcode)}
                autoComplete="postal-code"
              />
              {errors.postcode ? <span className="field__error">{errors.postcode}</span> : null}
            </div>

            <div className="field form-grid--full">
              <label className="field__label" htmlFor="country">
                Country
              </label>
              <input
                id="country"
                className="input"
                value={form.country}
                onChange={(event) => set('country', event.target.value)}
                aria-invalid={Boolean(errors.country)}
                autoComplete="country-name"
              />
              {errors.country ? <span className="field__error">{errors.country}</span> : null}
            </div>

            <div className="field form-grid--full">
              <label className="field__label" htmlFor="note">
                Delivery note (optional)
              </label>
              <textarea
                id="note"
                className="textarea"
                rows={3}
                value={form.note}
                onChange={(event) => set('note', event.target.value)}
                placeholder="Gate code, preferred delivery window…"
              />
            </div>
          </div>

          <div className="notice-inline">
            <LockIcon size={15} />
            This is a demonstration checkout. No card details are collected and no payment is taken.
          </div>
        </div>

        <aside className="card summary">
          <div className="card__title">Order summary</div>

          <div className="stack" style={{ gap: 6, marginTop: 8 }}>
            {cart.items.map((line) => (
              <div className="cart-summary__line" key={line.productId}>
                <span>
                  {line.quantity} × {line.name}
                </span>
                <span className="mono">{money(line.lineTotal)}</span>
              </div>
            ))}
          </div>

          <hr className="divider" />

          <div className="summary__row">
            <span>Subtotal</span>
            <span className="mono">{money(cart.subtotal)}</span>
          </div>
          <div className="summary__row">
            <span>Shipping</span>
            <span className="mono">{cart.shippingFee === 0 ? 'Free' : money(cart.shippingFee)}</span>
          </div>
          <div className="summary__row">
            <span>Tax</span>
            <span className="mono">{money(cart.taxTotal)}</span>
          </div>
          <div className="summary__row summary__row--total">
            <span>Total</span>
            <span className="mono">{money(cart.grandTotal)}</span>
          </div>

          <button type="submit" className="btn btn--primary btn--block btn--lg" disabled={submitting} style={{ marginTop: 8 }}>
            {submitting ? <span className="spinner" /> : `Place order · ${money(cart.grandTotal)}`}
          </button>

          <button
            type="button"
            className="btn btn--ghost btn--block btn--sm"
            onClick={() => {
              void send('Before I check out, is there anything I should know about shipping or returns?')
              setOpen(true)
            }}
          >
            <SparkIcon size={14} />
            Ask about shipping or returns
          </button>
        </aside>
      </form>
    </div>
  )
}
