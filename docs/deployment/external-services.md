# 外部 embedding 与 Qdrant 配置

适用于本地完整版本，以及普通用户只读、管理员维护公共题库的云端内测版本。配置支持连接百炼 OpenAI 兼容 embedding API 与 Qdrant Cloud；不会自动购买服务、搬迁数据或更改用户权限。

## 配置方式

使用 Docker Compose 2.24.4 或更新版本。`docker-compose.external.yml` 叠加在原本地/生产模板上，移除本地 `embedding-service`、`qdrant` 和对应启动依赖。API 统一经前端代理访问，不再额外发布后端 8080 端口，避免 Windows 保留端口导致启动失败。前端、Java、MySQL、Redis、源文件存储仍在部署机器上。

| 配置 | 本地 | 云端内测 |
| --- | --- | --- |
| 基础环境文件 | `.env` | `.env.prod` |
| 外部环境文件 | `.env.external.local` | `.env.external.prod` |
| 外部环境示例 | `.env.external.local.example` | `.env.external.prod.example` |
| 认证模式 | `local-admin` | `email-verified` |
| 用户维护 | 开放自己私有资源 | 普通用户关闭，ADMIN 仍维护公共资源 |
| 推荐新集合 | `interview_atoms_v4_local` | `interview_atoms_v4_prod` |

从对应示例创建私有外部环境文件，填入 Key、集群 URL 和环境专用集合名。真实 `.env.*` 文件应保持 Git 忽略；两个环境不能共用集合，否则独立 MySQL 中的 atom ID/岗位 ID 可能冲突。

百炼 CSV 的 `openAiCompatible` 是 `APP_EMBEDDING_BASE_URL`，通常形如 `https://<业务空间>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`，这里**不要加 `/embeddings`**。`APP_EMBEDDING_API_KEY` 取 CSV 中的 `apiKey`。模型默认 `text-embedding-v4`；向量维度统一来自 `QDRANT_VECTOR_SIZE`，示例为 768；每批默认最多 10 段，避免超过该模型批量限制。[百炼接口文档](https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api/)

现有 LangChain4j OpenAI SDK 负责调用，`APP_EMBEDDING_READ_TIMEOUT_MS` 用作每批请求的 SDK 总超时预算。请求不自动重试，不加载本地模型兜底；批次数量或向量维度异常会拒绝结果，失败索引仍按现有题库工作流保留待重试状态。

Qdrant 使用 HTTPS 集群地址和数据库 API Key；Key 应允许所用集合的读取、写入、集合创建和 payload 索引维护。管理员首次同步会创建缺失集合，并在写入前幂等准备过滤字段索引。普通检索、健康检查只读取，不会因 401/403/404 或网络问题尝试建库。已有集合可通过管理员发布/重建动作准备索引；预置数据也必须包含这些索引。[Qdrant 鉴权](https://qdrant.tech/documentation/cloud/authentication/)、[索引文档](https://qdrant.tech/documentation/manage-data/indexing/)

## 本地使用

先完成数据准备，再从仓库根目录运行：

```powershell
./scripts/deploy-local.ps1 -ExternalServices
```

可用 `-ExternalEnvFile` 指定其他私有外部环境文件。该模式与 `-ExposeDataServices` 互斥，因为调试覆盖文件会重新引入本地 Qdrant。脚本继续使用 Docker Desktop 的 `desktop-linux` context、构建缓存和失败重试；启动成功后停止被替换的旧本地 Qdrant/embedding 服务，保留容器与数据目录。

不传 `-ExternalServices` 时保持现有本地完整部署方式。切回旧服务前须核对 MySQL 与旧向量集合的一致性，不能假定切换期间的编辑已写入旧索引。

## 云端配置

先检查合并结果，不必启动容器：

```shell
docker compose --env-file .env.prod --env-file .env.external.prod -f docker-compose.prod.yml -f docker-compose.external.yml config --quiet
```

确认域名、HTTPS、SMTP、管理员和数据已准备好后，实际启动也需要带同一组环境文件与 Compose 文件。初次从旧部署切换时，旧本地向量服务会成为孤立容器，需明确停止这两个服务后才能释放内存；不要批量删除卷或无关容器。

尚无域名时，使用示例中的 `FRONTEND_HTTP_BIND=127.0.0.1:8080`，不启用 `https` profile，通过 `ssh -N -L 18080:127.0.0.1:8080 your-server` 后访问 `http://127.0.0.1:18080`。此阶段可以验证注册与权限，不等于公网网站已开放。

2 GB ECS 可继续叠加 `docker-compose.small.yml`。它限制 Java 堆为 320 MiB、后端容器为 640 MiB、MySQL 为 512 MiB、Redis 为 96 MiB、前端与 Caddy 各 48 MiB，并收紧连接池及 Redis 数据内存。五个容器上限合计 1344 MiB；这些是启动预算，仍需结合机器实际可用内存、启动峰值和业务负载实测。swap 只缓冲峰值，不能当作正常并发容量。容器日志使用 Docker local 驱动轮转，每个容器保留最多 3 个 10 MiB 日志文件。

镜像在开发机或 CI 构建后，可用 `docker save` / `docker load` 传输，最后叠加 `docker-compose.images.yml`，并设置 `INTERWISE_IMAGE_TAG`；对应镜像名为 `interwise/backend:<tag>` 和 `interwise/frontend:<tag>`。这样 ECS 启动时无需编译 Java 或前端：

```shell
docker compose --env-file .env.prod --env-file .env.external.prod -f docker-compose.prod.yml -f docker-compose.external.yml -f docker-compose.small.yml -f docker-compose.images.yml up -d
```

正式域名准备好后再启用 `https` profile。Caddy 自动将 HTTP 跳转到 HTTPS，API/上传直接转发 Java 并覆盖外部传入的来源 IP；静态页面仍由 Nginx 提供。入口保留 21 MiB 请求上限，访问日志不记录查询参数和 Referer，避免 SSE URL 中的 token 进入日志。

云模板保持邮箱验证认证，`APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED=false`。设置 `APP_BOOTSTRAP_ADMIN_USERNAME` 和 `APP_BOOTSTRAP_ADMIN_EMAIL`，使用完全匹配的用户名与邮箱完成验证码注册，才能得到首个 ADMIN；不会自动提升已存在的 USER。先进入设置确认后台入口，再验收公共题库工作台。

外部云示例提供保守的管理员文档构建起点：每次 1 文件、单文件 5 MiB、最多 20 万提取字符、10 块和 60 候选。它们不是 2 GB 内存保证：文本限制是在提取后检查，直接 JSON 导入也不受候选数参数限制。先小批量操作并避免重叠构建；应用的共享作业池未改成单线程，避免让面试报告都排在长构建后面。管理员编辑、发布、归档与重建暂按串行方式执行；版本号冲突修复不代表旧批量操作和远程索引同步具备完整并发事务保护。

## 数据准备与验收

外部覆盖配置明确关闭启动导入和启动补建，并清空旧 E5 的 `query:` / `passage:` 前缀。这些设置不妨碍管理员通过现有审核发布与重建动作同步题库。

1. 备份 MySQL、当前 Qdrant 索引和题库来源文件，记录所用模型及集合。云端只准备选定公共题库和关联元数据；本地保留原账号与私有内容。
2. 使用新集合和统一的新模型处理对应题库。即使维度都为 768，E5 与百炼向量也不能混用。
3. 迁到空集合时，MySQL 的 `SYNCED` 状态不会自动变为待同步；应通过有明确范围的管理员重建流程完成，不能只改 URL 就开始面试。
4. 验证集合数量、维度、字段索引、公共/私有权限和严格岗位过滤，抽样检查召回质量。原 0.55 / 0.70 分数阈值需随新模型复验。
5. 检查 `/api/health` 的 Qdrant 状态；它现在检查配置集合的读取权限和可用性，不只是节点是否存活。空集合尚未创建时返回 `DOWN` 是预期行为。
6. 从 ECS 测量完整查询延迟、内存峰值和管理员导入期间的响应；本机到 Qdrant 的连通不代表杭州 ECS 的网络表现。

重建前先固定全部 atom ID，再分批执行；列表按更新时间排序，边翻页边重建会重复或遗漏。迁移过程中避免同时编辑题库。生产只导公共原子的当前发布快照，并按岗位/知识库名称核对 ID 映射；不要直接搬运本地用户、Provider、私有文件、构建任务或历史版本快照。写入并核对新集合之前，生产副本应保持 `PENDING`。

本次接入配置不等于公网部署完成。正式域名证书、邮件收信与注册、备案及实际业务负载，仍需逐项验收；本地代理模拟测试不能代替这些检查。
