import { Link } from 'react-router-dom'
import { useAssistant } from '../state/AssistantContext'
import { EmptyState } from '../components/States'
import { SparkIcon } from '../components/Icons'

export function NotFoundPage() {
  const { setOpen } = useAssistant()

  return (
    <div className="page">
      <EmptyState
        title="That page does not exist"
        text="The link may be out of date. Head back to the shop, or ask the assistant to find what you were looking for."
        actions={
          <>
            <Link to="/shop" className="btn btn--primary">
              Browse the catalogue
            </Link>
            <button type="button" className="btn btn--ghost" onClick={() => setOpen(true)}>
              <SparkIcon size={15} />
              Ask the assistant
            </button>
          </>
        }
      />
    </div>
  )
}
