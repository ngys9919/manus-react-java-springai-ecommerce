import { useAssistant } from '../../state/AssistantContext'
import { SparkIcon } from '../Icons'

export function AssistantLauncher() {
  const { open, setOpen, status, messages } = useAssistant()
  if (open) return null

  const available = status?.available !== false

  return (
    <button
      type="button"
      className="assistant-launcher"
      onClick={() => setOpen(true)}
      aria-label="Open the shopping assistant"
    >
      <span className={`assistant-launcher__badge${available ? '' : ' assistant-launcher__badge--offline'}`} />
      <SparkIcon size={18} />
      {messages.length > 0 ? 'Continue chat' : 'Ask the assistant'}
    </button>
  )
}
