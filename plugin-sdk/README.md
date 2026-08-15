# ComicHub Plugin SDK 草案

插件开发者只需要实现搜索、详情和章节图片解析，不需要处理书架、下载、缓存和阅读器。

第一版协议采用受限 TypeScript/JavaScript 风格，最终由 App 内的沙箱运行时执行。插件只能访问 `manifest.json` 中声明的域名和能力。

插件通过 `ctx.http` 访问网络。请求会统一经过 App 的 Network Gateway，自动处理域名限速、并发、缓存、有限重试、429/5xx 退避和熔断。插件不需要、也不允许实现代理轮换、指纹伪装或验证码绕过。

对于普通 HTML 网站，优先使用声明式插件：只填写搜索 URL 模板、列表项选择器、标题/链接/封面选择器、章节选择器和图片选择器。这样无需编写 Kotlin，也无需执行网站 JavaScript。

需要条件判断、分页拼接或多个页面结构时，可以使用脚本插件包 `package.javascript.example.json`。脚本包在受限 Rhino 沙箱中执行，只提供同步的 `search(ctx, query, page)`、`detail(ctx, url)` 和 `pages(ctx, url)` 函数。`ctx.http.get`、`ctx.url.encode` 和 `ctx.html` 是唯一的运行时帮助对象；HTML 选择器支持 `select`、`selectText`、`selectAttr`、`text` 和 `attr`。脚本不能访问 Java/Android API、动态执行代码或未在 manifest 中声明的域名，并受指令数上限约束。

插件的 `manifest.rateLimit` 会自动转换为 Network Gateway 的默认限速和并发策略；带用户会话请求默认不进入公共缓存。

完整声明式 JSON 包格式见 `package.example.json`；脚本包格式见 `package.javascript.example.json`。当前 App 代码已经可以解析、校验并加载两种包，脚本插件和声明式插件共用签名、仓库、限速、缓存和回滚流程。

## CLI 工作流

项目包含独立的 `plugin-cli` JVM 工具。它复用 App 的插件解析器，不需要 Android 设备即可完成发布前检查：

```text
comic-source init my-source
comic-source validate my-source
comic-source test my-source
comic-source fixture capture https://example.com/page.html my-source/fixtures/detail.html
comic-source keygen signing-keys
comic-source package my-source dist/my-source.json --private-key signing-keys/private_key.pem --key-id my-key
```

`init` 会生成 `package.json`、三个离线快照和 `fixtures/fixture.json`。后者声明内容来源说明以及期望的搜索结果、标题、章节数和页面数。`test` 会依次执行 `search`、`detail` 和 `pages`，并核对这些期望值；只读这些快照，fixture 测试失败时不会访问网络。仓库中可直接运行的示例位于 `fixtures/example-source`。当前示例是项目自有的合成授权 fixture，不代表已经接入真实漫画网站；接入真实授权源时，应使用获得许可的页面快照替换它，并保留可审计的来源说明。

从源码树运行 CLI 的 Windows 命令是：

```text
C:\Gradle\gradle-8.9\bin\gradle.bat :plugin-cli:run --args="test ../plugin-sdk/fixtures/example-source"
```

生成的签名包是现有 `SignedPluginEnvelope` 格式，签名算法为 `SHA256withRSA`。`keygen` 生成 2048 位 RSA PKCS#8 私钥和 X.509 公钥；私钥只应保存在发布者自己的安全环境中，不要提交到仓库。远程仓库仍需同时填写插件 SHA-256，App 会在安装前执行签名和哈希校验。

远程仓库索引格式见 `repository.example.json`。远程安装应使用签名信封：签名算法当前为 `SHA256withRSA`，App 只接受预置可信公钥对应的 `keyId`，并在安装前校验插件文件的 SHA-256。未签名 JSON 仅用于本地开发导入。

## 推荐实现顺序

1. `search`
2. `detail`
3. `pages`
4. 固定 HTML fixture 测试
5. 打包和签名

复杂网站如果需要用户登录或人工验证，应返回 `REQUIRES_USER_INTERACTION`，由 App 打开受控 WebView，不在后台自动绕过验证。
