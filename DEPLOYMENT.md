# InterWise Oracle Cloud Always Free 部署指南

本指南用于单台 Oracle Cloud Always Free 云服务器的内测部署。内测阶段可以先使用公网 IP 访问文字面试；视频面试、麦克风和摄像头能力必须等域名和 HTTPS 完成后再开放给用户。

Oracle 官方 Always Free 文档说明，Ampere A1 `VM.Standard.A1.Flex` 在免费额度内可按总量分配到 4 OCPU 和 24 GB 内存。当前项目包含 Spring Boot、MySQL、Redis、Qdrant、Vue 前端和本地 embedding，建议优先创建一台 A1 Flex，至少 2 OCPU / 8 GB，能申请到资源时建议 4 OCPU / 16-24 GB。

参考：Oracle Always Free 资源说明 <https://docs.oracle.com/iaas/Content/FreeTier/resourceref.htm>，Docker Ubuntu 安装文档 <https://docs.docker.com/installation/ubuntulinux/>。

## 1. Oracle 服务器准备

在 Oracle Cloud Console 中创建实例：

- 镜像：Ubuntu 22.04 LTS 或 Ubuntu 24.04 LTS，选择 Always Free Eligible
- Shape：`VM.Standard.A1.Flex`
- OCPU / 内存：最低 2 OCPU / 8 GB，推荐 4 OCPU / 16-24 GB
- Boot volume：建议 100 GB 起步，题库、镜像、数据库和上传文件都会占用磁盘

OCI VCN 安全列表或网络安全组只开放：

- `22`：SSH
- `80`：Web 访问
- `443`：后续配置 HTTPS 时再开放

不要开放 `3306`、`6379`、`6333`、`8080`。MySQL、Redis、Qdrant、后端都只在 Docker 内网访问。

SSH 登录后安装基础工具：

```bash
sudo apt update
sudo apt install -y ca-certificates curl git vim
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

将项目放到服务器目录：

```bash
mkdir -p /opt/interwise
cd /opt/interwise
git clone <your-repo-url> .
```

## 2. 配置环境变量

```bash
cp .env.prod.example .env
vim .env
```

必须替换所有 `replace_with_...` 值。`APP_CORS_ALLOWED_ORIGINS` 在没有域名时填写公网 IP 来源，例如：

```env
APP_CORS_ALLOWED_ORIGINS=http://123.123.123.123
MCP_ALLOWED_ORIGINS=http://123.123.123.123
```

`JWT_SIGN_KEY`、`DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`QUESTION_BANK_ADMIN_TOKEN`、`MCP_READ_TOKEN` 必须使用强随机值，不要复用示例内容。可以在服务器上生成：

```bash
openssl rand -base64 32
```

邮件验证码需要 SMTP 授权码，不是邮箱登录密码。QQ 邮箱需要先在邮箱设置中开启 SMTP 服务。

## 3. 构建并启动

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

首次启动会初始化 MySQL、导入 JSON 题库并同步 Qdrant 向量，Oracle A1 免费实例上可能需要几分钟。只要 `backend` 日志没有持续报错，等待初始化完成即可。

启动后访问：

```text
http://服务器公网IP
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
- `docker compose -f docker-compose.prod.yml restart` 后数据和上传文件不丢失

公网 IP + HTTP 阶段不验收视频面试。浏览器摄像头和麦克风要求 HTTPS 或 localhost，等域名和 HTTPS 完成后再开放。

## 5. HTTPS 与视频面试

视频面试上线前必须完成：

- 购买域名并将 `A` 记录解析到 Oracle 公网 IP
- OCI 安全列表开放 `443`
- 配置 HTTPS 证书
- 将 `.env` 中的来源改成 HTTPS 域名，例如：

```env
APP_CORS_ALLOWED_ORIGINS=https://interwise.example.com
MCP_ALLOWED_ORIGINS=https://interwise.example.com
```

当前 `frontend/nginx.conf` 只提供容器内 HTTP。正式 HTTPS 可以在宿主机前置 Caddy / Nginx / Traefik 做 TLS 终止，再反向代理到前端容器 `80`。

## 6. 备份与更新

上线后每天备份 MySQL：

```bash
mkdir -p backups
docker compose -f docker-compose.prod.yml exec -T db sh -c \
  'mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  > "backups/interwise-$(date +%F).sql"
find backups -name "interwise-*.sql" -mtime +7 -delete
```

更新前先备份，再拉代码重建：

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f backend
```

`.env`、`mysql_data/`、`redis_data/`、`qdrant_data/`、`uploads/`、`backups/` 不要提交 Git。

## 7. 正式商用前待补

- 购买域名、备案、DNS 解析、HTTPS
- 云数据库、云 Redis、对象存储
- 接口限流、防刷、日志监控和异常告警
- 隐私政策、用户协议、数据删除和导出机制
