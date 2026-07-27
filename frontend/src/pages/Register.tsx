import { type FormEvent, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import PasswordField from '../components/PasswordField'

export default function Register() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const { setUserId } = useAuth()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const res = await fetch('http://localhost:9000/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      })

      const data = await res.json()

      if (!res.ok) {
        setError(data.error ?? '登録に失敗しました')
        return
      }

      const meRes = await fetch('http://localhost:9000/api/v1/auth/me', { credentials: 'include' })
      const me = await meRes.json()
      setUserId(me.userId)
      navigate('/')
    } catch {
      setError('通信エラーが発生しました')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-side auth-side--register">
        <span className="auth-icon">🌱</span>
        <h2>はじめまして</h2>
        <p>アカウントを作って、自分だけの献立とレシピ帳を育てていきましょう。</p>
      </div>

      <div className="auth-form-panel">
        <div>
          <h1>新規登録</h1>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="register-email">メールアドレス</label>
            <input
              id="register-email"
              className="input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </div>

          <PasswordField
            id="register-password"
            label="パスワード"
            value={password}
            onChange={setPassword}
            hint="8文字以上・英数字を含めてください"
          />

          {error && <p className="form-error">{error}</p>}

          <button type="submit" className="btn btn--secondary btn--block" disabled={loading}>
            {loading ? '処理中...' : 'アカウントを作成する'}
          </button>
        </form>

        <p className="auth-footnote">
          すでにアカウントをお持ちの方は <Link to="/login">ログインはこちら</Link>
        </p>
      </div>
    </div>
  )
}
