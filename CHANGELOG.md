# Changelog

## 0.5.0-beta01 — 2026-08-16

### Added

- PageLoom Beta 公开发布文档、贡献规范、安全策略和 CI。
- EPUB、MOBI、PDF、CBZ/ZIP 和图片本地阅读路径的公开说明。
- release APK 公开面检查和本地 release signing 配置模板。
- 外部数据源插件的正式安装、仓库、签名和授权边界说明。

### Changed

- 公开品牌统一为 PageLoom；历史 `applicationId` 和包名暂保持兼容。
- 正式 source registry 不再注册内置合成 source。
- 移除 MYCOMIC 真实适配器、真实站点会话实现和对应 UI；真实授权源需由外部插件提供。
- 测试数据只保留在测试和 fixture 路径。

### Known limitations

- Beta 不内置真实商业网站数据源。
- Release signing 需要用户在本地配置；仓库不包含密钥。
- 外部插件的授权、版权和目标网站条款责任由插件作者和用户承担。
