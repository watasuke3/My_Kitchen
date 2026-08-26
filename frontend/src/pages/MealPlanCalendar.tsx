import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  fetchMealPlans, assignMealPlan, updateMealPlanStatus, deleteMealPlan,
  type MealPlan, type MealType, type MealPlanStatus,
} from '../api/mealPlans'
import { fetchRecipes, type Recipe } from '../api/recipes'

const MEAL_TYPES: { key: MealType; label: string }[] = [
  { key: 'breakfast', label: '🌅 朝食' },
  { key: 'lunch',     label: '☀️ 昼食' },
  { key: 'dinner',    label: '🌙 夕食' },
]

const STATUS_LABELS: Record<MealPlanStatus, string> = {
  planned: '予定',
  eaten:   '✅ 食べた',
  skipped: '⏭ 食べなかった',
}

function toISODate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

// 指定日を含む週の月曜日を返す
function startOfWeek(d: Date): Date {
  const copy = new Date(d)
  const day  = copy.getDay()
  const diff = day === 0 ? -6 : 1 - day
  copy.setDate(copy.getDate() + diff)
  copy.setHours(0, 0, 0, 0)
  return copy
}

function addDays(d: Date, n: number): Date {
  const copy = new Date(d)
  copy.setDate(copy.getDate() + n)
  return copy
}

export default function MealPlanCalendar() {
  const navigate = useNavigate()
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))
  const [mealPlans, setMealPlans] = useState<MealPlan[]>([])
  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingCell, setEditingCell] = useState<{ date: string; mealType: MealType } | null>(null)

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)), [weekStart])
  const from = toISODate(days[0])
  const to   = toISODate(days[6])

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const [plans, rs] = await Promise.all([fetchMealPlans(from, to), fetchRecipes()])
        setMealPlans(plans)
        setRecipes(rs)
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : '取得に失敗しました')
      } finally {
        setLoading(false)
      }
    }
    void load()
  }, [from, to])

  function planFor(date: string, mealType: MealType): MealPlan | undefined {
    return mealPlans.find((p) => p.date === date && p.mealType === mealType)
  }

  async function handleAssign(date: string, mealType: MealType, recipeIds: number[]) {
    try {
      const updated = await assignMealPlan(date, mealType, recipeIds)
      setMealPlans((prev) => [...prev.filter((p) => !(p.date === date && p.mealType === mealType)), updated])
      setEditingCell(null)
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '割り当てに失敗しました')
    }
  }

  async function handleStatus(plan: MealPlan, status: MealPlanStatus) {
    try {
      const updated = await updateMealPlanStatus(plan.id, status)
      setMealPlans((prev) => prev.map((p) => (p.id === plan.id ? updated : p)))
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '実績の更新に失敗しました')
    }
  }

  async function handleClear(plan: MealPlan) {
    if (!confirm('この枠の献立を削除しますか？')) return
    try {
      await deleteMealPlan(plan.id)
      setMealPlans((prev) => prev.filter((p) => p.id !== plan.id))
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
        <h1>📅 献立カレンダー</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn--ghost" onClick={() => setWeekStart((w) => addDays(w, -7))}>← 前の週</button>
          <button className="btn btn--ghost" onClick={() => setWeekStart(startOfWeek(new Date()))}>今週</button>
          <button className="btn btn--ghost" onClick={() => setWeekStart((w) => addDays(w, 7))}>次の週 →</button>
        </div>
      </div>

      <div className="meal-calendar">
        {days.map((d) => {
          const dateStr = toISODate(d)
          return (
            <div key={dateStr} className="meal-calendar-day">
              <h3>{d.getMonth() + 1}/{d.getDate()} ({['日', '月', '火', '水', '木', '金', '土'][d.getDay()]})</h3>
              {MEAL_TYPES.map(({ key, label }) => {
                const plan = planFor(dateStr, key)
                const isEditing = editingCell?.date === dateStr && editingCell?.mealType === key
                return (
                  <div key={key} className="meal-calendar-cell">
                    <span className="meal-calendar-cell-label">{label}</span>
                    {plan && plan.recipes.length > 0 ? (
                      <>
                        <ul className="meal-calendar-recipes">
                          {plan.recipes.map((r) => <li key={r.id}>{r.title}</li>)}
                        </ul>
                        <span className="badge">{STATUS_LABELS[plan.status]}</span>
                        <div className="meal-calendar-actions">
                          <button className="btn btn--ghost" onClick={() => handleStatus(plan, 'eaten')}>食べた</button>
                          <button className="btn btn--ghost" onClick={() => handleStatus(plan, 'skipped')}>食べなかった</button>
                          <button className="btn btn--icon btn--danger" onClick={() => handleClear(plan)} aria-label="削除">🗑</button>
                        </div>
                      </>
                    ) : (
                      <button className="btn btn--ghost" onClick={() => setEditingCell({ date: dateStr, mealType: key })}>
                        + 追加
                      </button>
                    )}
                    {isEditing && (
                      <RecipePicker
                        recipes={recipes}
                        onCancel={() => setEditingCell(null)}
                        onConfirm={(ids) => handleAssign(dateStr, key, ids)}
                      />
                    )}
                  </div>
                )
              })}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function RecipePicker({
  recipes, onCancel, onConfirm,
}: {
  recipes: Recipe[]
  onCancel: () => void
  onConfirm: (recipeIds: number[]) => void
}) {
  const [selected, setSelected] = useState<number[]>([])

  function toggle(id: number) {
    setSelected((prev) => (prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id]))
  }

  return (
    <div className="meal-calendar-picker">
      {recipes.length === 0 ? (
        <p>レシピがまだありません</p>
      ) : (
        <ul>
          {recipes.map((r) => (
            <li key={r.id}>
              <label>
                <input type="checkbox" checked={selected.includes(r.id)} onChange={() => toggle(r.id)} />
                {r.title}
              </label>
            </li>
          ))}
        </ul>
      )}
      <div className="meal-calendar-actions">
        <button className="btn btn--primary" onClick={() => onConfirm(selected)} disabled={selected.length === 0}>決定</button>
        <button className="btn btn--ghost" onClick={onCancel}>キャンセル</button>
      </div>
    </div>
  )
}
