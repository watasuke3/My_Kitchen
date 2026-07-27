import { type FormEvent, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import PasswordField from '../components/PasswordField'

export default function Login() {
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
      const res = await fetch('http://localhost:9000/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      })

      const data = await res.json()

      if (!res.ok) {
        setError(data.error ?? 'ログインに失敗しました')
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
      <div className="auth-side auth-side--login">
        <span className="auth-icon">🍳</span>
        <h2>おかえりなさい</h2>
        <p>今日は何を作りましょうか。ログインして、あなたの献立とレシピを続けましょう。</p>
      </div>

      <div className="auth-form-panel">
        <div>
          <h1>ログイン</h1>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="login-email">メールアドレス</label>
            <input
              id="login-email"
              className="input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </div>

          <PasswordField id="login-password" label="パスワード" value={password} onChange={setPassword} />

          {error && <p className="form-error">{error}</p>}

          <button type="submit" className="btn btn--primary btn--block" disabled={loading}>
            {loading ? '処理中...' : 'ログインする'}
          </button>
        </form>

        <p className="auth-footnote">
          アカウントをお持ちでない方は <Link to="/register">新規登録はこちら</Link>
        </p>
      </div>
    </div>
  )
}
