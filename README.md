# sudokuWebApp
A fun sudoku app

## Environment variables

Set these the same way everywhere the app runs (local `.env`, `docker run -e`, Render's
environment variable dashboard, etc.) so credentials never diverge between environments:

| Variable | Purpose |
|---|---|
| `SUDOKU_MONGO_DB_URI` | MongoDB connection string (e.g. an Atlas SRV URI) |
| `SUDOKU_JWT_SECRET` | Secret key used to sign login JWTs (any long random string) |

On first startup the app seeds the `puzzles` collection with 1000 randomly generated
9x9 Sudoku puzzles if it isn't already populated.
