#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080/api/v1}"
USERNAME="${LEDGER_DEMO_USERNAME:-ledger-demo}"
PASSWORD="${LEDGER_DEMO_PASSWORD:-LedgerDemo2026!}"
YEAR="${LEDGER_DEMO_YEAR:-$(date +%Y)}"
MONTH="${LEDGER_DEMO_MONTH:-$(date +%m)}"

json_post() {
  curl -sS -X POST "$1" -H 'Content-Type: application/json' "${@:2}"
}

login_payload=$(json_post "$API_BASE/auth/login" --data "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
token=$(jq -r '.data.accessToken // empty' <<<"$login_payload")
if [[ -z "$token" ]]; then
  register_payload=$(json_post "$API_BASE/auth/register" --data "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"账本演示\"}")
  token=$(jq -r '.data.accessToken // empty' <<<"$register_payload")
fi
[[ -n "$token" ]] || { echo "无法登录或创建 $USERNAME" >&2; exit 1; }

api_get() { curl -sS "$API_BASE$1" -H "Authorization: Bearer $token"; }
api_post() { curl -sS -X POST "$API_BASE$1" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data "$2"; }

account_id() {
  local name="$1" type="$2" opening="$3" id
  id=$(api_get '/ledger/accounts' | jq -r --arg name "$name" '.data[] | select(.name == $name) | .id' | head -1)
  if [[ -z "$id" ]]; then
    id=$(api_post '/ledger/accounts' "{\"name\":\"$name\",\"accountType\":\"$type\",\"openingBalance\":$opening}" | jq -r '.data.id')
  fi
  printf '%s' "$id"
}

category_id() {
  local name="$1" kind="$2" parent_id="${3:-}" id
  id=$(api_get '/ledger/categories' | jq -r --arg name "$name" --arg kind "$kind" --arg parent "$parent_id" '.data[] | select(.name == $name and .kind == $kind and ((.parentId // "") | tostring) == $parent) | .id' | head -1)
  if [[ -z "$id" ]]; then
    local body="{\"name\":\"$name\",\"kind\":\"$kind\"}"
    [[ -n "$parent_id" ]] && body="{\"name\":\"$name\",\"kind\":\"$kind\",\"parentId\":$parent_id}"
    id=$(api_post '/ledger/categories' "$body" | jq -r '.data.id')
  fi
  printf '%s' "$id"
}

cash=$(account_id '现金' cash 500)
bank=$(account_id '招商银行卡' bank 3200)
wallet=$(account_id '支付宝' wallet 280)
food=$(category_id '餐饮' EXPENSE)
transport=$(category_id '交通' EXPENSE)
housing=$(category_id '居住' EXPENSE)
shopping=$(category_id '购物' EXPENSE)
salary=$(category_id '工资' INCOME)
side_income=$(category_id '副业' INCOME)
breakfast=$(category_id '早餐' EXPENSE "$food")
coffee=$(category_id '咖啡' EXPENSE "$food")

transaction() {
  local key="$1" kind="$2" date="$3" account="$4" category="$5" amount="$6" payee="$7"
  curl -sS -X POST "$API_BASE/ledger/transactions" -H "Authorization: Bearer $token" -H "Idempotency-Key: seed-$key" -H 'Content-Type: application/json' --data "{\"kind\":\"$kind\",\"occurredOn\":\"$date\",\"accountId\":$account,\"categoryId\":$category,\"amount\":$amount,\"payee\":\"$payee\",\"source\":\"debug-seed\"}" >/dev/null
}

for month in 01 02 03 04 05 06 07 08; do
  transaction "salary-$YEAR-$month" INCOME "$YEAR-$month-05" "$bank" "$salary" 11650 '工资到账'
  transaction "housing-$YEAR-$month" EXPENSE "$YEAR-$month-03" "$bank" "$housing" 2500 '房租'
  transaction "food-$YEAR-$month" EXPENSE "$YEAR-$month-12" "$wallet" "$food" 268 '日常餐饮'
  transaction "transport-$YEAR-$month" EXPENSE "$YEAR-$month-18" "$wallet" "$transport" 96 '通勤出行'
done

transaction "salary-$YEAR-$MONTH" INCOME "$YEAR-$MONTH-05" "$bank" "$salary" 11650 '工资到账'
transaction "side-$YEAR-$MONTH" INCOME "$YEAR-$MONTH-09" "$wallet" "$side_income" 680 '设计稿结算'
transaction "rent-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-03" "$bank" "$housing" 2500 '房租'
transaction "breakfast-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-07" "$wallet" "$breakfast" 18 '早餐铺'
transaction "coffee-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-08" "$wallet" "$coffee" 32 '咖啡'
transaction "dinner-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-11" "$wallet" "$food" 86 '家庭晚餐'
transaction "metro-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-14" "$wallet" "$transport" 12 '地铁'
transaction "groceries-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-16" "$cash" "$shopping" 236 '超市采购'
transaction "taxi-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-20" "$wallet" "$transport" 42 '打车'
transaction "lunch-$YEAR-$MONTH" EXPENSE "$YEAR-$MONTH-23" "$wallet" "$food" 46 '工作日午餐'

api_post '/ledger/budgets' "{\"monthKey\":\"$YEAR-$MONTH\",\"categoryId\":$food,\"amount\":900}" >/dev/null
api_post '/ledger/budgets' "{\"monthKey\":\"$YEAR-$MONTH\",\"categoryId\":$transport,\"amount\":300}" >/dev/null
api_post '/ledger/budgets' "{\"monthKey\":\"$YEAR-$MONTH\",\"categoryId\":$shopping,\"amount\":500}" >/dev/null

recurring_id=$(api_get '/ledger/recurring' | jq -r '.data[] | select(.title == "本地联调会员") | .id' | head -1)
if [[ -z "$recurring_id" ]]; then
  api_post '/ledger/recurring' "{\"title\":\"本地联调会员\",\"accountId\":$wallet,\"categoryId\":$food,\"amount\":38,\"frequency\":\"MONTHLY\",\"nextDue\":\"$YEAR-$MONTH-08\"}" >/dev/null
fi
api_post '/ledger/recurring/run' '{}' >/dev/null

echo "账本调试数据已就绪"
echo "账号: $USERNAME"
echo "密码: $PASSWORD"
echo "年份: ${YEAR}，月份: ${MONTH}"
