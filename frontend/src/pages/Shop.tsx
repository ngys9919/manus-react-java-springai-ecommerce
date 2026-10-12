import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type { ProductPage } from '../types'
import { ProductCard } from '../components/ProductCard'
import { EmptyState, ErrorState, ProductGridSkeleton } from '../components/States'
import { SearchIcon, SparkIcon } from '../components/Icons'
import { useAssistant } from '../state/AssistantContext'

const SORTS = [
  { value: 'relevance', label: 'Most relevant' },
  { value: 'price-asc', label: 'Price: low to high' },
  { value: 'price-desc', label: 'Price: high to low' },
  { value: 'rating', label: 'Top rated' },
  { value: 'newest', label: 'Newest' },
  { value: 'name', label: 'Name A–Z' },
]

const PRICE_BANDS = [
  { label: 'Any price', min: null, max: null },
  { label: 'Under $250', min: null, max: 250 },
  { label: '$250 – $800', min: 250, max: 800 },
  { label: '$800 – $1500', min: 800, max: 1500 },
  { label: 'Over $1500', min: 1500, max: null },
]

export function Shop() {
  const [params, setParams] = useSearchParams()
  const { send, setOpen } = useAssistant()

  const q = params.get('q') ?? ''
  const category = params.get('category') ?? 'all'
  const sort = params.get('sort') ?? 'relevance'
  const page = Number(params.get('page') ?? '0')
  const inStock = params.get('inStock') === 'true'
  const bandIndex = Number(params.get('band') ?? '0')

  const [data, setData] = useState<ProductPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [nonce, setNonce] = useState(0)
  const [searchDraft, setSearchDraft] = useState(q)

  useEffect(() => setSearchDraft(q), [q])

  const band = PRICE_BANDS[bandIndex] ?? PRICE_BANDS[0]

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    api
      .fetchProducts(
        {
          q,
          category,
          sort,
          page,
          size: 12,
          inStock,
          minPrice: band.min,
          maxPrice: band.max,
        },
        controller.signal,
      )
      .then(setData)
      .catch((cause) => {
        if (controller.signal.aborted) return
        setError(cause instanceof ApiError ? cause.message : 'Could not load products.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [q, category, sort, page, inStock, band.min, band.max, nonce])

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => {
      if (value === null || value === '') {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    })
    if (!('page' in changes)) {
      next.delete('page')
    }
    setParams(next)
  }

  const activeFilters = useMemo(() => {
    const chips: { label: string; clear: () => void }[] = []
    if (q) chips.push({ label: `“${q}”`, clear: () => update({ q: null }) })
    if (category !== 'all') chips.push({ label: data?.facets.categories.find((c) => c.slug === category)?.name ?? category, clear: () => update({ category: null }) })
    if (bandIndex !== 0) chips.push({ label: band.label, clear: () => update({ band: null }) })
    if (inStock) chips.push({ label: 'In stock only', clear: () => update({ inStock: null }) })
    return chips
  }, [q, category, bandIndex, inStock, data, band.label])

  const askAssistant = () => {
    const parts = ['Find me']
    if (category !== 'all') parts.push(category)
    if (q) parts.push(`matching “${q}”`)
    if (band.max) parts.push(`under $${band.max}`)
    if (band.min && !band.max) parts.push(`over $${band.min}`)
    parts.push(inStock ? 'that are in stock' : '')
    void send(parts.filter(Boolean).join(' '))
    setOpen(true)
  }

  return (
    <div className="page page--wide">
      <div className="breadcrumbs">
        <span>Home</span>
        <span>/</span>
        <span>Shop</span>
      </div>

      <div className="shop-layout">
        <aside className="filters" aria-label="Filters">
          <div>
            <div className="filters__group-title">Search</div>
            <form
              onSubmit={(event) => {
                event.preventDefault()
                update({ q: searchDraft.trim() || null })
              }}
              className="row"
              style={{ gap: 6 }}
            >
              <input
                className="input"
                placeholder="Search…"
                value={searchDraft}
                onChange={(event) => setSearchDraft(event.target.value)}
                aria-label="Search products"
              />
              <button type="submit" className="btn btn--primary btn--sm btn--icon" aria-label="Search">
                <SearchIcon size={15} />
              </button>
            </form>
          </div>

          <div>
            <div className="filters__group-title">Category</div>
            <button
              type="button"
              className={`filter-option${category === 'all' ? ' filter-option--active' : ''}`}
              onClick={() => update({ category: null })}
            >
              <span>All products</span>
              <span className="filter-option__count">{data?.totalElements ?? ''}</span>
            </button>
            {(data?.facets.categories ?? []).map((facet) => (
              <button
                type="button"
                key={facet.slug}
                className={`filter-option${category === facet.slug ? ' filter-option--active' : ''}`}
                onClick={() => update({ category: facet.slug })}
              >
                <span>
                  {facet.icon} {facet.name}
                </span>
                <span className="filter-option__count">{facet.count}</span>
              </button>
            ))}
          </div>

          <div>
            <div className="filters__group-title">Price</div>
            {PRICE_BANDS.map((option, index) => (
              <button
                type="button"
                key={option.label}
                className={`filter-option${bandIndex === index ? ' filter-option--active' : ''}`}
                onClick={() => update({ band: index === 0 ? null : String(index) })}
              >
                <span>{option.label}</span>
              </button>
            ))}
          </div>

          <div>
            <div className="filters__group-title">Availability</div>
            <label className="filter-option" style={{ cursor: 'pointer' }}>
              <span>In stock only</span>
              <input
                type="checkbox"
                checked={inStock}
                onChange={(event) => update({ inStock: event.target.checked ? 'true' : null })}
              />
            </label>
          </div>

          <button type="button" className="btn btn--subtle btn--block btn--sm" onClick={askAssistant}>
            <SparkIcon size={14} />
            Ask the assistant instead
          </button>
        </aside>

        <section>
          <div className="toolbar">
            <div className="grow">
              <h1 className="section__title" style={{ fontSize: '1.25rem' }}>
                {category === 'all'
                  ? q
                    ? `Results for “${q}”`
                    : 'All products'
                  : data?.facets.categories.find((c) => c.slug === category)?.name ?? 'Products'}
              </h1>
              <p className="section__subtitle">
                {loading ? 'Loading…' : `${data?.totalElements ?? 0} products`}
              </p>
            </div>

            <label className="row small muted" style={{ gap: 8 }}>
              Sort
              <select className="select" value={sort} onChange={(event) => update({ sort: event.target.value })}>
                {SORTS.map((option) => (
                  <option value={option.value} key={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {activeFilters.length > 0 ? (
            <div className="row row--wrap" style={{ marginBottom: 16, gap: 8 }}>
              {activeFilters.map((chip) => (
                <button type="button" key={chip.label} className="pill pill--brand" onClick={chip.clear}>
                  {chip.label} ✕
                </button>
              ))}
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => setParams(new URLSearchParams())}>
                Clear all
              </button>
            </div>
          ) : null}

          {error ? (
            <ErrorState
              title="We could not load the catalogue"
              message={error}
              onRetry={() => setNonce((value) => value + 1)}
              secondary={
                <button type="button" className="btn btn--ghost" onClick={askAssistant}>
                  <SparkIcon size={14} />
                  Ask the assistant
                </button>
              }
            />
          ) : loading && !data ? (
            <ProductGridSkeleton count={9} />
          ) : data && data.items.length === 0 ? (
            <EmptyState
              title="Nothing matched those filters"
              text="Try widening the price range, clearing the search term, or let the assistant look for you."
              actions={
                <>
                  <button type="button" className="btn btn--ghost" onClick={() => setParams(new URLSearchParams())}>
                    Clear filters
                  </button>
                  <button type="button" className="btn btn--primary" onClick={askAssistant}>
                    <SparkIcon size={14} />
                    Ask the assistant
                  </button>
                </>
              }
            />
          ) : (
            <>
              <div className="product-grid" style={{ opacity: loading ? 0.6 : 1, transition: 'opacity 160ms ease' }}>
                {data?.items.map((product) => (
                  <ProductCard product={product} key={product.sku} />
                ))}
              </div>

              {data && data.totalPages > 1 ? (
                <div className="row row--between" style={{ marginTop: 26 }}>
                  <button
                    type="button"
                    className="btn btn--ghost"
                    disabled={!data.hasPrevious}
                    onClick={() => update({ page: String(Math.max(0, data.page - 1)) })}
                  >
                    Previous
                  </button>
                  <span className="small muted mono">
                    Page {data.page + 1} of {data.totalPages}
                  </span>
                  <button
                    type="button"
                    className="btn btn--ghost"
                    disabled={!data.hasNext}
                    onClick={() => update({ page: String(data.page + 1) })}
                  >
                    Next
                  </button>
                </div>
              ) : null}
            </>
          )}
        </section>
      </div>
    </div>
  )
}
