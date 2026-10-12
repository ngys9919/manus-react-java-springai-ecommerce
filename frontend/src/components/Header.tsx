import { useState, type FormEvent } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../state/AuthContext'
import { useCart } from '../state/CartContext'
import { useAssistant } from '../state/AssistantContext'
import { CartIcon, ChartIcon, SearchIcon, SparkIcon, UserIcon } from './Icons'
import { initials } from '../lib/format'

export function Header() {
  const { user, signOut } = useAuth()
  const { cart, openDrawer } = useCart()
  const { setOpen, status } = useAssistant()
  const navigate = useNavigate()
  const [term, setTerm] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const query = term.trim()
    navigate(query ? `/shop?q=${encodeURIComponent(query)}` : '/shop')
  }

  const itemCount = cart?.itemCount ?? 0

  return (
    <header className="header">
      <div className="header__inner">
        <Link to="/" className="logo" aria-label="Shopwise home">
          <span className="logo__mark">S</span>
          Shopwise
        </Link>

        <nav className="nav" aria-label="Main">
          <NavLink to="/shop" className={({ isActive }) => `nav__link${isActive ? ' nav__link--active' : ''}`}>
            Shop
          </NavLink>
          <NavLink to="/orders" className={({ isActive }) => `nav__link${isActive ? ' nav__link--active' : ''}`}>
            Orders
          </NavLink>
          {user?.staff ? (
            <NavLink to="/insights" className={({ isActive }) => `nav__link${isActive ? ' nav__link--active' : ''}`}>
              Insights
            </NavLink>
          ) : null}
        </nav>

        <form className="search" role="search" onSubmit={onSubmit}>
          <SearchIcon size={15} className="muted" />
          <input
            type="search"
            placeholder="Search products…"
            aria-label="Search products"
            value={term}
            onChange={(event) => setTerm(event.target.value)}
          />
        </form>

        <div className="header__actions">
          <button
            type="button"
            className="btn btn--subtle btn--sm"
            onClick={() => setOpen(true)}
            title={status?.available === false ? 'Assistant is in offline mode' : 'Ask the shopping assistant'}
          >
            <SparkIcon size={15} />
            <span className="visually-hidden-mobile">Ask</span>
          </button>

          <button type="button" className="icon-btn" onClick={openDrawer} aria-label={`Cart, ${itemCount} items`}>
            <CartIcon size={18} />
            {itemCount > 0 ? <span className="badge-count">{itemCount > 99 ? '99+' : itemCount}</span> : null}
          </button>

          {user ? (
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                className="icon-btn"
                onClick={() => setMenuOpen((open) => !open)}
                aria-label="Account menu"
                aria-expanded={menuOpen}
              >
                <span className="mono" style={{ fontSize: '0.78rem', fontWeight: 700 }}>
                  {initials(user.fullName)}
                </span>
              </button>
              {menuOpen ? (
                <div
                  className="card"
                  style={{
                    position: 'absolute',
                    right: 0,
                    top: 46,
                    minWidth: 216,
                    padding: 14,
                    zIndex: 50,
                    boxShadow: 'var(--shadow-lg)',
                  }}
                  onMouseLeave={() => setMenuOpen(false)}
                >
                  <div style={{ fontWeight: 620, fontSize: '0.9rem' }}>{user.fullName}</div>
                  <div className="muted tiny">{user.email}</div>
                  <div className="row" style={{ marginTop: 8, gap: 6 }}>
                    <span className={`pill ${user.staff ? 'pill--brand' : ''}`}>{user.role}</span>
                  </div>
                  <hr className="divider" style={{ margin: '12px 0' }} />
                  {user.staff ? (
                    <Link
                      to="/insights"
                      className="nav__link"
                      style={{ display: 'flex', gap: 8, padding: '8px 4px' }}
                      onClick={() => setMenuOpen(false)}
                    >
                      <ChartIcon size={16} /> Assistant insights
                    </Link>
                  ) : null}
                  <button
                    type="button"
                    className="btn btn--ghost btn--block btn--sm"
                    style={{ marginTop: 8 }}
                    onClick={() => {
                      signOut()
                      setMenuOpen(false)
                      navigate('/')
                    }}
                  >
                    Sign out
                  </button>
                </div>
              ) : null}
            </div>
          ) : (
            <Link to="/login" className="icon-btn" aria-label="Sign in">
              <UserIcon size={18} />
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
