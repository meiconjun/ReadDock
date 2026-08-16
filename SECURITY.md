# 安全策略

## 目前的支持范围

ReadDock 目前处于 Beta 阶段。我们会优先处理当前公开版本和 main 分支中的安全问题。

报告时请尽量提供：

- ReadDock 版本；
- Android 版本和设备/API level；
- 最小复现步骤；
- 影响范围；
- 你认为的修复方向（如果有）。

请不要附上真实账号、Cookie、私钥、个人文件、私有 URL 或未授权漫画内容。

## 私下报告

不要在公开 Issue 中发布可直接利用的漏洞细节。请优先使用 GitHub Security Advisory：

https://github.com/meiconjun/ReadDock/security/advisories/new

如果该页面暂时不可用，请先在 Issue 中只说明“需要私下联系”，不要公开漏洞细节。

## 插件相关安全问题

插件是外部代码或数据配置。用户应只安装来源可信、签名有效、权限合理并且符合目标网站条款的插件。

ReadDock 会限制插件的 manifest、权限、域名和网络请求，但不能替用户审查第三方插件的全部行为，也不为第三方数据源的版权、隐私或可用性负责。

ReadDock 不绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制。
