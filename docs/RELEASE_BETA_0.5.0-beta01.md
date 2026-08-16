# ReadDock 0.5.0-beta01 发布说明

## 范围

这是一个可公开测试的 Android Beta，包含本地漫画导入、在线插件运行时、书架、阅读进度、图片阅读器和插件 CLI。

主仓库不提供真实商业网站数据源、真实页面选择器、CDN 地址、章节/图片抓取快照或站点会话实现。用户自行安装的插件必须遵守目标网站条款、版权和授权要求。ReadDock 不绕过验证码、付费墙、访问控制或反爬机制。

## 构建

```text
gradle :app:assembleRelease
gradle :app:verifyReleasePublicSurface
```

本地工程已配置 ReadDock release keystore。其他开发者应复制 `keystore.properties.example`，使用自己的 keystore；不要把密钥、密码或配置文件提交到 Git。公开 CI 应通过加密 secrets 注入签名配置。

## 校验

```powershell
Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

发布时应将版本号、APK 文件名、SHA-256 和签名指纹一并填写到 GitHub Release。未签名 APK 不应作为最终分发包。

本次本地验证产物：

- Release APK：`app/build/outputs/apk/release/app-release.apk`
- Release SHA-256：`A1B09170BDBBDDAA329B89552BC632FAB45822B98D601ED244A969870A82699F`
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Debug SHA-256：`72CBE2E7707A95B9ED0857D375F7C42CB39B7CA6D650EE10EE9F103CBAC7383E`
- Release 证书 SHA-256：`02:3E:21:2A:D2:E5:79:EA:E4:F5:C4:A8:78:53:44:F0:E1:D3:12:F2:E8:32:D6:07:35:F9:5F:5C:F3:7B:4B:52`

## 已知限制

- 当前 Beta 不内置真实商业网站数据源。
- 外部插件仓库必须由用户配置 HTTPS 地址和可信公钥。
- applicationId 已确定为 `com.readdock.app`，与 ReadDock 品牌同步。
- 大文件导入、特殊 MOBI 变体和站点兼容性仍需更多真实授权测试。
