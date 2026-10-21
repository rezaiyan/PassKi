# syntax=docker/dockerfile:1
# Build context: passki-web/ (the check catalog is mirrored in content/checks, so ../docs is not needed).

FROM node:22-alpine AS base
WORKDIR /app

FROM base AS deps
# better-sqlite3 is a native addon: build tools in case no prebuilt musl binary matches.
RUN apk add --no-cache libc6-compat python3 make g++
COPY package.json package-lock.json ./
RUN npm ci

FROM deps AS builder
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM base AS runner
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    PASSKI_DB_PATH=/app/data/passki.db
RUN apk add --no-cache libc6-compat && \
    addgroup -g 1001 nodejs && \
    adduser -S nextjs -G nodejs && \
    mkdir -p /app/data && chown nextjs:nodejs /app/data

COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/content ./content

USER nextjs
VOLUME ["/app/data"]
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1:3000/api/demo/server-config >/dev/null || exit 1
CMD ["node", "server.js"]
