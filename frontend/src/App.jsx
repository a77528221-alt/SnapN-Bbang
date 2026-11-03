import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import HomePage from './pages/HomePage';
import SettlePage from './pages/SettlePage';
import ManagePage from './pages/ManagePage';
import ParticipantPage from './pages/ParticipantPage';
import ProfilePage from './pages/ProfilePage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/settle" element={<SettlePage />} />
        <Route path="/s/:sid/manage" element={<ManagePage />} />
        <Route path="/r/:sid" element={<ParticipantPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
