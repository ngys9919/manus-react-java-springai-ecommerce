import { useEffect, useState } from 'react'
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom'
import { ToastProvider } from './state/ToastContext'
import { AuthProvider } from './state/AuthContext'
import { CartProvider } from './state/CartContext'
import { AssistantProvider } from './state/AssistantContext'
import { Header } from './components/Header'
import { Footer } from './components/Footer'
import { CartDrawer } from './components/CartDrawer'
import { ToastStack } from './components/ToastStack'
import { AssistantLauncher } from './components/assistant/AssistantLauncher'
import { AssistantPanel } from './components/assistant/AssistantPanel'
import { Home } from './pages/Home'
import { Shop } from './pages/Shop'
import { ProductDetailPage } from './pages/ProductDetailPage'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { OrdersPage } from './pages/OrdersPage'
import { LoginPage } from './pages/LoginPage'
import { InsightsPage } from './pages/InsightsPage'
import { NotFoundPage } from './pages/NotFoundPage'

/** Maps the current route to the page context the assistant is told about. */
function usePageContext(): string {
  const { pathname } = useLocation()
  if (pathname.startsWith('/product/')) return 'product'
  if (pathname.startsWith('/shop')) return 'shop'
  if (pathname.startsWith('/cart')) return 'cart'
  if (pathname.startsWith('/checkout')) return 'checkout'
  if (pathname.startsWith('/orders')) return 'orders'
  if (pathname.startsWith('/insights')) return 'insights'
  return 'home'
}

function Shell() {
  const pageContext = usePageContext()
  const [seedPrompt, setSeedPrompt] = useState<string | null>(null)

  // Expose a tiny bridge so any page can push a question into the assistant.
  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<string>).detail
      if (typeof detail === 'string' && detail.trim()) {
        setSeedPrompt(detail)
      }
    }
    window.addEventListener('shopwise:ask', handler as EventListener)
    return () => window.removeEventListener('shopwise:ask', handler as EventListener)
  }, [])

  return (
    <div className="app-shell">
      <Header />
      <main className="grow">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/shop" element={<Shop />} />
          <Route path="/product/:slug" element={<ProductDetailPage />} />
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:reference" element={<OrdersPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/insights" element={<InsightsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <Footer />

      <CartDrawer />
      <AssistantLauncher />
      <AssistantPanel
        pageContext={pageContext}
        seedPrompt={seedPrompt}
        onSeedConsumed={() => setSeedPrompt(null)}
      />
      <ToastStack />
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <CartProvider>
            <AssistantProvider>
              <Shell />
            </AssistantProvider>
          </CartProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
