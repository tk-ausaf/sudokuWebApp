import { Link } from 'react-router-dom';

const REASON_TEXT = {
  WRONG_MOVE: 'A wrong digit was entered.',
  TIMEOUT: 'Time ran out on a turn.',
  BOARD_COMPLETE: 'The board was filled with no mistakes.',
};

/** Shows the win/loss/draw result of a finished multiplayer game, from the viewer's perspective. */
export default function MultiplayerEndScreen({ outcome, endReason, yourSlot }) {
  const isDraw = outcome === 'DRAW';
  const youWon = !isDraw && outcome === `${yourSlot}_WIN`;

  const panelClass = isDraw
    ? 'success-panel success-panel--draw'
    : youWon
      ? 'success-panel'
      : 'success-panel success-panel--loss';

  const title = isDraw ? "It's a draw!" : youWon ? 'You won!' : 'You lost';

  return (
    <div className={panelClass}>
      <div className="success-panel__icon" aria-hidden="true">
        {isDraw ? '=' : youWon ? '✓' : '✗'}
      </div>
      <h2 className="success-panel__title">{title}</h2>
      <p className="success-panel__subtitle">{REASON_TEXT[endReason] || 'The game has ended.'}</p>
      <div className="board-actions">
        <Link className="btn btn--primary" to="/multiplayer/new">
          New multiplayer game
        </Link>
        <Link className="btn btn--ghost" to="/">
          Back to single player
        </Link>
      </div>
    </div>
  );
}