# ReadDock 产品规划

## 我们想解决的问题

很多阅读器把本地文件、在线站点和插件混在一起，出了问题很难判断是文件、网络、解析器还是站点适配器。ReadDock 想把这些部分拆开：

- 本地文件由阅读器负责；
- 在线数据由外部插件负责；
- 宿主应用负责权限、网络边界、缓存和阅读体验；
- 合成 fixture 负责离线测试，不把真实站点内容带进主应用。

这样做的代价是，ReadDock 不会开箱即用地提供一长串在线漫画源。但这让主仓库更容易公开维护，也让插件作者可以在自己的仓库里独立承担授权和维护责任。

## Beta 阶段

当前 Beta 的目标不是功能最多，而是先把基础路径做稳：

- 本地 EPUB、MOBI、PDF、CBZ/ZIP 和图片导入；
- 书架、阅读进度和本地图片缓存；
- 图片缩放、拖拽、横向翻页和纵向阅读；
- 搜索、详情、章节和图片阅读的通用插件协议；
- 插件的权限、域名、签名、更新和回滚；
- 离线 fixture、插件 CLI 和 release APK 检查；
- GitHub 上的 Issue、Pull Request、CI 和安全响应流程。

## 工程边界

~~~text
ReadDock Android App
├── Compose UI / Local Reader / Online Reader
├── Room Library / Image Cache
├── Source API / Source Runtime
└── External Plugin Packages
~~~

正式 App 的 source registry 默认是空的，只加载用户安装并启用的外部插件。合成 source 只存在于测试或 fixture 路径，不会出现在正式 UI 或 release APK 中。

## 插件应该遵守什么

插件 manifest 需要说明：

- 插件 ID、版本、API 版本和许可证；
- HTTPS 基地址和允许访问的域名；
- search、detail、chapters、pages 等能力；
- network、user_session 等权限；
- 请求速率和并发限制；
- 是否需要用户在外部流程中操作。

运行时会拒绝未声明的域名和能力。受限 JavaScript 只用于 HTML 解析、URL 编码和经过网关的请求，不能访问 Android API、任意文件或系统设置。

需要登录或人工操作时，插件可以声明 requiresUserInteraction，但不能自动化绕过验证码、付费墙、访问控制或网站安全策略。

## 下一步

接下来比较值得做的事情：

- 继续打磨本地导入失败提示和大文件体验；
- 增加更多本地格式的兼容性测试；
- 改善插件仓库的签名和发布记录；
- 增加书架筛选、标签和更好的阅读历史；
- 在获得明确授权后，通过独立插件仓库提供合规数据源；
- 把 GitHub Actions 的 release 签名接入加密 secrets。

## 明确不放进主仓库的内容

- 真实商业网站适配器、站点选择器、CDN 地址或抓取数据；
- Cookie、Referer、User-Agent 伪装、代理池、指纹伪装或验证码绕过；
- 未经授权的漫画目录、章节、图片或用户数据；
- release keystore、私钥、Cookie、凭据、私有 URL 或本机路径。
