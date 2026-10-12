import { useToast } from '../state/ToastContext'
import { AlertIcon, CheckIcon, CloseIcon, InfoIcon } from './Icons'

export function ToastStack() {
  const { toasts, dismiss } = useToast()
  if (toasts.length === 0) return null

  return (
    <div className="toast-stack" aria-live="polite" aria-atomic="false">
      {toasts.map((toast) => (
        <div className={`toast toast--${toast.tone}`} key={toast.id} role={toast.tone === 'error' ? 'alert' : 'status'}>
          <div style={{ flexShrink: 0, marginTop: 1 }}>
            {toast.tone === 'success' ? <CheckIcon size={16} /> : toast.tone === 'error' ? <AlertIcon size={16} /> : <InfoIcon size={16} />}
          </div>
          <div className="grow">
            <div className="toast__title">{toast.title}</div>
            {toast.body ? <div className="toast__body">{toast.body}</div> : null}
          </div>
          <button
            type="button"
            className="icon-btn"
            style={{ width: 24, height: 24, border: 'none', background: 'transparent' }}
            onClick={() => dismiss(toast.id)}
            aria-label="Dismiss notification"
          >
            <CloseIcon size={14} />
          </button>
        </div>
      ))}
    </div>
  )
}
