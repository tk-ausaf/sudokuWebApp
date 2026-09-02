export default function LeaderboardTable({ entries }) {
  if (!entries || entries.length === 0) {
    return <p className="page-subtitle">No solves yet in this period - be the first!</p>;
  }

  return (
    <table className="leaderboard">
      <thead>
        <tr>
          <th>#</th>
          <th>Player</th>
          <th>Puzzles Solved</th>
        </tr>
      </thead>
      <tbody>
        {entries.map((entry) => (
          <tr key={`${entry.rank}-${entry.displayName}`}>
            <td>{entry.rank}</td>
            <td>{entry.displayName}</td>
            <td>{entry.solvedCount}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}