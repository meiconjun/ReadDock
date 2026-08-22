# ReadDock

ReadDock 是一个先把本地阅读做好、再通过外部插件连接在线数据源的 Android 漫画阅读器。

如果你手上已经有 EPUB、MOBI、PDF、CBZ/ZIP 或图片文件，可以直接导入 ReadDock，在手机或平板上阅读。想接入在线数据源时，再安装符合自己授权和目标网站条款的外部插件。

当前版本：0.5.0-beta02

## 先说清楚 ReadDock 是什么

ReadDock 不是漫画内容网站，也不提供内置漫画目录。它更像一个阅读器和插件运行时：

- 本地文件由应用复制到自己的私有存储，导入后可以离线阅读。
- 外部插件负责提供搜索、详情、章节和图片地址。
- 插件必须声明自己的权限、域名和能力，并通过校验后才能安装。
- 主仓库不包含真实商业网站适配器，也不负责提供未经授权的漫画内容。

我们希望把“阅读自己的文件”和“使用外部数据源”分开，让两件事都更容易理解，也更容易测试和维护。

## 目前可以做什么

ReadDock Beta 目前支持：

- EPUB、MOBI、PDF、CBZ/ZIP、单张图片和图片文件夹；
- 书架、阅读历史、阅读进度和本地图片缓存；
- 图片缩放、拖拽、横向翻页、纵向阅读和超大图片保护；
- 本地阅读器点击页面隐藏/显示控制层，并可拖动阅读进度条跳转页面；
- 外部数据源插件的导入、启用、停用、更新、回滚和卸载；
- 声明式 CSS 插件和受限 JavaScript 插件；
- Android 手机和平板。

这是 Beta，不是完成品。已知限制见文末。

## 隐私和安全

导入的本地文件会放在 ReadDock 的应用私有目录中，用于解析和阅读，不会因为导入操作被上传网络。网络请求只可能来自用户启用的数据源插件、插件仓库或在线图片加载。

插件不是完全可信的“主题文件”，而是会参与数据请求和解析的外部代码或配置。ReadDock 会限制插件的能力、权限、域名和请求频率；用户仍然应该只安装自己信任、来源清楚并且符合授权要求的插件。

ReadDock 不绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制。需要用户操作的数据源只能通过 requiresUserInteraction 协议把流程交还给用户。

## 第一次构建

需要：

- Java 17；
- Android SDK 35；
- Gradle 8.9；
- Android Studio（用于设备调试时）。

构建 Debug APK：

~~~text
gradle :app:assembleDebug
~~~

APK 位于：

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

安装到已连接设备：

~~~text
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

## 导入本地漫画

打开“书架”，选择“导入本地”，然后选择：

- 一个 EPUB、MOBI、PDF 或 CBZ/ZIP 文件；
- 一张图片；
- 多张图片；
- 一个图片文件夹。

导入完成后，文件会复制到应用私有目录。删除书架项目时，应用保存的副本也会一起删除。ReadDock 不会把这些本地文件上传到网络。

## 安装外部插件

打开“插件”页面，可以：

1. 导入本地插件 JSON 包；
2. 配置受信任的 HTTPS 插件仓库、keyId 和 RSA 公钥 Base64；
3. 读取签名 repository index，查看可用插件、权限和域名；
4. 安装后启用、停用、更新、回滚或卸载；
5. 取消仓库请求，并在失败后重试。

本地导入也必须使用受信任公钥验证签名。ReadDock 不会因为导入 JSON 而关闭签名校验。

主仓库不内置真实商业网站数据源。外部插件的作者和使用者需要自行确认版权、授权、隐私和目标网站条款。

插件开发说明见 [docs/PLUGIN_DEVELOPMENT.md](docs/PLUGIN_DEVELOPMENT.md)，协议示例见 [plugin-sdk/README.md](plugin-sdk/README.md)。

## 运行测试

常用测试命令：

~~~text
gradle :core:source-runtime:test
gradle :core:data:testDebugUnitTest
gradle :app:testDebugUnitTest
gradle :plugin-cli:test
gradle :app:assembleDebug
~~~

发布前再运行：

~~~text
gradle :app:verifyReleasePublicSurface
~~~

这个任务会构建 release APK，并检查 APK 中是否混入真实站点适配器、测试源、fixture 或开发文案。

## 插件 CLI

插件开发工具的命令名是 readdock-source：

~~~text
readdock-source init example-source
readdock-source validate example-source
readdock-source test example-source
readdock-source package example-source dist/example-source.json
readdock-source keygen signing-keys
~~~

仓库里的 example-source 只包含项目自有的合成离线内容，用于测试搜索、详情、章节和页面解析，不代表 ReadDock 已接入任何真实商业网站。

## 给插件开发者的依赖

发布版本会把插件协议和运行时发布到 GitHub Packages。Gradle 项目可以这样配置仓库：

~~~kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/meiconjun/ReadDock")
        credentials {
            username = providers.gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                .get()
            password = providers.gradleProperty("gpr.key")
                .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                .get()
        }
    }
}
~~~

当前 Beta 坐标是：

~~~text
io.readdock:source-api:0.5.0-beta02
io.readdock:source-runtime:0.5.0-beta02
~~~

Packages 需要 GitHub 身份验证。请使用自己的令牌或 GitHub Actions 密钥，不要把令牌写进源码、Gradle 文件或 README。

## 代码结构

- app：Android 界面、本地阅读器和在线阅读器；
- core/source-api：插件协议、数据模型和权限定义；
- core/source-runtime：插件加载、网络网关、签名和仓库校验；
- core/data：书架、阅读进度、图片缓存和下载队列；
- plugin-sdk：插件包格式、示例和离线 fixture；
- plugin-cli：插件开发和发布前校验工具。

## Beta 目前的边界

- 在线内容需要用户自行安装合规的外部插件；
- 本仓库不提供真实商业网站数据源或漫画目录；
- 插件仓库需要用户自己配置 HTTPS 地址和可信公钥；
- 部分需要用户操作的数据源能力只提供协议，插件仍需自行实现；
- 大文件、特殊 MOBI 变体和不同站点的兼容性还需要更多测试。

## 发布和签名

当前 Android 身份是 com.readdock.app。Debug 构建不需要正式签名；公开发布应使用维护者自己的 release keystore。

本机 release keystore 和 keystore.properties 已被 Git 忽略，不能提交到仓库。其他维护者请参考 [keystore.properties.example](keystore.properties.example)，不要复制或共享现有私钥。

发布说明和本次验证结果见 [docs/RELEASE_BETA_0.5.0-beta02.md](docs/RELEASE_BETA_0.5.0-beta02.md)。

## 贡献和许可证

欢迎提交能在公开仓库中安全复现的问题，或改进本地阅读、插件协议和测试基础设施的 Pull Request。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 和 [SECURITY.md](SECURITY.md)。

ReadDock 使用 MIT License。漫画文件、外部插件和目标网站内容的版权与授权责任由使用者和插件作者承担。
