# 品牌命名检查

本轮按用户给出的候选名称做了本地一致性检查，并使用公开 GitHub/Web 搜索做了有限的占用情况检查。搜索结果不能代替商标、域名、应用商店或完整 GitHub 名称查重，因此不对任何候选名称宣称唯一。

| 候选 | GitHub/公开搜索观察 | 本轮结论 |
| --- | --- | --- |
| PageLoom | 已发现 `pageloom` 相关 GitHub/公开产品活动，不能视为未占用。 | 暂用工作名称，待最终确认 |
| PanelHarbor | 本轮有限搜索未确认同名仓库。 | 未清权，待确认 |
| InkNest | 已发现 `p2devs/InkNest` 漫画移动应用仓库。 | 不建议 |
| FrameLoom | 已发现 `sixteenmillimeter/frameloom` 项目。 | 不建议 |
| StoryWeave | 已发现 `ShiyangZheng/storyweave` 仓库。 | 不建议 |
| ReadDock | 本轮有限搜索未确认同名仓库。 | 未清权，待确认 |
| InkAtlas | 已发现 GitHub 上的 InkAtlas 相关工具内容。 | 未清权，待确认 |
| PanelTrail | 本轮有限搜索未确认同名仓库。 | 未清权，待确认 |

## 当前采用方式

- 公开品牌、Android label、README 标题、Gradle root project、主要 UI 类名和 CLI 名称使用 `PageLoom`/`pageloom-source`。
- `applicationId = com.comichub.app`、Android namespace、历史数据库文件名 `comichub.db` 和 `com.comichub.*` 内部包名暂时保留，以避免未经评估地破坏已安装应用、Room 数据库、签名或升级路径。
- `ComicHubDatabase` 已改为 `PageLoomDatabase`，不改变数据库文件名；主要 UI 类已改为 `PageLoomApp` 和 `PageLoomTheme`。

最终项目名、GitHub 仓库名、商标/域名可用性和是否迁移 applicationId 仍需用户确认。完成确认前不要把 `PageLoom` 视为唯一或已注册名称。
