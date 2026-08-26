const BASE = 'http://localhost:9000/api/v1'

export type MealType = 'breakfast' | 'lunch' | 'dinner'
export type MealPlanStatus = 'planned' | 'eaten' | 'skipped'

export interface MealPlanRecipeSummary {
  id: number
  title: string
}

export interface MealPlan {
  id: number
  userId: number
  date: string
  mealType: MealType
  status: MealPlanStatus
  createdAt: string
  updatedAt: string
  recipes: MealPlanRecipeSummary[]
}

export async function fetchMealPlans(from: string, to: string): Promise<MealPlan[]> {
  const res = await fetch(`${BASE}/meal-plans?from=${from}&to=${to}`, { credentials: 'include' })
  if (!res.ok) throw new Error('献立の取得に失敗しました')
  return res.json()
}

export async function assignMealPlan(date: string, mealType: MealType, recipeIds: number[]): Promise<MealPlan> {
  const res = await fetch(`${BASE}/meal-plans`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ date, mealType, recipeIds }),
  })
  if (!res.ok) throw new Error('献立の割り当てに失敗しました')
  return res.json()
}

export async function updateMealPlanStatus(id: number, status: MealPlanStatus): Promise<MealPlan> {
  const res = await fetch(`${BASE}/meal-plans/${id}/status`, {
    method: 'PATCH',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  })
  if (!res.ok) throw new Error('実績の更新に失敗しました')
  return res.json()
}

export async function deleteMealPlan(id: number): Promise<void> {
  const res = await fetch(`${BASE}/meal-plans/${id}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  if (!res.ok) throw new Error('献立の削除に失敗しました')
}
