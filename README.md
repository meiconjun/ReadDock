# ComicHub

ComicHub 是一个面向 Android 的漫画阅读器原型，目标是让漫画源插件易于开发、可审计、可观测。

当前阶段包含：

- Android Compose 应用骨架
- `ComicSource` 漫画源协议
- 本地 `MockSource`
- 搜索、详情、章节、阅读、书架原型页面
- 插件 manifest、签名仓库和受限脚本协议草案
- 插件更新历史、失败回滚、图片文件缓存和并发下载队列
- 独立 `plugin-cli` 开发工具：初始化、校验、离线 fixture 测试、fixture 捕获、打包签名和 RSA 密钥生成
- 当前 UI 仍是功能验证优先的 Compose 原型，暂不投入视觉精修

## 打开项目

使用 Android Studio 打开此目录，等待 Gradle Sync 完成后运行 `app` 配置。当前开发机已安装 Android Studio、SDK Platform 35、Gradle 8.9 和 Java 17。

## 模块

- `app`：Compose UI 和应用入口
- `core:source-api`：漫画源领域模型与插件协议基础
- `core:source-runtime`：源注册表、JSON/声明式 CSS/受限 JavaScript 插件、Network Gateway、签名与仓库客户端
- `core:data`：Room 书架/章节/阅读进度数据、图片缓存和下载队列
- `plugin-sdk`：面向插件作者的协议和 JSON 插件包示例
- `plugin-cli`：插件开发者命令行工具，不依赖 Android 设备即可运行 fixture 解析和发布前校验

## 插件 CLI

从项目根目录执行 `:plugin-cli:run` 时，Gradle 的工作目录是 `plugin-cli`，因此访问仓库内示例需要使用 `../plugin-sdk/...`；安装发行版后则按当前目录解析路径。

```text
comic-source init example-source
comic-source validate example-source
comic-source test example-source
comic-source fixture capture https://example.com/page.html example-source/fixtures/detail.html
comic-source keygen signing-keys
comic-source package example-source dist/example-source.json --private-key signing-keys/private_key.pem --key-id example-key
```

`package` 默认输出未签名 JSON；提供 `--private-key` 和 `--key-id` 时输出现有运行时支持的 `SignedPluginEnvelope`。私钥支持 PKCS#8 PEM 或 DER，公钥可用于 `validate --require-signature --public-key ... --key-id ...`。`test` 读取 `search.html`、`detail.html`、`pages.html` 和可选的 `fixture.json`；若存在后者，还会核对内容来源说明和期望解析结果，不会访问真实网站。当前仓库示例使用项目自有合成内容，真实授权源仍需在获得授权后替换 fixture 并补充来源记录。

## 模拟器验收

当前开发机已安装 API 35 Google APIs x86_64 模拟器，AVD 名称为 `ComicHub_API35`。启动后可用以下命令安装并打开 Debug APK：

```text
adb -s emulator-5554 install -r -d app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.comichub.app/.MainActivity
```

已验证发现页、详情/章节、阅读页、书架、插件页和 App 强制停止后的 Room 阅读进度恢复；当前示例阅读进度可恢复到第 3/6 页。

## 当前验证

```text
:core:data:testDebugUnitTest   PASS (4 tests)
:core:source-runtime:test       PASS (27 tests)
:app:testDebugUnitTest         PASS (3 tests)
:plugin-cli:test                PASS (5 tests)
:app:assembleDebug              PASS
```

本轮还为 fixture 增加了可选的 `fixtures/fixture.json` 契约：CLI 会核对内容来源说明以及搜索结果、标题、章节数和页面数，旧版没有该文件的插件目录仍保持兼容。

声明式插件只需要提供搜索/详情/章节/图片的 CSS 选择器；复杂站点也可使用受限 JavaScript 插件函数。网络请求统一经过限速、并发、缓存、重试和熔断层。远程插件还需要通过可信公钥签名和 SHA-256 校验。
