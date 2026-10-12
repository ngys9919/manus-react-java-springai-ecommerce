import { Link } from 'react-router-dom'
import { useAssistant } from '../state/AssistantContext'
import { SparkIcon } from './Icons'

export function Footer() {
  const { setOpen, onboarding, status } = useAssistant()

  return (
    <footer className="footer">
      <div className="footer__inner">
        <div>
          <div className="logo" style={{ marginBottom: 6 }}>
            <span className="logo__mark">S</span>
            Shopwise
          </div>
          <div className="tiny">
            A demo storefront: Vite + React on the front, Spring Boot 4 and Spring AI on the back.
          </div>
        </div>

        <div className="row row--wrap" style={{ gap: 18 }}>
          <Link to="/shop" className="nav__link">
            Shop
          </Link>
          <Link to="/orders" className="nav__link">
            Track an order
          </Link>
          <button type="button" className="nav__link" onClick={() => setOpen(true)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            Shopping assistant
          </button>
        </div>

        <div className="tiny" style={{ maxWidth: 260 }}>
          <div className="row" style={{ gap: 6 }}>
            <SparkIcon size={14} />
            {status?.available === false ? 'Assistant running in offline mode' : `Assistant on ${status?.model ?? 'standby'}`}
          </div>
          <div style={{ marginTop: 4 }}>
            Support: <a href={`mailto:${onboarding?.supportEmail ?? 'support@shopwise.dev'}`}>{onboarding?.supportEmail ?? 'support@shopwise.dev'}</a>
          </div>
        </div>
      </div>
    </footer>
  )
}
