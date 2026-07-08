import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchRecipes, deleteRecipe, type Recipe } from '../api/recipes'

const CATEGORY_LABELS: Record<string, string> = {
  朝食: '🌅 朝食',
  昼食: '☀️ 昼食',
  夕食: '🌙 夕食',
  おやつ: '🍩 おやつ',
  その他: '📌 その他',
}

export default function RecipeList() {
  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    fetchRecipes()
      .then(setRecipes)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  async function handleDelete(id: number) {
    if (!confirm('このレシピを削除しますか？')) return
    try {
      await deleteRecipe(id)
      setRecipes((prev) => prev.filter((r) => r.id !== id))
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '削除に失敗しました')
    }
  }

  if (loading) return <div style={styles.center}>読み込み中...</div>
  if (error)   return <div style={styles.center}>エラー: {error}</div>

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1 style={{ margin: 0 }}>🍳 レシピ一覧</h1>
        <button style={styles.primaryBtn} onClick={() => navigate('/recipes/new')}>
          + 新規作成
        </button>
      </div>

      {recipes.length === 0 ? (
        <p style={{ color: '#888' }}>レシピがまだありません。最初のレシピを追加しましょう！</p>
      ) : (
        <div style={styles.grid}>
          {recipes.map((r) => (
            <div key={r.id} style={styles.card}>
              <div style={styles.cardTop}>
                <span style={styles.badge}>{CATEGORY_LABELS[r.category] ?? r.category}</span>
                <span style={styles.meta}>⏱ {r.cookTimeMinutes}分 / {r.servings}人前</span>
              </div>
              <h2 style={styles.cardTitle}>{r.title}</h2>
              {r.description && <p style={styles.cardDesc}>{r.description}</p>}
              <div style={styles.cardActions}>
                <button style={styles.secondaryBtn} onClick={() => navigate(`/recipes/${r.id}`)}>
                  詳細・編集
                </button>
                <button style={styles.dangerBtn} onClick={() => handleDelete(r.id)}>
                  削除
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <button style={styles.backBtn} onClick={() => navigate('/')}>← ホームへ</button>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { maxWidth: 800, margin: '40px auto', padding: '0 16px' },
  center:    { textAlign: 'center', marginTop: 80 },
  header:    { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 },
  grid:      { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 },
  card:      { border: '1px solid #e0e0e0', borderRadius: 8, padding: 16, background: '#fff' },
  cardTop:   { display: 'flex', justifyContent: 'space-between', marginBottom: 8 },
  badge:     { fontSize: 12, background: '#f0f4ff', borderRadius: 4, padding: '2px 8px' },
  meta:      { fontSize: 12, color: '#888' },
  cardTitle: { margin: '4px 0 8px', fontSize: 18 },
  cardDesc:  { fontSize: 14, color: '#555', margin: '0 0 12px', whiteSpace: 'pre-wrap' },
  cardActions: { display: 'flex', gap: 8 },
  primaryBtn:  { padding: '8px 16px', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer' },
  secondaryBtn:{ padding: '6px 12px', background: '#f0f4ff', color: '#4f46e5', border: '1px solid #c7d2fe', borderRadius: 6, cursor: 'pointer' },
  dangerBtn:   { padding: '6px 12px', background: '#fff', color: '#dc2626', border: '1px solid #fca5a5', borderRadius: 6, cursor: 'pointer' },
  backBtn:     { marginTop: 32, background: 'none', border: 'none', color: '#888', cursor: 'pointer', fontSize: 14 },
}
