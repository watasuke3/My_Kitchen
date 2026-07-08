import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchRecipe, deleteRecipe, type Recipe } from '../api/recipes'

export default function RecipeDetail() {
  const { id }    = useParams<{ id: string }>()
  const navigate  = useNavigate()
  const [recipe, setRecipe]   = useState<Recipe | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)

  useEffect(() => {
    fetchRecipe(Number(id))
      .then(setRecipe)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id])

  async function handleDelete() {
    if (!confirm('このレシピを削除しますか？')) return
    try {
      await deleteRecipe(Number(id))
      navigate('/recipes')
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '削除に失敗しました')
    }
  }

  if (loading) return <div style={styles.center}>読み込み中...</div>
  if (error)   return <div style={styles.center}>エラー: {error}</div>
  if (!recipe) return <div style={styles.center}>レシピが見つかりません</div>

  return (
    <div style={styles.container}>
      <button style={styles.backBtn} onClick={() => navigate('/recipes')}>← 一覧へ</button>

      <div style={styles.card}>
        <div style={styles.cardHeader}>
          <span style={styles.badge}>{recipe.category}</span>
          <span style={styles.meta}>⏱ {recipe.cookTimeMinutes}分 / 👤 {recipe.servings}人前</span>
        </div>

        <h1 style={styles.title}>{recipe.title}</h1>

        {recipe.description && (
          <p style={styles.description}>{recipe.description}</p>
        )}

        <p style={styles.timestamp}>
          作成: {new Date(recipe.createdAt).toLocaleString('ja-JP')}
          {recipe.updatedAt !== recipe.createdAt &&
            `　更新: ${new Date(recipe.updatedAt).toLocaleString('ja-JP')}`}
        </p>
      </div>

      <div style={styles.actions}>
        <button style={styles.editBtn} onClick={() => navigate(`/recipes/${id}/edit`)}>
          ✏️ 編集
        </button>
        <button style={styles.deleteBtn} onClick={handleDelete}>
          🗑 削除
        </button>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container:   { maxWidth: 640, margin: '40px auto', padding: '0 16px' },
  center:      { textAlign: 'center', marginTop: 80 },
  backBtn:     { background: 'none', border: 'none', color: '#888', cursor: 'pointer', fontSize: 14, marginBottom: 16 },
  card:        { border: '1px solid #e0e0e0', borderRadius: 10, padding: 24, background: '#fff' },
  cardHeader:  { display: 'flex', justifyContent: 'space-between', marginBottom: 12 },
  badge:       { fontSize: 13, background: '#f0f4ff', borderRadius: 4, padding: '2px 10px', color: '#4f46e5' },
  meta:        { fontSize: 13, color: '#888' },
  title:       { fontSize: 24, margin: '0 0 16px' },
  description: { fontSize: 15, color: '#444', whiteSpace: 'pre-wrap', lineHeight: 1.7 },
  timestamp:   { fontSize: 12, color: '#aaa', marginTop: 20 },
  actions:     { display: 'flex', gap: 12, marginTop: 20 },
  editBtn:     { padding: '10px 24px', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer' },
  deleteBtn:   { padding: '10px 24px', background: '#fff', color: '#dc2626', border: '1px solid #fca5a5', borderRadius: 6, cursor: 'pointer' },
}
