import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchRecipe, deleteRecipe, type Recipe } from '../api/recipes'

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

  if (loading) return <div className="page center-state">読み込み中...</div>
  if (error)   return <div className="page center-state">エラー: {error}</div>
  if (!recipe) return <div className="page center-state">レシピが見つかりません</div>

  return (
    <div className="page page--narrow">
      <button className="breadcrumb-back" onClick={() => navigate('/recipes')}>← 一覧へ戻る</button>

      <div className="card card--pad">
        <div className="detail-card-header">
          <span className={`badge ${CATEGORY_BADGE_CLASS[recipe.category] ?? 'badge--other'}`}>
            {recipe.category}
          </span>
          <span className="meta">
            {recipe.cookTimeMinutes !== null ? `⏱ ${recipe.cookTimeMinutes}分 ・ ` : ''}👤 {recipe.servings}人前
          </span>
        </div>

        <h1 className="detail-title">{recipe.title}</h1>

        {recipe.description && (
          <p className="detail-description">{recipe.description}</p>
        )}

        {recipe.ingredients.length > 0 && (
          <div className="detail-ingredients">
            <h2>🧂 具材</h2>
            <ul>
              {recipe.ingredients.map((i) => (
                <li key={i.id}>
                  {i.name}
                  {(i.amount !== null || i.unit !== null) &&
                    ` ${i.amount ?? ''}${i.unit ?? ''}`}
                </li>
              ))}
            </ul>
          </div>
        )}

        <p className="timestamp">
          作成: {new Date(recipe.createdAt).toLocaleString('ja-JP')}
          {recipe.updatedAt !== recipe.createdAt &&
            `　更新: ${new Date(recipe.updatedAt).toLocaleString('ja-JP')}`}
        </p>
      </div>

      <div className="form-actions" style={{ justifyContent: 'flex-start', marginTop: 20 }}>
        <button className="btn btn--primary" onClick={() => navigate(`/recipes/${id}/edit`)}>
          ✏️ 編集する
        </button>
        <button className="btn btn--danger" onClick={handleDelete}>
          🗑 削除する
        </button>
      </div>
    </div>
  )
}
