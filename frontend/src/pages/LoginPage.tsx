import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../state/AuthContext'
import { useToast } from '../state/ToastContext'
import { ApiError } from '../lib/api'
import { AlertIcon, LockIcon, SparkIcon } from '../components/Icons'

/**
 * Accounts seeded by V2__seed.sql and published on this page on purpose, so the demo can be
 * explored without a setup step. They only exist in the local development database and grant
 * nothing outside it. Marked with an inline exception so the pre-push secret scan stays
 * meaningful rather than being ignored wholesale.
 */
const DEMO_ACCOUNTS = [
  { label: 'Shopper', email: 'customer@shopwise.dev', password: 'Password123!', note: 'Orders, cart, assistant memory' }, // secret-scan:allow seeded demo fixture
  { label: 'Support agent', email: 'agent@shopwise.dev', password: 'Support123!', note: 'Adds the staff insights dashboard' }, // secret-scan:allow seeded demo fixture
  { label: 'Store admin', email: 'admin@shopwise.dev', password: 'Admin123!', note: 'Full access' }, // secret-scan:allow seeded demo fixture
]

export function LoginPage() {
  const { signIn, signUp } = useAuth()
  const { push } = useToast()
  const navigate = useNavigate()
  const location = useLocation()

  const [mode, setMode] = useState<'signin' | 'register'>('signin')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/'

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    if (mode === 'register' && fullName.trim().length < 2) {
      setFieldErrors({ fullName: 'Please tell us your name.' })
      return
    }
    if (!email.trim()) {
      setFieldErrors({ email: 'Email is required.' })
      return
    }
    if (password.length < 8) {
      setFieldErrors({ password: 'Passwords are at least 8 characters.' })
      return
    }

    setBusy(true)
    try {
      const user =
        mode === 'signin'
          ? await signIn(email.trim(), password)
          : await signUp(fullName.trim(), email.trim(), password)
      push({ tone: 'success', title: `Welcome${user.fullName ? `, ${user.fullName.split(' ')[0]}` : ''}`, body: 'Your guest cart was merged into your account.' })
      navigate(redirectTo, { replace: true })
    } catch (cause) {
      if (cause instanceof ApiError) {
        setFieldErrors(cause.problem.errors ?? {})
        setError(cause.message)
      } else {
        setError('We could not sign you in. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  const useDemo = (account: (typeof DEMO_ACCOUNTS)[number]) => {
    setMode('signin')
    setEmail(account.email)
    setPassword(account.password)
    setError(null)
    setFieldErrors({})
  }

  return (
    <div className="page">
      <div className="cart-layout" style={{ gridTemplateColumns: 'minmax(0, 1fr) 340px' }}>
        <div className="card card--pad stack" style={{ gap: 18 }}>
          <div>
            <h1 className="section__title" style={{ fontSize: '1.4rem' }}>
              {mode === 'signin' ? 'Sign in' : 'Create an account'}
            </h1>
            <p className="section__subtitle">
              {mode === 'signin'
                ? 'Your cart and your assistant conversation follow you in.'
                : 'Register to keep your order history and let the assistant remember your preferences.'}
            </p>
          </div>

          <div className="row" style={{ gap: 8 }}>
            <button
              type="button"
              className={`btn ${mode === 'signin' ? 'btn--primary' : 'btn--ghost'} btn--sm`}
              onClick={() => setMode('signin')}
            >
              Sign in
            </button>
            <button
              type="button"
              className={`btn ${mode === 'register' ? 'btn--primary' : 'btn--ghost'} btn--sm`}
              onClick={() => setMode('register')}
            >
              Register
            </button>
          </div>

          {error ? (
            <div className="assistant-banner assistant-banner--danger">
              <AlertIcon size={16} />
              <div>{error}</div>
            </div>
          ) : null}

          <form onSubmit={onSubmit} className="stack" style={{ gap: 14 }}>
            {mode === 'register' ? (
              <div className="field">
                <label className="field__label" htmlFor="fullName">
                  Full name
                </label>
                <input
                  id="fullName"
                  className="input"
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  autoComplete="name"
                  aria-invalid={Boolean(fieldErrors.fullName)}
                />
                {fieldErrors.fullName ? <span className="field__error">{fieldErrors.fullName}</span> : null}
              </div>
            ) : null}

            <div className="field">
              <label className="field__label" htmlFor="email">
                Email
              </label>
              <input
                id="email"
                className="input"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                aria-invalid={Boolean(fieldErrors.email)}
              />
              {fieldErrors.email ? <span className="field__error">{fieldErrors.email}</span> : null}
            </div>

            <div className="field">
              <label className="field__label" htmlFor="password">
                Password
              </label>
              <input
                id="password"
                className="input"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                aria-invalid={Boolean(fieldErrors.password)}
              />
              {fieldErrors.password ? (
                <span className="field__error">{fieldErrors.password}</span>
              ) : (
                <span className="field__hint">At least 8 characters.</span>
              )}
            </div>

            <button type="submit" className="btn btn--primary btn--lg" disabled={busy}>
              {busy ? <span className="spinner" /> : mode === 'signin' ? 'Sign in' : 'Create account'}
            </button>
          </form>

          <div className="notice-inline">
            <LockIcon size={15} />
            Passwords are stored as BCrypt hashes. Sessions use signed JWTs.
          </div>

          <div className="tiny muted">
            Just browsing? <Link to="/shop" style={{ color: 'var(--brand-strong)' }}>Continue as a guest</Link> —
            guest carts and conversations work too.
          </div>
        </div>

        <aside className="card summary">
          <div className="row" style={{ gap: 8 }}>
            <SparkIcon size={16} />
            <strong style={{ fontSize: '0.94rem' }}>Demo accounts</strong>
          </div>
          <p className="small muted">Seeded for this environment. Click one to fill the form.</p>
          <div className="stack" style={{ gap: 10 }}>
            {DEMO_ACCOUNTS.map((account) => (
              <button
                type="button"
                key={account.email}
                className="card card--pad"
                style={{ textAlign: 'left', cursor: 'pointer' }}
                onClick={() => useDemo(account)}
              >
                <div style={{ fontWeight: 620, fontSize: '0.88rem' }}>{account.label}</div>
                <div className="tiny mono muted">{account.email}</div>
                <div className="tiny muted" style={{ marginTop: 2 }}>
                  {account.note}
                </div>
              </button>
            ))}
          </div>
        </aside>
      </div>
    </div>
  )
}
