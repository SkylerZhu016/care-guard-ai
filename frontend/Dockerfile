FROM node:22.17.0-alpine AS build
WORKDIR /app/frontend
RUN corepack enable && corepack prepare pnpm@11.9.0 --activate
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile --store-dir /pnpm/store
COPY frontend/ .
RUN pnpm build

FROM nginx:1.27.5-alpine
COPY --from=build /app/frontend/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
