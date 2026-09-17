FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Baked into the JS bundle at build time - the deployed backend's own origin, since frontend and
# backend are separate services. Pass at build time: docker build --build-arg VITE_API_BASE_URL=...
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# Plain static hosting - no custom server code, just nginx serving the built files with an SPA
# fallback (see nginx.conf). The frontend's own wake-up ping to the backend runs client-side (see
# App.jsx), so nginx needs no per-request logic of its own.
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80