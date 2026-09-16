/** Renders `total` hearts, dulling the first `used` of them - a visual stand-in for "X of Y wrong attempts left". */
export default function HeartMeter({ total, used }) {
  const hearts = [];
  for (let i = 0; i < total; i++) {
    const isUsed = i < used;
    hearts.push(
      <span key={i} className={isUsed ? 'heart heart--dull' : 'heart'} aria-hidden="true">
        &#9829;
      </span>,
    );
  }

  return (
    <span className="heart-meter" role="img" aria-label={`${Math.max(total - used, 0)} of ${total} attempts remaining`}>
      {hearts}
    </span>
  );
}