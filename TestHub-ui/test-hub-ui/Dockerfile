# ══════════════════════════════════════════════════════════════════════════════
# Stage 1 — Build React
# ══════════════════════════════════════════════════════════════════════════════
FROM node:18-alpine AS builder

WORKDIR /app

# Copier package.json en premier pour profiter du cache Docker
COPY package.json package-lock.json ./

# Installer les dépendances
RUN npm ci --legacy-peer-deps

# Copier le reste du code
COPY . .

# Build production
RUN npm run build

# ══════════════════════════════════════════════════════════════════════════════
# Stage 2 — Serveur Nginx
# ══════════════════════════════════════════════════════════════════════════════
FROM nginx:alpine

# Copier le build React
COPY --from=builder /app/dist /usr/share/nginx/html

# Copier la config Nginx personnalisée
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s \
    CMD wget -qO- http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
