# InterWise 内测云端部署指南

本指南用于单台国内云服务器的内测部署。内测阶段先使用公网 IP 访问，正式商用前再补域名、备案、HTTPS、云数据库、对象存储、限流和监控。

## 1. 服务器准备

推荐 Ubuntu 22.04 LTS，2 核 4G 起步；如果多人同时面试，建议 4 核 8G。安全组只开放：

- `22`：SSH
- `80`：Web 访问

不要开放 `3306`、`6379`、`8080`。

安装 Docker、Docker Compose 和 Git 后，将项目放到服务器目录，例如：

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
```

`JWT_SIGN_KEY`、`DB_PASSWORD`、`MYSQL_ROOT_PASSWORD` 必须使用强随机值，不要复用示例内容。

## 3. 构建并启动

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

启动后访问：

```text
http://服务器公网IP
```

生产 Compose 只暴露前端 `80` 端口；后端、MySQL、Redis 只在 Docker 网络内访问。

## 4. 内测验收

依次验证：

- 注册、登录、退出登录
- 文字面试启动与 SSE 流式回复
- 简历上传与解析
- 面试结束后生成报告
- 历史记录、AI Mentor 页面
- 头像上传和刷新后仍可访问
- `docker compose -f docker-compose.prod.yml restart` 后数据和上传文件不丢失

公网 IP + HTTP 阶段不验收视频面试。浏览器摄像头和麦克风通常要求 HTTPS 或 localhost，等域名和 HTTPS 完成后再开放。

## 5. 备份与更新

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

`.env`、`mysql_data/`、`redis_data/`、`uploads/` 不要提交 Git。

## 6. 正式商用前待补

- 购买域名、备案、DNS 解析、HTTPS
- 密码哈希升级到 BCrypt 或 Argon2
- 云数据库、云 Redis、对象存储
- 接口限流、防刷、日志监控和异常告警
- 隐私政策、用户协议、数据删除和导出机制
