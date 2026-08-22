# ReadDock 0.5.0-beta02

这是 ReadDock 的第二个公开 Beta。本版本完善了外部插件仓库管理，并增强了本地漫画阅读器的沉浸式操作。

## 本次更新

- 支持读取签名的外部 repository index，并展示插件版本、域名、权限、能力、请求限制和人工交互要求；
- 支持外部插件安装、更新、取消、失败重试、回滚和卸载；
- 本地插件导入也要求使用受信任公钥验证签名；
- 本地阅读支持点击页面隐藏/显示控制层和系统栏；
- 增加可拖动的阅读进度条，并对页码跳转做边界保护；
- 增加外部插件管理和本地阅读器交互测试。

## 构建和验证

~~~text
gradle :plugin-cli:test :core:source-runtime:test :app:test :app:verifyReleasePublicSurface
gradle :app:assembleDebug :plugin-cli:installDist
git diff --check
~~~

上述验证在发布分支通过。`verifyReleasePublicSurface` 确认 release APK 不包含 MYCOMIC 真实适配器或其他被禁止的测试源内容。

## 下载

- `ReadDock-0.5.0-beta02-release.apk`：使用维护者 release keystore 签名的安装包；
- `app-debug.apk`：用于本地调试的安装包；
- `ReadDock-0.5.0-beta02-cli.zip`：`readdock-source` 插件 CLI 分发包。

SHA-256：

~~~text
ReadDock-0.5.0-beta02-release.apk
43578F2773CE51ACE3B693D85E26CA9C1366D5695E7B5D15E4A45378339E6AA0

app-debug.apk
188E3053752036E19180D813AD23C90D2073F3E68709B30BC54566FB779CB5D5

ReadDock-0.5.0-beta02-cli.zip
1C90E662292C62C327453513419C7980D5E65959E350C0AF2D8630A323CF17AE
~~~

## GitHub Packages

插件开发者可以使用：

~~~text
io.readdock:source-api:0.5.0-beta02
io.readdock:source-runtime:0.5.0-beta02
~~~

Packages 会由 GitHub Actions 在本 Release 发布后完成推送。首次发布可能需要几分钟才会出现在 Packages 页面。

## 重要边界

MYCOMIC 真实适配器仍位于独立插件仓库，不包含在 ReadDock 主仓库或本次 APK 中。ReadDock 不绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制。外部插件必须遵守目标网站条款、版权和授权要求。

这是 Beta 版本，大文件、特殊 MOBI 变体和不同数据源的兼容性仍需更多测试。遇到问题请提交 Issue，并附上可复现步骤和设备信息，不要上传漫画文件或账号凭据。
