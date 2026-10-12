import { Link } from 'react-router-dom'
import * as api from '../lib/api'
import { useCart } from '../state/CartContext'
import { useAssistant } from '../state/AssistantContext'
import { money } from '../lib/format'
import { CartIcon, SparkIcon, TrashIcon, TruckIcon } from '../components/Icons'
import { EmptyState, ErrorState, LineSkeleton } from '../components/States'
import { RecommendationRail } from '../components/RecommendationRail'

export function CartPage() {
  const { cart, loading, error, refresh, setQuantity, remove, clear } = useCart()
  const { send, setOpen } = useAssistant()

  if (loading && !cart) {
    return (
      <div className="page">
        <h1 className="section__title" style={{ marginBottom: 20 }}>
          Your cart
        </h1>
        <div className="cart-layout">
          <div className="card">
            <div className="stack" style={{ padding: 16 }}>
              <LineSkeleton height={72} />
              <LineSkeleton height={72} />
              <LineSkeleton height={72} />
            </div>
          </div>
          <div className="card summary">
            <LineSkeleton height={20} />
            <LineSkeleton height={20} />
            <LineSkeleton height={44} />
          </div>
        </div>
      </div>
    )
  }

  if (error && !cart) {
    return (
      <div className="page">
        <ErrorState message={error} onRetry={() => void refresh()} />
      </div>
    )
  }

  if (!cart || cart.empty) {
    return (
      <div className="page">
        <h1 className="section__title" style={{ marginBottom: 20 }}>
          Your cart
        </h1>
        <EmptyState
          title="Your cart is empty"
          text="Add something from the catalogue, or tell the assistant what you are looking for and let it find the right product."
          icon={<CartIcon size={22} />}
          actions={
            <>
              <Link to="/shop" className="btn btn--primary">
                Browse products
              </Link>
              <button
                type="button"
                className="btn btn--ghost"
                onClick={() => {
                  void send('Help me find something to buy')
                  setOpen(true)
                }}
              >
                <SparkIcon size={15} />
                Ask the assistant
              </button>
            </>
          }
        />
      </div>
    )
  }

  const progress = cart.freeShippingThreshold > 0
    ? Math.min(100, Math.round((cart.subtotal / cart.freeShippingThreshold) * 100))
    : 0

  return (
    <div className="page page--wide">
      <div className="section__head">
        <div>
          <h1 className="section__title">Your cart</h1>
          <p className="section__subtitle">
            {cart.itemCount} item{cart.itemCount === 1 ? '' : 's'} from {cart.distinctItemCount} product
            {cart.distinctItemCount === 1 ? '' : 's'}
          </p>
        </div>
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => void clear()}>
          Empty cart
        </button>
      </div>

      {cart.warnings.length > 0 ? (
        <div className="assistant-banner assistant-banner--warn" style={{ marginBottom: 16 }}>
          <div>
            {cart.warnings.map((warning) => (
              <div key={warning}>{warning}</div>
            ))}
          </div>
        </div>
      ) : null}

      <div className="cart-layout">
        <div className="card">
          {cart.items.map((line) => (
            <div className="cart-line" key={line.productId}>
              <div className="cart-line__media">
                <img src={line.imageUrl ?? '/media/products/placeholder.svg'} alt={line.name} />
              </div>

              <div className="stack" style={{ gap: 6 }}>
                <Link to={`/product/${line.slug}`} style={{ fontWeight: 580 }}>
                  {line.name}
                </Link>
                <div className="tiny muted">
                  {line.brand} · {line.sku} · {money(line.unitPrice)} each
                </div>
                {!line.inStock ? <span className="pill pill--danger">No longer available</span> : null}
                {line.quantityAdjusted ? (
                  <span className="pill pill--warn">Quantity reduced to available stock</span>
                ) : null}
              </div>

              <div className="stack" style={{ alignItems: 'flex-end', gap: 8 }}>
                <div style={{ fontWeight: 660 }}>{money(line.lineTotal)}</div>
                <div className="row" style={{ gap: 8 }}>
                  <div className="qty">
                    <button type="button" onClick={() => void setQuantity(line.productId, line.quantity - 1)} aria-label="Decrease quantity">
                      −
                    </button>
                    <span className="qty__value mono">{line.quantity}</span>
                    <button
                      type="button"
                      onClick={() => void setQuantity(line.productId, line.quantity + 1)}
                      disabled={line.quantity >= line.stockQuantity}
                      aria-label="Increase quantity"
                    >
                      +
                    </button>
                  </div>
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm btn--icon"
                    onClick={() => void remove(line.productId, line.name)}
                    aria-label={`Remove ${line.name}`}
                  >
                    <TrashIcon size={15} />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>

        <aside className="card summary">
          <div className="card__title">Order summary</div>

          <div className="tiny muted" style={{ marginTop: 4 }}>
            <div className="row" style={{ gap: 7, marginBottom: 6 }}>
              <TruckIcon size={14} />
              {cart.freeShippingRemaining > 0
                ? `Add ${money(cart.freeShippingRemaining)} for free shipping`
                : 'Free shipping unlocked'}
            </div>
            <div className="progress">
              <div className="progress__bar" style={{ width: `${progress}%` }} />
            </div>
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

          <Link to="/checkout" className="btn btn--primary btn--block btn--lg" style={{ marginTop: 8 }}>
            Proceed to checkout
          </Link>

          <button
            type="button"
            className="btn btn--ghost btn--block btn--sm"
            onClick={() => {
              void send('Is everything in my cart a good choice? Anything I should swap?')
              setOpen(true)
            }}
          >
            <SparkIcon size={14} />
            Review my cart with the assistant
          </button>

          <div className="tiny muted" style={{ textAlign: 'center' }}>
            Demo checkout — no real payment is taken.
          </div>
        </aside>
      </div>

      {/* Keyed on the basket's contents so the suggestions follow it: add a camera and the
          smart-home accessories appear without a reload. */}
      <RecommendationRail
        surface="cart_cross_sell"
        layout="grid"
        refreshKey={cart.items.map((item) => item.productId).join(',')}
        loader={(signal) => api.fetchCartRecommendations(4, signal)}
      />
    </div>
  )
}
