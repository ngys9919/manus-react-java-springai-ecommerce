import { Link } from 'react-router-dom'
import type { ProductSummary } from '../types'
import { money, stockLabel } from '../lib/format'
import { StarIcon } from './Icons'
import { useCart } from '../state/CartContext'
import { useState } from 'react'

interface ProductCardProps {
  product: ProductSummary
  /**
   * Why this product is being shown. Recommendations are only useful if the shopper can see the
   * reasoning, so a rail passes the server's explanation straight through.
   */
  reason?: string
  /** Fired when the card is activated, used to attribute a click to the rail that produced it. */
  onActivate?: () => void
}

export function ProductCard({ product, reason, onActivate }: ProductCardProps) {
  const { add } = useCart()
  const [busy, setBusy] = useState(false)

  const onAdd = async () => {
    setBusy(true)
    await add({ productId: product.id, quantity: 1, name: product.name })
    setBusy(false)
  }

  return (
    <article className="product-card">
      <Link
        to={`/product/${product.slug}`}
        className="product-card__media"
        aria-label={product.name}
        onClick={onActivate}
      >
        <img
          src={product.imageUrl ?? '/media/products/placeholder.svg'}
          alt={product.name}
          loading="lazy"
          width={480}
          height={360}
        />
        <div className="product-card__badges">
          {product.discountPercent ? (
            <span className="pill pill--danger">-{product.discountPercent}%</span>
          ) : null}
          {!product.inStock ? <span className="pill">Out of stock</span> : null}
          {product.inStock && product.lowStock ? (
            <span className="pill pill--warn">{stockLabel(product.stockQuantity, product.inStock, product.lowStock)}</span>
          ) : null}
        </div>
      </Link>

      <div className="product-card__body">
        <div className="product-card__brand">{product.brand}</div>
        <Link to={`/product/${product.slug}`} className="product-card__name" onClick={onActivate}>
          {product.name}
        </Link>
        {reason ? (
          <p className="product-card__reason">{reason}</p>
        ) : (
          <p className="product-card__desc">{product.shortDescription}</p>
        )}

        <div className="rating">
          <StarIcon className="rating__star" />
          <span className="mono">{product.rating.toFixed(1)}</span>
          <span>· {product.reviewCount} reviews</span>
        </div>

        <div className="product-card__footer">
          <div className="price">
            <span className="price__now">{money(product.price, product.currency)}</span>
            {product.compareAtPrice ? (
              <span className="price__was">{money(product.compareAtPrice, product.currency)}</span>
            ) : null}
          </div>
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={onAdd}
            disabled={!product.inStock || busy}
            title={product.inStock ? 'Add to cart' : 'Out of stock'}
          >
            {busy ? <span className="spinner" /> : 'Add'}
          </button>
        </div>
      </div>
    </article>
  )
}
