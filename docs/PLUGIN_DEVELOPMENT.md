# ReadDock 数据源插件开发手册

本手册面向为自己有权访问的数据源编写外部插件的开发者。插件作者负责数据来源、授权、版权和目标站点条款；ReadDock 只提供统一模型、受限运行时、缓存和阅读器。

主仓库不提供真实商业网站适配器，也不接受绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制的实现。需要用户操作时，插件只能声明 `requiresUserInteraction` 并把状态交给用户处理。

## 最小插件结构

```text
example-source/
├── package.json
├── source.ts                 # 仅作 API 参考
└── fixtures/
    ├── search.html
    ├── detail.html
    ├── pages.html
    └── fixture.json
```

`fixtures` 必须是开发者自有或明确授权的合成/离线快照，不要提交真实用户数据或未获授权的漫画页面、章节和图片。

## 数据契约

- `search(query, page)` 返回 `ComicSummary`；`sourceId` 必须等于 manifest 的 `id`。
- `detail(comicId)` 返回当前漫画的元数据和章节；章节必须属于当前 `comicId`。
- `pages(chapterId)` 按阅读顺序返回图片 URL；图片 URL 必须落在 manifest 声明域名内。
- 章节 URL 应稳定、可重新打开并去重；不要从推荐、排行、页脚或其他漫画区域收集链接。
- 无结果返回空列表；失败应使用结构化的 `SourceFailure` 语义，不把堆栈或内部类名展示给用户。

## Manifest 示例

```json
{
  "id": "com.example.source",
  "name": "Example Source",
  "version": "0.1.0",
  "apiVersion": 1,
  "baseUrl": "https://example.com",
  "domains": ["example.com"],
  "capabilities": ["search", "detail", "chapters", "pages"],
  "permissions": ["network"],
  "rateLimit": {"requestsPerMinute": 20, "concurrency": 2},
  "requiresUserInteraction": false,
  "license": "MIT"
}
```

插件包必须使用 HTTPS，声明最小权限和最小域名范围。受限 JavaScript 只提供 HTML 解析、URL 编码和经过网关的请求，不提供文件系统、Android API 或任意代码加载。

## Fixture 测试

从工程根目录运行：

```text
gradle :plugin-cli:run --args="test ../plugin-sdk/fixtures/example-source"
```

CLI 只读取 `fixtures/search.html`、`detail.html`、`pages.html` 和可选的 `fixture.json`，按搜索→详情→章节页面顺序执行解析，不访问真实站点。`fixture.json` 可记录内容授权说明和期望数量：

```json
{
  "contentAuthorization": "project-owned synthetic fixture",
  "expected": {"searchResults": 1, "chapters": 1, "pages": 2}
}
```

## 用户交互协议

`requiresUserInteraction` 只表示某个外部数据源可能需要用户在其允许的流程中完成操作。插件不得自动点击验证控件、伪装请求、复制会话信息到未授权请求或规避站点安全策略。宿主应显示普通用户能理解的提示，并允许取消、返回和重试。

## 发布前检查

1. 运行 `validate` 和离线 `test`。
2. 检查 manifest 的 HTTPS、域名、权限、速率和 API 版本。
3. 使用发布密钥签名插件包，不提交私钥。
4. 记录内容授权、许可证、来源和变更。
5. 确认 fixture 不含真实商业网站数据或个人信息。
6. 在独立插件仓库发布，按目标网站条款和版权要求维护。
