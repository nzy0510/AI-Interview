## Agent skills

### Issue tracker

Issues live as GitHub issues in `nzy0510/AI-Interview`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default label vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.


## 功能添加要求

- 准备一份更新文档，在添加新功能时，记录下所做的修改，防止后续遗忘，导致重复出现相同的错误
- 更新每个功能前，详细询问确认用户需求，一次性考虑周全，而非将应用跑起来就"完事大吉"，要符合生产级应用的需要
- 更新后对每个更改内容进行说明并解释
- 每次功能添加后，commit 到本地仓库，并新建 branch 分支，测试通过后push 到远程仓库,再合并到 main 分支，主分支上的代码必须通过测试且稳定运行