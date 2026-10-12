import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type { CategoryView, ProductSummary } from '../types'
import { ProductCard } from '../components/ProductCard'
import { EmptyState, ErrorState, ProductGridSkeleton } from '../components/States'
import { BoxIcon, SparkIcon, TruckIcon } from '../components/Icons'
import { useAssistant } from '../state/AssistantContext'

export function Home() {
  const { setOpen, onboarding, send } = useAssistant()
  const [categories, setCategories] = useState<CategoryView[]>([])
  const [featured, setFeatured] = useState<ProductSummary[]>([])
  const [deals, setDeals] = useState<ProductSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    Promise.all([
      api.fetchCategories(controller.signal),
      api.fetchProducts({ sort: 'rating', size: 4 }, controller.signal),
      api.fetchProducts({ sort: 'price-asc', size: 4, inStock: true }, controller.signal),
    ])
      .then(([categoryList, top, cheapest]) => {
        setCategories(categoryList)
        setFeatured(top.items)
        setDeals(cheapest.items)
      })
      .catch((cause) => {
        if (controller.signal.aborted) return
        setError(cause instanceof ApiError ? cause.message : 'Could not load the storefront.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [nonce])

  const productCount = categories.reduce((total, category) => total + category.productCount, 0)

  return (
    <div className="page page--wide">
      <section className="hero">
        <div className="hero__eyebrow">
          <SparkIcon size={14} />
          Spring AI shopping assistant
        </div>
        <h1 className="hero__title">Shop smarter. Ask first, buy once.</h1>
        <p className="hero__text">
          Describe what you need in your own words. The assistant searches the real catalogue, compares
          specs side by side, explains the trade-offs and can drop the right item in your cart — all
          without leaving the conversation.
        </p>
        <div className="hero__actions">
          <button type="button" className="btn btn--primary btn--lg" onClick={() => setOpen(true)}>
            <SparkIcon size={16} />
            Ask the assistant
          </button>
          <Link to="/shop" className="btn btn--ghost btn--lg">
            Browse the catalogue
          </Link>
        </div>
        <div className="hero__stats">
          <div>
            <div className="hero__stat-value mono">{productCount || '—'}</div>
            <div className="hero__stat-label">products in stock</div>
          </div>
          <div>
            <div className="hero__stat-value mono">{onboarding?.intents.length ?? '—'}</div>
            <div className="hero__stat-label">supported intents</div>
          </div>
          <div>
            <div className="hero__stat-value">Free</div>
            <div className="hero__stat-label">shipping over $99</div>
          </div>
        </div>
      </section>

      {error ? (
        <section className="section">
          <ErrorState
            title="The storefront could not load"
            message={error}
            onRetry={() => setNonce((value) => value + 1)}
          />
        </section>
      ) : null}

      {!error ? (
        <>
          <section className="section">
            <div className="section__head">
              <div>
                <h2 className="section__title">Shop by category</h2>
                <p className="section__subtitle">Six categories, hand-picked for this demo catalogue.</p>
              </div>
              <Link to="/shop" className="btn btn--ghost btn--sm">
                See everything
              </Link>
            </div>

            {loading ? (
              <div className="category-grid">
                {Array.from({ length: 6 }, (_, index) => (
                  <div className="skeleton" key={index} style={{ height: 104, borderRadius: 18 }} />
                ))}
              </div>
            ) : categories.length === 0 ? (
              <EmptyState
                title="No categories yet"
                text="The catalogue is empty. Seed the database and reload."
                icon={<BoxIcon size={22} />}
              />
            ) : (
              <div className="category-grid">
                {categories.map((category) => (
                  <Link className="category-tile" key={category.slug} to={`/shop?category=${category.slug}`}>
                    <span className="category-tile__icon" aria-hidden>
                      {category.icon ?? '•'}
                    </span>
                    <span className="category-tile__name">{category.name}</span>
                    <span className="category-tile__meta">
                      {category.productCount} products
                      {category.priceFrom ? ` · from $${category.priceFrom}` : ''}
                    </span>
                  </Link>
                ))}
              </div>
            )}
          </section>

          <section className="section">
            <div className="section__head">
              <div>
                <h2 className="section__title">Best rated right now</h2>
                <p className="section__subtitle">What shoppers keep coming back for.</p>
              </div>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => {
                  void send('Which of your best rated products would you recommend for everyday use?')
                  setOpen(true)
                }}
              >
                <SparkIcon size={14} />
                Ask which to pick
              </button>
            </div>
            {loading ? <ProductGridSkeleton count={4} /> : (
              <div className="product-grid">
                {featured.map((product) => (
                  <ProductCard product={product} key={product.sku} />
                ))}
              </div>
            )}
          </section>

          <section className="section">
            <div className="section__head">
              <div>
                <h2 className="section__title">Best value under budget</h2>
                <p className="section__subtitle">The cheapest in-stock picks across the store.</p>
              </div>
            </div>
            {loading ? <ProductGridSkeleton count={4} /> : (
              <div className="product-grid">
                {deals.map((product) => (
                  <ProductCard product={product} key={product.sku} />
                ))}
              </div>
            )}
          </section>

          <section className="section">
            <div className="card card--pad" style={{ display: 'grid', gap: 22, gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}>
              <div className="stack" style={{ gap: 8 }}>
                <SparkIcon size={20} />
                <h3 className="card__title">Compare without the tabs</h3>
                <p className="card__body">
                  Ask the assistant to compare two products and it renders a spec table with the
                  differences highlighted, plus a plain-language verdict.
                </p>
                <button
                  type="button"
                  className="btn btn--subtle btn--sm"
                  onClick={() => {
                    void send('Compare the Pulse 12 Pro and the Vertex Ultra 5G')
                    setOpen(true)
                  }}
                >
                  See an example
                </button>
              </div>
              <div className="stack" style={{ gap: 8 }}>
                <TruckIcon size={20} />
                <h3 className="card__title">Act, not just answer</h3>
                <p className="card__body">
                  The assistant can add items to your cart, adjust quantities and look up an order —
                  with your permission and never beyond your own data.
                </p>
                <button
                  type="button"
                  className="btn btn--subtle btn--sm"
                  onClick={() => {
                    void send('What is in my cart right now?')
                    setOpen(true)
                  }}
                >
                  Check my cart
                </button>
              </div>
              <div className="stack" style={{ gap: 8 }}>
                <BoxIcon size={20} />
                <h3 className="card__title">A human when you need one</h3>
                <p className="card__body">
                  Every answer has a handoff button. A ticket is raised with the transcript attached so
                  you never repeat yourself.
                </p>
                <button
                  type="button"
                  className="btn btn--subtle btn--sm"
                  onClick={() => {
                    void send('I need to talk to a human')
                    setOpen(true)
                  }}
                >
                  Talk to a person
                </button>
              </div>
            </div>
          </section>
        </>
      ) : null}
    </div>
  )
}
