import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import Home from './pages/Home'
import Login from './pages/Login'
import Register from './pages/Register'
import RecipeList from './pages/RecipeList'
import RecipeForm from './pages/RecipeForm'
import RecipeDetail from './pages/RecipeDetail'
import MealPlanCalendar from './pages/MealPlanCalendar'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login"    element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<ProtectedRoute><Home /></ProtectedRoute>} />
          <Route path="/recipes" element={<ProtectedRoute><RecipeList /></ProtectedRoute>} />
          <Route path="/recipes/new" element={<ProtectedRoute><RecipeForm /></ProtectedRoute>} />
          <Route path="/recipes/:id" element={<ProtectedRoute><RecipeDetail /></ProtectedRoute>} />
          <Route path="/recipes/:id/edit" element={<ProtectedRoute><RecipeForm /></ProtectedRoute>} />
          <Route path="/meal-plans" element={<ProtectedRoute><MealPlanCalendar /></ProtectedRoute>} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
