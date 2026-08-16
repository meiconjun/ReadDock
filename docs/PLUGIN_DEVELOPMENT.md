# ReadDock 外部数据源插件开发

这份手册写给想为自己有权访问的数据源编写 ReadDock 插件的开发者。

ReadDock 主仓库只提供协议、运行时和测试工具。真实数据源应该放在独立仓库，由插件作者自己负责来源授权、版权、隐私和目标网站条款。主仓库不接受真实商业网站适配器，也不接受绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制的实现。

## 先从一个离线插件开始

仓库示例使用项目自有的合成内容，不访问网络：

~~~text
plugin-sdk/
└── fixtures/
    └── example-source/
        ├── package.json
        └── fixtures/
            ├── search.html
            ├── detail.html
            ├── pages.html
            └── fixture.json
~~~

fixture 适合测试搜索、详情、章节和页面解析。不要把真实用户数据、未授权的漫画页面、章节或图片放进 fixture。

## 一个最小 manifest

~~~json
{
  "id": "com.example.source",
  "name": "Example Source",
  "version": "0.1.0",
  "apiVersion": 1,
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
~~~

manifest 里最重要的是把边界写清楚：

- 只使用 HTTPS；
- 只声明确实需要的域名和权限；
- 给请求设置合理的速率和并发限制；
- 需要用户操作时，把 requiresUserInteraction 设为 true；
- 写清楚插件许可证和内容来源。

运行时会拒绝未声明的域名、能力和不可信的插件签名。

## 数据返回约定

插件实现四类常见操作：

- search(query, page)：返回 ComicSummary；
- detail(comicId)：返回漫画信息和属于该漫画的章节；
- pages(chapterId)：按阅读顺序返回页面；
- 失败：返回结构化的 SourceFailure，而不是把堆栈、内部类名或原始请求错误交给用户。

页面 URL 必须落在 manifest 声明的域名内。章节链接要稳定、可重新打开并去重，不要把推荐、排行、页脚或其他漫画区域的链接误当成章节。

## 运行离线 fixture 测试

从工程根目录运行：

~~~text
gradle :plugin-cli:run --args="validate ../plugin-sdk/fixtures/example-source"
gradle :plugin-cli:run --args="test ../plugin-sdk/fixtures/example-source"
~~~

CLI 只读取本地的 search.html、detail.html、pages.html 和可选的 fixture.json，不会访问真实站点。fixture.json 可以记录授权说明和期望结果：

~~~json
{
  "contentAuthorization": "project-owned synthetic fixture",
  "expected": {
    "searchResults": 1,
    "chapters": 1,
    "pages": 2
  }
}
~~~

## 需要用户操作时

requiresUserInteraction 只表示用户可能需要在目标服务允许的流程中自己完成登录或其他操作。它不允许插件：

- 自动点击验证控件；
- 伪装请求或设备指纹；
- 把会话信息复制到未授权请求；
- 绕过验证码、访问控制或网站安全策略。

宿主应用应该提供清楚的状态、取消、返回和重试路径。

## 受限 JavaScript

受限 JavaScript 只用于 HTML 解析、URL 编码和经过网络网关的请求。它不能访问：

- Android API；
- 本地文件系统；
- 系统设置、通讯录或剪贴板；
- 任意代码加载能力；
- manifest 之外的域名。

## 发布前检查

在独立插件仓库发布前，请确认：

1. 数据来源和内容有明确授权；
2. manifest 的 HTTPS、域名、权限、速率和 API 版本合理；
3. validate 和离线 test 都通过；
4. 插件包使用发布密钥签名，私钥只保存在本机安全位置；
5. 许可证、来源和变更记录完整；
6. fixture 不包含真实商业网站数据或个人信息；
7. 用户能理解插件需要的权限和人工操作。

插件 CLI 的更多命令见 [plugin-sdk/README.md](../plugin-sdk/README.md)。
