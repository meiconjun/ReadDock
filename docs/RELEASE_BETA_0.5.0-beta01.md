# PageLoom 0.5.0-beta01 发布说明

## 范围

这是一个可公开测试的 Android Beta，包含本地漫画导入、在线插件运行时、书架、阅读进度、图片阅读器和插件 CLI。

主仓库不提供真实商业网站数据源、真实页面选择器、CDN 地址、章节/图片抓取快照或站点会话实现。用户自行安装的插件必须遵守目标网站条款、版权和授权要求。PageLoom 不绕过验证码、付费墙、访问控制或反爬机制。

## 构建

```text
gradle :app:assembleRelease
gradle :app:verifyReleasePublicSurface
```

如果没有 `keystore.properties`，Gradle 会生成未签名 release APK；复制 `keystore.properties.example` 为本地配置模板并使用自己的 keystore，不要把密钥或配置文件提交到 Git。

## 校验

```powershell
Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

发布时应将版本号、APK 文件名、SHA-256 和签名指纹一并填写到 GitHub Release。未签名 APK 不应作为最终分发包。

本次本地验证产物：

- `app/build/outputs/apk/release/app-release-unsigned.apk`
- SHA-256：`0D1E3F8438DCE1E7932635F4222D58CE6524E5FFBF0EC7B7834A2D86AFEB54B1`
- Debug 验证包 `app/build/outputs/apk/debug/app-debug.apk` 的 SHA-256：`F09BEA94FBC432AE549CF17A56E3434077B0B7200A1E75AD5850EDBD40BDA1EA`

## 已知限制

- 当前 Beta 不内置真实商业网站数据源。
- 外部插件仓库必须由用户配置 HTTPS 地址和可信公钥。
- `applicationId` 仍为历史兼容值 `com.comichub.app`；品牌名称和 applicationId 尚未合并迁移。
- 大文件导入、特殊 MOBI 变体和站点兼容性仍需更多真实授权测试。
