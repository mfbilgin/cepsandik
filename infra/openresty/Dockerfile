FROM openresty/openresty:alpine

# Install dependencies
RUN apk add --no-cache \
    git \
    openssl-dev

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

# Create log directory
RUN mkdir -p /usr/local/openresty/nginx/logs

EXPOSE 80 443

CMD ["/usr/local/openresty/bin/openresty", "-g", "daemon off;"]
