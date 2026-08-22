# ReadDock 0.5.0-beta03

这是 ReadDock 的第三个公开 Beta，修复了 WebReader 章节导航切换后仍显示旧 WebView、页面无法继续滚动的问题。

## 本次更新

- 点击上一章或下一章时，在点击瞬间重新计算目标章节；
- 目标章节同步更新 selectedChapter、webReaderUrl 和 WebView generation；
- 章节切换会卸载旧 WebView，并停止旧加载后加载新 URL；
- 只有 WebView 实际回调 URL 和页面脚本确认目标文档后，才报告加载成功；
- 增加加载、失败、取消和重试状态；
- 漫画区域仅通过短按切换控制栏，滑动不会误触发；
- MYCOMIC 适配器仍位于独立插件仓库，不包含在主 App 中。

## 构建和验证

~~~text
gradle :app:testDebugUnitTest :core:source-runtime:test :core:data:testDebugUnitTest :plugin-cli:test
gradle :app:assembleDebug :app:verifyReleasePublicSurface
git diff --check
~~~

主 App 使用维护者 release keystore 签名；公开 APK 不包含 MYCOMIC 真实适配器、测试源或离线 fixture。

## 重要边界

ReadDock 不绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制。外部插件必须遵守目标网站条款、版权和授权要求。
