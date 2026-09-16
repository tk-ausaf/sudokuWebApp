import { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import DropdownMenu, { DropdownItem } from './DropdownMenu.jsx';
import Icon from './Icon.jsx';
import './TopBar.css';

export default function TopBar() {
  const { isLoggedIn, username, logout, token } = useAuth();
  const navigate = useNavigate();
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
        <NavLink to="/" end className="topbar__brand">
          Sudoku
        </NavLink>

        {visitorCount !== null && (
          <div className="visitor-box" title="Unique visitors today">
            <span className="visitor-box__label">Visitors Today</span>
            <span className="visitor-box__count">{visitorCount}</span>
          </div>
        )}

        <div className="topbar__spacer" />

        {/* Everything below "Play" (the brand link) lives in this menu rather than as its own
            always-visible link - keeps the header from growing every time a new section is
            added, e.g. a future "Stats" or "Friends" page just becomes another item here. */}
        <DropdownMenu label="Navigation menu" trigger={<Icon name="menu" />}>
          {isLoggedIn && <DropdownItem onSelect={() => navigate('/history')}>History</DropdownItem>}
          <DropdownItem onSelect={() => navigate('/leaderboard')}>Leaderboard</DropdownItem>
          <DropdownItem onSelect={() => navigate('/multiplayer/new')}>Multiplayer</DropdownItem>
        </DropdownMenu>

        <div className="topbar__auth">
          {isLoggedIn ? (
            <DropdownMenu
              label={`Account menu for ${username}`}
              trigger={
                <>
                  <span className="topbar__username">{username}</span>
                  <span aria-hidden="true">&#9662;</span>
                </>
              }
            >
              <DropdownItem onSelect={logout}>Log out</DropdownItem>
            </DropdownMenu>
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