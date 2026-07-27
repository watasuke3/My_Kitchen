import { Navigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { type ReactNode } from 'react'

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { userId, loading } = useAuth()

  if (loading) return <div className="page center-state">読み込み中...</div>
  if (userId === null) return <Navigate to="/login" replace />
  return <>{children}</>
}
