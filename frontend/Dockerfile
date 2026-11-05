FROM node:22-alpine AS build
# 构建上下文是仓库根：PolicyView 直接 ?raw 导入 ../docs/policies/*.md，
# 前端工程与 docs 必须保持和仓库一致的相对位置（frontend/src/... → ../../../../docs）
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci
COPY frontend ./frontend
COPY docs/policies ./docs/policies

WORKDIR /app/frontend
# Vite 只把 VITE_ 前缀注入前端；compose 用 build args 传进来
ARG VITE_TMAP_KEY=
ENV VITE_TMAP_KEY=$VITE_TMAP_KEY
RUN npm run build

FROM nginx:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
