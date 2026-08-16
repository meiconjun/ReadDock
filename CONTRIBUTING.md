# 贡献指南

欢迎参与 ReadDock。

这个项目还处在 Beta 阶段，最有帮助的贡献通常不是“再加一个大功能”，而是让已有的本地阅读、图片阅读、插件边界和测试更稳定、更容易理解。

## 开始之前

提交 Issue 或 Pull Request 前，请确认：

- 问题可以用公开信息安全复现；
- 没有附带账号、Cookie、私钥、个人文件或本机路径；
- 没有附带真实商业网站页面、章节、图片或未授权内容；
- 改动没有把真实站点适配器带回主仓库。

如果问题涉及安全漏洞，请不要发公开 Issue，先看 [SECURITY.md](SECURITY.md)。

## 推荐的开发流程

1. Fork 仓库，或者从 main 创建一个短生命周期分支。
2. 使用 Java 17、Android SDK 35 和 Gradle 8.9。
3. 先运行与你改动相关的测试。
4. 如果改动插件运行时，补充离线合成 fixture 测试。
5. 如果改动用户可见文案，确认普通用户不会看到内部类名、调试信息或测试内容。
6. 提交前运行 git diff --check。
7. 通过 Pull Request 合并到 main。

常用命令：

~~~text
gradle :core:source-runtime:test
gradle :core:data:testDebugUnitTest
gradle :app:testDebugUnitTest
gradle :plugin-cli:test
gradle :app:assembleDebug
~~~

## 关于插件和数据源

主仓库接受通用插件协议、权限模型、运行时能力和合成 fixture。

真实数据源应该放在独立仓库。插件作者需要自己确认来源授权、许可证、版权、隐私和目标网站条款。任何绕过验证码、付费墙、访问控制或反爬机制的代码都不会被接受。

## Pull Request 写什么

请尽量用几句话回答：

- 这次改动解决了什么问题？
- 用户会看到什么变化？
- 运行了哪些检查？
- 还有哪些已知限制？

保持一个 PR 只处理一件主要事情。不要把签名密钥、构建产物或本机配置加入提交。
