# ---------- Stage 1: build the Angular app ----------
# Build stage runs on the native CPU (output is plain HTML/JS/CSS)
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# ---------- Stage 2: serve with nginx (non-root, port 8080) ----------
FROM nginxinc/nginx-unprivileged:1.27-alpine
# Fill in the DNS resolver from /etc/resolv.conf when the container starts
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
