# 阿里云轻量内测部署

本方案使用 2 vCPU / 2 GiB ECS 运行前端、Java、MySQL 和 Redis；embedding 通过百炼 `text-embedding-v4` API 生成，向量索引由 Qdrant Cloud 托管。服务器不运行本地 embedding 模型或 Qdrant 容器。

当前阶段已完成受限访问的云端部署和面试功能验收，尚未开放公网网站。下列步骤与模板可公开复用；真实服务器地址、账号、密钥、数据库快照和机器专用操作记录应另行私密保存。

## 配置与启动

先阅读[外部服务配置与数据迁移](external-services.md)。本地与云端必须使用独立的 Qdrant 集合；已有数据库的 `SYNCED` 标记不能证明新集合已准备好。迁移、发布与索引同步仍遵守现有题库权限和审核流程。

| 项目 | 云端内测配置 |
| --- | --- |
| 基础配置 | 从 `.env.prod.example` 创建私有 `.env.prod`，配置数据库、签名、加密和 SMTP |
| 外部服务 | 从 `.env.external.prod.example` 创建私有 `.env.external.prod`，配置 embedding 与 Qdrant |
| 认证 | `email-verified`；首次管理员必须匹配预设用户名、邮箱并通过验证码注册 |
| 题库 | 普通用户只读，ADMIN 维护公共题库；启动导入和自动重建关闭 |
| 对话模型 | 每个账号在「大模型配置」中添加并启用自己的 Provider；embedding Key 不作为系统默认对话 Key |
| 无域名入口 | `FRONTEND_HTTP_BIND=127.0.0.1:8080`，通过 SSH 隧道访问 |

镜像在开发机或 CI 构建，经 `docker save` / `docker load` 传输并核对哈希后，设置 `INTERWISE_IMAGE_TAG`。服务器使用相同顺序合并配置；执行目录应包含 Compose 文件、Caddyfile 和两个私有环境文件：

```shell
docker compose --env-file .env.prod --env-file .env.external.prod -f docker-compose.prod.yml -f docker-compose.external.yml -f docker-compose.small.yml -f docker-compose.images.yml config --quiet
docker compose --env-file .env.prod --env-file .env.external.prod -f docker-compose.prod.yml -f docker-compose.external.yml -f docker-compose.small.yml -f docker-compose.images.yml up -d --no-build
```

不要将展开后的 Compose 环境变量输出到共享日志。服务器私有环境文件应限制为部署账号可读。

没有域名时，在本机运行下列命令并保持 SSH 会话开启，将 `your-server` 换为自己的 SSH 连接配置：

```shell
ssh -N -L 127.0.0.1:18080:127.0.0.1:8080 your-server
```

浏览器访问 `http://127.0.0.1:18080`。该地址只能供建立了隧道的本机使用，不能直接发给普通访客。检查 `/api/health`，再验证邮件验证码、注册、登录、管理员后台和面试。

本地版本继续使用 `./scripts/deploy-local.ps1 -ExternalServices`，保留 `local-admin` 认证和用户自己的私有题库维护能力。

## 资源与维护

`docker-compose.small.yml` 为后端、MySQL、Redis、前端分别设置 640 / 512 / 96 / 48 MiB 容器上限；启用 HTTPS 后的 Caddy 上限为 48 MiB。Java 堆上限为 320 MiB。Docker 日志轮转为每个容器最多 3 个 10 MiB 文件。

这些是小规模内测的起始预算，不能保证 20 场同时进行的 AI 面试。此次机器配置了 2 GiB swap 缓冲峰值；swap 不能代替正常业务所需内存。管理员先串行、小批量维护，实际文档解析峰值仍需验证。

数据备份应同时考虑 MySQL、知识来源文件、可重建的向量索引及必要的加密配置。加密密钥丢失后，数据库中的 Provider 密文不能正常读取。备份文件可读、哈希一致不等于已经验证恢复成功。

## 本次验收与版本归档（2026-09-10）

- 后端 446 项测试、后台首次加载组件测试 1 项、前后端构建通过；本地和 ECS 实际容器、健康接口及重启检查通过。
- 真实邮件验证码、注册、登录、首次 ADMIN、公共题库维护入口和后台自动加载通过；匿名访问受保护接口返回 401。
- 生产迁入 998 条公共题目的当前发布快照；新集合为 768 维，9 个过滤字段索引，重启未触发自动重建。
- 用户已完成云端对话模型配置，并反馈实际面试流程验证无误；此项为用户手工验收，不代表自动化端到端回归或多人容量测试。
- ECS 到外部 embedding / Qdrant 的少量真实查询通过；20 个并发健康读取通过。检索质量标注评测、多人面试压测、文档构建峰值及长期运行仍待验证。

部署源码归档分支为 `codex/deploy-aliyun-beta`，从 `master` 的 `6b2349f9d656749fcd4ad0274edb96869e32e760` 创建。当前运行镜像标签为 `20260910-cloud-beta2`，镜像先于本次 Git 归档构建。此次提交推送不重建或替换线上镜像；后续发布应同时记录源码提交号和镜像摘要。

## 后续步骤

1. **对外访问前：域名、备案与 HTTPS。** 杭州 ECS 属于中国内地地域；按阿里云要求完成适用的备案后，再开放网站。随后配置域名解析、`DOMAIN_NAME`、正式 CORS 来源、Caddy `https` profile 和公网 80/443 入口，实测证书、跳转及面试流式响应。[阿里云备案期间访问说明](https://help.aliyun.com/zh/icp-filing/the-influence-of-the-record-during-the-site-visit)
2. **保留更多内测数据前：定期备份与恢复演练。** 当前已保存迁移前后备份，尚未配置周期备份或完成完整恢复演练；应在隔离环境恢复一次，验证账号、配置、题库和索引可用。
3. **邀请更多用户前：逐级验证容量与普通账号权限。** 从少量同时进行的真实面试开始，观察延迟、错误、内存和 swap，再决定可开放人数；补充普通用户无法维护公共题库的完整页面验收。
4. **持续运行时：监测与费用提醒。** 设置磁盘、内存、服务异常和外部 API 费用告警，结合真实问题决定是否升配或调整资源预算。

这些是下一阶段工作，不由提交或推送分支自动触发。
