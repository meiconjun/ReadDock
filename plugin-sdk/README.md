# PageLoom Plugin SDK

这里提供 PageLoom 外部数据源插件的协议示例、声明式包格式、受限脚本示例和离线合成 fixture。

## 示例文件

- `manifest.example.json`：manifest 字段
- `package.example.json`：CSS 选择器插件包
- `package.javascript.example.json`：受限 JavaScript 插件包
- `repository.example.json`：签名仓库索引结构
- `source.example.ts`：开发者侧 API 参考
- `fixtures/example-source`：项目自有合成内容的离线测试包

## 运行离线测试

```text
gradle :plugin-cli:run --args="validate ../plugin-sdk/fixtures/example-source"
gradle :plugin-cli:run --args="test ../plugin-sdk/fixtures/example-source"
```

测试只读取本地快照，不访问网络。fixture 中的标题、作者和页面是合成内容，不代表 PageLoom 已接入真实商业网站。

## 安全边界

manifest 必须声明 HTTPS 基地址、允许域名、能力、权限、速率和并发限制。运行时会拒绝未声明域名、未签名或签名不可信的包。脚本只能使用有限的 HTML 解析和网关请求能力，不接触 Android API、文件系统或任意系统能力。

需要登录或人工操作的外部数据源可设置 `requiresUserInteraction: true`。这不会授权插件绕过验证码、付费墙、访问控制或反爬机制；用户必须自行完成合法操作，插件作者必须遵守目标网站条款、版权和许可要求。

## 发布建议

使用 `pageloom-source` 完成初始化、校验、fixture 测试、签名打包和 RSA 公钥生成。开发密钥与发布密钥分离，私钥只保存在本机安全位置，不要提交到 Git。
