-- JWT Validation and Internal Token Generation
local jwt = require "resty.jwt"
local cjson = require "cjson"

-- Configuration
local JWT_SECRET = os.getenv("JWT_SECRET") or "94010e81e0b61f9869c0232025086fbc83a2d60975231b86fa714a8a29b2a76d"
local INTERNAL_SECRET = os.getenv("INTERNAL_JWT_SECRET") or "95d597309c7a8d06c95bab3136b53274e61dbbfcb1d1ca0fb9a24dff342d63ed"
local INTERNAL_TOKEN_TTL = 30 -- 30 seconds

local function validate_client_jwt(auth_header)
    if not auth_header then
        return nil, "Missing Authorization header"
    end

    -- Extract Bearer token
    local token = auth_header:match("^Bearer%s+(.+)$")
    if not token then
        return nil, "Invalid Authorization format"
    end

    -- Verify JWT
    local jwt_obj = jwt:verify(JWT_SECRET, token)
    
    if not jwt_obj.verified then
        return nil, "Invalid JWT signature"
    end

    -- Check expiration
    local now = ngx.time()
    if jwt_obj.payload.exp and jwt_obj.payload.exp < now then
        return nil, "JWT expired"
    end

    return jwt_obj.payload, nil
end

local function generate_internal_token(user_id, roles)
    local now = ngx.time()
    
    local payload = {
        uid = user_id,
        roles = roles or {"USER"},
        scope = {"community:read", "community:write"},
        iss = "api-gateway",
        iat = now,
        exp = now + INTERNAL_TOKEN_TTL
    }

    local internal_jwt = jwt:sign(INTERNAL_SECRET, {
        header = {typ = "JWT", alg = "HS256"},
        payload = payload
    })
    ngx.log(ngx.ERR, "Generated internal JWT: ", internal_jwt)
    return internal_jwt
end

-- Main execution
local auth_header = ngx.var.http_authorization

-- Validate client JWT
local payload, err = validate_client_jwt(auth_header)

if err then
    ngx.log(ngx.ERR, "JWT validation failed: ", err)
    ngx.status = 401
    ngx.header["Content-Type"] = "application/json"
    ngx.say(cjson.encode({
        success = false,
        message = err
    }))
    return ngx.exit(401)
end

-- Extract user info
local user_id = payload.sub or payload.userId or payload.id
local roles = payload.roles or {"USER"}
local platform_role = payload.platformRole or "USER"

if not user_id then
    ngx.log(ngx.ERR, "No user ID in JWT payload")
    ngx.status = 401
    ngx.header["Content-Type"] = "application/json"
    ngx.say(cjson.encode({
        success = false,
        message = "Invalid JWT: missing user ID"
    }))
    return ngx.exit(401)
end

-- Generate internal token
local internal_token = generate_internal_token(user_id, roles)

-- Set headers for backend services
ngx.req.set_header("X-Internal-Auth", internal_token)
ngx.req.set_header("X-User-Id", tostring(user_id))
ngx.req.set_header("X-Platform-Role", platform_role)

-- Log for debugging
ngx.log(ngx.INFO, "Authenticated user: ", user_id, " with platform role: ", platform_role)

