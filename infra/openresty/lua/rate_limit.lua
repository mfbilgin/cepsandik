-- Rate Limiting using Token Bucket Algorithm
-- Uses nginx shared dict for distributed rate limiting

local _M = {}

-- Faz 3.11 — PROD-makul varsayılanlar + env ile override (UAT bunları
-- RL_* env'leriyle yükseltir; kod değişmeden). Gerekçe:
--   auth: brute-force koruması (login/register/reset) — IP başına dar.
--   protected: per-USER (IP değil) sayılır; guardian ceremony 3-round +
--     tally challenge retry-loop (~10×) çağrı-yoğun → 50 çok dar olurdu,
--     100/dk/kullanıcı meşru akışa yer bırakır, kötüye kullanımı sınırlar.
--   default: kimliksiz (public /audit-record vb.) IP başına orta.
-- Çift-oy bütünlüğü rate-limit DEĞİL kripto nullifier + DB unique (3.12) ile.
local function env_num(name, fallback)
    local v = tonumber(os.getenv(name) or "")
    if v and v > 0 then return v end
    return fallback
end

local RATE_LIMITS = {
    auth      = { limit = env_num("RL_AUTH", 15),       window = 60 },
    protected = { limit = env_num("RL_PROTECTED", 100), window = 60 },
    default   = { limit = env_num("RL_DEFAULT", 60),    window = 60 }
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
