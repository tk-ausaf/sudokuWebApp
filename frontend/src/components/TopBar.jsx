import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import './TopBar.css';

export default function TopBar() {
  const { isLoggedIn, username, logout, token } = useAuth();
  const [visitorCount, setVisitorCount] = useState(null);

  useEffect(() => {
    api
      .recordVisit(token)
      .then((result) => setVisitorCount(result.count))
      .catch(() => {});
    // Runs once per app load, not per navigation - TopBar stays mounted for the whole SPA session.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <header className="topbar">
      <div className="topbar__inner">
        {visitorCount !== null && (
          <div className="visitor-box" title="Unique visitors today">
            <span className="visitor-box__label">Visitors Today</span>
            <span className="visitor-box__count">{visitorCount}</span>
          </div>
        )}
        <span className="topbar__brand">Sudoku</span>
        <nav className="topbar__nav">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'topbar__link topbar__link--active' : 'topbar__link')}>
            Play
          </NavLink>
          {isLoggedIn && (
            <NavLink to="/history" className={({ isActive }) => (isActive ? 'topbar__link topbar__link--active' : 'topbar__link')}>
              History
            </NavLink>
          )}
          <NavLink to="/leaderboard" className={({ isActive }) => (isActive ? 'topbar__link topbar__link--active' : 'topbar__link')}>
            Leaderboard
          </NavLink>
          <NavLink to="/multiplayer/new" className={({ isActive }) => (isActive ? 'topbar__link topbar__link--active' : 'topbar__link')}>
            Multiplayer
          </NavLink>
        </nav>
        <div className="topbar__auth">
          {isLoggedIn ? (
            <>
              <span className="topbar__username">{username}</span>
              <button className="btn btn--ghost" onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <NavLink to="/login" className="btn btn--secondary">
              Log in
            </NavLink>
          )}
        </div>
      </div>
    </header>
  );
}