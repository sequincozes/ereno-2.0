# ERENO UI: static build served by nginx, which also proxies /api to the
# backend and adds the API token there, so the token never reaches the browser.
# Build context: app/

FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Same-origin API: the browser calls /api/... on nginx.
ARG VITE_API_URL=/
# Optional Firebase (login + presets). Empty = local mode without login.
ARG VITE_AUTH_ENABLED=false
ARG VITE_APIKEY=
ARG VITE_AUTHDOMAIN=
ARG VITE_DATABASEURL=
ARG VITE_PROJECTID=
ARG VITE_STORAGEBUCKET=
ARG VITE_MESSAGINGSENDERID=
ARG VITE_APPID=
ARG VITE_MEASUREMENTID=
ENV VITE_API_URL=$VITE_API_URL VITE_AUTH_ENABLED=$VITE_AUTH_ENABLED VITE_APIKEY=$VITE_APIKEY \
    VITE_AUTHDOMAIN=$VITE_AUTHDOMAIN VITE_DATABASEURL=$VITE_DATABASEURL VITE_PROJECTID=$VITE_PROJECTID \
    VITE_STORAGEBUCKET=$VITE_STORAGEBUCKET VITE_MESSAGINGSENDERID=$VITE_MESSAGINGSENDERID \
    VITE_APPID=$VITE_APPID VITE_MEASUREMENTID=$VITE_MEASUREMENTID
RUN npm run build

FROM nginx:1.27-alpine
# Rendered by the image's entrypoint with envsubst (ERENO_API_UPSTREAM, ERENO_API_TOKEN).
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
ENV ERENO_API_UPSTREAM=http://api:8080 \
    NGINX_ENVSUBST_FILTER=^ERENO_
EXPOSE 80
