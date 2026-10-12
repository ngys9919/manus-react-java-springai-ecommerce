import { Link } from 'react-router-dom'
import { useCart } from '../state/CartContext'
import { money } from '../lib/format'
import { CartIcon, CloseIcon, TrashIcon, TruckIcon } from './Icons'
import { EmptyState, ErrorState, LineSkeleton } from './States'
import { useEffect } from 'react'

export function CartDrawer() {
  const { cart, drawerOpen, closeDrawer, loading, error, refresh, setQuantity, remove, clear } = useCart()

  useEffect(() => {
    if (!drawerOpen) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeDrawer()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [drawerOpen, closeDrawer])

  if (!drawerOpen) return null

  const progress = cart && cart.freeShippingThreshold > 0
    ? Math.min(100, Math.round(((cart.subtotal) / cart.freeShippingThreshold) * 100))
    : 0

  return (
    <>
      <div className="drawer-backdrop" onClick={closeDrawer} aria-hidden />
      <aside className="drawer" role="dialog" aria-label="Shopping cart" aria-modal="true">
        <div className="drawer__head">
          <div className="row" style={{ gap: 9 }}>
            <CartIcon size={18} />
            <strong style={{ fontSize: '0.98rem' }}>Your cart</strong>
            {cart && !cart.empty ? <span className="pill">{cart.itemCount} items</span> : null}
          </div>
          <button type="button" className="icon-btn" onClick={closeDrawer} aria-label="Close cart">
            <CloseIcon size={17} />
          </button>
        </div>

        <div className="drawer__body">
          {loading && !cart ? (
            <div className="stack" style={{ paddingTop: 12 }}>
              <LineSkeleton height={64} />
              <LineSkeleton height={64} />
              <LineSkeleton height={64} />
            </div>
          ) : error && !cart ? (
            <div style={{ paddingTop: 12 }}>
              <ErrorState message={error} onRetry={() => void refresh()} />
            </div>
          ) : !cart || cart.empty ? (
            <div style={{ paddingTop: 12 }}>
              <EmptyState
                title="Your cart is empty"
                text="Browse the catalogue or ask the assistant to find something for you."
                icon={<CartIcon size={22} />}
                actions={
                  <>
                    <Link to="/shop" className="btn btn--primary" onClick={closeDrawer}>
                      Browse products
                    </Link>
                  </>
                }
              />
            </div>
          ) : (
            <>
              {cart.warnings.length > 0 ? (
                <div className="assistant-banner assistant-banner--warn" style={{ margin: '12px 0' }}>
                  <div>
                    {cart.warnings.map((warning) => (
                      <div key={warning}>{warning}</div>
                    ))}
                  </div>
                </div>
              ) : null}

              {cart.items.map((line) => (
                <div className="cart-line" key={line.productId} style={{ gridTemplateColumns: '72px minmax(0, 1fr)' }}>
                  <div className="cart-line__media" style={{ width: 72, height: 58 }}>
                    <img src={line.imageUrl ?? '/media/products/placeholder.svg'} alt={line.name} />
                  </div>
                  <div className="stack" style={{ gap: 8 }}>
                    <div className="row row--between" style={{ alignItems: 'flex-start' }}>
                      <div>
                        <div style={{ fontSize: '0.9rem', fontWeight: 580 }}>
                          <Link to={`/product/${line.slug}`} onClick={closeDrawer}>
                            {line.name}
                          </Link>
                        </div>
                        <div className="tiny muted">{line.brand} · {line.sku}</div>
                      </div>
                      <div style={{ fontWeight: 650, fontSize: '0.9rem' }}>{money(line.lineTotal)}</div>
                    </div>
                    <div className="row row--between">
                      <div className="qty">
                        <button
                          type="button"
                          onClick={() => void setQuantity(line.productId, line.quantity - 1)}
                          aria-label={`Decrease quantity of ${line.name}`}
                        >
                          −
                        </button>
                        <span className="qty__value mono">{line.quantity}</span>
                        <button
                          type="button"
                          onClick={() => void setQuantity(line.productId, line.quantity + 1)}
                          disabled={line.quantity >= line.stockQuantity}
                          aria-label={`Increase quantity of ${line.name}`}
                        >
                          +
                        </button>
                      </div>
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        onClick={() => void remove(line.productId, line.name)}
                      >
                        <TrashIcon size={14} />
                        Remove
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </>
          )}
        </div>

        {cart && !cart.empty ? (
          <div className="drawer__foot">
            <div className="tiny muted">
              <div className="row" style={{ gap: 7, marginBottom: 6 }}>
                <TruckIcon size={14} />
                {cart.freeShippingRemaining > 0
                  ? `Add ${money(cart.freeShippingRemaining)} more for free shipping`
                  : 'Free shipping unlocked'}
              </div>
              <div className="progress">
                <div className="progress__bar" style={{ width: `${progress}%` }} />
              </div>
            </div>

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

            <Link to="/checkout" className="btn btn--primary btn--block btn--lg" onClick={closeDrawer}>
              Checkout
            </Link>
            <div className="row row--between">
              <Link to="/cart" className="btn btn--ghost btn--sm" onClick={closeDrawer}>
                View full cart
              </Link>
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => void clear()}>
                Empty cart
              </button>
            </div>
          </div>
        ) : null}
      </aside>
    </>
  )
}
