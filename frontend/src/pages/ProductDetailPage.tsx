import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type { ProductDetail, ProductSummary } from '../types'
import { money, stockLabel } from '../lib/format'
import { useCart } from '../state/CartContext'
import { useAssistant } from '../state/AssistantContext'
import { CheckIcon, SparkIcon, StarIcon, TruckIcon } from '../components/Icons'
import { ErrorState, LineSkeleton } from '../components/States'
import { ProductCard } from '../components/ProductCard'

export function ProductDetailPage() {
  const { slug = '' } = useParams()
  const { add } = useCart()
  const { send, setOpen } = useAssistant()

  const [product, setProduct] = useState<ProductDetail | null>(null)
  const [related, setRelated] = useState<ProductSummary[]>([])
  const [quantity, setQuantity] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [busy, setBusy] = useState(false)
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    setNotFound(false)
    setQuantity(1)

    api
      .fetchProduct(slug, controller.signal)
      .then(async (detail) => {
        setProduct(detail)
        const siblings = await api.fetchProducts(
          { category: detail.categorySlug, size: 5, sort: 'rating' },
          controller.signal,
        )
        setRelated(siblings.items.filter((item) => item.sku !== detail.sku).slice(0, 4))
      })
      .catch((cause) => {
        if (controller.signal.aborted) return
        if (cause instanceof ApiError && cause.status === 404) {
          setNotFound(true)
        } else {
          setError(cause instanceof ApiError ? cause.message : 'Could not load this product.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [slug, nonce])

  const askAboutProduct = (question: string) => {
    void send(question)
    setOpen(true)
  }

  if (loading) {
    return (
      <div className="page">
        <div className="product-detail">
          <div className="skeleton" style={{ aspectRatio: '4 / 3', borderRadius: 26 }} />
          <div className="stack">
            <LineSkeleton width="30%" height={12} />
            <LineSkeleton width="80%" height={26} />
            <LineSkeleton width="100%" />
            <LineSkeleton width="92%" />
            <LineSkeleton width="60%" height={34} />
          </div>
        </div>
      </div>
    )
  }

  if (notFound) {
    return (
      <div className="page">
        <ErrorState
          title="We could not find that product"
          message="It may have been renamed or removed. Browse the catalogue or ask the assistant to search for it."
          secondary={
            <>
              <Link to="/shop" className="btn btn--ghost">
                Browse the catalogue
              </Link>
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => askAboutProduct(`Can you find a product with the reference ${slug}?`)}
              >
                <SparkIcon size={14} />
                Ask the assistant
              </button>
            </>
          }
        />
      </div>
    )
  }

  if (error || !product) {
    return (
      <div className="page">
        <ErrorState message={error ?? 'Could not load this product.'} onRetry={() => setNonce((value) => value + 1)} />
      </div>
    )
  }

  const attributes = Object.entries(product.attributes ?? {})

  return (
    <div className="page page--wide">
      <div className="breadcrumbs">
        <Link to="/">Home</Link>
        <span>/</span>
        <Link to="/shop">Shop</Link>
        <span>/</span>
        <Link to={`/shop?category=${product.categorySlug}`}>{product.categoryName}</Link>
        <span>/</span>
        <span>{product.name}</span>
      </div>

      <div className="product-detail">
        <div className="product-detail__media">
          <img src={product.imageUrl ?? '/media/products/placeholder.svg'} alt={product.name} width={880} height={660} />
        </div>

        <div className="stack" style={{ gap: 18 }}>
          <div>
            <div className="product-card__brand">{product.brand}</div>
            <h1 style={{ fontSize: '1.7rem', marginTop: 6 }}>{product.name}</h1>
            <div className="row" style={{ marginTop: 10, gap: 12, flexWrap: 'wrap' }}>
              <span className="rating">
                <StarIcon className="rating__star" />
                <span className="mono">{product.rating.toFixed(1)}</span>
                <span>· {product.reviewCount} reviews</span>
              </span>
              <span className={`pill ${product.inStock ? (product.lowStock ? 'pill--warn' : 'pill--success') : 'pill--danger'}`}>
                {stockLabel(product.stockQuantity, product.inStock, product.lowStock)}
              </span>
              <span className="pill mono">{product.sku}</span>
            </div>
          </div>

          <div className="price">
            <span className="price__now" style={{ fontSize: '1.6rem' }}>
              {money(product.price, product.currency)}
            </span>
            {product.compareAtPrice ? <span className="price__was">{money(product.compareAtPrice)}</span> : null}
            {product.discountPercent ? <span className="pill pill--danger">Save {product.discountPercent}%</span> : null}
          </div>

          <p className="card__body">{product.description}</p>

          {product.highlights.length > 0 ? (
            <ul className="highlight-list">
              {product.highlights.map((highlight) => (
                <li key={highlight}>
                  <CheckIcon size={15} />
                  <span>{highlight}</span>
                </li>
              ))}
            </ul>
          ) : null}

          <div className="row" style={{ gap: 12, flexWrap: 'wrap' }}>
            <div className="qty">
              <button type="button" onClick={() => setQuantity((value) => Math.max(1, value - 1))} disabled={quantity <= 1} aria-label="Decrease quantity">
                −
              </button>
              <span className="qty__value mono">{quantity}</span>
              <button
                type="button"
                onClick={() => setQuantity((value) => Math.min(product.stockQuantity, value + 1))}
                disabled={quantity >= product.stockQuantity}
                aria-label="Increase quantity"
              >
                +
              </button>
            </div>
            <button
              type="button"
              className="btn btn--primary btn--lg"
              disabled={!product.inStock || busy}
              onClick={async () => {
                setBusy(true)
                await add({ productId: product.id, quantity, name: product.name })
                setBusy(false)
              }}
            >
              {busy ? <span className="spinner" /> : 'Add to cart'}
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--lg"
              onClick={() => askAboutProduct(`Is the ${product.name} a good choice? What are the trade-offs?`)}
            >
              <SparkIcon size={16} />
              Ask about this
            </button>
          </div>

          <div className="notice-inline">
            <TruckIcon size={15} />
            Free shipping over $99 · 30-day returns on unopened items · 2-year manufacturer warranty
          </div>

          <div className="card card--pad stack" style={{ gap: 10 }}>
            <div className="card__title">Ask the assistant</div>
            <div className="quick-replies">
              <button
                type="button"
                className="quick-reply"
                onClick={() => askAboutProduct(`Compare the ${product.name} with a close alternative`)}
              >
                Compare with an alternative
              </button>
              <button
                type="button"
                className="quick-reply"
                onClick={() => askAboutProduct(`Is the ${product.name} good value for money?`)}
              >
                Is it good value?
              </button>
              <button
                type="button"
                className="quick-reply"
                onClick={() => askAboutProduct(`Who is the ${product.name} best suited for?`)}
              >
                Who is it for?
              </button>
            </div>
          </div>
        </div>
      </div>

      {attributes.length > 0 ? (
        <section className="section" style={{ marginTop: 46 }}>
          <div className="section__head">
            <div>
              <h2 className="section__title">Full specification</h2>
              <p className="section__subtitle">The same data the assistant uses when it compares products.</p>
            </div>
          </div>
          <div className="card">
            <table className="spec-table">
              <tbody>
                {attributes.map(([key, value]) => (
                  <tr key={key}>
                    <th scope="row">{key}</th>
                    <td>{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      {related.length > 0 ? (
        <section className="section">
          <div className="section__head">
            <div>
              <h2 className="section__title">More in {product.categoryName}</h2>
            </div>
          </div>
          <div className="product-grid">
            {related.map((item) => (
              <ProductCard product={item} key={item.sku} />
            ))}
          </div>
        </section>
      ) : null}
    </div>
  )
}
