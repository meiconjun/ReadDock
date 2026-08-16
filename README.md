# ReadDock

ReadDock 是一个 Android 漫画阅读器 Beta，面向希望把在线数据源、本地漫画文件和可审计插件统一放进一个阅读体验的用户与开发者。

当前公开版本：`0.5.0-beta01`。

## 能做什么

- Android 手机和平板阅读
- 在线数据源插件：搜索、详情、章节和图片阅读
- 本地导入与阅读：EPUB、MOBI、PDF、CBZ/ZIP，以及图片文件和图片文件夹
- 书架、阅读历史、阅读进度和本地图片缓存
- 图片缩放、拖拽、横向翻页、纵向阅读和超大图片保护
- 声明式 CSS 插件与受限 JavaScript 插件
- 签名插件包、插件仓库、版本校验和失败回滚
- `plugin-cli`：初始化、校验、离线 fixture 测试、打包和密钥生成

## 隐私与安全边界

本地 EPUB、MOBI、PDF、CBZ/ZIP 和图片文件会复制到应用私有存储，仅用于本地解析和阅读，不会由 ReadDock 上传网络。网络请求只来自用户启用的数据源插件、插件仓库或在线图片加载。

插件运行在 ReadDock 的统一协议边界内：只能使用声明的能力、权限和域名；插件包应使用可信公钥签名。插件作者和用户必须遵守目标网站条款、版权和授权要求。主仓库不提供真实商业网站数据源，也不绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制。

需要登录或人工操作的数据源只能通过通用的 `requiresUserInteraction` 协议由外部插件声明；本 Beta 不内置任何真实站点会话适配器。

## 模块

- `app`：ReadDock Android UI、在线阅读器和本地阅读器
- `core:source-api`：数据源模型、插件能力和权限协议
- `core:source-runtime`：声明式/受限脚本插件运行时、网络网关、签名和仓库校验
- `core:data`：Room 书架、阅读进度、图片缓存和下载队列
- `plugin-sdk`：插件包格式、示例和离线合成 fixture
- `plugin-cli`：插件开发与发布前校验工具

## 构建 Debug APK

要求：Android Studio、Android SDK 35、Gradle 8.9 和 Java 17。

```text
gradle :app:assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。安装到已连接设备：

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如需在没有环境变量的终端执行，可使用本机 Gradle 8.9 路径：

```text
C:\Gradle\gradle-8.9\bin\gradle.bat :app:assembleDebug
```

## 测试

```text
gradle :core:source-runtime:test
gradle :core:data:testDebugUnitTest
gradle :app:testDebugUnitTest
gradle :plugin-cli:test
gradle :app:assembleDebug
```

发布前还应执行：

```text
gradle :app:verifyReleasePublicSurface
```

该检查会构建 release APK，并检查公开构建中不能出现真实站点适配器、生产 fixture、开发 UI 文案或敏感站点标识。

## 导入和阅读本地文件

打开“书架”后选择“导入本地”，可以导入单个 EPUB、MOBI、PDF、CBZ/ZIP 或图片文件；也可以多选图片或选择一个图片文件夹。文件复制到应用私有目录后即可离线阅读。删除书架项目会同时删除应用私有目录中的副本。

## 安装外部插件

打开“插件”页面：

1. 使用“导入”选择本地插件 JSON 包；或配置受信任的 HTTPS 插件仓库。
2. 只有通过 manifest、权限、域名和签名校验的插件才会安装。
3. 安装后可以启用、停用、更新、回滚或卸载插件。

插件开发和发布说明见 [docs/PLUGIN_DEVELOPMENT.md](docs/PLUGIN_DEVELOPMENT.md) 与 [plugin-sdk/README.md](plugin-sdk/README.md)。仓库内的 `plugin-sdk/fixtures` 只用于开发和 CI，不会注册到正式 Beta，也不会进入 release APK。

## 插件 CLI

安装发行版后使用 `readdock-source`：

```text
readdock-source init example-source
readdock-source validate example-source
readdock-source test example-source
readdock-source package example-source dist/example-source.json
readdock-source keygen signing-keys
```

仓库示例使用项目自有合成内容，只访问离线快照，不代表 ReadDock 已接入任何真实商业网站。

## Beta 限制

- 当前 Beta 不内置真实商业网站数据源，在线内容需要用户自行安装合规的外部插件。
- `applicationId` 已确定为 `com.readdock.app`；由于项目尚未对外发布，本次 Beta 同步迁移了 Android 身份、namespace 和内部源码包名。
- release signing 只使用项目维护者本机的 keystore；真实签名密钥和 `keystore.properties` 不得提交到仓库。
- 插件仓库需要用户配置 HTTPS 地址、keyId 和可信公钥；本项目不提供默认远程仓库。
- 部分需要用户交互的数据源能力只保留协议和安全边界，外部插件仍需自行实现合规的交互流程。

## 版权与授权

ReadDock 是阅读器和插件运行时，不提供漫画内容目录。用户对导入文件、安装插件、访问目标站点以及由此产生的版权、许可、隐私和条款责任负责。请仅阅读你有权访问和保存的内容。

## 发布与校验

Beta 发布信息见 [docs/RELEASE_BETA_0.5.0-beta01.md](docs/RELEASE_BETA_0.5.0-beta01.md)。构建产物的 SHA-256 可在 PowerShell 中生成：

```powershell
Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

本机 release signing 使用 `signing-keys/readdock-release.jks` 和被 Git 忽略的 `keystore.properties`。签名证书指纹可用以下命令查看：

```powershell
keytool -list -v -keystore .\signing-keys\readdock-release.jks -alias readdock-release
```

不要提交 keystore、密码或 `keystore.properties`；公开 CI 应通过加密 secrets 配置签名。

欢迎通过 Issue 和 Pull Request 报告可复现的问题。提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)、[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) 和 [SECURITY.md](SECURITY.md)。
