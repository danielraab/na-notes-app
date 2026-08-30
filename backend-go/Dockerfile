# syntax=docker/dockerfile:1

FROM golang:1.25-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
# CGO disabled: modernc.org/sqlite is a pure-Go SQLite driver, so the
# resulting binary has no C toolchain/glibc dependency at runtime.
RUN CGO_ENABLED=0 go build -o /out/server ./cmd/server

FROM alpine:3.20
RUN adduser -D -H app
WORKDIR /app
COPY --from=build /out/server ./server
RUN mkdir -p /data && chown app:app /data
VOLUME ["/data"]
ENV DATABASE_URL=/data/notes.db
ENV LISTEN_ADDR=:8080
EXPOSE 8080
USER app
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O- http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["./server"]
