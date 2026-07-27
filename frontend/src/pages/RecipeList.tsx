import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchRecipes, deleteRecipe, type Recipe } from '../api/recipes'

const CATEGORY_LABELS: Record<string, string> = {
  朝食: '🌅 朝食',
  昼食: '☀️ 昼食',
  夕食: '🌙 夕食',
  おやつ: '🍩 おやつ',
  和食: '🍚 和食',
  中華: '🥟 中華',
  洋食: '🍝 洋食',
  その他: '📌 その他',
}

const CATEGORY_BADGE_CLASS: Record<string, string> = {
  朝食: 'badge--breakfast',
  昼食: 'badge--lunch',
  夕食: 'badge--dinner',
  おやつ: 'badge--snack',
  和食: 'badge--japanese',
  中華: 'badge--chinese',
  洋食: 'badge--western',
  その他: 'badge--other',
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

  if (loading) return <div className="page center-state">読み込み中...</div>
  if (error)   return <div className="page center-state">エラー: {error}</div>

  return (
    <div className="page">
      <button className="breadcrumb-back" onClick={() => navigate('/')}>← ホームへ戻る</button>

      <div className="list-header">
        <h1>🍳 レシピ一覧</h1>
        <button className="btn btn--primary" onClick={() => navigate('/recipes/new')}>
          + 新規作成
        </button>
      </div>

      {recipes.length === 0 ? (
        <div className="empty-state">レシピがまだありません。最初のレシピを追加しましょう！</div>
      ) : (
        <div className="recipe-grid">
          {recipes.map((r) => (
            <div
              key={r.id}
              className="card recipe-card"
              role="button"
              tabIndex={0}
              onClick={() => navigate(`/recipes/${r.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') navigate(`/recipes/${r.id}`)
              }}
            >
              <button
                className="btn btn--icon btn--danger recipe-card-delete"
                onClick={(e) => {
                  e.stopPropagation()
                  handleDelete(r.id)
                }}
                aria-label="削除"
              >
                🗑
              </button>
              <div className="recipe-card-top">
                <span className={`badge ${CATEGORY_BADGE_CLASS[r.category] ?? 'badge--other'}`}>
                  {CATEGORY_LABELS[r.category] ?? r.category}
                </span>
              </div>
              <h2>{r.title}</h2>
              {r.description && <p className="desc">{r.description}</p>}
              <span className="meta">⏱ {r.cookTimeMinutes}分 ・ 👤 {r.servings}人前</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
