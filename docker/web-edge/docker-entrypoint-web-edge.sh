#!/bin/sh
set -eu
# Single-quoted vars for envsubst — add new secrets here if the template references them.
envsubst '$GATEWAY_SHARED_SECRET' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
exec "$@"
