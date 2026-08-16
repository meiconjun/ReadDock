# ReadDock 产品规划

## 定位

ReadDock 是一个 Android 本地优先漫画阅读器。它把本地文件阅读、在线数据源插件、书架和受限阅读器能力分开，让用户可以阅读自有文件，也可以在自行确认授权和条款后安装外部数据源插件。

公开 Beta 的核心原则：主仓库不内置真实商业网站数据源，不包含真实站点页面/图片快照，不绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制。

## 当前能力

- Android 手机和平板
- EPUB、MOBI、PDF、CBZ/ZIP 和图片文件/文件夹导入
- 搜索、详情、章节、在线图片阅读、书架和阅读进度
- 本地文件私有存储，导入后不上传网络
- 声明式 CSS 与受限 JavaScript 插件
- manifest 能力、权限、域名约束、签名、更新和回滚
- 图片缓存、限流、重试、熔断、健康状态和超大图片保护
- 离线合成 fixture 和独立插件 CLI

## 模块边界

```text
ReadDock Android App
├── Compose UI / Local Reader / Online Reader
├── Room Library / Image Cache
├── Source API / Source Runtime
└── External Plugin Packages
```

`app` 的正式 source registry 默认为空，只加载用户安装并启用的外部插件。合成 source 只能位于测试或 fixture 路径。

## 插件协议

插件 manifest 声明：

- `id`、版本、API 版本和许可证
- HTTPS `baseUrl` 与允许域名
- `search`、`detail`、`chapters`、`pages` 能力
- `network`、`user_session` 等权限
- 速率和并发限制
- 是否需要用户交互

插件不能访问 Android API、任意文件、系统设置、通讯录、剪贴板或未声明域名。需要用户交互时，宿主只提供协议级状态和返回路径；插件不得自动化绕过验证或访问控制。

## 路线图

### Beta

- 稳定本地导入和阅读路径
- 稳定图片阅读器手势、缓存和进度
- 发布前 APK 敏感内容扫描
- 完成插件安全边界和离线 fixture 契约
- 建立 GitHub Issue、PR、CI 和安全响应流程

### 后续

- 可选的通用用户交互宿主能力
- 更完整的插件仓库签名与透明发布记录
- 书架筛选、标签和更多本地格式
- 在获得明确许可后，通过独立插件仓库提供授权数据源

## 不在本仓库提供

- 真实商业网站适配器、域名、选择器、CDN 地址或抓取数据
- Cookie/Referer/User-Agent 伪装、代理池、指纹伪装或验证码绕过
- 内置未经授权的漫画目录
- 签名密钥、私有 URL、访问凭据或用户本机路径
