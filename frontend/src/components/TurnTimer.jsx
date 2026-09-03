import { useEffect, useState } from 'react';

/** Renders a live countdown to `deadline` (an ISO timestamp string), ticking every second. */
export default function TurnTimer({ deadline }) {
  const [secondsLeft, setSecondsLeft] = useState(() => remaining(deadline));

  useEffect(() => {
    setSecondsLeft(remaining(deadline));
    const interval = setInterval(() => setSecondsLeft(remaining(deadline)), 1000);
    return () => clearInterval(interval);
  }, [deadline]);

  if (!deadline) return null;

  return (
    <span className={secondsLeft <= 5 ? 'turn-timer turn-timer--low' : 'turn-timer'}>
      {secondsLeft}s
    </span>
  );
}

function remaining(deadline) {
  if (!deadline) return 0;
  const ms = new Date(deadline).getTime() - Date.now();
  return Math.max(0, Math.ceil(ms / 1000));
}