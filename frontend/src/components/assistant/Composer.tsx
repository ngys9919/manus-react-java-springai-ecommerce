import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { SendIcon } from '../Icons'

interface Props {
  disabled: boolean
  sending: boolean
  placeholder: string
  hint: string
  onSend: (text: string) => void
}

const MAX_LENGTH = 1200

export function Composer({ disabled, sending, placeholder, hint, onSend }: Props) {
  const [value, setValue] = useState('')
  const areaRef = useRef<HTMLTextAreaElement>(null)

  // Grow with the content up to the CSS max-height.
  useEffect(() => {
    const area = areaRef.current
    if (!area) return
    area.style.height = 'auto'
    area.style.height = `${Math.min(area.scrollHeight, 132)}px`
  }, [value])

  const submit = () => {
    const text = value.trim()
    if (!text || disabled || sending) return
    onSend(text)
    setValue('')
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <div>
      <div className="composer">
        <label className="visually-hidden" htmlFor="assistant-input">
          Message the shopping assistant
        </label>
        <textarea
          id="assistant-input"
          ref={areaRef}
          rows={1}
          value={value}
          maxLength={MAX_LENGTH}
          placeholder={placeholder}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={onKeyDown}
          disabled={disabled}
        />
        <button
          type="button"
          className="composer__send"
          onClick={submit}
          disabled={disabled || sending || value.trim().length === 0}
          aria-label="Send message"
        >
          {sending ? <span className="spinner" /> : <SendIcon size={15} />}
        </button>
      </div>
      <div className="composer__hint">
        <span>{hint}</span>
        <span className="mono">
          {value.length > 0 ? `${value.length}/${MAX_LENGTH}` : 'Enter to send'}
        </span>
      </div>
    </div>
  )
}
