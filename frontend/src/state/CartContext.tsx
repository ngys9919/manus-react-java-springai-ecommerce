import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import * as api from '../lib/api'
import { ApiError } from '../lib/api'
import type { CartView } from '../types'
import { useToast } from './ToastContext'

interface CartContextValue {
  cart: CartView | null
  loading: boolean
  error: string | null
  drawerOpen: boolean
  openDrawer: () => void
  closeDrawer: () => void
  refresh: () => Promise<void>
  add: (input: { productId?: number; sku?: string; quantity?: number; name?: string }) => Promise<boolean>
  setQuantity: (productId: number, quantity: number) => Promise<void>
  remove: (productId: number, name?: string) => Promise<void>
  clear: () => Promise<void>
}

const CartContext = createContext<CartContextValue | null>(null)

export function CartProvider({ children }: { children: ReactNode }) {
  const [cart, setCart] = useState<CartView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const { push } = useToast()
  const inFlight = useRef(false)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const next = await api.fetchCart()
      setCart(next)
      setError(null)
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Could not load your cart.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const add = useCallback<CartContextValue['add']>(
    async (input) => {
      if (inFlight.current) return false
      inFlight.current = true
      try {
        const next = await api.addToCart({
          productId: input.productId,
          sku: input.sku,
          quantity: input.quantity ?? 1,
        })
        setCart(next)
        push({
          tone: 'success',
          title: 'Added to cart',
          body: input.name ? `${input.name} is in your cart.` : undefined,
        })
        setDrawerOpen(true)
        return true
      } catch (cause) {
        push({
          tone: 'error',
          title: 'Could not add that',
          body: cause instanceof ApiError ? cause.message : 'Please try again.',
        })
        return false
      } finally {
        inFlight.current = false
      }
    },
    [push],
  )

  const setQuantity = useCallback<CartContextValue['setQuantity']>(
    async (productId, quantity) => {
      const previous = cart
      try {
        const next = await api.updateCartItem(productId, quantity)
        setCart(next)
      } catch (cause) {
        setCart(previous ?? null)
        push({
          tone: 'error',
          title: 'Could not update the cart',
          body: cause instanceof ApiError ? cause.message : undefined,
        })
      }
    },
    [cart, push],
  )

  const remove = useCallback<CartContextValue['remove']>(
    async (productId, name) => {
      try {
        const next = await api.removeCartItem(productId)
        setCart(next)
        push({ tone: 'info', title: 'Removed', body: name ? `${name} was removed.` : undefined })
      } catch (cause) {
        push({
          tone: 'error',
          title: 'Could not remove that',
          body: cause instanceof ApiError ? cause.message : undefined,
        })
      }
    },
    [push],
  )

  const clear = useCallback(async () => {
    try {
      setCart(await api.clearCart())
    } catch (cause) {
      push({ tone: 'error', title: 'Could not empty the cart', body: cause instanceof ApiError ? cause.message : undefined })
    }
  }, [push])

  const value = useMemo(
    () => ({
      cart,
      loading,
      error,
      drawerOpen,
      openDrawer: () => setDrawerOpen(true),
      closeDrawer: () => setDrawerOpen(false),
      refresh,
      add,
      setQuantity,
      remove,
      clear,
    }),
    [cart, loading, error, drawerOpen, refresh, add, setQuantity, remove, clear],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext)
  if (!context) {
    throw new Error('useCart must be used inside CartProvider')
  }
  return context
}
