#!/usr/bin/env bash
# 将 docker/nacos/config/*.example 批量导入本地 Nacos（namespace: wisdomhub）。
# 用法：
#   export NACOS_PASSWORD='your-password'
#   bash docker/nacos/import.sh
# 可选环境变量：NACOS_SERVER_ADDR / NACOS_NAMESPACE / NACOS_GROUP / NACOS_USERNAME
set -euo pipefail

SERVER_ADDR="${NACOS_SERVER_ADDR:-127.0.0.1:8848}"
NAMESPACE="${NACOS_NAMESPACE:-wisdomhub}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
USERNAME="${NACOS_USERNAME:-nacos}"
PASSWORD="${NACOS_PASSWORD:?请先 export NACOS_PASSWORD=你的Nacos密码}"

BASE_URL="http://${SERVER_ADDR}/nacos"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="${SCRIPT_DIR}/config"

if [ ! -d "${CONFIG_DIR}" ]; then
  echo "配置目录不存在：${CONFIG_DIR}" >&2
  exit 1
fi

# 1. 登录，提取 accessToken
TOKEN="$(curl -sS -X POST "${BASE_URL}/v3/auth/user/login" \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [ -z "${TOKEN}" ]; then
  echo "登录失败：请检查用户名和密码。" >&2
  exit 1
fi
echo "登录成功：${USERNAME}"

# 2. 逐个发布
shopt -s nullglob
ok=0
total=0
for f in "${CONFIG_DIR}"/*.example; do
  total=$((total + 1))
  dataId="$(basename "${f}" .example)"
  if curl -sS -X POST "${BASE_URL}/v3/admin/cs/config" \
      -H "accessToken: ${TOKEN}" \
      --data-urlencode "dataId=${dataId}" \
      --data-urlencode "groupName=${GROUP}" \
      --data-urlencode "namespaceId=${NAMESPACE}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${f}" > /dev/null; then
    echo "  已发布 ${dataId}"
    ok=$((ok + 1))
  else
    echo "  失败   ${dataId}" >&2
  fi
done

echo "完成：${ok} / ${total} 个配置已导入到命名空间 ${NAMESPACE}。"
