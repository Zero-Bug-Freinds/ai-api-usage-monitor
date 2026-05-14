# Pattern B edge image — template baked in; GATEWAY_SHARED_SECRET at runtime (envsubst).
# ALB Target Group HTTP health checks should use port 8080 and path /healthz (no Host allowlist on that listener).

FROM nginx:1.27-alpine
RUN apk add --no-cache gettext
COPY docker/web-edge/nginx.conf.template /etc/nginx/nginx.conf.template
COPY docker/web-edge/docker-entrypoint-web-edge.sh /docker-entrypoint-web-edge.sh
RUN chmod +x /docker-entrypoint-web-edge.sh
EXPOSE 80 8080
ENTRYPOINT ["/docker-entrypoint-web-edge.sh"]
CMD ["nginx", "-g", "daemon off;"]
