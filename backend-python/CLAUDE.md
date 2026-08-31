# CLAUDE.md — backend-python

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Python-specific notes.

- Follow standard Python idiom: `ruff check .` / `ruff format --check .`
  clean, and `mypy app` clean. Use `uv run <tool>` rather than invoking
  tools from a manually-activated virtualenv.
- Business rules belong in `app/notes/service.py` (and, if it grows one,
  an equivalent `app/users/service.py`), not in `app/api/routes_*.py`
  handlers — see
  [`docs/decisions/0001-fastapi-and-project-layout.md`](docs/decisions/0001-fastapi-and-project-layout.md).
- Never bypass `sqlite3`'s parameterized queries (`?` placeholders) for
  user-controlled input.
- Run `uv run ruff check . && uv run ruff format --check . && uv run mypy app && uv run pytest`
  before considering a change done.
- If a change touches `openapi/openapi.yaml` semantics (new field,
  endpoint, or behavior), update `app/api/dto.py` and the relevant route
  module together, and flag that other backend implementations need the
  equivalent change.
- This implementation only supports SQLite (no PostgreSQL) — see
  [`docs/decisions/0002-sqlite-only.md`](docs/decisions/0002-sqlite-only.md)
  before assuming otherwise.
