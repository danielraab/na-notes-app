# syntax=docker/dockerfile:1

FROM rust:1.90-slim-bookworm AS build
# rusqlite's "bundled" feature (see docs/decisions/0002-sqlite-only.md)
# compiles SQLite's C sources itself, so it needs a C toolchain even
# though the rest of the dependency tree (reqwest/lettre use rustls, not
# OpenSSL) doesn't.
RUN apt-get update && apt-get install -y --no-install-recommends build-essential && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY Cargo.toml Cargo.lock ./
# Build a throwaway binary first, keyed only on the manifest, so
# dependencies are cached in their own layer and don't rebuild on every
# source change.
RUN mkdir src && echo "fn main() {}" > src/main.rs && cargo build --release && rm -rf src
COPY src ./src
RUN touch src/main.rs && cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates wget && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --home-dir /app app
WORKDIR /app
COPY --from=build /src/target/release/server ./server
RUN mkdir -p /data && chown app:app /data
VOLUME ["/data"]
ENV DATABASE_URL=/data/notes.db
ENV LISTEN_ADDR=:8080
EXPOSE 8080
USER app
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O- http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["./server"]
