const BASE = 'http://localhost:9000/api/v1'

export interface Recipe {
  id: number
  userId: number
  title: string
  description: string | null
  category: string
  servings: number
  cookTimeMinutes: number
  createdAt: string
  updatedAt: string
}

export interface RecipeInput {
  title: string
  description?: string
  category: string
  servings: number
  cookTimeMinutes: number
}

export async function fetchRecipes(): Promise<Recipe[]> {
  const res = await fetch(`${BASE}/recipes`, { credentials: 'include' })
  if (!res.ok) throw new Error('レシピ一覧の取得に失敗しました')
  return res.json()
}

export async function fetchRecipe(id: number): Promise<Recipe> {
  const res = await fetch(`${BASE}/recipes/${id}`, { credentials: 'include' })
  if (!res.ok) throw new Error('レシピの取得に失敗しました')
  return res.json()
}

export async function createRecipe(input: RecipeInput): Promise<Recipe> {
  const res = await fetch(`${BASE}/recipes`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error('レシピの作成に失敗しました')
  return res.json()
}

export async function updateRecipe(id: number, input: RecipeInput): Promise<Recipe> {
  const res = await fetch(`${BASE}/recipes/${id}`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error('レシピの更新に失敗しました')
  return res.json()
}

export async function deleteRecipe(id: number): Promise<void> {
  const res = await fetch(`${BASE}/recipes/${id}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  if (!res.ok) throw new Error('レシピの削除に失敗しました')
}
