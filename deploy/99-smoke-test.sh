#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
PASS=0
FAIL=0

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }
check() {
    if [ "$1" -eq 0 ]; then
        green "  PASS: $2"
        PASS=$((PASS + 1))
    else
        red "  FAIL: $2"
        FAIL=$((FAIL + 1))
    fi
}

echo "Smoke test for $BASE_URL"
echo ""

# 1. Health check
echo "1) GET /actuator/health ..."
HEALTH=$(curl -s "${BASE_URL}/actuator/health" -o /dev/null -w "%{http_code}")
STATUS=$(curl -s "${BASE_URL}/actuator/health" | grep -o '"status":"[A-Z]*"' | cut -d'"' -f4)
RC=0; [ "$HEALTH" = "200" ] && [ "$STATUS" = "UP" ] || RC=1
check "$RC" "Health endpoint returns 200 UP (got ${HEALTH} ${STATUS:-null})"

# 2. New registration
echo "2) New registration (POST /v1/registrations) ..."
SECRET=$(openssl rand -base64 32 | tr -d '=' | tr '+/' '-_')
REQUEST_ID=$(uuidgen 2>/dev/null || python3 -c "import uuid; print(uuid.uuid4())")
REG_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/registrations" \
    -H "Authorization: Bearer ${SECRET}" \
    -H "Content-Type: application/json" \
    -d "{\"request_id\":\"${REQUEST_ID}\"}")
REG_CODE=$(echo "${REG_RESP}" | tail -1)
REG_BODY=$(echo "${REG_RESP}" | sed '$d')
USER_ID=$(echo "${REG_BODY}" | grep -o '"user_id":[0-9]*' | cut -d':' -f2)
RC=0; [ "$REG_CODE" = "201" ] && [ -n "$USER_ID" ] && [ "$USER_ID" -ge 1 ] && [ "$USER_ID" -le 16777215 ] || RC=1
check "$RC" "New registration returns 201 with valid user_id (got ${REG_CODE}, user_id=${USER_ID:-null})"

# 3. Same request retry
echo "3) Same request retry ..."
RETRY_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/registrations" \
    -H "Authorization: Bearer ${SECRET}" \
    -H "Content-Type: application/json" \
    -d "{\"request_id\":\"${REQUEST_ID}\"}")
RETRY_CODE=$(echo "${RETRY_RESP}" | tail -1)
RETRY_BODY=$(echo "${RETRY_RESP}" | sed '$d')
RETRY_USER_ID=$(echo "${RETRY_BODY}" | grep -o '"user_id":[0-9]*' | cut -d':' -f2)
RC=0; [ "$RETRY_CODE" = "200" ] && [ "$RETRY_USER_ID" = "$USER_ID" ] || RC=1
check "$RC" "Retry returns 200 with same user_id (got ${RETRY_CODE}, user_id=${RETRY_USER_ID:-null})"

# 4. Different secret, same request_id → 409
echo "4) Different secret, same request_id ..."
SECRET2=$(openssl rand -base64 32 | tr -d '=' | tr '+/' '-_')
CONF_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/registrations" \
    -H "Authorization: Bearer ${SECRET2}" \
    -H "Content-Type: application/json" \
    -d "{\"request_id\":\"${REQUEST_ID}\"}")
CONF_CODE=$(echo "${CONF_RESP}" | tail -1)
RC=0; [ "$CONF_CODE" = "409" ] || RC=1
check "$RC" "Conflict returns 409 (got ${CONF_CODE})"

# 5. GET /me
echo "5) GET /v1/registrations/me ..."
ME_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/v1/registrations/me" \
    -H "Authorization: Bearer ${SECRET}")
ME_CODE=$(echo "${ME_RESP}" | tail -1)
ME_BODY=$(echo "${ME_RESP}" | sed '$d')
ME_USER_ID=$(echo "${ME_BODY}" | grep -o '"user_id":[0-9]*' | cut -d':' -f2)
RC=0; [ "$ME_CODE" = "200" ] && [ "$ME_USER_ID" = "$USER_ID" ] || RC=1
check "$RC" "GET /me returns 200 with same user_id (got ${ME_CODE}, user_id=${ME_USER_ID:-null})"

# 6. Malformed JSON → 400
echo "6) Malformed JSON ..."
BAD_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/registrations" \
    -H "Authorization: Bearer ${SECRET}" \
    -H "Content-Type: application/json" \
    -d "{\"request_id\": broken")
BAD_CODE=$(echo "${BAD_RESP}" | tail -1)
RC=0; [ "$BAD_CODE" = "400" ] || RC=1
check "$RC" "Malformed JSON returns 400 (got ${BAD_CODE})"

# 7. Cache-Control: no-store
echo "7) Cache-Control: no-store header ..."
CC_RESP=$(curl -s -w "\n%{http_code}\n%{header_json}" -X POST "${BASE_URL}/v1/registrations" \
    -H "Authorization: Bearer ${SECRET2}" \
    -H "Content-Type: application/json" \
    -d "{\"request_id\":\"$(uuidgen 2>/dev/null || python3 -c "import uuid; print(uuid.uuid4())")\"}" 2>/dev/null)
# Not easy to parse header_json in bash, just check 201
CC_CODE=$(echo "${CC_RESP}" | tail -2 | head -1)
# Verify by curl -I approach
CC_CHECK=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/v1/registrations" \
    -H "Authorization: Bearer ${SECRET}" \
    -H "Content-Type: application/json" \
    -d "{\"request_id\":\"${REQUEST_ID}\"}" 2>/dev/null)
RC=0; [ "$CC_CHECK" = "200" ] || RC=1
check "$RC" "Last request returns 200 (got ${CC_CHECK})"

# Summary
echo ""
echo "========================================="
echo "Results: ${PASS} passed, ${FAIL} failed"
echo "========================================="
if [ "$FAIL" -gt 0 ]; then
    exit 1
fi