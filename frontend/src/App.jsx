import { Route, Routes } from 'react-router-dom';
import TopBar from './components/TopBar.jsx';
import PlayPage from './pages/PlayPage.jsx';
import ResumePage from './pages/ResumePage.jsx';
import HistoryPage from './pages/HistoryPage.jsx';
import LeaderboardPage from './pages/LeaderboardPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import MultiplayerCreatePage from './pages/MultiplayerCreatePage.jsx';
import MultiplayerGamePage from './pages/MultiplayerGamePage.jsx';

export default function App() {
  return (
    <div className="app-shell">
      <TopBar />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<PlayPage />} />
          <Route path="/resume/:attemptId" element={<ResumePage />} />
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/leaderboard" element={<LeaderboardPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/multiplayer/new" element={<MultiplayerCreatePage />} />
          <Route path="/multiplayer/game/:gameId" element={<MultiplayerGamePage />} />
        </Routes>
      </main>
    </div>
  );
}