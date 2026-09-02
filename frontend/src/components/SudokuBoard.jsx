import './SudokuBoard.css';

const SIZE = 9;

/**
 * clues: 81-char string, '0' = blank, digit = given (read-only) clue.
 * values: 81-char string, the cells currently displayed (blank cells shown as '0').
 * onCellChange(nextValues): called with the full 81-char string after an edit.
 */
export default function SudokuBoard({ clues, values, onCellChange, readOnly = false }) {
  function handleChange(index, rawValue) {
    if (readOnly) return;
    const digit = rawValue.replace(/[^1-9]/g, '').slice(-1);
    const next = values.split('');
    next[index] = digit || '0';
    onCellChange(next.join(''));
  }

  function handleKeyDown(index, event) {
    if (event.key !== 'Backspace' && event.key !== 'Delete') return;
    if (readOnly || clues[index] !== '0') return;
    const next = values.split('');
    next[index] = '0';
    onCellChange(next.join(''));
  }

  const cells = [];
  for (let index = 0; index < SIZE * SIZE; index++) {
    const row = Math.floor(index / SIZE);
    const col = index % SIZE;
    const isClue = clues[index] !== '0';
    const displayValue = values[index] === '0' ? '' : values[index];

    const classes = [
      'sudoku-cell',
      isClue && 'sudoku-cell--clue',
      col % 3 === 2 && col !== SIZE - 1 && 'sudoku-cell--border-r',
      row % 3 === 2 && row !== SIZE - 1 && 'sudoku-cell--border-b',
    ]
      .filter(Boolean)
      .join(' ');

    cells.push(
      <input
        key={index}
        className={classes}
        inputMode="numeric"
        maxLength={1}
        value={displayValue}
        readOnly={isClue || readOnly}
        aria-readonly={isClue || readOnly}
        aria-label={`Row ${row + 1}, column ${col + 1}`}
        onChange={(event) => handleChange(index, event.target.value)}
        onKeyDown={(event) => handleKeyDown(index, event)}
      />,
    );
  }

  return (
    <div className="sudoku-board" role="grid" aria-label="Sudoku board">
      {cells}
    </div>
  );
}