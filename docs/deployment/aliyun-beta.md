# 阿里云小规模公网部署

本方案使用 2 vCPU / 2 GiB ECS 运行前端、Java、MySQL 和 Redis；embedding 通过百炼 `text-embedding-v4` API 生成，向量索引由 Qdrant Cloud 托管。服务器不运行本地 embedding 模型或 Qdrant 容器。

2026-09-18 已在 [interwise.net.cn](https://interwise.net.cn) 启用公网 HTTPS，原有受限访问方式仍可用于维护。下列步骤与模板可公开复用；服务器登录信息、账号、密钥、数据库快照和机器专用操作记录应另行私密保存。

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

## 正式域名与 HTTPS

完成适用的 ICP 备案后，将域名 A 记录指向 ECS 公网地址，并在安全组开放 TCP 80、443。数据库、Redis、后端不发布宿主机端口；前端继续只绑定 `127.0.0.1:8080`，由 Caddy 提供公网入口。

私有 `.env.prod` 中设置正式域名、允许的浏览器来源和已核准的备案号：

```dotenv
DOMAIN_NAME=interwise.net.cn
APP_CORS_ALLOWED_ORIGINS=https://interwise.net.cn,http://127.0.0.1:18080
FRONTEND_HTTP_BIND=127.0.0.1:8080
VITE_ICP_RECORD=浙ICP备2026078082号
VITE_PUBLIC_SECURITY_RECORD=浙公网安备33020602001742号
INTERWISE_IMAGE_TAG=20260918-public1
```

`VITE_ICP_RECORD` 和 `VITE_PUBLIC_SECURITY_RECORD` 必须在前端构建时传入；仅更改运行环境不会更新已构建页面。直接使用 Docker 构建时分别传入同名 `--build-arg`，Compose 构建会从上述环境文件传递。备案号按核准结果填写，不自行添加网站序号；未配置时不显示相应链接。

在已有内测数据服务的服务器上，先备份并验证配置，再只更新应用及入口：

```shell
docker compose --env-file .env.prod --env-file .env.external.prod -f docker-compose.prod.yml -f docker-compose.external.yml -f docker-compose.small.yml -f docker-compose.images.yml --profile https config --quiet

docker compose --env-file .env.prod --env-file .env.external.prod -f docker-compose.prod.yml -f docker-compose.external.yml -f docker-compose.small.yml -f docker-compose.images.yml --profile https up -d --no-build --pull never --no-deps backend frontend caddy
```

以上更新命令要求镜像已导入且 MySQL、Redis 已运行。Caddy 自动申请和续期证书，`caddy_data`、`caddy_config` 必须持久保存，80/443 保持可达。不能把首次申请成功当作长期续期已经验证。[Caddy 官方说明](https://caddyserver.com/docs/automatic-https)

## 公安备案页脚

用户于 2026-09-21 提供公安备案通过的查询截图：域名为 `interwise.net.cn`，备案号为 `浙公网安备33020602001742号`。页脚查询链接使用核准编号 [33020602001742](https://beian.mps.gov.cn/#/query/webSearch?code=33020602001742)，不能直接使用通用示例中的陕西备案号。

图标保存为 `frontend/src/assets/head-logo.png`，原始资源来自[公安备案平台](https://beian.mps.gov.cn/img/logo01.dd7ff50e.png)，随前端构建发布，避免浏览器依赖第三方图片加载。备案文字与官方图标共同链接到查询页面，在窄屏下与 ICP 信息换行显示。

仅更新前端时，可设置 `INTERWISE_FRONTEND_IMAGE_TAG`，不设置则沿用 `INTERWISE_IMAGE_TAG`。在镜像导入、配置校验通过后，使用上述 Compose 文件顺序并执行 `up -d --no-build --pull never --no-deps frontend`，保留后端及数据服务的运行版本。

## 资源与维护

`docker-compose.small.yml` 为后端、MySQL、Redis、前端分别设置 640 / 512 / 96 / 48 MiB 容器上限；启用 HTTPS 后的 Caddy 上限为 48 MiB。Java 堆上限为 320 MiB。Docker 日志轮转为每个容器最多 3 个 10 MiB 文件。

这些是小规模内测的起始预算，不能保证 20 场同时进行的 AI 面试。此次机器配置了 2 GiB swap 缓冲峰值；swap 不能代替正常业务所需内存。管理员先串行、小批量维护，实际文档解析峰值仍需验证。

数据备份应同时考虑 MySQL、知识来源文件、可重建的向量索引及必要的加密配置。加密密钥丢失后，数据库中的 Provider 密文不能正常读取。备份文件可读、哈希一致不等于已经验证恢复成功。

## 首次内测验收与版本归档（2026-09-10）

- 后端 446 项测试、后台首次加载组件测试 1 项、前后端构建通过；本地和 ECS 实际容器、健康接口及重启检查通过。
- 真实邮件验证码、注册、登录、首次 ADMIN、公共题库维护入口和后台自动加载通过；匿名访问受保护接口返回 401。
- 生产迁入 998 条公共题目的当前发布快照；新集合为 768 维，9 个过滤字段索引，重启未触发自动重建。
- 用户已完成云端对话模型配置，并反馈实际面试流程验证无误；此项为用户手工验收，不代表自动化端到端回归或多人容量测试。
- ECS 到外部 embedding / Qdrant 的少量真实查询通过；20 个并发健康读取通过。检索质量标注评测、多人面试压测、文档构建峰值及长期运行仍待验证。

部署源码归档分支为 `codex/deploy-aliyun-beta`，从 `master` 的 `6b2349f9d656749fcd4ad0274edb96869e32e760` 创建。当时运行镜像标签为 `20260910-cloud-beta2`，镜像先于首次 Git 归档构建；该版本现已被下述公网版本替换。

## 公网上线记录（2026-09-18）

- 部署分支通过合并提交 `4e2014d` 纳入 `master` 的 `290e5a0`，保留双方历史；主分支本身未被改动。
- 运行源码为 `a97578a2450fa61349cd5bf8ce3d5e339be0a9dd`，前后端镜像标签均为 `20260918-public1`，镜像包含对应源码标签。后续部署文档提交不改变该运行源码版本。
- 后端 432 项、前端 144 项测试通过，前后端镜像构建通过。公网 HTTP 返回 308 并跳转 HTTPS；正式域名 HTTPS 和健康接口返回 200，MySQL、Redis、Qdrant 均为 UP。
- Let's Encrypt 证书包含正式域名；本次签发证书到期时间为 2026-12-17 08:51:19 UTC。Caddy 已配置自动续期，仍需后续运行监测。
- 邮箱验证认证、注册和找回密码继续启用；匿名受保护请求返回 401，正式来源 CORS 通过，未允许来源返回 403。普通用户的题库维护继续关闭。
- MySQL、Redis 容器未重建；其他私有配置与外部服务配置逐项核对未变。原有 1 个账号、1 个启用的对话 Provider 配置及 998 条已同步公共题目保留；未触发题库重新导入或重建。
- 上线前在服务器保存数据库、私有配置和应用文件备份并核对校验值；本次备份尚未复制到异机，也未进行完整恢复演练。
- 2026-09-21 用户确认已在正式域名登录并完成一次面试验收，未发现明显差错；同日复查健康接口，应用、MySQL、Redis、Qdrant 均为 UP。这是用户手工验收，不代表自动化端到端回归、普通账号权限或多人容量验证；浏览器自动控制仍连接超时，移动端备案页脚等页面细节尚未独立验收。

前端镜像 ID 为 `sha256:dec99ecb60cbac6faf58c95c5bd680ec6ad9765c0631b71e9d2c6996e9a1a71e`，后端为 `sha256:78ef423b1e3101fb8a41134bf4bbefa1c8853522c2e9c787db1592065ca02b70`。发布清单同时记录镜像包校验值、配置校验值和验证结果，并与敏感备份分开保存，不提交数据库或凭据。

## 自动备份与运维验收（2026-09-21）

- 已配置北京时间每 6 小时备份 MySQL、Redis、Qdrant、应用文件和必要私有配置；age 公钥加密后上传杭州私有 OSS 桶，并完整下载校验哈希。ECS 保留最近两份，OSS 生产前缀按生命周期保留 30 天。
- OSS 使用 ECS RAM 角色及内网 HTTPS，角色仅能访问备份桶和指定前缀，不包含对象删除权限；解密私钥保存在维护者设备，服务器只有公钥。
- 已从实际 OSS 下载副本完成隔离恢复演练：六个组件校验、MySQL 逐表检查、历史 Provider 密钥解密及错误密钥反例、Redis 恢复、Qdrant 998 条向量及检索均通过。旧的上线前备份也完成其所含数据库的恢复验证。
- 每分钟健康检查已实际由 cron 执行；异常依赖、备份失败或超过 8 小时未成功、磁盘高占用、证书不足 14 天会通知维护者。CPU / 内存 / 磁盘云监控规则已启用，指标缺失也配置为告警；邮箱联系人已激活，SMTP 与云监控测试邮件链路均有成功回执，临时测试规则已清理。
- 生产容器和正式域名健康检查通过，业务镜像保持上述公网版本。恢复演练未启动业务后端、未调用外部模型，不代表生产切换或完整灾难恢复耗时已验证。
- 同日进一步核对收件箱并由用户确认，两类测试通知均已实际收到；脚本通知补齐日期与 Message-ID 邮件头，测试标题改为中文，便于识别。

安装、恢复命令、阈值、故障处理与一致性边界见[运维说明](../../scripts/ops/README.md)。私有回执和解密私钥另行保存。外部独立站点探测及模型费用告警尚未启用。

## 后续步骤

1. **补充页面与普通账号验收。** 正式域名登录和一次面试已由用户手工验收；继续补查管理员后台、移动端备案页脚，以及普通账号不能维护公共题库的完整流程。
2. **持续验证备份可恢复。** 定时备份与首次完整组件恢复演练已完成；妥善另存解密私钥，建议每月及重要版本升级后重复演练，切换生产前另行核对跨服务一致性。
3. **邀请更多用户前：逐级验证容量与普通账号权限。** 从少量同时进行的真实面试开始，观察延迟、错误、内存和 swap，再决定可开放人数；补充普通用户无法维护公共题库的完整页面验收。
4. **持续运行时：补充外部可用性与费用提醒。** 主机资源、服务依赖、备份和证书告警已启用；独立站点探测及外部 API 费用告警仍待按预算单独配置，结合实际问题决定是否升配。

这些是下一阶段工作，不由提交或推送分支自动触发。
