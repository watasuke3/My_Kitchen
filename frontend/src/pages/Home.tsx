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
    <div style={styles.container}>
      <h1 style={styles.heading}>🍳 My Kitchen</h1>
      <p style={styles.sub}>ユーザーID: {userId}</p>

      <div style={styles.menu}>
        <button style={styles.menuBtn} onClick={() => navigate('/recipes')}>
          📋 レシピ一覧
        </button>
        <button style={styles.menuBtn} onClick={() => navigate('/recipes/new')}>
          ✨ レシピを追加
        </button>
      </div>

      <button style={styles.logoutBtn} onClick={handleLogout}>ログアウト</button>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { maxWidth: 480, margin: '80px auto', padding: '0 16px', textAlign: 'center' },
  heading:   { fontSize: 32, marginBottom: 8 },
  sub:       { color: '#888', marginBottom: 32 },
  menu:      { display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 40 },
  menuBtn:   { padding: '14px 0', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: 8, fontSize: 16, cursor: 'pointer' },
  logoutBtn: { background: 'none', border: 'none', color: '#aaa', cursor: 'pointer', fontSize: 14 },
}
