import type { QuickReply } from '../../types'
import type { UiMessage } from '../../state/AssistantContext'
import { BlockRenderer, QuickReplies } from './BlockRenderer'
import { SparkIcon, ThumbDownIcon, ThumbUpIcon } from '../Icons'
import { classNames, relativeTime } from '../../lib/format'

interface Props {
  messages: UiMessage[]
  sending: boolean
  quickReplies: QuickReply[]
  onQuickReply: (reply: QuickReply) => void
  onRate: (message: UiMessage, rating: 'up' | 'down') => void
}

export function MessageList({ messages, sending, quickReplies, onQuickReply, onRate }: Props) {
  const lastAssistantIndex = findLastAssistant(messages)

  // Suggestions already rendered inside a block must not appear twice.
  const shownInBlocks = new Set(
    messages
      .flatMap((message) => message.blocks ?? [])
      .flatMap((block) => block.actions ?? [])
      .map((reply) => reply.label),
  )
  const trailingReplies = quickReplies.filter((reply) => !shownInBlocks.has(reply.label))

  return (
    <>
      {messages.map((message, index) => (
        <MessageBubble
          key={`${index}-${message.id ?? message.createdAt ?? index}`}
          message={message}
          isLastAssistant={index === lastAssistantIndex}
          onQuickReply={onQuickReply}
          onRate={onRate}
        />
      ))}

      {sending ? (
        <div className="msg">
          <div className="msg__avatar" aria-hidden>
            <SparkIcon size={14} />
          </div>
          <div className="msg__content">
            <div className="bubble bubble--assistant" style={{ padding: 0 }}>
              <span className="typing" aria-label="The assistant is typing">
                <span />
                <span />
                <span />
              </span>
            </div>
          </div>
        </div>
      ) : null}

      {!sending && trailingReplies.length > 0 ? (
        <div className="msg">
          <div className="msg__avatar" aria-hidden>
            <SparkIcon size={14} />
          </div>
          <div className="msg__content">
            <QuickReplies replies={trailingReplies} onQuickReply={onQuickReply} />
          </div>
        </div>
      ) : null}
    </>
  )
}

function MessageBubble({
  message,
  isLastAssistant,
  onQuickReply,
  onRate,
}: {
  message: UiMessage
  isLastAssistant: boolean
  onQuickReply: (reply: QuickReply) => void
  onRate: (message: UiMessage, rating: 'up' | 'down') => void
}) {
  const isUser = message.role === 'USER'
  const isSystem = message.role === 'SYSTEM'
  const failed = message.state === 'failed'

  return (
    <div className={classNames('msg', isUser && 'msg--user')}>
      {!isUser ? (
        <div className="msg__avatar" aria-hidden>
          <SparkIcon size={14} />
        </div>
      ) : null}

      <div className="msg__content">
        {message.content ? (
          <div
            className={classNames(
              'bubble',
              isUser ? 'bubble--user' : isSystem ? 'bubble--system' : 'bubble--assistant',
              failed && 'bubble--failed',
            )}
          >
            {message.content}
          </div>
        ) : null}

        {message.blocks?.map((block, index) => (
          <BlockRenderer key={`${block.type}-${index}`} block={block} onQuickReply={onQuickReply} />
        ))}

        {!isUser && message.toolCalls?.length ? (
          <div className="msg__tools">
            {message.toolCalls.map((tool) => (
              <span className="tool-chip" key={`${tool.name}-${tool.label}`} title={tool.name}>
                <SparkIcon size={11} />
                {tool.label ?? tool.name}
              </span>
            ))}
          </div>
        ) : null}

        {!isUser && !failed ? (
          <div className="msg__meta">
            {message.createdAt ? <span>{relativeTime(message.createdAt)}</span> : null}
            {message.intent ? <span className="pill tiny" style={{ padding: '2px 8px' }}>{message.intent.replace(/_/g, ' ')}</span> : null}
            {message.model && message.model !== 'deterministic-fallback' ? <span>{message.model}</span> : null}
            {message.model === 'deterministic-fallback' ? <span>offline answer</span> : null}

            {isLastAssistant && message.id ? (
              <span className="row" style={{ gap: 6 }}>
                <button
                  type="button"
                  className={classNames('thumb', message.feedback === 'up' && 'thumb--on')}
                  onClick={() => onRate(message, 'up')}
                  aria-label="This answer was helpful"
                >
                  <ThumbUpIcon size={13} />
                </button>
                <button
                  type="button"
                  className={classNames('thumb', message.feedback === 'down' && 'thumb--on')}
                  onClick={() => onRate(message, 'down')}
                  aria-label="This answer was not helpful"
                >
                  <ThumbDownIcon size={13} />
                </button>
              </span>
            ) : null}
          </div>
        ) : null}

        {failed ? <div className="tiny" style={{ color: 'var(--danger)' }}>Not delivered — use retry below.</div> : null}
      </div>
    </div>
  )
}

function findLastAssistant(messages: UiMessage[]): number {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'ASSISTANT') return index
  }
  return -1
}
