import { useEffect } from 'react';
import { Route, Routes } from 'react-router-dom';
import TopBar from './components/TopBar.jsx';
import AddEmailBanner from './components/AddEmailBanner.jsx';
import BackendWakingOverlay from './components/BackendWakingOverlay.jsx';
import PlayPage from './pages/PlayPage.jsx';
import ResumePage from './pages/ResumePage.jsx';
import HistoryPage from './pages/HistoryPage.jsx';
import LeaderboardPage from './pages/LeaderboardPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import ForgotPasswordPage from './pages/ForgotPasswordPage.jsx';
import ResetPasswordPage from './pages/ResetPasswordPage.jsx';
import MultiplayerCreatePage from './pages/MultiplayerCreatePage.jsx';
import MultiplayerGamePage from './pages/MultiplayerGamePage.jsx';

export default function App() {
  useEffect(() => {
    // Fire-and-forget: wakes a cold-started backend as early as possible on page load, without
    // blocking anything or surfacing an error if it fails - BackendWakingOverlay only reacts to
    // an actual user-initiated API call failing, not to this best-effort nudge. Relative path,
    // proxied same-origin like every other REST call - see api/proxy/[...path].js.
    fetch('/health').catch(() => {});
  }, []);

  return (
    <div className="app-shell">
      <BackendWakingOverlay />
      <TopBar />
      <AddEmailBanner />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<PlayPage />} />
          <Route path="/resume/:attemptId" element={<ResumePage />} />
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/leaderboard" element={<LeaderboardPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/multiplayer/new" element={<MultiplayerCreatePage />} />
          <Route path="/multiplayer/game/:gameId" element={<MultiplayerGamePage />} />
        </Routes>
      </main>
    </div>
  );
}