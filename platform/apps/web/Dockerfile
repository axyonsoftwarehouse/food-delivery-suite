FROM node:24-alpine AS build
WORKDIR /app
RUN corepack enable
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/web apps/web
COPY tools/package.json tools/package.json
RUN pnpm install --frozen-lockfile
ARG API_INTERNAL_URL=http://api:4001
ENV API_INTERNAL_URL=$API_INTERNAL_URL
RUN pnpm --filter @foodie/web build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV API_INTERNAL_URL=http://api:4001
RUN corepack enable
COPY --from=build /app/package.json /app/pnpm-lock.yaml /app/pnpm-workspace.yaml ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/apps/web ./apps/web
COPY --from=build /app/tools/package.json ./tools/package.json
WORKDIR /app/apps/web
EXPOSE 3001
CMD ["node", "node_modules/next/dist/bin/next", "start", "--hostname", "0.0.0.0", "--port", "3001"]
