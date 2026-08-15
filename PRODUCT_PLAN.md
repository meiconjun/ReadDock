# 漫画阅读 App 产品规划

## 1. 项目定位

暂定代号：ComicHub。

这是一个 Android 漫画阅读器，内容由用户添加的漫画源插件提供。项目的核心差异化不是“内置很多网站”，而是：

1. 插件开发门槛低，普通 Web 开发者也能编写漫画源。
2. 阅读器、缓存、书架和同步能力与漫画源解耦。
3. 网络访问稳定、可观测、可限速，并在网站要求交互验证时交给用户处理。
4. 插件权限、来源和更新过程可验证。

项目只接入获得授权、允许公开访问或用户自有的内容，不绕过验证码、登录限制、付费墙、DRM 或其他访问控制。

## 2. 产品边界

### MVP 要做

- Android 手机和平板阅读
- 搜索、漫画详情、章节列表、章节阅读
- 书架、阅读历史、阅读进度
- 长条/Webtoon 和分页两种阅读模式
- 图片缓存和离线阅读
- 漫画源插件安装、启用、停用和更新
- 插件运行日志与源健康状态
- 内置本地测试源，不依赖真实网站进行开发测试

### 暂不做

- 自动绕过验证码或反爬挑战
- 隐藏式代理池、IP 轮换、设备指纹伪装
- 插件直接执行任意 Android/Kotlin 代码
- 内置未经授权的内容目录
- 用户账号体系和云同步（放到第二阶段）

## 3. 总体架构

```text
Android App
├── UI / Jetpack Compose
├── Reader              阅读器、手势、方向、预加载
├── Library             书架、历史、分类、进度
├── Domain              Comic、Chapter、Page、SourceError
├── Data                Room、下载任务、图片缓存
├── Source Runtime      插件加载、沙箱、权限、版本回滚
├── Network Gateway     限速、缓存、重试、熔断、日志
└── WebView Bridge      需要用户交互时的受控浏览器入口

Source Plugin
├── manifest.json       元数据、能力、权限、域名、限制
├── source.js/ts        搜索、详情、章节、图片解析逻辑
└── tests/              固定 HTML/JSON fixture 和解析测试
```

建议技术栈：Kotlin、Jetpack Compose、Room、OkHttp、WorkManager。插件运行时采用受限 JavaScript，插件不直接接触 Android API。

## 4. 插件协议设计

### 4.1 插件目标

插件作者只需要关心网站结构，不需要理解 App 的数据库、图片缓存、下载队列或阅读器实现。

### 4.2 最小插件结构

```text
example-source/
├── manifest.json
├── source.ts
├── icon.png
└── tests/
    ├── search.html
    ├── detail.html
    └── chapters.html
```

### 4.3 manifest.json 草案

```json
{
  "id": "com.example.source",
  "name": "Example Source",
  "version": "0.1.0",
  "apiVersion": "1",
  "baseUrl": "https://example.com",
  "domains": ["example.com"],
  "capabilities": ["search", "detail", "chapters", "pages"],
  "permissions": ["network"],
  "rateLimit": {
    "requestsPerMinute": 20,
    "concurrency": 2
  },
  "requiresUserInteraction": false,
  "license": "MIT"
}
```

### 4.4 source.ts 草案

```ts
export default defineSource({
  async search(ctx, query, page) {
    const response = await ctx.http.get(`/search?q=${ctx.url.encode(query)}&page=${page}`)
    return ctx.html(response).select(".comic-card").map(card => ({
      title: card.selectText(".title"),
      url: card.selectAttr("a", "href"),
      cover: card.selectAttr("img", "src")
    }))
  },

  async detail(ctx, url) {
    const doc = await ctx.html(await ctx.http.get(url))
    return {
      title: doc.selectText("h1"),
      author: doc.selectText(".author"),
      description: doc.selectText(".description"),
      chapters: doc.select(".chapter a").map(a => ({
        title: a.text(),
        url: a.attr("href")
      }))
    }
  },

  async pages(ctx, chapterUrl) {
    const doc = await ctx.html(await ctx.http.get(chapterUrl))
    return doc.select(".page img").map(img => ({
      url: img.attr("data-src") ?? img.attr("src")
    }))
  }
})
```

### 4.5 插件 API 的原则

- 默认只允许访问 manifest 声明的域名。
- 默认只允许网络、解析和有限缓存能力。
- 不开放文件系统、系统设置、通讯录、剪贴板和任意 Android API。
- 插件失败要返回结构化错误，例如 `RATE_LIMITED`、`LOGIN_REQUIRED`、`CHALLENGE_REQUIRED`、`PARSER_BROKEN`。
- 所有插件都可以使用固定 fixture 做离线单元测试。
- 插件包需要签名，支持版本校验和一键回滚。

## 5. 网络稳定性设计

这里把“反爬能力”定义为合规的访问兼容性和稳定性，不主动规避网站的安全策略。

### 5.1 Network Gateway

- 每个域名单独限速和并发控制
- 请求去重，避免重复加载同一页面
- ETag/Last-Modified 和本地缓存
- 指数退避和有限重试
- 429、503 等状态码自动进入冷却期
- 连续解析失败后自动熔断该源
- 统一记录延迟、状态码、解析耗时和失败原因
- 图片下载支持断点续传和失败重试

### 5.2 浏览器交互通道

当网站要求登录或人工验证时：

1. 插件返回 `REQUIRES_USER_INTERACTION`。
2. App 打开受控 WebView，让用户自行完成网站允许的操作。
3. 后台不自动点击验证码、不注入绕过脚本、不模拟人类指纹。
4. 用户明确授权后，才在该网站范围内复用必要的会话状态。

### 5.3 源健康中心

每个源显示：

- 最近一次成功请求
- 最近一次失败原因
- 当前冷却状态
- 解析器版本
- 最近 24 小时成功率
- 是否需要用户登录

这样网站结构变化时，插件作者能快速定位问题，而不是让用户只看到“加载失败”。

## 6. App 核心数据模型

```text
Source
Comic
Chapter
Page
LibraryEntry
ReadingProgress
DownloadTask
PluginInstall
SourceHealth
```

漫画和章节记录必须带 `sourceId`，不能只使用网站 URL。这样同一部漫画在不同源之间可以去重、迁移和重新绑定。

## 7. 插件开发体验

后续提供一个独立的命令行工具：

```text
comic-source init example-source
comic-source test
comic-source fixture capture
comic-source validate
comic-source package
```

开发者可以在电脑浏览器中使用固定网页快照调试解析器，不必每次都安装 APK。发布前执行：

- manifest 校验
- 权限和域名校验
- fixture 解析测试
- 网络请求数量检查
- 插件签名
- 兼容的 API 版本检查

## 8. MVP 开发顺序

### 阶段 0：协议验证

- 定义数据模型和错误模型
- 用本地 HTML fixture 实现一个 MockSource
- 确认搜索、详情、章节、图片四个流程

### 阶段 1：阅读器骨架

- Compose 页面框架
- Room 数据库
- 书架和阅读进度
- 本地图片阅读

### 阶段 2：插件运行时

- JS 沙箱
- manifest 权限控制
- Network Gateway
- 插件安装、更新、回滚

### 阶段 3：网络稳定性

- 限速、缓存、重试、熔断
- WebView 交互通道
- 源健康中心
- 开发者 CLI 和 fixture 测试

### 阶段 4：公开测试

- 只接入授权或明确允许访问的测试源
- 收集插件开发者反馈
- 再决定是否开放第三方插件仓库

## 9. 第一版成功标准

- 新开发者在 30 分钟内可以创建并运行一个 MockSource。
- 一个简单 HTML 网站的搜索和章节解析代码不超过 100 行。
- 插件崩溃不能导致 App 崩溃。
- 单个源异常不会拖慢其他源。
- 所有网络失败都有可读的错误原因。
- 插件更新失败可以自动回滚。

## 10. 当前最重要的技术决策

第一版建议采用“声明式选择器 + 受限 JavaScript”的混合模式：

- 简单网站用 JSON/CSS 选择器完成，不需要写代码。
- 复杂网站用 JavaScript 扩展处理分页、嵌套 JSON 和特殊字段。
- 复杂到需要浏览器验证的网站只允许用户交互，不做自动化绕过。

这比“每个插件都是一个 Android APK”更容易开发、更新和审计，也比“所有网站逻辑写死在 App 内”更容易维护。
