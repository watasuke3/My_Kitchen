import { useAuth } from '../contexts/AuthContext'
import { useNavigate } from 'react-router-dom'

export default function Home() {
  const { userId, setUserId } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await fetch('http://localhost:9000/api/v1/auth/logout', {
      method: 'POST',
      credentials: 'include',
    })
    setUserId(null)
    navigate('/login')
  }

  return (
    <div className="page">
      <div className="top-bar">
        <span className="brand">
          <span className="brand-mark">🍳</span>
          My Kitchen
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span className="user-chip">👤 ID: {userId}</span>
          <button className="btn btn--ghost" onClick={handleLogout}>ログアウト</button>
        </div>
      </div>

      <div className="hero">
        <h1>今日は、何を作りましょう？</h1>
        <p>レシピを見返したり、新しいひと皿を書き留めたりしてみましょう。</p>
      </div>

      <div className="nav-tiles">
        <button className="nav-tile" onClick={() => navigate('/recipes')}>
          <span className="tile-icon">📖</span>
          <h3>レシピ一覧</h3>
          <p>これまで登録したレシピを見る</p>
          <span className="tile-arrow">見る →</span>
        </button>

        <button className="nav-tile nav-tile--secondary" onClick={() => navigate('/recipes/new')}>
          <span className="tile-icon">✨</span>
          <h3>レシピを追加</h3>
          <p>新しいレシピを書き留める</p>
          <span className="tile-arrow">追加する →</span>
        </button>
      </div>
    </div>
  )
}
