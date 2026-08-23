# ReadDock 0.5.0-beta04

这是 ReadDock 的第四个公开 Beta，正式合并上一轮完成但未进入 Beta03 的 UI 和图标更新，并配套发布 MYCOMIC 外部插件 `0.1.4`。

## 本次更新

- 更新首页、书架、历史、插件和设置页面的视觉层级、卡片布局与滚动行为；
- 修复插件页面按钮位置异常和页面无法上下滑动的问题；
- 增加 ReadDock 启动图标，并配置自适应图标资源；
- MYCOMIC 章节解析只读取当前漫画章节容器，排除推荐漫画章节；
- MYCOMIC 真实适配器仍位于独立插件仓库，不包含在主 App 中。

## 构建和验证

~~~text
gradle test
gradle :app:assembleRelease :app:verifyReleasePublicSurface
git diff --check
~~~

主 App 使用维护者 release keystore 签名；公开 APK 不包含 MYCOMIC 真实适配器、测试源或离线 fixture。

## 重要边界

ReadDock 不绕过验证码、付费墙、登录限制、DRM、访问控制或反爬机制。外部插件必须遵守目标网站条款、版权和授权要求。
