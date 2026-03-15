# 字节跳动 Web 前端开发面试核心知识库

这份文档为“字节跳动-Web前端开发”岗位的 AI 面试官提供命题与评判标准。字节前端技术偏向实战、框架底层源码、前端工程化以及计算机网络深度考核，重点考察候选人的知识广度和对技术细节的“极致追求”。

## 1. 原生 JavaScript 与运行机制 (字节必考基本功)
前端的根基，字节对于 JS 语言特性和 V8 引擎底层的要求极高。

### 1.1 原型链与继承
- **原型与原型链**：解释 `prototype` 与 `__proto__`。画出从对象实例到 `Object.prototype` 的指向。
- **继承实现**：手写继承代码（组合继承、寄生组合式继承的最佳手写实现）。`Object.create()` 原理剖析。

### 1.2 闭包与执行上下文
- **作用域与闭包**：闭包到底是什么？在实际开发中的应用场景（防抖/节流、模块化、柯里化）。闭包引起的内存泄漏及排查。
- **this 与上下文**：手撕 `call`, `apply`, `bind` 原理（特别注意处理边界边界情况，如 `null`，返回值）。箭头函数的 `this` 指向为什么不能被改变？

### 1.3 异步编程 (Event Loop 面试修罗场)
- **事件循环**：区分宏任务 (Microtasks) 和微任务 (Macrotasks)。结合复杂的 `setTimeout` + `Promise` + `async/await` 出看代码说输出题。Node.js 和 浏览器的 Event Loop 的细微差别。
- **Promise 原理**：如果候选人声称熟悉 Promise，要求**手写一个简易版 Promise (A+ 规范)**，尤其是 `Then` 的链式调用和异步解决逻辑。

## 2. 流行框架深度解析 (Vue/React 源码级理解)
字节目前大部分新业务推崇 React，但旧系统及部分部门使用 Vue。至少需精通其中一种的底层。

### 2.1 Vue 技术栈深度
- **响应式原理**：Vue2 (`Object.defineProperty`) 与 Vue3 (`Proxy`) 的巨大区别是什么？`Proxy` 到底解决了什么问题？收集依赖与派发更新（Dep 和 Watcher 的协作）的源码级解析。
- **Virtual DOM 与 Diff 算法**：Vue 中的双端比较法是如何运作的？Vue3 在 Diff 算法和编译时做了哪些极致的优化（如静态提升 Static Hoisting，Block Tree，PatchFlag）？
- **生命周期与组件通信**：常用的几种跨层级组件数据通信方式（Provide, DefineExpose, Pinia）。Vue3 何时真正发起 DOM 挂载？

### 2.2 React 技术栈剖析
- **Fiber 架构**：为什么 React 16 要引入 Fiber (切片渲染解决掉帧)？单链表树形结构的优势。
- **Hooks 底层**：`useState`, `useEffect` 的底层环形链表存储机制。为什么 Hooks 不能写在条件语句或者循环中？
- **状态管理**：Redux 核心流（Action -> Reducer -> Store），Redux-Saga/Thunk 中间件机制，或者对比 Zustand。

## 3. 面向性能与工程化 (资深与高薪必考点)
能否独当一面构建大前端项目，主要考察工程化深度。

### 3.1 打包与构建工具 (Webpack vs Vite)
- **Webpack 原理**：Loader（链式调用） 和 Plugin（基于 Tapable 机制钩子函数）的本质区别。
- **性能优化手段**：Tree-Shaking 是如何静态分析删除无用代码的（依赖 ES6 模块机制）？分包策略 (SplitChunks)；代码压缩；DLL 技术。
- **Vite 为什么快**：解释 ESM 原生支持、 esbuild 预构建机制（Go语言带来的性能降维打击）、以及 HMR 热更新原理。

### 3.2 浏览器原理与前端网络
- **渲染过程**：HTML 解析、DOM 树构建、CSSOM 树构建、Render Tree（重点区分 `display:none` 和 `visibility:hidden` 在树中的体现），布局（Layout/Reflow）与绘制（Paint/Repaint）。
- **性能优化实战**：白屏时间过长（FP/FCP）的优化方案。CDN 机制，CSS 放在头部，JS 延迟加载 (defer vs async)。
- **安全防范**：XSS（跨站脚本攻击）、CSRF（跨站请求伪造）的原理与详细防范方案（如 SameSite Cookie，Token）。

## 4. 场景设计与手写代码 (字节大杀器)
理论结合实际解决问题的能力。
- **高阶场景**：
  - “如何设计并实现一个并发控制的 `Promise.all`（限制最大并发数为 N）？”
  - “如何处理几十万条超长列表数据的丝滑渲染？（虚拟列表 Virtual List 原理与实现）”
  - “大文件断点续传与秒传的切片设计机制。”
- **基础手撕**：深拷贝（考虑循环引用及特殊对象 `Map`/`Set`/`RegExp`）、数组扁平化、函数防抖与节流、手写一个 `EventEmitter` (发布订阅模式)。
