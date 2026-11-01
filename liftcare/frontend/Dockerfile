# ==============================================================================
# LiftCare React + Vite Frontend — Multi-Stage Production Dockerfile
# Serves static assets via Nginx with embedded API reverse-proxying
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Frontend Production Artifacts
# ------------------------------------------------------------------------------
FROM node:20-alpine AS builder
WORKDIR /workspace

# Install dependencies with cached lockfile
COPY package*.json ./
RUN npm ci

# Copy sources and compile production bundle
COPY . .
# Empty VITE_API_BASE_URL ensures relative API path (/api/...) matching Nginx proxy
ENV VITE_API_BASE_URL=""
RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Minimal Production Nginx Web Server
# ------------------------------------------------------------------------------
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Clean default Nginx assets
RUN rm -rf ./*

# Copy compiled static assets from builder stage
COPY --from=builder /workspace/dist ./

# Copy custom Nginx configuration with SPA routing and backend proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -q --spider http://localhost/nginx-health || exit 1

CMD ["nginx", "-g", "daemon off;"]
