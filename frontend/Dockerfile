# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --legacy-peer-deps
COPY . .
ARG VITE_ICP_RECORD=""
ARG VITE_PUBLIC_SECURITY_RECORD=""
ENV VITE_ICP_RECORD=${VITE_ICP_RECORD}
ENV VITE_PUBLIC_SECURITY_RECORD=${VITE_PUBLIC_SECURITY_RECORD}
RUN npm run build

# Run stage
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
