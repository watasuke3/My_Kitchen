import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'

interface AuthState {
  userId: number | null
  loading: boolean
}

interface AuthContextValue extends AuthState {
  setUserId: (id: number | null) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ userId: null, loading: true })

  useEffect(() => {
    fetch('http://localhost:9000/api/v1/auth/me', { credentials: 'include' })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setState({ userId: data?.userId ?? null, loading: false }))
      .catch(() => setState({ userId: null, loading: false }))
  }, [])

  const setUserId = (id: number | null) => setState((s) => ({ ...s, userId: id }))

  return <AuthContext.Provider value={{ ...state, setUserId }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
