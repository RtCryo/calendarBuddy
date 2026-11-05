import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell, NotFound } from './components/AppShell'
import { FamilyPage } from './pages/FamilyPage'
import { CalendarPage } from './pages/CalendarPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

export function AppRoutes() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<ProtectedRoute />}>
      <Route element={<AppShell />}>
        <Route path="/" element={<Navigate to="/calendar" replace />} />
        <Route path="/calendar" element={<CalendarPage />} />
        <Route path="/family" element={<FamilyPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Route>
  </Routes>
}

export default function App() {
  return <BrowserRouter><AppRoutes /></BrowserRouter>
}
