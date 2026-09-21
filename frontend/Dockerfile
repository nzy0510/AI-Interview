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
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# Keep our config separate from the stock config checked by the IPv6 entrypoint.
COPY nginx.conf /etc/nginx/conf.d/interwise.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
