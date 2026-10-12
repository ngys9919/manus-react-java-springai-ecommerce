import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as api from '../lib/api'
import type { UserView } from '../types'

interface AuthContextValue {
  user: UserView | null
  ready: boolean
  signIn: (email: string, password: string) => Promise<UserView>
  signUp: (fullName: string, email: string, password: string) => Promise<UserView>
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserView | null>(null)
  const [ready, setReady] = useState(false)

  // Restore the session on first paint so the header does not flicker.
  useEffect(() => {
    let cancelled = false
    if (!api.authToken()) {
      setReady(true)
      return
    }
    api
      .fetchMe()
      .then((me) => {
        if (!cancelled) setUser(me)
      })
      .catch(() => {
        api.setAuthToken(null)
      })
      .finally(() => {
        if (!cancelled) setReady(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await api.login(email, password)
    api.setAuthToken(response.token)
    setUser(response.user)
    return response.user
  }, [])

  const signUp = useCallback(async (fullName: string, email: string, password: string) => {
    const response = await api.register(fullName, email, password)
    api.setAuthToken(response.token)
    setUser(response.user)
    return response.user
  }, [])

  const signOut = useCallback(() => {
    api.setAuthToken(null)
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, ready, signIn, signUp, signOut }), [user, ready, signIn, signUp, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
