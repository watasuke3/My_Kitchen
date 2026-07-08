import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createRecipe, updateRecipe, fetchRecipe, type RecipeInput } from '../api/recipes'

const CATEGORIES = ['朝食', '昼食', '夕食', 'おやつ', 'その他']

export default function RecipeForm() {
  const { id } = useParams<{ id?: string }>()
  const isEdit  = id !== undefined
  const navigate = useNavigate()

  const [title, setTitle]               = useState('')
  const [description, setDescription]   = useState('')
  const [category, setCategory]         = useState('その他')
  const [servings, setServings]         = useState(2)
  const [cookTime, setCookTime]         = useState(30)
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
        setCookTime(r.cookTimeMinutes)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id, isEdit])

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
      cookTimeMinutes: cookTime,
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

  if (loading) return <div style={styles.center}>読み込み中...</div>

  return (
    <div style={styles.container}>
      <h1>{isEdit ? '✏️ レシピを編集' : '✨ 新しいレシピ'}</h1>

      {error && <div style={styles.errorBox}>{error}</div>}

      <form onSubmit={handleSubmit} style={styles.form}>
        <label style={styles.label}>
          タイトル *
          <input
            style={styles.input}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="例: 肉じゃが"
            maxLength={255}
          />
        </label>

        <label style={styles.label}>
          説明
          <textarea
            style={{ ...styles.input, height: 80, resize: 'vertical' }}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="作り方のメモなど（任意）"
          />
        </label>

        <div style={styles.row}>
          <label style={{ ...styles.label, flex: 1 }}>
            カテゴリ
            <select style={styles.input} value={category} onChange={(e) => setCategory(e.target.value)}>
              {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
            </select>
          </label>

          <label style={{ ...styles.label, flex: 1 }}>
            人数
            <input
              style={styles.input} type="number" min={1} max={20}
              value={servings} onChange={(e) => setServings(Number(e.target.value))}
            />
          </label>

          <label style={{ ...styles.label, flex: 1 }}>
            調理時間 (分)
            <input
              style={styles.input} type="number" min={1} max={999}
              value={cookTime} onChange={(e) => setCookTime(Number(e.target.value))}
            />
          </label>
        </div>

        <div style={styles.actions}>
          <button type="button" style={styles.cancelBtn} onClick={() => navigate(-1)}>
            キャンセル
          </button>
          <button type="submit" style={styles.submitBtn} disabled={submitting}>
            {submitting ? '保存中...' : isEdit ? '更新する' : '作成する'}
          </button>
        </div>
      </form>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { maxWidth: 640, margin: '40px auto', padding: '0 16px' },
  center:    { textAlign: 'center', marginTop: 80 },
  errorBox:  { background: '#fef2f2', border: '1px solid #fca5a5', borderRadius: 6, padding: '8px 12px', marginBottom: 16, color: '#dc2626' },
  form:      { display: 'flex', flexDirection: 'column', gap: 16 },
  label:     { display: 'flex', flexDirection: 'column', gap: 4, fontSize: 14, fontWeight: 600 },
  input:     { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 14, fontWeight: 400 },
  row:       { display: 'flex', gap: 12 },
  actions:   { display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 8 },
  cancelBtn: { padding: '8px 20px', background: '#fff', border: '1px solid #d1d5db', borderRadius: 6, cursor: 'pointer' },
  submitBtn: { padding: '8px 24px', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer' },
}
