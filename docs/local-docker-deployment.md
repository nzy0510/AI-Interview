# 本地 Docker 部署与故障恢复

## 标准部署

Windows 本地环境从仓库根目录运行：

```powershell
.\scripts\deploy-local.ps1
```

该脚本执行以下固定流程：

1. 清除当前进程中可能残留的 `DOCKER_HOST`，显式选择 Docker Desktop 的 `desktop-linux` context。
2. 校验 `.env` 中数据库用户密码、MySQL root 密码、JWT、Provider 密钥加密密钥和统计盐值不为空、不是示例占位符且长度合格；进程环境变量即使被显式设为空也会拒绝。部署固定读取受版本控制的 `docker-compose.example.yml`，不依赖可能过期的本地副本。
3. 先校验 Compose，再构建镜像；首次构建失败会确认 Docker 引擎仍可用，保留 BuildKit/Maven 缓存并自动重试一次。
4. 使用已构建镜像启动容器，等待 `http://127.0.0.1/api/user/auth-config` 返回 HTTP 200；启动失败会自动输出容器状态和末尾日志。
5. 输出最终 Compose 状态。

不要用 `DOCKER_HOST=npipe:////./pipe/docker_engine_linux` 启动 Compose。该旧 named pipe 会绕过 Docker Desktop context 的 Windows 路径转换，使 `E:\...:/container/path` 绑定挂载被 Linux daemon 判定为非法。

## 数据服务端口

应用容器通过 `redis:6379` 和 `qdrant:6333` 访问数据服务，默认不需要映射到宿主机。这样可以避开 Windows、WSL 或 Hyper-V 动态保留端口范围。

需要用宿主机工具直接连接时运行：

```powershell
.\scripts\deploy-local.ps1 -ExposeDataServices
```

默认调试端口为 Redis `16379`、Qdrant `16333`。如果端口被占用，可显式更换：

```powershell
.\scripts\deploy-local.ps1 -ExposeDataServices -RedisHostPort 17379 -QdrantHostPort 17333
```

## Docker Desktop 异常退出

先区分 Docker Desktop / 引擎启动故障、镜像构建故障和应用容器故障。`dockerInference` / `engine.sock` 无法访问属于本次 Desktop 启动故障；BuildKit `EOF` 或应用报错需要各自的日志，不能仅凭截图断定内存不足、项目太重或数据损坏。

安全恢复顺序：

1. 读取 `%LOCALAPPDATA%\Docker\log\host\com.docker.backend.exe.log*` 等当次日志，记录报错组件、时间和版本；只输出必要片段，不上传整份诊断或配置。
2. 检查 `desktop-linux` 引擎是否可访问、Desktop 后台进程是否仍在运行；能访问时先记录现有容器、卷和状态。确认是已失败的本机 Desktop 后再结束对应进程，不影响其他 Docker context 或用户仍在运行的任务。
3. 普通退出重启后仍出现同样错误时，按下方已验证的 socket 修复步骤处理；不要继续重复同一操作。
4. 重启后运行 `docker --context desktop-linux version` 和 `docker --context desktop-linux info`，确认 **Server** 可读。`docker --version` 只有客户端版本，窗口打开也不代表引擎可用。
5. 核对原有容器和卷仍在，并完成本次任务需要的实际容器操作；需要本地应用时再运行 `./scripts/deploy-local.ps1`。故障前已经停止的其他项目容器保持原状态。

后端 Dockerfile 使用 BuildKit cache mount 保存 `/root/.m2/repository`。即使某次镜像构建在依赖下载阶段中断，下一次构建也会复用已完成下载，不再从零开始。

## 2026-09-21 复盘：残留 socket 导致反复启动失败

本次环境为 Windows Docker Desktop **4.83.0（build 234302）**，修复后 Engine **29.6.2** 可用。已确认两处残留通信文件阻止初始化：

```text
%LOCALAPPDATA%\Docker\run\dockerInference
%LOCALAPPDATA%\docker-secrets-engine\engine.sock
```

它们是 Windows AF_UNIX socket / reparse point，不是 MySQL 数据或题库文件。日志先报 `initializing Inference manager`，处理后又报 `initializing Secrets Engine`，均包含 `The file cannot be accessed by the system`。Docker 官方仓库有相同症状及目录改名绕过方法的[问题报告 #554](https://github.com/docker/desktop-feedback/issues/554)；这是旁证，不等于已证明本机最初异常退出的原因。

**本次操作失误：** 已看到两处残留，却先只处理 `Docker\run` 并启动，又单独处理 `docker-secrets-engine` 再启动。中间启动失败留下新的通信文件，导致同类错误反复出现。后续必须完整检查该报错链，在一次停机期间处理已确认的相关目录，再统一启动。

最终成功的处理顺序：

1. 保存相关日志，完全退出 Desktop；确认本次失败的 `Docker Desktop` / `com.docker.backend` 进程已经结束，核对进程可执行文件属于 Docker 安装目录。
2. 确认上述两个**父目录**的绝对路径位于当前用户 `%LOCALAPPDATA%` 对应位置，父目录自身不是指向其他位置的重解析点；核对目录内容确为本次故障的临时通信文件，版本或内容不同则重新诊断。不要将修复范围扩大到整个 `Docker` 目录。
3. 对存在的 `Docker\run` 和 `docker-secrets-engine` 使用 PowerShell `Rename-Item -LiteralPath`，分别改名为带唯一时间戳的 `.stale-interwise-...` 目录；为原位置重建空目录。两处都核对完成前不启动 Desktop，失败时保留已改名目录供排查。
4. 启动 Docker Desktop，等待服务端正常响应。通过自动化启动后台程序时使用 `Start-Process -WindowStyle Hidden`。
5. 用真实隔离容器恢复验证引擎可用，清理本次临时容器并确认原有容器、镜像、卷仍在。本次通过 MySQL / Redis / Qdrant 恢复和向量查询完成验证；不以“启动命令没有报错”作为结论。

本次保留了 `run.stale-interwise-20260921-123511`、`run.stale-interwise-20260921-123715` 和 `docker-secrets-engine.stale-interwise-20260921-123555`，没有恢复出厂设置或删除数据卷。初始异常退出的诱因尚未确认，不能把“内存压力”写成此次根因。

以下做法不是该故障的默认修复：关闭 Docker AI / Inference 开关、删除单个无法访问的 socket、反复强杀进程、降低资源配置、重装或重置 Docker。特别不要执行 `docker system prune --volumes`、`docker compose down -v`、删除 Docker 数据盘、`wsl --unregister`；这些操作可能影响与故障无关的数据。

## 同次恢复演练的错误假设

| 当时的错误假设 / 实际失败 | 已验证的解决方式 | 后续 Agent 必须检查 |
| --- | --- | --- |
| 假设 `--internal` 网络加 `-p` 一定产生宿主机可用端口；本次 `6333/tcp` 实际为空数组，直接取首项触发 `IndexError` | Qdrant 使用 `--network none`；探针使用 `--network container:<本次 Qdrant 容器>`，通过共享的隔离网络命名空间查询 loopback，不发布端口 | 检查真实 `NetworkSettings.Ports`；不要把本次版本表现推广为所有 Docker 版本的规则。离线演练不应为了访问方便连到生产网络 |
| 将 `mysqladmin ping` 当作初始化完成；它不足以证明最终 TCP 服务和目标数据库已准备好 | 对 `127.0.0.1` 的目标数据库执行带认证的 `SELECT 1` 成功后再导入 | 验证真正要用的连接、数据库和凭据，不能只看容器 Running 或进程存活 |
| 假设 `mysql:8.0` 镜像一定附带 `mysqlcheck`；实际返回 `command not found` | 使用镜像已有的 `mysql` 客户端逐表执行 `CHECK TABLE`，检查返回状态 | 先核对镜像内实际可用命令；不要为验收临时给生产容器安装工具 |

审查同时补上了演练资源的回收约束：`docker create` 成功就登记 ID，再 `start`；在 `finally` 中只删除本次登记的容器及其临时卷，先释放 bind mount，再清理本次私有临时目录。清理失败必须记入失败回执，不能因业务校验通过就报告演练成功。

部署分支的 `scripts/ops/restore_rehearsal.py` 已采用这些处理方式。2026-09-21 的实际 OSS 副本完成六个组件校验、MySQL 逐表检查、历史 Provider 密钥解密、Redis 恢复以及 998 条 Qdrant 向量和检索验证；没有启动业务后端或调用外部模型。这是当次证据，不是后续版本无需复验的保证。
