# Stage 1: Build Angular app
FROM node:24-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --quiet
COPY . .
RUN npx ng build --configuration production

# Stage 2: Serve with nginx
FROM nginx:1.27-alpine
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
