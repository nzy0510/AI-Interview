# 产品化打磨 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复面试配置选项的前后端映射 Bug，清理 Reports 模块的开发者向文案，优化 Settings UX，重构 Setup 页面布局（删除准备清单、重点能力移至侧边栏）。

**Architecture:** 纯前端修改 + 1 处后端映射表对齐。改动的文件：`src/views/InterviewSetup.vue`（布局重构）、`src/views/History.vue`（文案中文化 + 入口修复）、`src/mock/reports.js`（文案）、`src/views/Settings.vue`（UX 提示）、后端 `InterviewServiceImpl.java`（映射修复）。无新增文件，无 API 变更。

**Tech Stack:** Vue 3 + Element Plus + Spring Boot + Java 17

---

### Task 1: 修复 focusAreas 前后端映射不匹配

**问题：** 前端 `setup.js` 的 `focusOptions` values 为 `['projects', 'depth', 'architecture', 'algorithm', 'communication', 'pressure']`，但后端 `humanFocusArea()` 只映射了 `['projects', 'depth', 'breadth', 'systemDesign', 'communication', 'stress']`。`architecture`/`algorithm`/`pressure` 落入 default 分支，不会被翻译为中文标签。

**策略：** 以前端值为准，修改后端映射以对齐前端。

**Files:**
- Modify: `src/main/java/com/interview/service/impl/InterviewServiceImpl.java:523-533`

- [ ] **Step 1: 修改后端 humanFocusArea 映射**

将 `humanFocusArea` 的 case 标签改为与前端 `focusOptions` 的 value 一致：

```java
private String humanFocusArea(String focusArea) {
    return switch (focusArea) {
        case "projects" -> "项目经历深挖";
        case "depth" -> "技术原理深度";
        case "architecture" -> "系统设计与架构";
        case "algorithm" -> "算法基础与思维";
        case "communication" -> "表达沟通与协作";
        case "pressure" -> "压力应对与稳定性";
        default -> focusArea;
    };
}
```

- [ ] **Step 2: 构建后端验证编译**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/interview/service/impl/InterviewServiceImpl.java
git commit -m "fix(backend): 修复 focusAreas 前后端 value 映射不匹配

前端 focusOptions 使用 architecture/algorithm/pressure，后端 humanFocusArea
之前用 systemDesign/breadth/stress，导致这些选项的标签在 System Prompt 中
不能被正确翻译为中文。改为与前端一致的 key。"
```

---

### Task 2: 清理 Reports 模块开发者向文案

**问题：** History.vue 中存在英文技术术语（"Architectural Intelligence"、"Growth Lens"、"Volume"、"Score"、"Mode Mix"、"Trend" 等），属于面向内部开发者的仪表盘用语，不适合面向普通用户的产品。

**Files:**
- Modify: `src/views/History.vue` — 替换英文章节标签为中文
- Modify: `src/mock/reports.js` — 替换 hero 区域的开发者向 kicker

- [ ] **Step 1: 修改 reports.js 的 hero 文案**

```js
export const reportCenterConfig = {
  hero: {
    kicker: '面试报告',
    title: '把面试结果变成可追踪的成长记录',
    description: '这里会把历史面试、能力分布、趋势变化和反馈摘要放在同一个观察面里，方便后续继续迭代。',
    tags: ['历史归档', '趋势观察', '能力画像']
  },
  filters: [
    { label: '全部', value: 'all' },
    { label: '文字', value: 'text' },
    { label: '视频', value: 'video' }
  ],
  emptyStates: {
    all: '还没有完成过面试，先完成一次训练后，这里会自动生成报告中心。',
    filtered: '没有匹配到结果，试试清空筛选或者换一个关键词。'
  }
}
```

移除 `settingsSections`（该数据在实际页面中未被使用，属于死代码）。

- [ ] **Step 2: 替换 History.vue 中的英文章节标签**

将 History.vue 模板和脚本中的英文章节 kicker/title 替换为面向用户的中文：

| 行号 | 原文 | 改为 |
|------|------|------|
| 7 | `Architectural Intelligence` | `面试报告` |
| 70 | `kicker="Overview"` / `筛选与摘要` | title 保持中文，kicker 改为 `数据概览` |
| 106 | `kicker="Growth Lens"` / `能力成长曲线` | kicker 改为 `成长趋势` |
| 131 | `kicker="Recent Performance"` / `最近表现与能力画像` | kicker 改为 `近期表现` |
| 159 | `kicker="Knowledge Coverage"` / `知识领域覆盖` | kicker 改为 `知识覆盖` |
| 179 | `kicker="Interview Ledger"` / `历史面试记录` | kicker 改为 `面试记录` |
| 249 | `kicker="Radar View"` / `六维能力评级` | kicker 改为 `能力雷达` |
| 268 | `kicker="AI Review"` / `综合反馈` | kicker 改为 `AI 点评` |
| 279 | `kicker="Sentiment"` / `情感分析` | kicker 改为 `情绪分析` |
| 321 | `kicker="Knowledge Map"` / `知识星图 · 本场面试` | kicker 改为 `知识图谱` |
| 341 | `kicker="Next Moves"` / `提升建议` | kicker 改为 `后续行动` |

同时将 Overview 指标卡（`overviewMetrics`）中的英文 kicker/label/trend 改为中文：

```js
const overviewMetrics = computed(() => [
  {
    kicker: '报告数',
    label: '累计报告',
    value: sortedHistoryList.value.length || '--',
    trend: sortedHistoryList.value.length ? '稳步积累' : '待开始',
    tagType: 'info',
    description: '所有归档面试记录都会在这里汇总。'
  },
  {
    kicker: '平均分',
    label: '平均得分',
    value: sortedHistoryList.value.length ? `${averageScore.value}` : '--',
    trend: sortedHistoryList.value.length ? '基线' : '暂无',
    tagType: sortedHistoryList.value.length ? 'success' : 'info',
    description: '用来快速判断整体稳定性。'
  },
  {
    kicker: '模式分布',
    label: '视频 / 文字',
    value: sortedHistoryList.value.length
      ? `${sortedHistoryList.value.filter((row) => row.interviewMode === 'video').length} / ${sortedHistoryList.value.filter((row) => row.interviewMode !== 'video').length}`
      : '--',
    trend: '结构',
    tagType: 'primary',
    description: '帮助查看训练模式的分布。'
  },
  {
    kicker: '变化趋势',
    label: '最近变化',
    value: scoreDelta.value == null ? '--' : `${scoreDelta.value > 0 ? '+' : ''}${scoreDelta.value}`,
    trend: scoreDelta.value == null ? '无对比' : scoreDelta.value > 0 ? '上升' : scoreDelta.value < 0 ? '回落' : '持平',
    tagType: scoreDelta.value == null ? 'info' : scoreDelta.value > 0 ? 'success' : scoreDelta.value < 0 ? 'warning' : 'info',
    description: '和上一场面试对比的分数变化。'
  }
])
```

- [ ] **Step 3: 验证前端构建**

```bash
cd frontend && npx vite build
```

预期：构建成功，无新增警告。

- [ ] **Step 4: Commit**

```bash
git add src/views/History.vue src/mock/reports.js
git commit -m "refactor(frontend): 清理 Reports 模块开发者向英文文案

- reports.js hero kicker 从 'Report Center' 改为 '面试报告'
- History.vue 所有 section kicker 从英文（Growth Lens/Knowledge Coverage 等）
  改为中文（成长趋势/知识覆盖 等）
- Overview 指标卡标签从 Volume/Score/Mode Mix/Trend 改为中文
- 移除 reports.js 中未被引用的 settingsSections 死代码"
```

---

### Task 3: 修复 Settings 页面的 dead badge 文案

**问题：** `reports.js` 中的 `settingsSections` 包含 "待接入"、"前端占位"、"占位模块" 等开发向 badges，但这些数据从未在页面中使用。Settings.vue 本身已正常工作，但缺少一个用户名不可修改的 UX 提示。

**Files:**
- Modify: `src/views/Settings.vue:27-29` — 在用户名输入框下方增加提示

- [ ] **Step 1: 为 Settings 用户名字段添加 UX 说明**

在 Settings.vue 的用户名字段下方增加一行提示文字：

```html
<el-form-item label="用户名">
  <el-input :model-value="profile.username" disabled />
  <span class="field-hint">用户名为账户唯一标识，注册后不可修改</span>
</el-form-item>
```

在 `<style scoped>` 中添加对应样式：

```css
.field-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
}
```

- [ ] **Step 2: 验证前端构建**

```bash
cd frontend && npx vite build
```

- [ ] **Step 3: Commit**

```bash
git add src/views/Settings.vue
git commit -m "feat(frontend): Settings 用户名字段增加不可修改提示

用户名为账户唯一标识，注册后不支持修改。在 disabled 输入框下方
增加提示文字，避免用户困惑。"
```

---

### Task 4: Setup 页面布局重构 — 删除准备清单，重点能力移至侧边栏

**问题：** 当前布局侧边栏有"准备清单"模块，但 checklist 仅为纯前端交互（不传给后端，不影响面试流程），属于无实际作用的占位模块。而"重点能力"（focusAreas）直接影响 System Prompt 中的追问策略，重要性远高于 checklist，却放在左侧主栏，不如模式选择显眼。

**调整目标：**
1. 删除侧边栏的"准备清单"（Checklist）模块
2. 将"重点能力"（Focus）从左侧主栏移到侧边栏，紧贴"面试模式选择"的起始按钮
3. Focus 在侧边栏用紧凑的 chip/checkbox 布局，保持 sticky 特性

**数据流确认（重要）：**
- `focusAreas` 已经通过 `startInterviewAPI` → `InterviewServiceImpl.startInterview()` → `serializeFocusAreas()` → DB `focus_areas` 列 → `chatStream()` → `buildSetupInstructions()` → System Prompt 注入 `"- 重点能力：项目经历深挖、技术原理深度\n- 提问策略：优先围绕重点能力设计追问"` 
- position 已经通过 `ragRetriever.retrieveWithScores(position, ...)` 过滤 RAG 知识库
- **两者均已生效**，本 Task 仅做布局调整让用户感知更直观

**Files:**
- Modify: `src/views/InterviewSetup.vue` — 模板 + 脚本修改
- Modify: `src/mock/setup.js` — 删除 `checklist` 和 `placeholderModules`（死代码）

- [ ] **Step 1: 删除 Checklist 模板和相关脚本代码**

**模板删除**（InterviewSetup.vue 第 205-224 行）：

删除整个 Checklist section：
```html
<!-- 删除这整个 section -->
<section class="surface-card section-block">
  <div class="block-head">
    <div>
      <p class="section-kicker">Checklist</p>
      <h3 class="section-title">准备清单</h3>
    </div>
    <el-tag effect="plain" type="info">{{ completedChecklist.length }}/{{ setupDefaults.checklist.length }}</el-tag>
  </div>
  <el-checkbox-group v-model="completedChecklist" class="checklist-stack">
    <el-checkbox v-for="item in setupDefaults.checklist" :key="item" :label="item" border>{{ item }}</el-checkbox>
  </el-checkbox-group>
</section>
```

**脚本删除**：删除 `completedChecklist` 的声明和所有引用：

```js
// 删除这一行（原第 254-257 行）：
const completedChecklist = ref([
  setupDefaults.checklist[0],
  setupDefaults.checklist[1]
])
```

- [ ] **Step 2: 将 Focus 模块从主栏移到侧边栏**

**从主栏删除** Focus section（原第 146-165 行）：

删除：
```html
<section class="surface-card section-block">
  <div class="block-head">
    <div>
      <p class="section-kicker">Focus</p>
      <h3 class="section-title">重点能力</h3>
    </div>
    <el-tag effect="plain" type="info">{{ focusAreas.length }} 项已选</el-tag>
  </div>
  <el-checkbox-group v-model="focusAreas" class="focus-grid">
    <el-checkbox
      v-for="item in setupDefaults.focusOptions"
      :key="item.value"
      :label="item.value"
      border
    >
      {{ item.label }}
    </el-checkbox>
  </el-checkbox-group>
</section>
```

**在侧边栏添加** Focus section — 插入到 Mode section 的 `</section>` 后面、下一个 `<section>`（原 checklist 位置）替换为：

```html
<section class="surface-card section-block">
  <div class="block-head">
    <div>
      <p class="section-kicker">Focus</p>
      <h3 class="section-title">重点能力</h3>
    </div>
    <el-tag effect="plain" type="info">{{ focusAreas.length }} 项已选</el-tag>
  </div>
  <p class="focus-hint">选中的能力会通过提问策略影响 AI 面试官的追问方向。</p>
  <el-checkbox-group v-model="focusAreas" class="focus-stack">
    <el-checkbox
      v-for="item in setupDefaults.focusOptions"
      :key="item.value"
      :label="item.value"
      border
    >
      {{ item.label }}
    </el-checkbox>
  </el-checkbox-group>
</section>
```

注意：class 从 `focus-grid` 改为 `focus-stack`（CSS 适配侧边栏单列布局）。

- [ ] **Step 3: 清理 setup.js 中的死代码**

`src/mock/setup.js` 中删除 `checklist` 数组和 `placeholderModules` 数组（二者均已无引用）：

```js
// 最终保留的 export：
export const interviewSetupDefaults = {
  roleOptions: [
    'Java 后端开发',
    'Web 前端开发',
    '测试开发',
    '算法工程师',
    '产品经理'
  ],
  experienceLevels: [
    { label: '应届 / 0-1 年', value: 'junior', hint: '基础题与项目追问' },
    { label: '1-3 年', value: 'mid', hint: '业务与架构并重' },
    { label: '3-5 年', value: 'senior', hint: '系统设计与取舍' },
    { label: '5 年以上', value: 'principal', hint: '复杂场景与方案评审' }
  ],
  focusOptions: [
    { label: '项目经历', value: 'projects' },
    { label: '技术深度', value: 'depth' },
    { label: '系统设计', value: 'architecture' },
    { label: '算法基础', value: 'algorithm' },
    { label: '表达与沟通', value: 'communication' },
    { label: '压力应对', value: 'pressure' }
  ],
  modeOptions: [
    {
      value: 'text',
      title: '文字面试',
      description: '适合先做预热和题目梳理，后续可无缝接到文字问答。',
      tag: '基础模式'
    },
    {
      value: 'video',
      title: '视频面试',
      description: '适合进行更接近真实场景的对话训练，当前先保留入口。',
      tag: '进阶模式'
    }
  ]
}
```

- [ ] **Step 4: 添加 focus-stack 和 focus-hint 的 CSS**

在 InterviewSetup.vue 的 `<style scoped>` 中添加：

```css
.focus-hint {
  margin: 0 0 14px;
  color: #5a6678;
  font-size: 13px;
  line-height: 1.6;
}

.focus-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
```

同时删除 `.checklist-stack` 样式（原第 713-716 行）：

```css
/* 删除：*/
.checklist-stack {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
```

- [ ] **Step 5: 验证前端构建**

```bash
cd frontend && npx vite build
```

- [ ] **Step 6: Commit**

```bash
git add src/views/InterviewSetup.vue src/mock/setup.js
git commit -m "refactor(frontend): Setup 页面删除准备清单，重点能力移至侧边栏

- 删除 Checklist 模块（纯前端交互，不影响面试流程）
- 将 Focus（重点能力）从主栏移至侧边栏，紧贴面试模式选择
- 增加 focus-hint 提示用户选中的能力会影响 AI 追问方向
- 清理 setup.js 中 checklist/placeholderModules 死代码"
```

---

### Task 5: 修复 History.vue 面试入口跳转

**问题：** History.vue（Reports 页面）有 3 处"开始面试"按钮，但它们都跳转到 `router.push('/')`（Dashboard）。用户需要二次点击才能进入面试，体验断档。且 DashboardHome 虽然已在问题 1 中修复了参数传递，但这个跳转链路过长。

**修复策略：** 将"开始面试"按钮直接跳转到 `/interview/setup`（面试准备中心），用户的偏好会自动加载。

**Files:**
- Modify: `src/views/History.vue` — 3 处 `router.push('/')` 改为 `router.push('/interview/setup')`

- [ ] **Step 1: 修改 History.vue 的"开始面试"按钮跳转目标**

两处修改（不修改返回箭头按钮，那应该是回到首页）：

**第 15 行 — header 操作区"开始面试"按钮：**
```html
<!-- 原来 -->
<el-button type="primary" class="primary-cta" @click="router.push('/')">开始面试</el-button>
<!-- 改为 -->
<el-button type="primary" class="primary-cta" @click="router.push('/interview/setup')">开始面试</el-button>
```

**第 61 行 — 空状态"开始面试"按钮：**
```html
<!-- 原来 -->
<el-button type="primary" class="primary-cta" @click="router.push('/')">开始面试</el-button>
<!-- 改为 -->
<el-button type="primary" class="primary-cta" @click="router.push('/interview/setup')">开始面试</el-button>
```

- [ ] **Step 2: 验证前端构建**

```bash
cd frontend && npx vite build
```

- [ ] **Step 3: Commit**

```bash
git add src/views/History.vue
git commit -m "fix(frontend): History 页面面试入口改为跳转 Setup 而非 Dashboard

Reports 页面 2 处'开始面试'按钮之前跳转到首页，用户需要二次点击。
改为直接进入面试准备中心，偏好自动加载，一步到位。"
```

---

### Task 6: 最终验证与合并

- [ ] **Step 1: 构建前端确保无报错**

```bash
cd frontend && npx vite build
```

预期：构建成功，无 warning/error。

- [ ] **Step 2: 构建后端确保编译通过**

```bash
cd backend && mvn compile -q
```

预期：BUILD SUCCESS。

- [ ] **Step 3: 合并到 master 并推送**

```bash
git checkout master
git merge <feature-branch>
git push origin master
```

---
