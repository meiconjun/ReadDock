# ComicHub 插件开发手册

## 1. 插件的边界

一个插件对应一个漫画数据源（通常是一个站点），不是一部漫画。

插件负责把数据源的网页/API 转换成 ComicHub 的统一模型：

- `search(query, page)`：搜索并分页返回漫画摘要
- `detail(comicId)`：返回一部漫画的元数据和它自己的全部章节
- `pages(chapterId)`：按阅读顺序返回一个章节的图片

App 框架负责书架、阅读进度、图片缓存、下载队列、阅读器和页面导航。插件不应复制这些逻辑。

### 宿主交互约定

- 收藏必须由宿主数据层持久化，UI 只能通过可观察的 `Flow`/`StateFlow` 读取收藏状态；点击后要立即显示状态变化，写入失败时恢复原状态并显示错误。
- 页面跳转使用宿主导航栈。详情、阅读器和会话验证页都必须支持系统返回/边缘返回，不能用临时变量直接覆盖上一页。
- 搜索、详情、章节和图片加载都要有 loading、success、empty、error 状态。异常不能被静默吞掉；网络失败要提供重试或返回操作。
- `requiresUserInteraction` 表示数据请求需要浏览器会话，不表示必须把整站网页作为阅读器。宿主应优先从会话解析统一模型，并在应用内只渲染图片；只有 Cloudflare/登录验证时才显示受控网页会话页。

## 2. 数据契约

### 搜索

每个 `ComicSummary` 必须满足：

- `sourceId` 等于 manifest 的 `id`
- `id` 是该数据源内稳定、可重新打开的漫画标识，优先使用规范化 HTTPS URL
- `title` 来自当前搜索结果卡片，不要从导航栏、随机漫画入口或推荐区读取
- `coverUrl` 只能使用 manifest 声明域名中的地址
- `page` 从 1 开始；没有结果时返回空列表

### 详情和章节

`detail(comicId)` 返回的章节必须全部属于 `comicId` 对应的漫画：

- 章节 `id` 必须是可重新打开的规范化章节 URL
- `comicId` 必须回填当前漫画 ID
- 保持网站章节顺序
- 用章节 URL 去重
- 不得把推荐漫画、排行、页脚、相关推荐中的章节链接加入结果

不要对详情页执行无范围的全局章节选择，例如：

```kotlin
document.select("a[href]")
```

正确做法是先定位当前漫画的章节分组，再在分组内部查找链接：

```kotlin
document.select(".chapter-list a[href]")
```

如果网站没有稳定 class，应根据页面结构建立更明确的作用域，并在 fixture 中加入一条推荐区的伪章节，确保它不会被解析。

### 页面

`pages(chapterId)` 必须：

- 只读取当前章节容器中的图片
- 按页面在网页中的顺序返回
- 兼容 `src`、`data-src` 或站点实际使用的懒加载属性
- 过滤非图片、占位图和未声明域名
- 为每张图片生成稳定且唯一的 `id`

## 3. 选择插件类型

### 声明式插件：优先选择

普通 HTML 站点优先使用声明式 JSON，只配置 CSS 选择器：

- `search.pathTemplate`
- `search.itemSelector`
- `detail.chapterItemSelector`
- `pages.pageSelector`

格式参考 [package.example.json](../plugin-sdk/package.example.json)。

### JavaScript 插件

只有在需要条件判断、复杂分页或多个页面结构时才使用受限 JavaScript。脚本只能使用：

- `ctx.http.get`
- `ctx.url.encode`
- `ctx.html`
- `select`、`selectText`、`selectAttr`、`text`、`attr`

脚本不能访问 Java/Android API、动态执行代码、代理轮换、指纹伪装或未声明域名。

### 需要浏览器会话的网站

如果普通 HTTP 请求会被 Cloudflare、登录或 Cookie 校验拦截：

- manifest 设置 `requiresUserInteraction: true`
- 不要把 WebView Cookie 假装成普通 HTTP 已可用
- 不要实现验证码绕过、IP 轮换或指纹伪装
- 需要搜索/详情也依赖浏览器会话时，必须由宿主层提供会话加载能力；不能只把 `ctx.http.get` 当作 WebView
- 页面挑战应交给用户在受控 WebView 中完成

MYCOMIC 是浏览器会话型数据源的参考实现，见 [MyComicSource.kt](../core/source-runtime/src/main/kotlin/com/comichub/source/runtime/MyComicSource.kt) 和 [MyComicWebSession.kt](../app/src/main/java/com/comichub/app/MyComicWebSession.kt)。

## 4. 开发流程

先实现搜索，再实现详情和章节，最后实现图片：

```text
search → detail → pages → fixture → CLI test → validate → package/sign
```

从项目根目录运行：

```text
comic-source init my-source
comic-source validate my-source
comic-source test my-source
comic-source fixture capture https://example.com/page.html my-source/fixtures/detail.html
comic-source package my-source dist/my-source.json
```

真实数据源必须使用获得授权的页面快照，并在 fixture 中保留来源说明；fixture 测试不得依赖实时网络。

## 5. 测试要求

每个插件至少覆盖：

1. 空关键词搜索
2. 普通关键词搜索
3. 搜索第 2 页
4. 漫画详情
5. 多章节详情
6. 推荐区/排行区不会污染章节列表
7. 懒加载图片
8. 空结果和页面结构变化
9. 非法或未声明域名被拒绝
10. 需要人工验证时返回可识别的交互错误

发生“很多个第一话”这类问题时，优先检查章节选择器是否跨越了当前漫画容器。测试数据必须故意包含同页面的相关推荐，避免只用干净 HTML 得出假阳性。

## 6. 发布前检查清单

- [ ] 一个插件覆盖整个数据源，而不是写死一部漫画
- [ ] `search`、`detail`、`pages` 都支持分页/动态数据的实际形态
- [ ] 所有 URL 已规范化并限制在 manifest 域名内
- [ ] 章节和图片选择器有明确作用域
- [ ] 推荐、排行、页脚和导航不会进入业务数据
- [ ] 有离线 fixture 和交叉污染回归测试
- [ ] manifest 的能力、权限、限速和 `requiresUserInteraction` 正确
- [ ] 未使用代理池、验证码绕过、指纹伪装或 IP 轮换
- [ ] 通过 `validate`、`test` 和签名打包检查
