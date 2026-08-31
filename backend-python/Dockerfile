# syntax=docker/dockerfile:1

FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim AS build
WORKDIR /src
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy

# Install dependencies only (cached across builds unless the lockfile
# changes) — the project itself is run from source below, not installed
# as a package, so no build step is needed for our own code.
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project
COPY app ./app

FROM python:3.12-slim-bookworm
RUN useradd --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /src/.venv /app/.venv
COPY --from=build /src/app /app/app
ENV PATH="/app/.venv/bin:$PATH"
RUN mkdir -p /data && chown app:app /data
VOLUME ["/data"]
ENV DATABASE_URL=/data/notes.db
ENV LISTEN_ADDR=:8080
EXPOSE 8080
USER app
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import sys, urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/healthz', timeout=2).status == 200 else 1)"
ENTRYPOINT ["python", "-m", "app.main"]
