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

如果大型程序造成系统内存压力，Docker Desktop 可能异常退出，并在下一次启动时报告 `dockerInference` socket 无法访问，或在构建中返回 BuildKit `EOF`。

安全恢复顺序：

1. 退出造成内存压力的程序，确认系统已有足够可用内存。
2. 完全退出 Docker Desktop，等待 Docker Desktop 后台进程结束，再重新启动。
3. 运行 `docker --context desktop-linux info`，只有服务端版本可读取后才继续。
4. 重新运行 `./scripts/deploy-local.ps1`。不要降低 Docker 资源配置，也不要 factory reset。
5. 若仍出现同一 socket 错误，先收集诊断并备份 Docker runtime 目录；不要删除 `mysql_data`、`redis_data`、`qdrant_data`、`embedding_model_cache` 或 Docker volumes。

后端 Dockerfile 使用 BuildKit cache mount 保存 `/root/.m2/repository`。即使某次镜像构建在依赖下载阶段中断，下一次构建也会复用已完成下载，不再从零开始。
