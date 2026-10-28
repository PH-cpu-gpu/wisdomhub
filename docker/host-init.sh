#!/usr/bin/env bash
# 2 核 4GB 宿主机初始化：zram 压缩内存 + 磁盘 swap 兜底 + 内核参数 + Docker 日志轮转。
# 用法：sudo bash docker/host-init.sh
# 可重复执行。
set -euo pipefail

ZRAM_PERCENT="${ZRAM_PERCENT:-50}"
SWAPFILE_MB="${SWAPFILE_MB:-2048}"

[ "$(id -u)" -eq 0 ] || { echo "需要 root" >&2; exit 1; }

# ---------------------------------------------------------------- 1. zram
# 压缩内存，速度比磁盘 swap 快一到两个数量级。JVM 堆里大量冷页压缩比很高。
if ! command -v zramctl >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y -qq zram-tools
fi
cat >/etc/default/zramswap <<EOF
ALGO=lz4
PERCENT=${ZRAM_PERCENT}
PRIORITY=100
EOF
systemctl enable zramswap >/dev/null 2>&1 || true
systemctl restart zramswap

# ------------------------------------------------------- 2. 磁盘 swap 兜底
# 低优先级，只在 zram 满了之后才用。
if [ ! -f /swapfile ]; then
  fallocate -l "${SWAPFILE_MB}M" /swapfile
fi
chmod 600 /swapfile
if ! blkid /swapfile >/dev/null 2>&1; then
  mkswap /swapfile >/dev/null
fi
grep -q '^/swapfile[[:space:]]' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
swapon --show=NAME --noheadings | grep -qx '/swapfile' || swapon /swapfile

# ---------------------------------------------------------- 3. 内核参数
cat >/etc/sysctl.d/99-wisdomhub.conf <<'EOF'
# 有 zram 时应该激进换出冷页，内核按优先级选设备，磁盘 swap 只是兜底
vm.swappiness=100
vm.page-cluster=0
# Elasticsearch 不设会直接拒绝启动
vm.max_map_count=262144
# Redis fork 兜底
vm.overcommit_memory=1
net.core.somaxconn=1024
EOF
sysctl --system >/dev/null

# --------------------------------------------------- 4. Docker 日志轮转
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  cat >/etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
  systemctl restart docker
else
  echo "已存在 /etc/docker/daemon.json，请自行加入 log-driver/log-opts" >&2
fi

echo "--- 完成 ---"
free -m
swapon --show
zramctl
