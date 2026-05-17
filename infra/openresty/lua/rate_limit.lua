-- Rate Limiting using Token Bucket Algorithm
-- Uses nginx shared dict for distributed rate limiting

local _M = {}

-- Configuration
-- NOT (Sprint 5.E UAT): 3 emülatör tek Docker bridge IP'sinden gelir
-- (paylaşımlı IP bucket) ve dağıtık ceremony 3-round çok sayıda çağrı
-- yapar. UAT/geliştirme için limitler yükseltildi. Prod'da düşürülmeli.
local RATE_LIMITS = {
    -- Auth endpoints (login, register, password reset)
    auth = { limit = 1000, window = 60 },
    -- Protected endpoints (authenticated users)
    protected = { limit = 2000, window = 60 },
    -- Default (IP-based for unknown endpoints)
    default = { limit = 1000, window = 60 }
}

local shared_dict = ngx.shared.rate_limit

-- Get client identifier (IP or User ID)
local function get_client_key(endpoint_type)
    local user_id = ngx.req.get_headers()["X-User-Id"]
    local client_ip = ngx.var.remote_addr
    
    if user_id and endpoint_type == "protected" then
        return "ratelimit:user:" .. user_id
    else
        return "ratelimit:ip:" .. client_ip
    end
end

-- Check rate limit using sliding window
function _M.check(endpoint_type)
    local config = RATE_LIMITS[endpoint_type] or RATE_LIMITS.default
    local key = get_client_key(endpoint_type)
    local limit = config.limit
    local window = config.window
    
    local now = ngx.time()
    local window_key = key .. ":" .. math.floor(now / window)
    
    -- Get current count
    local count, err = shared_dict:get(window_key)
    if err then
        ngx.log(ngx.ERR, "Rate limit dict error: ", err)
        return true  -- Allow on error (fail-open)
    end
    
    count = count or 0
    
    if count >= limit then
        -- Rate limit exceeded
        local retry_after = window - (now % window)
        ngx.header["Retry-After"] = retry_after
        ngx.header["X-RateLimit-Limit"] = limit
        ngx.header["X-RateLimit-Remaining"] = 0
        ngx.header["X-RateLimit-Reset"] = math.floor(now / window) * window + window
        
        ngx.status = 429
        ngx.header["Content-Type"] = "application/json"
        ngx.say('{"success":false,"message":"Çok fazla istek gönderildi. Lütfen ' .. retry_after .. ' saniye bekleyin."}')
        return ngx.exit(429)
    end
    
    -- Increment counter
    local newval, err = shared_dict:incr(window_key, 1, 0, window)
    if err then
        ngx.log(ngx.ERR, "Rate limit incr error: ", err)
    end
    
    -- Set rate limit headers
    ngx.header["X-RateLimit-Limit"] = limit
    ngx.header["X-RateLimit-Remaining"] = math.max(0, limit - (newval or count + 1))
    ngx.header["X-RateLimit-Reset"] = math.floor(now / window) * window + window
    
    return true
end

return _M
