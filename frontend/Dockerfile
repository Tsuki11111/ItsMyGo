FROM node:22-alpine AS build
WORKDIR /app
# Vite 只把 VITE_ 前缀注入前端；compose 用 build args 传进来
ARG VITE_TMAP_KEY=
ENV VITE_TMAP_KEY=$VITE_TMAP_KEY
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
