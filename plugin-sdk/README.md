# ReadDock Plugin SDK

这里放的是外部数据源插件的协议示例和离线测试材料。

如果你想为自己有权访问的数据源编写插件，可以先从示例 manifest 和合成 fixture 开始。主仓库不提供真实商业网站适配器，也不鼓励把真实站点页面或未授权内容复制进 fixture。

## 目录里有什么

- manifest.example.json：manifest 字段示例；
- package.example.json：CSS 选择器插件包示例；
- package.javascript.example.json：受限 JavaScript 插件包示例；
- repository.example.json：签名仓库索引示例；
- source.example.ts：开发者侧 API 参考；
- fixtures/example-source：项目自有合成内容的离线测试包。

## 运行示例

从工程根目录运行：

~~~text
gradle :plugin-cli:run --args="validate ../plugin-sdk/fixtures/example-source"
gradle :plugin-cli:run --args="test ../plugin-sdk/fixtures/example-source"
~~~

这些命令只读取本地快照，不访问网络。fixture 里的标题、作者和页面都是合成内容，不代表 ReadDock 已经接入任何真实商业网站。

## 安全边界

插件 manifest 必须声明 HTTPS 基地址、允许域名、能力、权限、速率和并发限制。运行时会拒绝未声明的域名、未签名的包或签名不可信的包。

受限脚本只能做有限的 HTML 解析、URL 编码和网关请求，不能访问 Android API、任意文件或系统能力。

需要登录或人工操作的外部数据源可以设置 requiresUserInteraction: true。这只表示用户需要在允许的流程中自己完成操作，不代表插件可以绕过验证码、付费墙、访问控制或反爬机制。

## 发布插件前

请确认：

1. 数据来源和内容有明确授权；
2. manifest 的 HTTPS、域名、权限、速率和 API 版本合理；
3. 已运行 validate 和离线 test；
4. 使用发布密钥签名插件包，并把私钥留在安全位置；
5. 记录许可证、来源和变更；
6. 在独立仓库发布真实数据源插件。

## 协议依赖

如果插件工程需要使用 Kotlin 协议类，可以引用 ReadDock 的 GitHub Packages：

~~~text
io.readdock:source-api:0.5.0-beta01
io.readdock:source-runtime:0.5.0-beta01
~~~

仓库地址：`https://maven.pkg.github.com/meiconjun/ReadDock`。使用 GitHub 令牌访问时，请通过 Gradle 用户配置或 CI secret 提供凭据，不要把令牌放进项目文件。
