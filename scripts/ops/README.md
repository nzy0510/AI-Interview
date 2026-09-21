# 生产备份、恢复演练与告警

适用于 `/opt/interwise` 下的阿里云小规模部署，使用现有 `interview-db`、`interview-redis`、`interview-caddy` 容器和外部 Qdrant。脚本不启动业务后端，不替用户调用模型。

## 备份内容与保留

- MySQL 使用事务一致的逻辑备份；Redis 导出 RDB；Qdrant 导出当前配置集合的快照。
- 一并备份上传文件、知识来源文件、Compose、Caddy 配置和证书，以及两个私有环境文件。环境文件中的 Provider 加密配置是恢复历史密钥所必需的。
- 各组件记录 SHA-256，再使用 age 公钥加密整个归档。解密私钥单独保存在维护者设备，不能放进被备份的服务器或 Git。
- ECS 只保留脚本生成的最近两份加密归档，手工备份不参与清理。OSS 私有桶的 `production/` 前缀配置 30 天生命周期；这是同地域异机备份，不是跨地域容灾。
- 每次 OSS 上传后完整下载校验哈希，再记录异机备份成功。Qdrant 仅清理本脚本登记、且已包含在本地加密归档中的快照。
- Qdrant 快照创建请求结果不确定时停止后续创建，保留登记供人工核对，不能直接清空登记并重试。

这些是各服务分别取得的快照，不是跨 MySQL、文件和 Qdrant 的全局事务。正常成功时备份间隔为 6 小时；失败或积压会延长可恢复数据的时间间隔。最终切换恢复环境前应暂停写入、以 MySQL 已发布数据核对索引，必要时走现有管理员同步流程。

## 服务器安装

需要 Linux、Python 3.11+、Docker 访问权限，以及已核验官方校验值的 age / ossutil。本次验证版本为 age 1.3.2、ossutil 2.4.0。以下目录仅部署账号可读：

```shell
install -d -m 700 /opt/interwise/ops/bin /opt/interwise/ops/state
install -m 700 scripts/ops/interwise_ops.py /opt/interwise/ops/interwise_ops.py
install -m 600 scripts/ops/config.example.json /opt/interwise/ops/config.json
```

将工具放到 `bin` 目录，在私有 `config.json` 中填写 age **公钥收件人**、告警邮箱及 OSS 前缀，例如 `oss://your-private-bucket/production/`。不要把解密私钥填写到 `age_recipient`。SMTP 从现有 `.env.prod` 读取；外部向量配置从 `.env.external.prod` 读取，不额外复制账号密钥。

OSS 使用杭州 ECS RAM 实例角色，通过杭州内网 HTTPS 端点访问。脚本当前固定杭州，其他地域部署需要同步调整端点。角色仅需要目标桶的 `GetBucketInfo`、`GetBucketLocation`、`ListObjects`，以及备份前缀的 `PutObject`、`GetObject`、`AbortMultipartUpload`、`ListParts`；不授予删除对象权限，过期清理由桶生命周期负责。

先手工执行一次并检查返回值：

```shell
/usr/bin/python3 /opt/interwise/ops/interwise_ops.py backup --config /opt/interwise/ops/config.json
/usr/bin/python3 /opt/interwise/ops/interwise_ops.py check --config /opt/interwise/ops/config.json
/usr/bin/python3 /opt/interwise/ops/interwise_ops.py notify-test --config /opt/interwise/ops/config.json
```

在原有 crontab 中追加下列条目，不覆盖其他任务。服务器时区为 `Asia/Shanghai`，备份时间为 00:17、06:17、12:17、18:17；健康检查每分钟运行。各命令有独立文件锁，日志仅保存最近一次输出：

```cron
17 */6 * * * PATH=/usr/local/bin:/usr/bin:/bin /usr/bin/python3 /opt/interwise/ops/interwise_ops.py backup --config /opt/interwise/ops/config.json > /opt/interwise/ops/state/backup-last.log 2>&1
* * * * * PATH=/usr/local/bin:/usr/bin:/bin /usr/bin/python3 /opt/interwise/ops/interwise_ops.py check --config /opt/interwise/ops/config.json > /opt/interwise/ops/state/monitor-last.log 2>&1
```

`state/backup-state.json` 记录最近本地成功、OSS 校验成功和失败阶段；`state/monitor-state.json` 记录告警状态。安装后应查看 `crontab -l`、cron 服务和日志更新时间，确认调度确实运行。

## 告警范围

| 检查 | 触发条件 | 执行位置 |
| --- | --- | --- |
| 应用与依赖 | 连续三次检查失败，或健康 JSON 的 app/mysql/redis/qdrant/status 任一不是 UP | ECS 脚本 |
| 备份 | 最近尝试失败、执行超过 30 分钟，或 8 小时没有本地 / 已校验的 OSS 备份 | ECS 脚本 |
| 证书 | HTTPS 证书验证失败，或有效期不足 14 天 | ECS 脚本 |
| 磁盘 | 使用率至少 80% | ECS 脚本及云监控 |
| CPU、内存 | 一分钟平均使用率至少 85%，连续五次 | 阿里云云监控 |
| 云监控磁盘 | 使用率至少 80%，连续三次 | 阿里云云监控 |
| 主机指标缺失 | `NoDataPolicy=INSUFFICIENT_DATA` | 阿里云云监控 |

脚本通过 QQ SMTP 发送状态变化通知，持续故障最多每小时提醒一次，恢复只通知一次。云监控使用已激活的邮箱联系人及联系组，静默周期为一小时并发送恢复通知。主机指标缺失策略的含义见[官方 API 说明](https://help.aliyun.com/en/cms/cloudmonitor-1-0/developer-reference/api-cms-2019-01-01-putresourcemetricrule)。

通知包含标准日期与 Message-ID 邮件头；脚本测试邮件标题为 `[InterWise] 运维测试通知（无需处理）`。云监控邮件标题包含 `cpu.total` 等监控项名称，不一定包含 InterWise。

云监控与服务器脚本各验证一次邮件链路，发送端回执只表示服务接受请求，仍需在收件箱确认实际收到；测试后删除临时触发规则。不要为了测告警而停生产数据库或耗尽服务器资源。服务器内的 HTTPS 检查不能证明所有外部访客都能访问；当前未启用单独计费的站点探测，也没有设置模型供应商费用告警。

## 从 OSS 副本做隔离恢复演练

先将选定 `.tar.gz.age` 对象从 OSS 下载到私有目录，核对 `backup-state.json` 或独立保存的归档 SHA-256。保留至少一份离线或密码管理器中的 age 私钥副本；丢失私钥后无法解开归档。

本机需要 Docker Desktop 的 `desktop-linux` context、Python 3.11+、age，以及预先拉取/导入的 `mysql:8.0`、`redis:7-alpine`、与备份元数据一致的 Qdrant 镜像、对应生产后端镜像。当前验证 Qdrant 为 `qdrant/qdrant:v1.19.1`，后端为 `interwise/backend:20260918-public1`，后端镜像需包含 JDK。

从部署分支根目录执行，路径按实际私有目录调整：

```powershell
python scripts/ops/restore_rehearsal.py 'D:\private-backups\selected.tar.gz.age' `
  --age 'D:\private-tools\age.exe' `
  --identity 'D:\private-keys\backup-age-key.txt' `
  --workspace 'D:\private-backups\restore-scratch' `
  --receipt 'D:\private-backups\restore-receipt.json' `
  --backend-image 'interwise/backend:20260918-public1'
```

演练会验证归档路径及组件哈希，在新建的一次性容器中恢复 MySQL / Redis / Qdrant，检查各数据表、历史 Provider 密文解密和错误密钥反例、向量及索引配置，并执行一次向量自查询。快照前的动态点数只作为观测值，恢复后单独记录实际点数。

MySQL 和 Redis 无外部网络，Qdrant 与只读探针共享无外部网络的容器命名空间；不发布宿主机端口。Java 辅助程序只调用镜像中的密钥解密类，不启动 Spring、不发送邮件、不调用模型、不连接线上 Qdrant。完成后只清理本次登记的容器、卷和私有临时文件；若清理失败，回执标记失败并列出需处理资源。

回执 `passed=true`、`coverage=complete` 代表组件恢复验证通过，不代表已切换生产或完成全站灾难恢复。演练时长不包含新服务器准备、镜像下载、DNS 切换和用户登录验收。旧的上线前归档仅覆盖原有数据库等内容，会标为 `legacy_database_only`，不能替代完整备份。

实际生产恢复应在独立环境先通过上述演练，恢复匹配的镜像和配置，再人工核对账号、Provider、题库、文件及索引一致性，最后安排维护窗口切换；不要直接覆盖在线数据库。建议每月及重要版本升级后重复演练。

## 本地验证

```shell
python -m unittest discover -s tests -p test_ops.py
```

真实验收回执、备份、私有配置和解密密钥均不进入 Git。运维脚本升级不需要重建业务镜像。
