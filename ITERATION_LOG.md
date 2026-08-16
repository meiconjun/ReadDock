# ComicHub 迭代文档

这是一份持续维护的项目交接文档。每次开发结束后记录本次迭代的目标、实际完成内容、验证结果和下一步，避免项目依赖单次会话上下文。

## 项目状态

- 项目代号：ComicHub
- 平台：Android
- 当前阶段：在线/本地漫画阅读器与插件化源基础能力
- 当前版本：0.4.0
- 最近构建：Debug APK 构建成功
- 当前 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

## 迭代记录

### Iteration 0：项目初始化

状态：已完成

完成内容：

- 建立 Android 多模块工程
- 添加 Jetpack Compose 应用入口和基础页面
- 完成搜索、详情、章节、阅读、书架原型闭环
- 定义 `ComicSource`、`ComicSummary`、`ComicDetail`、`Chapter`、`ComicPage`
- 添加本地 `MockSource`
- 添加插件 manifest 和权限校验基础

验证结果：

- Android Studio、SDK Platform 35、Build Tools 35.0.0、Gradle 8.9、Java 17 已配置
- `:core:source-runtime:test` 通过
- `:app:assembleDebug` 通过

### Iteration 1：网络稳定性与声明式源

状态：已完成

完成内容：

- 实现 `NetworkGateway`
- 按域名限速和并发控制
- 公共请求缓存；带会话请求默认不进入缓存
- 429/5xx 有限重试和指数退避
- 连续失败熔断
- 实现 `UrlConnectionTransport`
- 实现 `DeclarativeSource`
- 普通 HTML 源只需配置 URL 模板和 CSS 选择器
- 添加 NetworkGateway 和声明式解析器测试
- 添加 Android Internet 权限

关键文件：

- `core/source-runtime/.../NetworkGateway.kt`
- `core/source-runtime/.../UrlConnectionTransport.kt`
- `core/source-runtime/.../DeclarativeSource.kt`
- `core/source-runtime/.../NetworkGatewayTest.kt`
- `core/source-runtime/.../DeclarativeSourceTest.kt`

验证结果：

- 8 个核心单元测试通过
- `:app:assembleDebug` 通过

### Iteration 2：JSON 插件包

状态：已完成

目标：

- 将声明式源从 Kotlin 配置对象升级为 JSON 插件包
- 加载前验证插件 API 版本、HTTPS、域名、权限和选择器配置
- 提供可复制的示例插件包
- 为后续插件仓库和 App 内插件管理页面提供基础

实际完成：

- 为 manifest、能力、权限和限速模型加入 JSON 序列化
- 实现 `PluginPackageLoader`
- 加载前校验插件 id、HTTPS、域名、API 版本和选择器配置
- 添加 `plugin-sdk/package.example.json`
- 增加合法 JSON 插件加载测试
- 增加不安全 manifest 拒绝测试

本次未完成：

- JavaScript 插件沙箱
- 远程插件仓库
- 插件签名和更新回滚
- 真实网站源适配

验证结果：

- 10 个 `:core:source-runtime:test` 测试通过
- `:app:assembleDebug` 通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 3：本地插件目录与管理页面

状态：已完成

完成内容：

- 实现 `LocalPluginStore`
- 插件保存到 App 私有目录，不需要存储权限
- 支持本地 JSON 文件导入
- 支持插件启用、停用和卸载
- 实现动态 `SourceRegistry`
- 启用插件后加入搜索源
- 安装、启用、停用或卸载后自动刷新当前搜索结果
- 新增 App 内“插件”页面
- 显示内置源、已安装插件、版本和启用状态
- 增加插件存储和非法包测试

关键文件：

- `core/source-runtime/.../LocalPluginStore.kt`
- `core/source-runtime/.../SourceRegistry.kt`
- `app/.../MainViewModel.kt`
- `app/.../ui/ComicHubApp.kt`

验证结果：

- 12 个 `:core:source-runtime:test` 测试通过
- `:app:assembleDebug` 通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 4：插件签名与远程仓库基础

状态：已完成

完成内容：

- 实现 RSA `SHA256withRSA` 插件签名校验
- 实现可信公钥信任库和 `keyId`
- 支持签名信封格式
- 本地开发包仍允许未签名导入，远程安全模式必须签名
- 实现插件仓库索引解析
- 校验仓库插件下载地址必须使用 HTTPS
- 校验插件 SHA-256
- 实现版本比较和更新检测
- 实现远程索引/插件下载客户端
- 添加签名、版本、索引、哈希和下载测试

关键文件：

- `core/source-runtime/.../PluginSecurity.kt`
- `core/source-runtime/.../PluginRepository.kt`
- `core/source-runtime/.../PluginRepositoryClient.kt`
- `plugin-sdk/repository.example.json`

验证结果：

- 20 个 `:core:source-runtime:test` 测试通过
- `:app:assembleDebug` 通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 5：远程仓库与安全更新入口

状态：已完成

完成内容：

- 在插件页面加入仓库索引 URL 配置
- 在插件页面加入可信 RSA 公钥和 keyId 配置
- 保存仓库配置到本地 SharedPreferences
- 支持检查远程仓库更新
- 显示已安装插件与远程版本差异
- 支持下载并安装远程更新
- 远程索引必须签名
- 下载内容必须通过 SHA-256 校验
- 远程插件安装强制要求插件包签名
- 本地开发导入仍保留未签名模式
- 增加安全安装回归测试

关键文件：

- `app/.../MainViewModel.kt`
- `app/.../ui/ComicHubApp.kt`
- `core/source-runtime/.../PluginRepositoryClient.kt`
- `core/source-runtime/.../LocalPluginStore.kt`

验证结果：

- 21 个 `:core:source-runtime:test` 测试通过
- `:app:assembleDebug` 通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 6：插件回滚与图片下载基础设施

状态：已完成

完成内容：

- 插件更新前自动保存历史版本，支持从插件页面回滚
- 回滚过程继续保留当前版本，便于在多个历史版本之间恢复
- 插件卸载时清理对应历史版本，避免卸载后残留可执行配置
- `NetworkResponse` 同时保留 UTF-8 文本和原始字节
- 插件仓库 SHA-256 校验改用原始字节，避免二进制响应被文本转换破坏
- 新增基于文件的图片缓存，使用 URL 的 SHA-256 作为文件名并按容量淘汰旧文件
- 新增有并发上限、去重、缓存复用和失败状态的图片下载队列
- 阅读页接入图片下载队列；图片失败时仍保留可显示的文本或已成功页面
- 新增回滚、原始字节校验、缓存淘汰和下载队列测试

关键文件：

- `core/source-runtime/.../LocalPluginStore.kt`
- `core/source-runtime/.../NetworkGateway.kt`
- `core/source-runtime/.../PluginRepositoryClient.kt`
- `core/data/.../FileImageCache.kt`
- `core/data/.../ImageDownloadQueue.kt`
- `app/.../MainViewModel.kt`
- `app/.../ui/ComicHubApp.kt`

验证结果：

- `:core:data:test`：3 个测试通过
- `:core:source-runtime:test`：23 个测试通过
- 本轮核心测试合计：26 个通过，0 个失败
- `:app:assembleDebug`：通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 7：Room 书架、阅读历史与进度

状态：已完成

目标：在不改变插件协议和现有阅读流程的前提下，将书架、阅读历史和阅读进度持久化到 Room。

完成内容：

- 将 `core:data` 升级为 Android Library，集中承载 Room 数据库和仓储；图片缓存与下载队列保持原有接口和行为
- 设计最小数据模型：`ComicEntity`、`ChapterEntity`、`LibraryEntryEntity`、`ReadingProgressEntity`
- 漫画、章节、书架条目和进度均使用 `sourceId + comicId (+ chapterId)` 复合键，避免不同插件的同名 ID 冲突
- 阅读进度的 `lastReadAt` 作为阅读历史排序依据，不额外复制历史数据
- `RoomLibraryRepository` 提供详情、章节、收藏、历史和进度恢复能力；数据库按应用私有目录持久化
- `MainViewModel` 默认接入 Room 仓储，同时保留仓储抽象用于测试；打开详情/章节、收藏和阅读滚动都会更新持久化数据
- 阅读页打开章节时恢复上次页码，并在滚动时保存当前页；书架页显示持久化收藏和最近阅读历史
- 保持现有插件安装、签名、回滚、NetworkGateway、图片缓存和下载队列流程不变
- 增加 Room Robolectric 数据库测试和 MainViewModel 注入式测试

关键文件：

- `core/data/.../LibraryDatabase.kt`
- `core/data/.../LibraryEntities.kt`
- `core/data/.../LibraryDao.kt`
- `core/data/.../LibraryRepository.kt`
- `core/data/.../RoomLibraryRepositoryTest.kt`
- `app/.../MainViewModel.kt`
- `app/.../ui/ComicHubApp.kt`
- `app/.../MainViewModelTest.kt`

验证结果：

- `:core:source-runtime:test`：23 个测试通过
- `:core:data:testDebugUnitTest`：4 个测试通过（图片缓存/下载 3 个，Room 1 个）
- `:app:testDebugUnitTest`：1 个 ViewModel 测试通过
- 本轮全量测试合计：28 个通过，0 个失败
- `:app:assembleDebug`：通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 8：源健康状态与请求日志

状态：已完成

目标：在不改变插件调用和网络策略的前提下，为漫画源请求增加可观察的健康状态、耗时和结构化失败信息。

完成内容：

- 在 `core:source-runtime` 增加 `SourceHealthTracker`
- `NetworkRequest` 增加可选 `sourceId`，旧调用保持兼容；未标记 sourceId 的请求按 host 归类
- `NetworkGateway` 记录成功、缓存命中、HTTP 失败、传输失败和熔断事件
- 记录请求次数、成功率、缓存命中次数、最近状态码、耗时、失败原因和最近 20 条日志
- 日志只保留 URL path，不保存 query 参数、请求体或会话信息
- App 的声明式插件 HTML 请求带上 manifest sourceId；插件仓库和图片请求仍可按 host 回退统计
- 插件页面新增源健康状态卡片，显示成功率、请求次数、延迟和最近错误
- 增加健康追踪器和 NetworkGateway 集成测试，验证缓存、失败统计和 query 脱敏

关键文件：

- `core/source-runtime/.../SourceHealth.kt`
- `core/source-runtime/.../NetworkGateway.kt`
- `core/source-runtime/.../SourceHealthTest.kt`
- `app/.../MainViewModel.kt`
- `app/.../ui/ComicHubApp.kt`

验证结果：

- `:core:source-runtime:test`：24 个测试通过
- `:core:data:testDebugUnitTest`：4 个测试通过
- `:app:testDebugUnitTest`：1 个测试通过
- 本轮全量测试合计：29 个通过，0 个失败
- `:app:assembleDebug`：通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 9：受限 JavaScript 插件运行时

状态：已完成

目标：在保持声明式插件、签名仓库、更新回滚和现有阅读流程兼容的前提下，为需要条件逻辑的漫画源提供受限 JavaScript 执行能力。

完成内容：

- 新增 `JavaScriptSourceDefinition` 和 `JavaScriptSource`，插件包通过 `script` 字段实现 `search`、`detail`、`pages` 三个同步入口
- 使用 Rhino 解释器和指令数上限执行脚本，关闭 Java 类访问，并拒绝 `Packages`、`JavaAdapter`、动态 `Function`/`eval` 等危险入口
- 只向脚本暴露 `ctx.http.get`、`ctx.url.encode` 和 Jsoup HTML 桥接；选择器支持 `select`、`selectText`、`selectAttr`、`text` 和 `attr`
- 所有脚本请求仍通过现有 `fetchHtml`/NetworkGateway，URL 必须是 HTTPS 且 host 必须匹配 manifest 声明域名
- 保持原有声明式 JSON 包解析、插件签名校验、LocalPluginStore、更新回滚和 App 插件加载路径不变
- 新增脚本插件 SDK 示例 `plugin-sdk/package.javascript.example.json`，并补充脚本 API、沙箱限制和兼容性说明
- 新增 JavaScript fixture、危险 token 拒绝和指令预算测试

关键文件：

- `core/source-runtime/.../JavaScriptSource.kt`
- `core/source-runtime/.../PluginPackageLoader.kt`
- `core/source-runtime/.../JavaScriptSourceTest.kt`
- `plugin-sdk/package.javascript.example.json`
- `plugin-sdk/README.md`

验证结果：

- `:core:source-runtime:test`：27 个测试通过
- `:core:data:testDebugUnitTest`：4 个测试通过
- `:app:testDebugUnitTest`：1 个测试通过
- 本轮全量测试合计：32 个通过，0 个失败
- `:app:assembleDebug`：通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 10：插件 CLI、离线 fixture 与打包签名工作流

状态：已完成

目标：把插件开发计划中的 `init`、fixture 校验、发布前验证和签名打包落地为独立 JVM CLI，不改变 App 内已有插件协议和安装流程。

完成内容：

- 新增 `:plugin-cli` Gradle JVM 模块，提供 `init`、`validate`、`test`、`fixture capture`、`package` 和 `keygen` 命令
- `init` 生成可被现有运行时直接加载的声明式 `package.json`、`search.html`、`detail.html`、`pages.html` 和 README
- `test` 通过离线 fixture 依次调用 `search`、`detail`、`pages`，覆盖声明式插件真实运行路径，不访问真实网站
- `fixture capture` 支持复制本地快照或捕获 HTTPS 响应，默认拒绝覆盖已有文件并拒绝 HTTP URL
- `validate` 复用 `PluginPackageLoader`，支持验证签名信封；`package` 复用现有 `SignedPluginEnvelope` 和 `SHA256withRSA`，支持 PKCS#8 PEM/DER 私钥
- `keygen` 生成 2048 位 RSA PKCS#8 私钥和 X.509 公钥，便于建立开发/发布签名流程
- 在 `plugin-sdk/fixtures/example-source` 增加可重复运行的声明式 fixture；增加 CLI 初始化、fixture 运行、签名校验和捕获覆盖测试
- 保持现有声明式插件、JavaScript 插件、Room、缓存、下载队列、仓库签名、更新回滚和 App 阅读流程不变

关键文件：

- `plugin-cli/src/main/kotlin/com/comichub/cli/Main.kt`
- `plugin-cli/src/test/kotlin/com/comichub/cli/CliTest.kt`
- `plugin-sdk/fixtures/example-source/`
- `plugin-sdk/README.md`

验证结果：

- `:plugin-cli:test`：3 个测试通过
- 使用仓库示例执行 CLI `test`：fixture 通过，search=1、chapters=1、pages=2
- `:core:source-runtime:test`：27 个测试通过
- `:core:data:testDebugUnitTest`：4 个测试通过
- `:app:testDebugUnitTest`：1 个测试通过
- 本轮相关测试合计：35 个通过，0 个失败
- `:plugin-cli:installDist`：通过
- `:app:assembleDebug`：通过
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 11：Android 模拟器安装与 UI 冒烟验收

状态：已完成

目标：在没有真机的情况下，用 Android 模拟器验证 APK 启动、核心阅读流程、Room 恢复和主要导航页面。

完成内容：

- 发现本机没有 emulator、AVD 和 sdkmanager；从 Android 官方下载并校验 command-line tools，安装到现有 SDK
- 安装 Emulator、API 35 Google APIs x86_64 系统镜像和 `ComicHub_API35` AVD
- 安装 Android Emulator Hypervisor Driver 2.2；`emulator -accel-check` 确认 AEHD 可用
- 安装最新 `app-debug.apk` 到 `emulator-5554` 并启动 `com.comichub.app/.MainActivity`
- 通过截图和 ADB 检查发现页、详情/章节、阅读页、书架和插件页
- 阅读中滚动到第 3 页后强制停止并重启 App，书架历史仍显示“第 3/6 页”，确认 Room 进度恢复
- 全程未发现 `FATAL EXCEPTION` 或 `AndroidRuntime` 崩溃日志

验证结果：

- AVD：`ComicHub_API35`，设备：`emulator-5554`
- APK 安装：成功
- 核心 UI 冒烟流程：通过
- Room 重启恢复：通过
- 模拟器截图保存在本地 `build/emulator-*.png`

备注：当前 UI 仍保持功能验证优先的原型形态，布局和视觉细节较为简陋；本阶段不以视觉精修为目标，待核心功能和真实授权源验证稳定后再统一设计。

仍待处理：网络失败、插件导入/更新失败等异常状态的逐项 UI 验收，以及真实授权漫画源的端到端 fixture。

### Iteration 12：错误状态和空结果提示优化

状态：已完成

目标：在保持原型 UI 方向不变的前提下，确保网络、插件和搜索异常能被用户看到并理解。

完成内容：

- 新增 `UiMessage` 和 `MessageTone`，区分插件/仓库操作成功提示与错误提示
- 详情页和阅读页显示打开漫画、打开章节、图片加载或 Room 保存失败信息
- 搜索无结果时显示明确空状态，不再只显示空白列表
- 插件导入失败、仓库 HTTPS/key 配置错误、签名/下载失败使用错误色提示
- 增加非法仓库 URL和非法插件导入的 ViewModel 回归测试
- 当时明确记录 UI 仅服务于功能验收；后续 Iteration 15 已完成阅读器布局精修

关键文件：

- `app/src/main/java/com/comichub/app/MainViewModel.kt`
- `app/src/main/java/com/comichub/app/ui/ComicHubApp.kt`
- `app/src/test/java/com/comichub/app/MainViewModelTest.kt`

验证结果：

- `:core:source-runtime:test`：27 个测试通过
- `:core:data:testDebugUnitTest`：4 个测试通过
- `:app:testDebugUnitTest`：3 个测试通过
- `:plugin-cli:test`：3 个测试通过
- 本轮相关测试合计：37 个通过，0 个失败
- `:app:assembleDebug`：通过
- 模拟器验证搜索不存在的 `zzz` 时显示“没有找到漫画，试试其他关键词。”，无崩溃
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

### Iteration 13：授权 fixture 契约与离线端到端校验

状态：已完成

目标：在不访问真实网站、不绕过登录或验证码的前提下，让插件 fixture 能记录内容来源说明，并对搜索→详情→章节页面解析结果做可重复的精确校验，为后续真实授权源接入提供安全的离线门槛。

完成内容：

- `plugin-cli init` 新增 `fixtures/fixture.json`，记录 `contentAuthorization` 和期望的搜索结果数、标题、章节数、页面数
- `plugin-cli test` 在现有声明式插件运行路径上读取快照并核对上述契约，fixture 被意外改动时返回失败原因
- 保持兼容：没有 `fixture.json` 的旧插件目录继续按原有“结果非空”规则运行
- 为仓库示例 `plugin-sdk/fixtures/example-source` 增加来源说明和精确期望值
- 增加契约成功、快照篡改失败和旧 fixture 兼容测试
- 文档明确：当前示例是项目自有的合成授权 fixture，不等同于已接入真实漫画网站；真实源需在获得授权后替换快照并保留来源记录

验证结果：

- `:core:source-runtime:test`：27 个测试通过
- `:core:data:testDebugUnitTest`：4 个测试通过
- `:app:testDebugUnitTest`：3 个测试通过
- `:plugin-cli:test`：5 个测试通过
- 本轮相关测试合计：39 个通过，0 个失败
- `:plugin-cli:run --args="test ../plugin-sdk/fixtures/example-source"`：通过，search=1、chapters=1、pages=2
- `:app:assembleDebug`：通过
- 模拟器 `ComicHub_API35` / `emulator-5554`：重新安装并启动 APK 成功，最近日志无崩溃
- 最新 APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)

仍待处理：真实授权漫画源需要用户提供具体源、许可证明或访问凭据；在此之前只能完成本地合成 fixture 和通用接入能力。WebView 用户交互通道仍是后续工作。

### Iteration 14：本地漫画导入与独立阅读器

状态：已完成

目标：在不伪装成本地在线源、不上传用户文件和不破坏现有在线漫画流程的前提下，支持导入并阅读本地漫画。

完成内容：

- 使用 Android Storage Access Framework 支持单文件、多选图片和文件夹递归导入
- 导入文件复制到 App 私有目录，使用文件哈希去重，避免原始 URI 失效
- 新增独立 `LocalComic` 数据模型、Room 持久化和本地书架
- 支持 JPG、JPEG、PNG、WEBP、GIF、PDF、EPUB、MOBI，并额外支持 CBZ/ZIP
- 图片按文件名自然排序；损坏图片按页面报告错误，不影响其他页面
- PDF 使用 `PdfRenderer` 按页读取并正确关闭渲染资源
- EPUB 解析容器、manifest、spine 和图片资源；无图片内容时支持文字页
- MOBI 在本地后台解析，无法解析时显示明确错误
- 新增独立 `LocalReaderScreen` 和 `LocalReaderViewModel`，不复用在线网页阅读器
- 阅读进度保存到 Room，App 重启后可恢复
- 修复阅读进度写入依赖页面加载任务、快速返回时可能被取消的问题；进度写入改为独立串行任务
- 删除本地漫画前确认，删除后立即从书架移除

关键文件：

- `app/src/main/java/com/comichub/app/local/LocalComicImporter.kt`
- `app/src/main/java/com/comichub/app/local/LocalComicParser.kt`
- `app/src/main/java/com/comichub/app/local/LocalReaderScreen.kt`
- `app/src/main/java/com/comichub/app/local/LocalReaderViewModel.kt`
- `core/data/src/main/kotlin/com/comichub/data/LibraryEntities.kt`
- `core/data/src/main/kotlin/com/comichub/data/LibraryRepository.kt`

验证结果：

- `gradle test`：通过
- `gradle :app:assembleDebug`：通过
- 使用实际约 137 MB EPUB 文件导入成功，解析为 184 页
- 模拟器验证封面、EPUB 页面、翻页、快速返回、Room 进度恢复和删除流程
- 未发现 `FATAL EXCEPTION` 或 `OutOfMemoryError`
- APK 版本：versionCode 6，versionName 0.4.0

### Iteration 15：本地与在线阅读器布局统一

状态：已完成

目标：解决阅读页面外围大片浅色留白和 Card 边框问题，统一本地图片阅读器与在线图片阅读器的阅读体验，同时保持 MYCOMIC WebView 会话流程不变。

完成内容：

- 本地阅读器改为黑色沉浸式阅读画布
- 本地漫画页面移除重复标题和多余内边距，图片尽量铺满可用宽度
- 在线图片阅读器移除页面外围 Card、12dp 外边距和页间空隙
- 在线漫画页面改为连续全宽显示，加载和错误占位使用深色画布
- 在线和本地阅读器顶部栏、翻页/章节控件和系统导航区域统一深色风格
- 使用 `ContentScale.Fit` 保持本地漫画页面完整，不因填充而裁切内容
- 保持在线阅读器的 Room 进度、章节切换、图片重试和 MYCOMIC WebView 验证流程不变

关键文件：

- `app/src/main/java/com/comichub/app/local/LocalReaderScreen.kt`
- `app/src/main/java/com/comichub/app/ui/ComicHubApp.kt`

验证结果：

- `gradle test :app:assembleDebug`：通过
- 模拟器验证本地 EPUB 184 页阅读器布局
- 模拟器验证在线《猎人游戏 W》第 07 话图片全宽显示
- 未发现 `FATAL EXCEPTION` 或 `OutOfMemoryError`
- 最新 Git commit：`c8f79cd7397da3ae48f89c2ca80abd62d2b74640`
- 当前 APK SHA-256：`4FFB0A4C941057C8AFCCE8E6DC319AF8E1174B2E4CEE073D37A3BCA9CD01127F`

### Iteration 16：统一阅读器手势与受限图片加载

状态：已完成

目标：统一本地和在线图片阅读器的缩放、拖拽与横向翻页手势，按页面受限加载在线图片，并隔离快速切页/切章时的异步结果，降低大图 OOM 和旧页面串入风险。

完成内容：

- 新增共享 `ZoomableReaderImage`：支持 1x–4x 双指缩放、以点击位置为中心的双击 2.5x 放大/还原、缩放拖拽和边界限制
- 未缩放时仅消费明显占优且达到约 64dp 的横向滑动；纵向滑动放行给在线 `LazyColumn`，缩放、拖拽、双指和双击不会触发翻页
- 本地阅读器保留单页显示，增加带请求序号的解码任务取消和旧 Bitmap 写回保护；EPUB、MOBI、PDF 继续走本地解析/文件路径，不进入 WebView
- 在线阅读器移除整章 `ByteArray` 缓存，改为可见页面按需下载、磁盘文件 bounds/采样解码，Compose 页面离开列表后取消任务；增加 24 MB Bitmap LRU、256 MB 磁盘缓存和 32 MB 单文件限制
- `NetworkRequest` 增加二进制响应模式和响应字节上限；图片请求限制为 24 MB，传输层超限时明确失败，避免额外 UTF-8 字符串副本
- 下载队列增加单页加载、并发 URL 去重和取消；单页失败仅重试目标页
- 章节、页面、图片预取和进度写入增加 reader generation/token 校验；章节导航幂等，快速切章、返回和 ViewModel 销毁时清理任务、页面引用及解码缓存
- 保留本地文件不上传网络的路径；在线图片仍通过现有授权会话/请求头获取

关键文件：

- `app/src/main/java/com/comichub/app/reader/ReaderGestures.kt`
- `app/src/main/java/com/comichub/app/reader/ReaderImageLoader.kt`
- `app/src/main/java/com/comichub/app/MainViewModel.kt`
- `app/src/main/java/com/comichub/app/local/LocalReaderViewModel.kt`
- `app/src/main/java/com/comichub/app/ui/ComicHubApp.kt`
- `core/data/src/main/kotlin/com/comichub/data/ImageDownloadQueue.kt`
- `core/data/src/main/kotlin/com/comichub/data/FileImageCache.kt`
- `core/source-runtime/src/main/kotlin/com/comichub/source/runtime/NetworkGateway.kt`
- `core/source-runtime/src/main/kotlin/com/comichub/source/runtime/UrlConnectionTransport.kt`

验证结果：

- `gradle :core:source-runtime:test`：36 个测试通过
- `gradle :core:data:testDebugUnitTest`：7 个测试通过
- `gradle :app:testDebugUnitTest`：19 个测试通过
- `gradle :app:assembleDebug`：通过
- 本轮上述测试合计：62 个通过，0 个失败
- 新增/覆盖单页按需加载、并发下载去重、缓存单项大小限制、二进制响应元数据保留、采样解码和章节不整章预取测试
- APK：[app-debug.apk](H:/comicfree/app/build/outputs/apk/debug/app-debug.apk)
- APK SHA-256：`C6546EB18719533E35C91682B15F930830A94CE0A971F49D1BB3D50FC4FD4CEE`

模拟器验证结果：

- 设备：`emulator-5554`，安装 Debug APK 成功
- 本地真实 EPUB `claymore test` 打开成功，共 184 页；双击放大后横向拖拽仍停留在第 22 页，双击还原后横滑到第 23 页，快速连续横滑到第 28 页
- 在线授权缓存章节《猎人游戏 W》第 07 话打开成功；纵向滚动从第 1/2 页移动到第 2/3 页，横滑定位到第 3/4 页；第 3 页缩放后横向拖拽未误翻页，返回并重新进入恢复第 3 页
- 通过 `adb logcat` 检查未发现 App 的 `FATAL EXCEPTION` 或 `OutOfMemoryError`；筛选到的 `AndroidRuntime` 仅为 Monkey 启动命令正常退出
- 未观察到旧图片、旧章节错误或旧进度串入当前页面，也未发现重复导航或崩溃

### Iteration 17：本地 PDF 与 MOBI 模拟器回归

状态：已完成

目标：继续验证 Iteration 16 后的本地文件格式路径，确认 PDF、MOBI 不通过 WebView，并检查大文件导入与本地阅读器稳定性。

验证结果：

- 真实 PDF 导入成功，解析为 2 页并在本地阅读器打开；页面标识为 `PDF`
- 真实 280 MB MOBI 导入成功，解析为 8 页并在本地阅读器打开；页面标识为 `MOBI`
- 两种格式均未进入 WebView；导入和打开期间未发现 `FATAL EXCEPTION`、`OutOfMemoryError` 或 ANR
- 当前可用 ZIP 样本为非漫画文本/SQL 文件，未将其误记为 CBZ/ZIP 回归通过
- 本轮仅补充模拟器验证记录，没有新增功能代码

## 当前技术原则

- 简单 HTML 源优先使用声明式 CSS 选择器
- 复杂逻辑后续使用受限脚本，不开放任意 Android API
- 插件只能访问 manifest 声明的域名
- 所有源请求统一经过 NetworkGateway
- 优先使用授权、公开允许访问或用户自有内容
- 任何真实网站适配都先使用 fixture 测试，再接入 App

## 下一迭代候选

1. 阅读器手势统一：双指缩放、拖拽、双击放大/还原和左右滑动翻页
2. 在线大图采样、有限缓存和页面生命周期清理，降低 OOM 风险
3. 在获得具体授权源信息后，替换合成 fixture 并完成真实源端到端验证
4. WebView 用户交互通道和结构化 `REQUIRES_USER_INTERACTION` 错误
5. PDF、MOBI、CBZ/ZIP 真实文件的模拟器回归验证

## 开发验证命令

当前机器上的 Gradle 路径：

```text
C:\Gradle\gradle-8.9\bin\gradle.bat
```

常用命令：

```text
gradle :core:source-runtime:test
gradle :core:data:testDebugUnitTest
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

如果新终端没有继承环境变量，使用 Android Studio 打开项目并执行 Gradle Sync 即可。

### Iteration 18：PageLoom 正式 Beta 公开发布整理

状态：已完成

时间：2026-08-16

目标：在保留现有提交历史和应用升级兼容边界的前提下，将工程整理为可公开发布的 Android Beta 项目。

本次决策记录：

- 暂用 `PageLoom` 作为产品品牌和公开项目工作名称；GitHub 名称查重仅作为可用性参考，不宣称名称唯一。
- `applicationId` 和现有 `com.comichub.*` 包名暂不变，避免未经确认地影响已安装应用、Room 数据库、签名和升级兼容性；品牌名称变更与 applicationId 变更分开处理。
- 从正式 App source registry 和生产 APK 路径移除 MYCOMIC 真实适配器、真实站点会话逻辑及其用户界面；通用插件 API、权限模型和用户交互协议继续保留，真实授权源需由外部插件提供。
- 本地合成数据仅保留在 `plugin-sdk/fixtures`、测试代码和 CI 校验路径；正式 Beta 不注册、不展示 fixture source。
- Beta 版本目标为 `0.5.0-beta01`，发布检查覆盖敏感字符串、真实源残留、fixture 泄漏、调试文案和 APK 哈希。

完成结果：

- 品牌：采用 `PageLoom` 作为工作名称；`InkNest`、`FrameLoom`、`StoryWeave` 已发现公开同名项目，不建议直接采用；其他候选和 PageLoom 均未完成商标/域名/完整 GitHub 查重，最终名称仍待确认。
- 兼容性：Android label、README、Gradle root project、主要 UI 类和 CLI 已统一为 PageLoom；`applicationId`、namespace、`com.comichub.*` 包名和 `comichub.db` 文件名保留，待单独评估迁移。
- MYCOMIC：删除主仓库中的真实适配器、真实站点会话/WebView 实现、站点选择器、CDN 地址、相关测试和用户界面；保留通用插件权限、HTTPS 域名限制和 `requiresUserInteraction` 协议。真实授权源需通过外部插件提供。
- 本地示例源：从生产 `SourceRegistry` 和 APK 路径移除；合成 source 移入测试代码，插件示例保留在 `plugin-sdk/fixtures`，由 CLI 离线验证，不注册到 Beta UI。
- UI：移除“原型”、内置示例源、MYCOMIC、网页会话等开发/站点专属文案；错误提示改为用户可执行的重试、返回或重新导入建议，不显示堆栈或内部请求错误。
- 发布基础设施：新增 LICENSE、CONTRIBUTING、CODE_OF_CONDUCT、SECURITY、CHANGELOG、Issue/PR 模板、CI、Beta 发布说明、release signing 模板和 release APK 公开面检查。
- 验证：`:core:source-runtime:test`、`:core:data:testDebugUnitTest`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`:plugin-cli:test`、`:plugin-cli:installDist` 和 `:app:verifyReleasePublicSurface` 均通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk` SHA-256 为 `F09BEA94FBC432AE549CF17A56E3434077B0B7200A1E75AD5850EDBD40BDA1EA`；未签名 release `app/build/outputs/apk/release/app-release-unsigned.apk` SHA-256 为 `0D1E3F8438DCE1E7932635F4222D58CE6524E5FFBF0EC7B7834A2D86AFEB54B1`。
- 发布限制：当前生成的是未签名 release APK；需在本地配置真实 release keystore 后再分发。外部插件的授权、版权、条款和隐私责任由插件作者与用户承担。
