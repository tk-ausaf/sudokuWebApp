import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import './TopBar.css';

export default function TopBar() {
  const { isLoggedIn, username, logout } = useAuth();

  return (
    <header className="topbar">
      <div className="topbar__inner">
        <span className="topbar__brand">Sudoku</span>
        <nav className="topbar__nav">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'topbar__link topbar__link--active' : 'topbar__link')}>
            Play
          </NavLink>
          <NavLink to="/history" className={({ isActive }) => (isActive ? 'topbar__link topbar__link--active' : 'topbar__link')}>
            History
          </NavLink>
          <NavLink to="/leaderboard" className={({ isActive }) => (isActive ? 'topbar__link topbar__link--active' : 'topbar__link')}>
            Leaderboard
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