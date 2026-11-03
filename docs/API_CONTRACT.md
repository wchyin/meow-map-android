# 喵喵数据地图：桌面端与 Android 共用接口 v1

本协议定义 Android、桌面端和服务器之间的数据格式与接口。接口示例见 `protocol/v1/examples.json`。当前客户端的本机功能已实现，服务器接口待接入。

## 1. 数据模型

一个 Record 是一次校园猫观察分享，有地图位置、标题、描述、照片、作者、点赞和评论。同一只猫可以有多条记录；互动归属记录。

| 业务 | 资源 |
| --- | --- |
| 公开地图分享 | `/records` |
| 个人观察手记 | `/observations`，仅本人可见 |
| 用户与个人资料 | User、Author |
| likes / comments | 都关联 `/records/{id}` |
| 草稿和选中的照片 | 本机保存；发布时上传照片并关联图片ID |

两端通过同一个 HTTPS API 访问账号和数据。服务器使用 Node.js / TypeScript / Fastify，提供认证、业务接口和图片存储。Android 通过 Repository 访问数据；桌面端由主进程发起网络请求。两端共用业务字段，页面布局分别设计。数据库的物理表名由服务器实现决定。

## 2. 通用格式与坐标

- 基础地址为 `https://<实际域名>/api/v1`；域名、部署和数据库另行落地。
- JSON 使用 UTF-8、camelCase。单对象成功响应 `{ "data": ... }`；列表 `{ "data": [...], "nextCursor": null }`。204 没有正文。
- ID 使用字符串，新记录由服务器生成 UUID。客户端按字符串保存和传递。
- createdAt / updatedAt 为服务器生成的 UTC 时间，固定 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`，如 `2026-10-29T12:00:00.000Z`。Android 内部转 epoch 毫秒，展示转设备时区。发布时间与个人观察表的发现时间不同。
- 内容为纯文本，不解析 HTML。长度按 UTF-16 code unit 计数，与 Kotlin String.length / JS length 一致。标题、地点、昵称、评论去首尾空白后校验；描述可空。服务端再次校验，未知写入字段拒绝400；客户端可忽略新增响应字段。
- 坐标统一 **WGS84**；latitude [-85,85]、longitude [-180,180]，必须是有限 JSON 数字。GeoJSON 数组顺序为 `[longitude, latitude]`。
- 地图底图、取点与保存使用一致的坐标约定。显示所用坐标系与WGS84不同时，由地图模块处理坐标，接口仍使用WGS84。[高德坐标说明](https://lbs.amap.com/api/javascript-api-v2/guide/transform/convertfrom)

## 3. 对象定义

User：`{id,email,nickname,avatarUrl}`，邮箱仅在本人 `/me` 及登录响应出现。公开 Author：`{id,nickname,avatarUrl}`，头像为空时显式 null；昵称1～20。

Photo：`{id,url}`，url 是无用户名密码的绝对 HTTPS 图片地址。数组顺序就是展示顺序，每条记录0～6张且ID不重复。服务器检查资源归属，不能接受任意URL或本地路径作为上传结果。

Record 必填字段：

| 字段 | 类型 / 含义 |
| --- | --- |
| id, userId | 字符串ID，userId = author.id |
| title / description / place | 字符串，长度分别1～60 / 0～2000 / 1～80 |
| latitude, longitude, coordinateSystem | 数字、数字、固定 `WGS84` |
| photos / author | Photo数组（可空） / Author |
| createdAt / updatedAt | UTC时间；updatedAt ≥ createdAt |
| version | 1～2147483647整数，仅正文/照片修改时递增 |
| likeCount / commentCount | 0～2147483647整数 |
| liked | 当前请求者是否点赞，游客为false |

Comment：`{id,recordId,userId,author,content,createdAt}`；content 1～500，userId = author.id。

RecordWrite（创建/编辑）：`{title,description,place,latitude,longitude,coordinateSystem,photoIds}`。不含作者、记录ID、计数、日期、version或图片URL。v1 编辑发送完整可编辑字段集；photoIds为[]表示移除全部照片。

Observation：`{id,userId,observedAt,place,count,catName,appearance,createdAt,updatedAt,version}`，仅本人可读写。observedAt是实际发现时间（UTC，1970年至服务端当前时间）；place为1～80；count为1～999整数；catName为1～60；appearance为0～200。数量是本次看到的数量，累计值可以重复计算同一只猫，不代表猫咪总个体数。createdAt/updatedAt/version遵守Record规则。

ObservationWrite：`{observedAt,place,count,catName,appearance}`。发现时间以UTC传输，数量为整数，毛色与特征以文本保存。作者由当前会话确定；创建时间和版本由服务器维护。

## 4. HTTP 接口

路径相对于 `/api/v1`。写操作（注册、登录、刷新除外）需 `Authorization: Bearer <accessToken>`。游客可读公开记录和评论；GET携带令牌时返回本人liked。过期/无效令牌返回401，不能悄悄变游客。

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| POST `/auth/register` | `{email,password,nickname}` | 201 `{data:Session}`，注册即建立会话 |
| POST `/auth/login` | `{email,password}` | 200 `{data:Session}` |
| POST `/auth/refresh` | `{refreshToken}` | 200 `{data:Session}`，轮换refreshToken |
| POST `/auth/logout` | `{refreshToken}` | 204，撤销当前会话，已撤销也成功 |
| GET `/me` | — | 200 `{data:User}` |
| PATCH `/me` | `{nickname}` | 200 `{data:User}`；v1先支持昵称 |
| GET `/records` | 下述查询参数 | 200 Record列表 |
| GET `/records/{id}` | — | 200 `{data:Record}`，`ETag: "<version>"` |
| POST `/records` | RecordWrite；幂等键 | 201 `{data:Record}`，ETag |
| PATCH `/records/{id}` | RecordWrite；`If-Match: "<version>"` | 200 `{data:Record}`，新ETag |
| DELETE `/records/{id}` | `If-Match: "<version>"` | 204，级联删除互动 |
| POST `/uploads` | multipart，单个file；幂等键 | 201 `{data:Photo}` |
| PUT / DELETE `/records/{id}/like` | 无正文 | 200 `{data:{recordId,liked,likeCount}}`，liked为最终状态 |
| GET `/records/{id}/comments` | limit、cursor | 200 Comment列表 |
| POST `/records/{id}/comments` | `{content}`；幂等键 | 201 `{data:Comment}` |
| DELETE `/comments/{id}` | — | 204 |
| GET `/me/stats` | 登录 | 200 `{data:{recordCount,commentCount,places:[{place,count}]}}` |
| GET `/observations` | q、from、before、limit、cursor；仅登录用户 | 200 Observation列表，仅本人 |
| GET `/observations/{id}` | 登录 | 200 `{data:Observation}`，ETag |
| POST `/observations` | ObservationWrite；幂等键 | 201 `{data:Observation}`，ETag |
| PATCH `/observations/{id}` | ObservationWrite；If-Match | 200 `{data:Observation}`，新ETag |
| DELETE `/observations/{id}` | If-Match | 204 |

Session：`{user:User,accessToken,refreshToken,expiresAt}`；expiresAt是accessToken的UTC到期时间。token是不透明字符串，客户端不依赖JWT格式。邮箱trim+lowercase；密码8～128，不trim。服务器独立散列密码；不上传Android本机密码散列/盐。refreshToken安全保存，退出清除凭据；并发401合并为一次刷新，原请求最多重试一次。刷新失败要求重新登录且保留草稿；刷新响应丢失不无限重试旧refreshToken。

`/me/stats`：recordCount是本人已发布记录数；commentCount是本人发表且仍存在的评论数；places按本人记录地点文字精确分组，count降序、place升序。统计不能从某一页列表推算。

### 查询、分页、日期

- `/records`：q（最长100，匹配标题/描述/地点/作者昵称）、userId、from、before、bbox、limit、cursor。组合为AND；userId来自/me即“我的”；不传表示不筛选。
- from含边界，before不含边界，均为UTC格式；from < before。结束日期应取设备本地时区“下一日零点”转UTC，不固定加24小时。
- `bbox=west,south,east,north` 为WGS84，west≤east、south≤north，v1不支持跨日期变更线的单个bbox。
- limit默认20，范围1～100。记录createdAt DESC、id DESC；评论createdAt ASC、id ASC。cursor为绑定筛选条件的不透明续页标记，不能解析或换筛选沿用。
- nextCursor为null才表示本轮结束。不承诺跨请求快照；新增内容刷新首页查看。服务器错误不能伪装为空数组。
- `/observations`按observedAt DESC、id DESC；q匹配地点/猫咪名称/毛色特征，from/before筛选observedAt。分页规则同上。身份只从令牌读取，不接受userId筛选他人手记；读取或修改他人手记统一404以避免泄露其存在。

### 并发、重试、删除

- POST records/comments/uploads带 `Idempotency-Key`（UUID）。同一用户、方法、规范路径和键绑定请求内容，事务内保存业务结果及幂等结果；并发重复只创建一次。保存至少24小时，重试返回同一业务结果及原成功状态；同键不同内容409 IDEMPOTENCY_CONFLICT。上传按文件内容摘要判断。超过24小时的未确认操作先核查，不盲目重发。
- 点赞PUT/DELETE表达最终状态，不能使用重试会反转的toggle；数据库唯一约束(recordId,userId)。
- 编辑/删除Record必须提供If-Match，缺少428，格式错误400，旧版本412 VERSION_CONFLICT。更新原子比较版本并递增，不能删除再插入。冲突提示重新读取并保留草稿。
- 删除后GET返回404；重复删除不存在资源返回204；对仍存在的他人资源返回403。评论同理。清理图片前检查其他引用。
- v1采用**在线提交+查询缓存刷新**，暂不定义后台离线写入队列/增量同步。本机草稿独立保存；删除成功失效相关缓存，完整刷新时原子替换该查询成员关系；详情404清理缓存。某一页没出现不等于全局删除，缓存不能自动重新上传。未来增量同步须先定义删除事件及同步游标。

### 图片与错误

- 单张上传最大5MiB，仅JPEG/PNG/WebP。服务端检查实际类型及像素（最长边≤4096），去EXIF保存，限制解码并发。Android本机25MiB是原图导入上限，上传仍须检查压缩后的文件。
- 先上传再提交photoIds；任一照片失败则保留编辑内容并提示，不能静默发布缺图记录。未绑定上传保留24小时供重试再回收；已引用图片不因上传时间过期删除。
- 失败响应：`{error:{code,message,requestId,fields:{title:"具体原因"}}}`，fields可省略。客户端按code分支；message不含堆栈、数据库信息、令牌。
- 状态/错误码：400 VALIDATION_ERROR；401 UNAUTHENTICATED；403 FORBIDDEN；404 NOT_FOUND；409 EMAIL_EXISTS或IDEMPOTENCY_CONFLICT；412 VERSION_CONFLICT；413 FILE_TOO_LARGE；415 UNSUPPORTED_MEDIA_TYPE；428 PRECONDITION_REQUIRED；429 RATE_LIMITED（Retry-After秒）；500 INTERNAL_ERROR；503 UNAVAILABLE。
- 网络超时/断网/取消与HTTP错误分开。仅GET、幂等操作及保留原键的POST可有限重试；400/403/412等不自动重试。服务端记录requestId，限制登录、上传、评论频率。

Observation创建同样必须带幂等键；编辑/删除同样遵守If-Match、428/412及缓存规则。私人手记DELETE成功204，不存在或非本人的手记统一404；客户端将重试后的404视为资源已不可用并清理缓存。私人手记不关联公开点赞评论，也不自动变为地图分享。服务端可使用独立observations表；客户端表名无需与数据库物理表名相同。

## 5. 客户端职责

- Android：UI → ViewModel → Repository → Room / API。本机保存草稿和查询缓存。
- 桌面端：页面通过IPC调用主进程的数据接口，主进程访问API。
- 用户身份由登录会话确定，公开作者资料与登录凭据分开存储。
- 地图分享与观察手记分别保存。个人手记仅本人可见；公开分享提供点赞与评论。
- 两端使用统一的字段名、ID类型、时间格式、坐标系和图片资源ID。

## 6. 联调验收

- 手机发布的地图记录可在桌面查看。
- 桌面发表的评论可在手机查看。
- 同一账号在两端查看和维护个人手记。
- 点赞状态一致，重复提交不生成重复数据。
- 并发编辑发生冲突时保留输入并提示刷新。
- 删除后另一端刷新列表，记录不再显示。
- 相同坐标在两端落在同一位置。
- 断网时可查看本机缓存并保存草稿。
