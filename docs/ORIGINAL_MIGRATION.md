# 原版喵喵地图 Android 迁移

分支：`feature/original-meow-migration`。当前开发版本：0.7.1。

0.6.0 对齐原版导航与配色，新增按观察时间连线的喵喵轨迹、打卡直达定位，以及公开与本人观察的来源和字段筛选。统计独立成页，支持本人记录和公开地图。手机使用底部导航和更多菜单，宽屏使用侧栏。可通过本机 `local.properties` 为云端调试版设置登录预填。

0.5.0 适配原版 `/observations`、`/profile` 和 `/cat-assistant`。观察页可切换本人记录与公开观察，地图支持自选附近半径及校园点按汇总，档案支持原版内置头像和用户照片，数据分析支持排除示例。寻猫回答可从引用打开当前已加载的地图打卡。拍照识猫仍由桌面 Electron 调用模型，共享服务尚未提供安卓可使用的图像识别接口。

以下兼容约定记录的是 0.4.0 迁移基线。0.5.0 已接入原版的昵称和个人头像修改，云端普通观察也可按原版规则公开浏览。

## 当前实现

- 原生 Compose 提供发现地图、公开观察、我的观察记录、猫咪档案、统计面板、寻猫小助手和个人档案。
- 适配原版 `server/index.js`：云端账号、公开地图分页、私人 records、cats、二进制图片上传、点赞和评论。
- 云端缓存与本机 Room 数据分开；缓存按账号保存，退出后不展示私人手记和档案。登录凭据通过 Android Keystore 加密。
- 猫咪档案增删改查，地图记录和手记关联固定猫咪 ID，相遇时间线。删除档案保留观察。
- 复用原版 `data/areas.json`（OpenStreetMap WGS84 数据及原版近似边界），地图显示校园范围，支持校园筛选、附近 2 km、距离排序、聚合和热力图。
- ZIP 便携备份包含本人数据和照片；兼容无外部照片路径的原版 v1 JSON。按资源类型跳过重复 ID，先导入档案，再导入观察；中断可重试。
- Room v3→v4 只新增可空 catId 字段，保留本机旧数据。原版本机账号不会自动转为云端账号，旧数据不会自动上传。

## 接口与兼容约定

旧的 `API_CONTRACT.md` 是未来目标协议，当前线上服务并未实现该协议。当前适配使用：

| 资源 | 当前接口 |
| --- | --- |
| 认证 | POST /auth/register、/auth/login、/auth/logout；GET /auth/me |
| 公开地图 | GET /map_records?offset=N；本人列表 ?mine=1 |
| 私人手记 | /records |
| 私人档案 | /cats |
| 修改与删除 | PUT、DELETE /资源/ID |
| 图片 | POST /uploads，application/octet-stream，单图至多 2 MiB |
| 互动 | /social/作者ID/记录ID/interactions、like、comments |

地图缓存 ID 使用 `作者ID~记录ID`；私人资源按账号隔离。上传最多六张照片。服务没有刷新令牌和修改昵称接口，不伪造这两种能力。原版服务登录会撤销此前会话，两端可能需要重新登录；同时在线需另行修改服务端策略。

0.6.1 使用与原版相同的高德中文瓦片。地图渲染时将 WGS84 打卡、轨迹、校园边界和定位转换为 GCJ-02，点选位置时转回 WGS84 供表单和校园判断使用。原版云端仍交换 GCJ-02 坐标，Room 和草稿仍保存 WGS84。原版 JSON 的无时区 datetime-local 按 Asia/Shanghai 解释。

演示服务当前使用 HTTP。Android 网络配置仅对该服务器 IP 开放明文；其他地址默认禁用。迁移到 HTTPS 后应移除该例外。令牌和密码不写入日志、缓存或备份。

## 构建与验证

本机 `local.properties` 使用现有 Android SDK。AGP 8.8.0 默认使用 Build Tools 35.0.0；本机也安装有 36.0.0，两者可共存。

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python scripts/check_migration.py
```

兼容旧大作业本机数据源可使用 `-PmeowLocal=true` 构建；默认云端模式。已有 Repository 设备测试直接验证本机数据层。原版 UI 流程测试需要随新导航更新，不能直接视为云端验收。

## 后续验收与范围

- 使用两端测试账号验收发布、图片、编辑删除、互动与权限，以及会话失效后的草稿保留。
- 实机核对校园地标落点、定位权限、拍照和后台恢复。算法测试不能替代地图地标目视核验。
- 原版电脑 JSON 的绝对照片路径不可在 Android 打开。可运行 `python scripts/desktop_backup.py --input 原版导出.json --output output/meow-backup.zip`，读取原图制作便携备份，再在手机中导入；也可先从桌面导入云端，再在手机登录查看。转换不修改源文件，单图需符合服务的 2 MiB 限制。
- 云端不提供 ETag、刷新令牌和幂等键。写请求不会自动重试；超时后先刷新确认，避免重复发布。
- AI 图鉴、推荐、识别仍需独立的服务端密钥代理与额度配置，当前不加入客户端密钥。
- 云端缓存目前为独立 JSON 快照，Room 保留旧本地数据与草稿；不支持离线自动发布或后台增量同步。

具体执行结果以本轮构建报告为准；原 `VALIDATION.md` 的 0.3.0 结果不代表本次迁移已通过所有设备测试。
