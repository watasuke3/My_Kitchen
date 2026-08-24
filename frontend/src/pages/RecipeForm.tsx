import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createRecipe, updateRecipe, fetchRecipe, type RecipeInput } from '../api/recipes'

const CATEGORIES = ['朝食', '昼食', '夕食', 'おやつ', '和食', '中華', '洋食', 'その他']

interface IngredientRow {
  name: string
  amount: string
  unit: string
}

const emptyIngredientRow: IngredientRow = { name: '', amount: '', unit: '' }

export default function RecipeForm() {
  const { id } = useParams<{ id?: string }>()
  const isEdit  = id !== undefined
  const navigate = useNavigate()

  const [title, setTitle]               = useState('')
  const [description, setDescription]   = useState('')
  const [category, setCategory]         = useState('その他')
  const [servings, setServings]         = useState(2)
  const [cookTime, setCookTime]         = useState('')
  const [ingredients, setIngredients]   = useState<IngredientRow[]>([{ ...emptyIngredientRow }])
  const [loading, setLoading]           = useState(isEdit)
  const [submitting, setSubmitting]     = useState(false)
  const [error, setError]               = useState<string | null>(null)

  useEffect(() => {
    if (!isEdit) return
    fetchRecipe(Number(id))
      .then((r) => {
        setTitle(r.title)
        setDescription(r.description ?? '')
        setCategory(r.category)
        setServings(r.servings)
        setCookTime(r.cookTimeMinutes !== null ? String(r.cookTimeMinutes) : '')
        setIngredients(
          r.ingredients.length > 0
            ? r.ingredients.map((i) => ({
                name: i.name,
                amount: i.amount !== null ? String(i.amount) : '',
                unit: i.unit ?? '',
              }))
            : [{ ...emptyIngredientRow }]
        )
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id, isEdit])

  function updateIngredient(index: number, field: keyof IngredientRow, value: string) {
    setIngredients((prev) => prev.map((row, i) => (i === index ? { ...row, [field]: value } : row)))
  }

  function addIngredientRow() {
    setIngredients((prev) => [...prev, { ...emptyIngredientRow }])
  }

  function removeIngredientRow(index: number) {
    setIngredients((prev) => prev.filter((_, i) => i !== index))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim()) { setError('タイトルは必須です'); return }
    setSubmitting(true)
    setError(null)

    const input: RecipeInput = {
      title: title.trim(),
      description: description.trim() || undefined,
      category,
      servings,
      cookTimeMinutes: cookTime.trim() === '' ? undefined : Number(cookTime),
      ingredients: ingredients
        .filter((row) => row.name.trim() !== '')
        .map((row) => ({
          name: row.name.trim(),
          amount: row.amount.trim() === '' ? undefined : Number(row.amount),
          unit: row.unit.trim() || undefined,
        })),
    }

    try {
      if (isEdit) {
        await updateRecipe(Number(id), input)
        navigate(`/recipes/${id}`)
      } else {
        const created = await createRecipe(input)
        navigate(`/recipes/${created.id}`)
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '保存に失敗しました')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <div className="page center-state">読み込み中...</div>

  return (
    <div className="page page--narrow">
      <button className="breadcrumb-back" onClick={() => navigate(-1)}>← 戻る</button>

      <h1 style={{ marginBottom: 20 }}>{isEdit ? '✏️ レシピを編集' : '✨ 新しいレシピ'}</h1>

      <div className="card card--pad">
        {error && <div className="form-error" style={{ marginBottom: 16 }}>{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="recipe-title">タイトル *</label>
            <input
              id="recipe-title"
              className="input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="例: 肉じゃが"
              maxLength={255}
            />
          </div>

          <div className="field">
            <label htmlFor="recipe-description">説明</label>
            <textarea
              id="recipe-description"
              className="textarea"
              style={{ height: 90 }}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="作り方のメモなど（任意）"
            />
          </div>

          <div className="form-row">
            <div className="field">
              <label htmlFor="recipe-category">カテゴリ</label>
              <select
                id="recipe-category"
                className="select"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
              </select>
            </div>

            <div className="field">
              <label htmlFor="recipe-servings">人数</label>
              <input
                id="recipe-servings"
                className="input" type="number" min={1} max={20}
                value={servings} onChange={(e) => setServings(Number(e.target.value))}
              />
            </div>

            <div className="field">
              <label htmlFor="recipe-cooktime">調理時間 (分・任意)</label>
              <input
                id="recipe-cooktime"
                className="input" type="number" min={1} max={999}
                value={cookTime}
                onChange={(e) => setCookTime(e.target.value)}
                placeholder="未定なら空欄でOK"
              />
            </div>
          </div>

          <div className="field">
            <label>具材（任意）</label>
            {ingredients.map((row, index) => (
              <div className="form-row" key={index} style={{ marginBottom: 8 }}>
                <input
                  className="input"
                  style={{ flex: 2 }}
                  value={row.name}
                  onChange={(e) => updateIngredient(index, 'name', e.target.value)}
                  placeholder="例: じゃがいも"
                  aria-label="具材名"
                />
                <input
                  className="input"
                  style={{ flex: 1 }}
                  value={row.amount}
                  onChange={(e) => updateIngredient(index, 'amount', e.target.value)}
                  placeholder="量 例: 200"
                  type="number"
                  aria-label="分量"
                />
                <input
                  className="input"
                  style={{ flex: 1 }}
                  value={row.unit}
                  onChange={(e) => updateIngredient(index, 'unit', e.target.value)}
                  placeholder="単位 例: g"
                  aria-label="単位"
                />
                <button
                  type="button"
                  className="btn btn--icon btn--danger"
                  onClick={() => removeIngredientRow(index)}
                  aria-label="この具材を削除"
                >
                  🗑
                </button>
              </div>
            ))}
            <button type="button" className="btn btn--outline" onClick={addIngredientRow}>
              + 具材を追加
            </button>
          </div>

          <div className="form-actions">
            <button type="button" className="btn btn--outline" onClick={() => navigate(-1)}>
              キャンセル
            </button>
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? '保存中...' : isEdit ? '更新する' : '作成する'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
