# InterWise Azure 运维说明

本文档用于记录 InterWise 在 Azure Ubuntu VM 上部署后的日常维护方式。当前部署模式是单台虚拟机运行 Docker Compose，适合内测、演示和早期用户验证。

当前服务器信息：

- Azure VM：`vm-interwise-test`
- 用户：`azureuser`
- 项目目录：`/opt/interwise`
- 访问地址：`https://interwise.japaneast.cloudapp.azure.com`
- Compose 文件：`docker-compose.prod.yml`
- 生产环境变量：`.env`

> `.env` 包含数据库密码、JWT 密钥、DeepSeek Key、邮箱 SMTP 授权码等敏感信息，不要提交到 GitHub，也不要发到聊天或截图中。

## 1. Azure 门户和 Ubuntu 终端分别负责什么

Azure 门户主要负责“服务器资源”：

- 启动、停止、重启 VM
- 查看公网 IP
- 查看 CPU、内存、网络、磁盘监控
- 开放或关闭端口，例如 `22`、`80`、`443`
- 设置预算、费用告警、自动关闭
- 删除资源组以停止继续计费

Ubuntu 终端主要负责“项目运行”：

- 拉取 GitHub 最新代码
- 修改 `.env`
- 启动、停止、重启 Docker Compose 服务
- 查看前端、后端、数据库、Redis、Qdrant 日志
- 做数据备份和恢复

当前项目不是通过 Azure 门户直接托管源码，而是 Azure VM 提供一台 Linux 服务器，项目在服务器内通过 Docker Compose 运行。

## 2. 常用命令解释

进入项目目录：

```bash
cd /opt/interwise
```

含义：切换到服务器上的项目部署目录。后续 `docker compose` 命令都应该在这个目录里执行。

查看容器状态：

```bash
docker compose --env-file .env -f docker-compose.prod.yml ps
```

含义：

- `docker compose`：使用 Docker Compose 管理多容器应用
- `--env-file .env`：读取生产环境变量
- `-f docker-compose.prod.yml`：指定生产 Compose 文件
- `ps`：查看服务是否运行

正常应看到这些服务为 `Up`：

- `interview-frontend`
- `interview-backend`
- `interview-db`
- `interview-redis`
- `interview-qdrant`
- `interview-caddy`（启用 HTTPS profile 时）

启动或重新部署（当前 HTTPS profile）：

```bash
docker compose --env-file .env -f docker-compose.prod.yml --profile https up -d --build
```

含义：

- `up`：创建并启动服务
- `-d`：后台运行，SSH 断开后服务仍继续运行
- `--build`：重新构建前端和后端镜像

适用场景：

- 第一次部署
- `git pull` 后更新代码
- Dockerfile 或依赖发生变化

仅启动已有容器：

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d
```

含义：不强制重新构建镜像，只把服务拉起来。

适用场景：

- VM 重启后确认服务
- 容器之前被停止但代码没变

重启所有服务：

```bash
docker compose --env-file .env -f docker-compose.prod.yml restart
```

含义：重启当前 Compose 管理的所有容器。

重启后端：

```bash
docker compose --env-file .env -f docker-compose.prod.yml restart backend
```

含义：只重启 Spring Boot 后端。

适用场景：

- 修改 `.env` 后需要后端重新读取配置
- 后端出现异常但数据库、Redis、Qdrant 正常

查看后端日志：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f backend
```

含义：

- `logs`：查看日志
- `-f`：持续跟随输出
- `backend`：只看后端服务

查看最近 100 行后端日志：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs --tail=100 backend
```

含义：只看最近日志，更适合排错时复制少量内容。

查看所有服务日志：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f
```

停止项目：

```bash
docker compose --env-file .env -f docker-compose.prod.yml down
```

含义：停止并删除容器和 Compose 网络。当前项目的数据目录是宿主机绑定目录，`mysql_data`、`redis_data`、`qdrant_data`、`uploads` 不会因为普通 `down` 被删除。

不要执行：

```bash
docker compose --env-file .env -f docker-compose.prod.yml down -v
```

`-v` 会删除 Docker volume。虽然当前主要数据是绑定目录，但生产环境中不要养成这个习惯。

更新代码（当前 HTTPS profile）：

```bash
cd /opt/interwise
git pull
docker compose --env-file .env -f docker-compose.prod.yml --profile https up -d --build
docker compose --env-file .env -f docker-compose.prod.yml ps
```

含义：

1. 拉取 GitHub 最新代码
2. 重新构建并启动容器
3. 确认服务状态

## 3. VM 停止、启动和自动关闭

Azure 门户里的“停止”是停止虚拟机。VM 停止后：

- 网站无法访问
- SSH 无法连接
- VM 计算费用通常会停止
- 磁盘、公网 IP 等资源仍可能继续产生少量费用

Azure 门户里的“启动”是启动虚拟机。VM 启动后，如果 Docker 正常启动，Compose 里配置了 `restart: unless-stopped` 的容器通常会自动恢复。

启动后建议检查：

```bash
cd /opt/interwise
docker compose --env-file .env -f docker-compose.prod.yml ps
```

自动关闭只会按时间关机，不会自动开机。内测阶段可以开启自动关闭省钱；如果需要用户随时访问，不建议开启自动关闭。

如果公网 IP 变化，需要修改 `.env`：

```env
APP_CORS_ALLOWED_ORIGINS=https://interwise.japaneast.cloudapp.azure.com
MCP_ALLOWED_ORIGINS=https://interwise.japaneast.cloudapp.azure.com
```

然后重启后端或重新启动 Compose：

```bash
docker compose --env-file .env -f docker-compose.prod.yml restart backend
```

长期使用建议绑定域名，并将公网 IP 改为静态或使用域名作为访问入口。

## 4. SSH 连接和长任务

Windows 本机可以配置 SSH 保活，减少空闲断连。

编辑：

```text
C:\Users\nzy\.ssh\config
```

加入：

```sshconfig
Host interwise-azure
    HostName 52.140.216.52
    User azureuser
    IdentityFile C:\Users\nzy\.ssh\vm-interwise-test_key.pem
    ServerAliveInterval 60
    ServerAliveCountMax 3
```

以后连接：

```powershell
ssh interwise-azure
```

执行长构建时建议使用 `tmux`：

```bash
sudo apt install -y tmux
tmux new -s deploy
```

在 `tmux` 里执行：

```bash
cd /opt/interwise
docker compose --env-file .env -f docker-compose.prod.yml --profile https up -d --build
```

如果 SSH 断开，重新登录后恢复现场：

```bash
tmux attach -t deploy
```

## 5. 数据和备份

当前服务器上需要重点保护：

- `/opt/interwise/.env`
- `/opt/interwise/mysql_data`
- `/opt/interwise/redis_data`
- `/opt/interwise/qdrant_data`
- `/opt/interwise/uploads`

建议先创建备份目录：

```bash
mkdir -p /opt/interwise/backups
```

备份 MySQL：

```bash
cd /opt/interwise
set -a
source .env
set +a
docker compose --env-file .env -f docker-compose.prod.yml exec -T db mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" ai_interview_ds > backups/ai_interview_$(date +%F_%H%M).sql
```

备份上传文件：

```bash
cd /opt/interwise
tar -czf backups/uploads_$(date +%F_%H%M).tar.gz uploads
```

备份 Qdrant 数据：

```bash
cd /opt/interwise
tar -czf backups/qdrant_data_$(date +%F_%H%M).tar.gz qdrant_data
```

建议在正式开放给用户前，将备份文件下载到本地或上传到对象存储。只放在同一台 VM 上，无法防止整台服务器误删或磁盘损坏。

## 6. 安全注意事项

已经贴出过或暴露过的密钥需要轮换：

- DeepSeek API Key
- 邮箱 SMTP 授权码
- `JWT_SIGN_KEY`
- `QUESTION_BANK_ADMIN_TOKEN`
- `MCP_READ_TOKEN`
- 数据库密码，若已经有真实用户数据，改数据库密码前要谨慎规划

端口暴露原则：

- 开放 `22`：SSH
- 开放 `80`：HTTP 兼容入口和证书续签
- 开放 `443`：HTTPS 正式访问
- 不要开放 `3306`、`6379`、`6333`、`8080`

`.env` 权限建议：

```bash
cd /opt/interwise
chmod 600 .env
```

不要把 `.env` 提交到 GitHub。

## 7. HTTPS、视频面试和域名

当前 Azure DNS + HTTPS 可以用于：

- 注册
- 登录
- 文本面试
- 简历上传
- 报告生成
- 历史记录
- 视频面试
- 麦克风和摄像头授权

视频面试、麦克风、摄像头能力需要 HTTPS。浏览器通常只允许在安全上下文中调用摄像头和麦克风 API，因此正式测试应优先使用 `https://interwise.japaneast.cloudapp.azure.com`。

后续如果购买自有域名：

1. 购买域名
2. 将域名 A 记录指向 Azure 公网 IP
3. 开放 `443`
4. 将 `DOMAIN_NAME` 改为自有域名
5. 将 `.env` 中的来源改为 `https://你的域名`

当前生产 Compose 支持可选 Caddy 入口。启用 HTTPS 时，`.env` 应包含：

```env
APP_CORS_ALLOWED_ORIGINS=https://你的域名
MCP_ALLOWED_ORIGINS=https://你的域名
DOMAIN_NAME=你的域名
FRONTEND_HTTP_BIND=127.0.0.1:8080
```

启动 HTTPS profile：

```bash
cd /opt/interwise
docker compose --env-file .env -f docker-compose.prod.yml --profile https up -d
docker compose --env-file .env -f docker-compose.prod.yml --profile https logs --tail=100 caddy
```

测试阶段 Caddy 会同时保留 HTTP 访问，不强制跳转 HTTPS。这样如果国内网络、代理或运营商链路对 443/TLS 不稳定，仍可用 HTTP 域名或公网 IP 完成基础功能测试。

## 8. 访问统计、限流和每日额度

当前生产配置默认开启：

- API 限流：登录、注册、验证码、重置密码、开始面试、AI 对话、报告生成、简历解析、Mentor 刷新、反馈提交和 MCP。
- 每日额度：默认每用户每天可开始面试 5 次、AI 对话 80 轮、简历解析 3 次、AI Mentor 生成 3 次。
- 行为记录：访问、登录注册、面试开始/结束、报告查看、异常、限流命中和反馈会写入 MySQL。

`.env` 需要设置：

```env
APP_ADMIN_TOKEN=强随机管理令牌
APP_ANALYTICS_HASH_SALT=强随机统计哈希盐
APP_RATE_LIMIT_ENABLED=true
APP_QUOTA_ENABLED=true
APP_DAILY_INTERVIEW_LIMIT=5
APP_DAILY_AI_CHAT_TURN_LIMIT=80
APP_DAILY_RESUME_PARSE_LIMIT=3
APP_DAILY_MENTOR_GENERATE_LIMIT=3
```

登录网站后访问：

```text
https://interwise.japaneast.cloudapp.azure.com/admin/analytics
```

输入 `APP_ADMIN_TOKEN` 可以查看运营统计、今日额度使用和最新反馈。

## 9. 是否可以改成 Azure 平台托管

可以，但不是当前项目的最小成本路径。

当前 VM + Docker Compose 的优点：

- 和本项目现有架构最匹配
- MySQL、Redis、Qdrant、后端、前端可以一起跑
- 部署和排错直观
- 内测成本低

缺点：

- 你要维护系统、Docker、磁盘、备份、安全补丁
- 扩容和高可用需要自己设计
- HTTPS、监控、告警要自己补

Azure 托管化方向包括：

- Azure Container Apps：托管容器运行环境，适合拆分后的前端、后端和任务服务
- Azure App Service for Containers：适合运行自定义容器，也可接 CI/CD
- Azure Database for MySQL：托管 MySQL
- Azure Cache for Redis：托管 Redis
- Qdrant Cloud 或自建 Qdrant 容器：用于向量检索
- Azure Container Registry：托管镜像仓库

但要迁到托管平台，建议先做这些改造：

1. 前端、后端、数据库、Redis、Qdrant 分离部署
2. 数据库迁移到 Azure Database for MySQL
3. Redis 迁移到 Azure Cache for Redis
4. Qdrant 使用 Qdrant Cloud 或单独容器服务
5. 上传文件迁移到对象存储
6. 用 GitHub Actions 构建镜像并推送到 Azure Container Registry
7. 用 Azure Container Apps 或 App Service 部署业务容器

现阶段建议继续使用 VM + Docker Compose。等确认有稳定用户、稳定功能和真实增长需求后，再迁移到托管架构。

## 10. 日常维护检查清单

每次更新代码：

```bash
cd /opt/interwise
git pull
docker compose --env-file .env -f docker-compose.prod.yml --profile https up -d --build
docker compose --env-file .env -f docker-compose.prod.yml ps
docker compose --env-file .env -f docker-compose.prod.yml logs --tail=100 backend
```

每次 VM 启动后：

```bash
cd /opt/interwise
docker compose --env-file .env -f docker-compose.prod.yml ps
```

每周检查：

- Azure 费用和预算告警
- 磁盘空间
- 后端日志是否有重复错误
- MySQL 备份是否生成
- DeepSeek 额度和调用异常
- 邮箱验证码是否能正常送达

磁盘检查：

```bash
df -h
```

内存检查：

```bash
free -h
```

容器资源检查：

```bash
docker stats
```

清理无用 Docker 构建缓存：

```bash
docker builder prune
```

不要随意执行会删除数据的命令，例如：

```bash
rm -rf mysql_data redis_data qdrant_data uploads
docker compose down -v
docker system prune -a --volumes
```

## 11. 推荐的后续路线

短期：

- 保持 VM + Docker Compose
- 轮换泄露过的密钥
- 配置 SSH 保活和 `tmux`
- 建立备份习惯

中期：

- 绑定域名
- 配置 HTTPS
- 将公网 IP 来源改为域名来源
- 验收视频面试能力

长期：

- 使用 GitHub Actions 自动构建镜像
- 引入 Azure Container Registry
- 数据库和 Redis 迁移到托管服务
- 根据用户量决定是否迁移到 Azure Container Apps 或 App Service
