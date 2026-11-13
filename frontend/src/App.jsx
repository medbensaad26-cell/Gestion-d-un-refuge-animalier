import { Route, Routes } from 'react-router'
import SiteLayout from './layouts/SiteLayout.jsx'
import HomePage from './pages/HomePage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import AnimalDetailPage from './pages/AnimalDetailPage.jsx'
import AnnouncementsPage from './pages/AnnouncementsPage.jsx'
import AdoptionRequestPage from './pages/AdoptionRequestPage.jsx'
import AdminSessionProvider from './context/AdminSessionProvider.jsx'
import AdminLoginPage from './pages/AdminLoginPage.jsx'
import AdminDashboardPage from './pages/AdminDashboardPage.jsx'
import ProtectedAdminArea from './layouts/ProtectedAdminArea.jsx'
import AdminAnimalsPage from './pages/AdminAnimalsPage.jsx'
import AdminAnimalEditorPage from './pages/AdminAnimalEditorPage.jsx'
import AdminAnimalDeletePage from './pages/AdminAnimalDeletePage.jsx'
import AdminMedicalPage from './pages/AdminMedicalPage.jsx'
import AdminAnnouncementsPage from './pages/AdminAnnouncementsPage.jsx'
import AdminAnnouncementEditorPage from './pages/AdminAnnouncementEditorPage.jsx'
import AdminRequestsPage from './pages/AdminRequestsPage.jsx'
import AdminRequestDetailPage from './pages/AdminRequestDetailPage.jsx'

export default function App() {
  return (
    <AdminSessionProvider><Routes>
      <Route path="admin" element={<ProtectedAdminArea />}>
        <Route index element={<AdminDashboardPage />} />
        <Route path="animaux" element={<AdminAnimalsPage />} />
        <Route path="animaux/nouveau" element={<AdminAnimalEditorPage creating />} />
        <Route path="animaux/:id/modifier" element={<AdminAnimalEditorPage />} />
        <Route path="animaux/:id/supprimer" element={<AdminAnimalDeletePage />} />
        <Route path="animaux/:id/dossier-medical" element={<AdminMedicalPage />} />
        <Route path="animaux/:animalId/annonce" element={<AdminAnnouncementEditorPage creating />} />
        <Route path="annonces" element={<AdminAnnouncementsPage />} />
        <Route path="annonces/nouvelle" element={<AdminAnnouncementEditorPage creating />} />
        <Route path="annonces/:id/modifier" element={<AdminAnnouncementEditorPage />} />
        <Route path="demandes" element={<AdminRequestsPage />} />
        <Route path="demandes/:id" element={<AdminRequestDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route element={<SiteLayout />}>
        <Route index element={<HomePage />} />
        <Route path="animaux/:id" element={<AnimalDetailPage />} />
        <Route path="animaux/:id/demande-adoption" element={<AdoptionRequestPage />} />
        <Route path="annonces" element={<AnnouncementsPage />} />
        <Route path="admin/connexion" element={<AdminLoginPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes></AdminSessionProvider>
  )
}
