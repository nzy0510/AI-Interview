# InterWise Azure VM 部署指南

本指南用于单台 Azure Ubuntu 虚拟机的内测部署。内测阶段可以先使用公网 IP 访问文字面试；视频面试、麦克风和摄像头能力必须等域名和 HTTPS 完成后再开放给用户。

当前项目包含 Spring Boot、MySQL、Redis、Qdrant、Vue 前端和本地 embedding。内测推荐使用 2 vCPU / 8 GiB 内存级别的 VM，例如 `Standard_B2ms` 或 `Standard_D2s_v3`。如果使用 2 vCPU / 4 GiB 内存，建议额外配置 4 GiB swap。

参考：Azure Linux VM 文档 <https://learn.microsoft.com/azure/virtual-machines/linux/>，Docker Ubuntu 安装文档 <https://docs.docker.com/installation/ubuntulinux/>。

## 1. Azure 服务器准备

在 Azure Portal 中创建虚拟机：

- 资源组：例如 `rg-interwise-test`
- 虚拟机名称：例如 `vm-interwise-test`
- 区域：选择离主要用户较近的区域，例如 Japan East
- 镜像：Ubuntu Server 22.04 LTS 或 24.04 LTS，x64
- 大小：推荐 `Standard_B2ms` 或 `Standard_D2s_v3`
- OS 磁盘：建议 64 GiB 起步，128 GiB 更稳
- 身份验证：SSH 公钥
- 入站端口：只开放 `22` 和 `80`，`443` 后续 HTTPS 再开放

Azure 网络安全组只开放：

- `22`：SSH
- `80`：Web 访问
- `443`：后续配置 HTTPS 时再开放

不要开放 `3306`、`6379`、`6333`、`8080`。MySQL、Redis、Qdrant、后端都只在 Docker 内网访问。

SSH 登录后安装基础工具：

```bash
sudo apt update
sudo apt install -y ca-certificates curl git vim openssl
```

按 Docker 官方 Ubuntu 文档安装 Docker Engine 和 Compose plugin：

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

重新登录 SSH 后确认：

```bash
docker --version
docker compose version
```

建议配置 4 GiB swap：

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

将项目放到服务器目录：

```bash
sudo mkdir -p /opt/interwise
sudo chown -R "$USER:$USER" /opt/interwise
cd /opt/interwise
git clone -b codex/azure-deployment-hardening https://github.com/nzy0510/AI-Interview .
```

## 2. 配置环境变量

```bash
cp .env.prod.example .env
nano .env
```

必须替换所有 `replace_with_...` 值。`APP_CORS_ALLOWED_ORIGINS` 和 `MCP_ALLOWED_ORIGINS` 在没有域名时填写 Azure VM 公网 IP 来源，例如：

```env
APP_CORS_ALLOWED_ORIGINS=http://52.140.216.52
MCP_ALLOWED_ORIGINS=http://52.140.216.52
```

`JWT_SIGN_KEY`、`DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`QUESTION_BANK_ADMIN_TOKEN`、`MCP_READ_TOKEN` 必须使用强随机值，不要复用示例内容。可以在服务器上生成：

```bash
openssl rand -base64 32
```

邮件验证码需要 SMTP 授权码，不是邮箱登录密码。QQ 邮箱需要先在邮箱设置中开启 SMTP 服务。

配置完成后保护 `.env` 权限：

```bash
chmod 600 .env
```

检查占位符是否已经替换完：

```bash
grep -n "replace_with" .env
```

如果没有输出，说明占位符已经替换完。

## 3. 构建并启动

先校验 Compose 配置：

```bash
docker compose --env-file .env -f docker-compose.prod.yml config
```

启动服务：

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --build
docker compose --env-file .env -f docker-compose.prod.yml ps
docker compose --env-file .env -f docker-compose.prod.yml logs --tail=100 backend
```

首次启动会初始化 MySQL、导入 JSON 题库并同步 Qdrant 向量，可能需要几分钟。只要 `backend` 日志没有持续报错，等待初始化完成即可。

启动后访问：

```text
http://Azure公网IP
```

生产 Compose 只暴露前端 `80` 端口；后端、MySQL、Redis、Qdrant 只在 Docker 网络内访问。

## 4. 内测验收

依次验证：

- 注册、登录、退出登录
- 文字面试启动与 SSE 流式回复
- 简历上传与解析
- 面试结束后生成报告
- 历史记录、AI Mentor 页面
- 头像上传和刷新后仍可访问
- `docker compose --env-file .env -f docker-compose.prod.yml restart` 后数据和上传文件不丢失

公网 IP + HTTP 阶段不验收视频面试。浏览器摄像头和麦克风要求 HTTPS 或 localhost，等域名和 HTTPS 完成后再开放。

## 5. 日常更新

服务器上的代码更新：

```bash
cd /opt/interwise
git pull
docker compose --env-file .env -f docker-compose.prod.yml up -d --build
docker compose --env-file .env -f docker-compose.prod.yml ps
```

只查看状态：

```bash
docker compose --env-file .env -f docker-compose.prod.yml ps
```

查看后端日志：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f backend
```

只重启后端：

```bash
docker compose --env-file .env -f docker-compose.prod.yml restart backend
```

## 6. HTTPS 与视频面试

视频面试上线前必须完成：

- 购买域名并将 `A` 记录解析到 Azure VM 公网 IP
- Azure 网络安全组开放 `443`
- 配置 HTTPS 证书
- 将 `.env` 中的来源改成 HTTPS 域名，例如：

```env
APP_CORS_ALLOWED_ORIGINS=https://interwise.example.com
MCP_ALLOWED_ORIGINS=https://interwise.example.com
DOMAIN_NAME=interwise.example.com
FRONTEND_HTTP_BIND=127.0.0.1:8080
```

当前生产 Compose 支持可选的 Caddy HTTPS 入口。启用 HTTPS 时，前端容器只绑定到宿主机本地端口 `127.0.0.1:8080`，公网 `80` 和 `443` 由 Caddy 接管并自动申请/续期证书。测试阶段 Caddy 会同时保留 HTTP 访问，不强制跳转 HTTPS，方便在国内网络或代理环境下排查 443/TLS 问题：

```bash
cd /opt/interwise
docker compose --env-file .env -f docker-compose.prod.yml --profile https up -d
docker compose --env-file .env -f docker-compose.prod.yml --profile https ps
docker compose --env-file .env -f docker-compose.prod.yml --profile https logs --tail=100 caddy
```

如果需要回退到公网 IP + HTTP，先停止 Caddy profile，删除或改回 `.env` 中的 HTTPS 变量，再重新启动普通 Compose。

## 7. 备份与恢复

上线后定期备份 MySQL：

```bash
cd /opt/interwise
mkdir -p backups
set -a
source .env
set +a
docker compose --env-file .env -f docker-compose.prod.yml exec -T db mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$DB_NAME" > "backups/interwise-$(date +%F).sql"
find backups -name "interwise-*.sql" -mtime +7 -delete
```

备份上传文件：

```bash
tar -czf "backups/uploads-$(date +%F).tar.gz" uploads
```

`.env`、`mysql_data/`、`redis_data/`、`qdrant_data/`、`uploads/`、`backups/` 不要提交 Git。

## 8. 正式商用前待补

- 轮换曾经暴露过的 DeepSeek Key、SMTP 授权码、JWT 和管理 token
- 购买域名、DNS 解析、HTTPS
- 稳定备份和异地保存
- 云数据库、云 Redis、对象存储
- 接口限流、防刷、日志监控和异常告警
- 隐私政策、用户协议、数据删除和导出机制
