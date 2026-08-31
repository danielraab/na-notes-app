# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# The backend URL is baked into the bundle at build time, same as
# frontend-react/frontend-svelte's Vite build — see
# docs/decisions/0002-build-time-api-url.md. Angular's environments
# pattern is the framework-native equivalent of Vite's import.meta.env
# inlining: this overwrites the (gitignored locally) environment.ts with
# the real value before `ng build` picks it up.
ARG NG_APP_API_BASE_URL=http://localhost:8080/api
RUN printf "export const environment = {\n  apiBaseUrl: '%s',\n};\n" "$NG_APP_API_BASE_URL" > src/environments/environment.ts
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/dist/frontend-angular/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O- http://127.0.0.1/ || exit 1
