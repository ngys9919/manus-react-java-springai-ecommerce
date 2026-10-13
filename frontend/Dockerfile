# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Stage 1 — build the static bundle
#
# `npm ci` installs strictly from package-lock.json, so the image is
# reproducible. Dev dependencies are needed here because the build runs tsc.
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts index.html ./
COPY public ./public
COPY src ./src

RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2 — serve it
#
# nginx serves the bundle and reverse-proxies /api and /actuator to the backend,
# so the browser sees one origin and no CORS configuration is needed.
# ---------------------------------------------------------------------------
FROM nginx:1.29-alpine AS runtime

RUN rm -f /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/nginx.conf
COPY snippets/ /etc/nginx/snippets/
COPY templates/ /etc/nginx/templates/
# Sourced by the nginx entrypoint before the templates are rendered, so a
# platform-injected PORT becomes the listen port. See the file for why it is
# .envsh rather than .sh.
COPY docker-entrypoint.d/ /docker-entrypoint.d/
RUN chmod +x /docker-entrypoint.d/*.envsh
COPY --from=build /build/dist /usr/share/nginx/html

# Defaults for the templated server block. The nginx image renders
# /etc/nginx/templates into /etc/nginx/conf.d at startup, substituting these.
# Compose overrides the upstream; nothing here needs a rebuild to change.
# 127.0.0.11 is Docker's embedded DNS, which is what the `resolve` directive in
# the server block needs; the entrypoint script replaces it with the platform's
# own resolver when /etc/resolv.conf says otherwise.
ENV SHOPWISE_LISTEN_PORT=80 \
    SHOPWISE_API_UPSTREAM=backend:8080 \
    SHOPWISE_DNS_RESOLVER=127.0.0.11

EXPOSE 80

# 127.0.0.1 rather than localhost: the name resolves to ::1 first inside a
# container, which would make this probe depend on the IPv6 listener. The port
# is read from the variable the entrypoint script may have overridden, so the
# probe follows the listener instead of assuming 80.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O- "http://127.0.0.1:${SHOPWISE_LISTEN_PORT:-80}/healthz" >/dev/null 2>&1 || exit 1
