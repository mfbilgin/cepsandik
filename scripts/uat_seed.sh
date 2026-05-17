#!/usr/bin/env bash
# Sprint 5.E UAT seed — 3 guardian-eligible user + community + election publish.
# Email verify dev-bypass YOK → userdb.is_verified=true (postgres exec).
# set -e KULLANILMAZ (curl|python pipe yük altında kırılgan); açık kontrol.
GW="${GW:-http://localhost:8088/v1}"
PW="UatPass123"
TS=$(date +%s)
COMPOSE="docker compose -f /c/Users/PC/Documents/Projects/cepsandik/infra/docker-compose.local.yaml -f /c/Users/PC/Documents/Projects/cepsandik/infra/docker-compose.uat.yaml"

# JSON path extractor (tolerant)
jx() { python -c "import sys,json
try:
 d=json.load(sys.stdin)
 print(d$1)
except Exception as e:
 pass"; }

fail() { echo "!! FAIL: $1"; exit 1; }

echo "== 1. 3 kullanıcı kaydı =="
EMAILS=();
for i in 1 2 3; do
  EMAIL="uat${TS}_g${i}@test.local"
  RESP=$(curl -s -X POST "$GW/auth/register" -H 'Content-Type: application/json' \
    -d "{\"firstName\":\"Uat$i\",\"lastName\":\"Guardian\",\"email\":\"$EMAIL\",\"password\":\"$PW\"}")
  USERID=$(printf '%s' "$RESP" | jx "['data']['id']")
  [ -z "$USERID" ] && fail "kayıt $i: $RESP"
  EMAILS+=("$EMAIL")
  echo "  user$i: $EMAIL  id=$USERID"
done

echo "== 2. Email verification bypass (userdb.is_verified=true) =="
EMAIL_LIST=$(printf "'%s'," "${EMAILS[@]}"); EMAIL_LIST="${EMAIL_LIST%,}"
$COMPOSE exec -T postgres psql -U postgres -d userdb -c \
  "UPDATE users SET is_verified=true WHERE email IN ($EMAIL_LIST);" 2>/dev/null \
  | grep -q UPDATE || $COMPOSE exec -T postgres psql -U postgres -d userdb -c \
  "UPDATE users SET verified=true WHERE email IN ($EMAIL_LIST);" 2>/dev/null
echo "  doğrulandı"

echo "== 3. Login + guardian opt-in (retry'li) =="
TOKENS=()
for idx in 0 1 2; do
  TOK=""
  for attempt in 1 2 3 4 5; do
    RESP=$(curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
      -d "{\"email\":\"${EMAILS[$idx]}\",\"password\":\"$PW\"}")
    TOK=$(printf '%s' "$RESP" | jx "['data']['accessToken']")
    [ -n "$TOK" ] && break
    sleep 2
  done
  [ -z "$TOK" ] && fail "login user$((idx+1)): $RESP"
  TOKENS+=("$TOK")
  curl -s -X PUT "$GW/users/me/push-token" -H "Authorization: Bearer $TOK" \
    -H 'Content-Type: application/json' \
    -d '{"pushToken":"uat-mock-token","guardianEligible":true}' >/dev/null 2>&1
  echo "  user$((idx+1)) login + opt-in OK"
done

echo "== 4. Topluluk (user1 OWNER, PUBLIC) =="
RESP=$(curl -s -X POST "$GW/communities" -H "Authorization: Bearer ${TOKENS[0]}" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"UAT Topluluk $TS\",\"description\":\"Sprint5E\",\"visibility\":\"PUBLIC\"}")
CID=$(printf '%s' "$RESP" | jx "['data']['id']")
[ -z "$CID" ] && fail "topluluk: $RESP"
echo "  communityId=$CID"

echo "== 5. user2,user3 katıl =="
for idx in 1 2; do
  curl -s -X POST "$GW/communities/$CID/members/join" \
    -H "Authorization: Bearer ${TOKENS[$idx]}" -H 'Content-Type: application/json' -d '{}' >/dev/null 2>&1
  echo "  user$((idx+1)) katıldı"
done

echo "== 6. Seçim + 2 aday =="
START=$(python -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(minutes=20)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
END=$(python -c "import datetime;print((datetime.datetime.utcnow()+datetime.timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
EBODY="{\"title\":\"UAT Secim $TS\",\"description\":\"Sprint5E\",\"communityId\":$CID,\"type\":\"SINGLE_CHOICE\",\"participantType\":\"ALL_MEMBERS\",\"maxSelections\":1,\"startTime\":\"$START\",\"endTime\":\"$END\",\"resultsPublic\":true}"
RESP=$(curl -s -X POST "$GW/elections" -H "Authorization: Bearer ${TOKENS[0]}" -H 'Content-Type: application/json' -d "$EBODY")
EID=$(printf '%s' "$RESP" | jx "['data']['id']")
[ -z "$EID" ] && fail "seçim: $RESP"
for c in "Aday Ada" "Aday Bege"; do
  curl -s -X POST "$GW/elections/$EID/candidates" -H "Authorization: Bearer ${TOKENS[0]}" \
    -H 'Content-Type: application/json' -d "{\"name\":\"$c\",\"candidateType\":\"TEXT_OPTION\"}" >/dev/null 2>&1
done
echo "  electionId=$EID start=$START"

echo "== 7. Publish =="
PUB=$(curl -s -X POST "$GW/elections/$EID/publish" -H "Authorization: Bearer ${TOKENS[0]}")
STATUS=$(printf '%s' "$PUB" | jx "['data']['status']")
echo "  status=$STATUS"
[ "$STATUS" != "SCHEDULED" ] && fail "publish: $PUB"

echo ""
echo "================ UAT SEED OK ================"
echo "electionId=$EID communityId=$CID status=$STATUS"
for idx in 0 1 2; do echo "  guardian$((idx+1)): ${EMAILS[$idx]} / $PW"; done
echo "============================================"
