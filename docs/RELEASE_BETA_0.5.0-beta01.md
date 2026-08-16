# ReadDock 0.5.0-beta01

这是一个面向公开测试的 Android Beta。它包含本地漫画导入、图片阅读器、书架、阅读进度、外部插件运行时和插件 CLI。

如果你只是想试用，优先安装 Debug APK 或由维护者签名的 Release APK。如果你要自己构建，请从仓库根目录运行下面的命令。

## 构建

~~~text
gradle :app:assembleRelease
gradle :app:verifyReleasePublicSurface
~~~

公开发布不能使用未签名 APK。其他维护者应复制 keystore.properties.example，并使用自己的 keystore。不要把 keystore、密码或 keystore.properties 提交到 GitHub；正式 CI 应通过加密 secrets 注入签名配置。

## 本次本地验证

- Release APK：app/build/outputs/apk/release/app-release.apk
- Release SHA-256：A1B09170BDBBDDAA329B89552BC632FAB45822B98D601ED244A969870A82699F
- Debug APK：app/build/outputs/apk/debug/app-debug.apk
- Debug SHA-256：72CBE2E7707A95B9ED0857D375F7C42CB39B7CA6D650EE10EE9F103CBAC7383E
- Release 证书 SHA-256：02:3E:21:2A:D2:E5:79:EA:E4:F5:C4:A8:78:53:44:F0:E1:D3:12:F2:E8:32:D6:07:35:F9:5F:5C:F3:7B:4B:52

这些是当前工作区的验证产物；如果重新构建，APK 哈希可能不同。发布 GitHub Release 时，请把实际上传文件的哈希一起记录。

## 产品边界

主仓库不提供真实商业网站数据源、真实页面选择器、CDN 地址、章节/图片抓取快照或站点会话实现。外部插件必须遵守目标网站条款、版权和授权要求。ReadDock 不绕过验证码、付费墙、访问控制或反爬机制。

## 已知限制

- 在线内容需要用户自行安装合规的外部插件；
- 外部插件仓库需要用户配置 HTTPS 地址和可信公钥；
- 大文件、特殊 MOBI 变体和不同站点的兼容性仍需更多测试；
- 部分需要人工操作的数据源只提供通用协议，插件仍需自行实现。
