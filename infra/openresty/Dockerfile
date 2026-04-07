FROM openresty/openresty:alpine

# Install dependencies (openssl + curl: self-signed bootstrap + healthcheck)
RUN apk add --no-cache \
    git \
    openssl \
    openssl-dev \
    curl

# Install lua-resty-jwt manually from GitHub
RUN cd /tmp && \
    git clone https://github.com/SkyLothar/lua-resty-jwt.git && \
    cd lua-resty-jwt && \
    cp -r lib/resty/* /usr/local/openresty/lualib/resty/ && \
    cd /tmp && \
    git clone https://github.com/jkeys089/lua-resty-hmac.git && \
    cd lua-resty-hmac && \
    cp -r lib/resty/* /usr/local/openresty/lualib/resty/ && \
    cd / && rm -rf /tmp/lua-resty-*

# Copy configuration and scripts
COPY conf/nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
COPY lua/ /usr/local/openresty/nginx/lua/
COPY html/ /usr/share/nginx/html/

# Create log directory
RUN mkdir -p /usr/local/openresty/nginx/logs

COPY docker-entrypoint.sh /docker-entrypoint.sh
# Windows checkout CRLF → "exec /docker-entrypoint.sh: no such file or directory" (shebang /bin/sh\r)
RUN sed -i 's/\r$//' /docker-entrypoint.sh && chmod +x /docker-entrypoint.sh

EXPOSE 80 443

ENTRYPOINT ["/bin/sh", "/docker-entrypoint.sh"]
CMD ["/usr/local/openresty/bin/openresty", "-g", "daemon off;"]
