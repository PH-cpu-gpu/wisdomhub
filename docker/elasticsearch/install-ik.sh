#!/usr/bin/env bash
# =============================================================================
# 下载并解压 IK 中文分词插件到 docker/elasticsearch/plugins/ik/
#
#   用法：bash docker/elasticsearch/install-ik.sh [ES版本]
#         不传版本则默认 8.18.8，必须与 docker-compose.yml 里 ES 镜像的版本一致。
#
# 背景（2025 年起的分发变更，旧教程里的 GitHub Releases 地址已失效）：
#   仓库迁移：  medcl/elasticsearch-analysis-ik  →  infinilabs/analysis-ik
#   官方说明：  插件包不再发布到 GitHub Releases，改由 get.infini.cloud 分发
#
# 装完需重启 ES 才生效：
#   docker compose up -d --force-recreate elasticsearch
# =============================================================================

set -euo pipefail

ES_VERSION="${1:-8.18.8}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${SCRIPT_DIR}/plugins/ik"
URL="https://get.infini.cloud/elasticsearch/analysis-ik/${ES_VERSION}"
TMP_ZIP="${TMPDIR:-/tmp}/ik-${ES_VERSION}-$$.zip"

echo "==> 下载 IK ${ES_VERSION}"
echo "    ${URL}"
mkdir -p "${DEST}"
curl -fL --retry 3 --max-time 300 -o "${TMP_ZIP}" "${URL}"

echo "==> 解压到 ${DEST}"
if command -v unzip >/dev/null 2>&1; then
  unzip -oq "${TMP_ZIP}" -d "${DEST}"
else
  python -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "${TMP_ZIP}" "${DEST}"
fi
rm -f "${TMP_ZIP}"

# 结构校验：plugin-descriptor.properties 必须在插件目录顶层，否则 ES 拒绝加载
if [ ! -f "${DEST}/plugin-descriptor.properties" ]; then
  echo "✗ 解压后找不到 plugin-descriptor.properties，目录结构不对" >&2
  echo "  若被多套了一层目录，请把内层内容上移到 ${DEST} 下" >&2
  exit 1
fi

echo "==> 插件信息"
grep -E '^(name|version|elasticsearch.version)' "${DEST}/plugin-descriptor.properties"

echo "==> 完成。重启 ES 生效："
echo "    docker compose up -d --force-recreate elasticsearch"
