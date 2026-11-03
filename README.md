# 喵喵数据地图 · Android

校园猫地图记录与交流应用。用 Android Studio 打开本目录即可开始开发。

当前版本：**0.3.0**。

## 运行

- JDK 17 或 21。
- Android SDK 35，设备最低 Android 8.0（API 26）。
- Windows：`./gradlew.bat assembleDebug`。
- macOS / Linux：`sh ./gradlew assembleDebug`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 本机 SDK 路径写在不提交的 `local.properties` 中，换电脑后由 Android Studio 重新生成。

## 当前可体验的功能

- 原生地图、点击标记查看记录、地图与列表浏览。
- 关键词搜索、日期范围筛选、只看我的。
- 本机账号注册、登录、退出和修改昵称。
- 地图选点、系统定位、拍照、相册多选。
- 记录新增、编辑和删除；照片压缩后复制到应用私有目录。
- 右上角「存草稿」保存未完成内容；重新打开相同编辑页可恢复，按账号隔离。
- 发布后自动清除对应草稿；删除记录、移除照片时清理无引用的私有图片。
- 地图记录点赞、取消点赞、评论和删除自己的评论。
- 个人记录数量、评论数量和地点分布统计。
- Room 持久化，应用重启后保留数据；账号密码加盐派生存储。
- 显示选中的日期范围，可一键清除所有筛选；图片读取失败时显示占位提示。
- 数据初始化或读取失败时展示错误和重新加载入口。
- 独立的个人观察手记：发现时间、地点、数量、猫咪名称与毛色特征；按账号隔离，支持新增、编辑、删除、关键词/日期筛选和累计观察数量。

首次运行包含三条明确标注的示例记录，默认地图中心是广州大学城校园附近。它们用于展示交互，不代表真实猫咪观察。注册自己的本机账号后可发布记录，也可以点赞和评论示例记录。

## 数据与地图

当前版本使用本机 Repository：账号、记录和互动只在当前安装内生效。它尚未连接服务器，不提供跨设备登录、共享记录或后台同步；后端接入点见 `docs/API_CONTRACT.md`。卸载应用会删除本机数据。

桌面端与 Android 共用同一份服务协议，界面可分别设计。地图分享对应 `/records`，私人观察手记对应 `/observations`。接口示例位于 `protocol/v1/examples.json`，网络数据源和服务器尚待实现。模块设计与分工见 `docs/INTEGRATION_REVIEW.md`。

原生地图使用 MapLibre Android SDK 及 OpenStreetMap 标准瓦片，不需要 API 密钥。底图依赖网络和瓦片服务可达性，已有记录、搜索与互动保存在本机。遵守地图归属标注，不进行瓦片预下载；正式部署或扩大使用前，根据网络环境和提供方条款配置合适的地图服务。

## 代码结构

```text
app/src/main/java/cn/edu/meowmap/
  MainActivity.kt           Android 入口
  MeowApplication.kt        数据库与 Repository 装配
  data/
    Database.kt            Room Entity、DAO、数据库及查询投影
    MeowRepository.kt      Repository 契约与本机实现
    MediaStore.kt          图片复制、缩放及方向处理
    PasswordHasher.kt      本机账号密码派生
  ui/
    MeowViewModel.kt        状态流、查询筛选及操作处理
    MeowApp.kt              主导航、地图、列表、账号与统计
    CampusMap.kt            原生地图及生命周期、系统定位
    EditorScreen.kt         发布编辑、相册与相机
    DetailScreen.kt         详情、点赞、评论与删除
    Theme.kt               配色与基础组件
```

UI → ViewModel → Repository → Room。Room 导出的数据库结构位于 `app/schemas/`，后续修改数据库时需要新增迁移，保留已有数据。

本地数据库版本为3，包含用户、地图记录、照片、点赞、评论、草稿和观察手记。Room管理数据库升级，覆盖安装时保留已有数据。

## 验证

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
# 构建后，在开发电脑检查 Room 生成的迁移 SQL：
python scripts/check_migration.py
# 启动模拟器或连接 Android 手机后：
.\gradlew.bat connectedDebugAndroidTest
```

单元测试覆盖组合筛选、日期边界、密码校验；设备测试覆盖 Room 数据增删改查、点赞、评论级联删除、账号所有权隔离及发布交流流程。

0.2.0 增加了草稿照片序列化、私有图片清理范围、初始化重试与操作防重入等单元测试。新增设备测试覆盖草稿恢复、账号隔离、照片共享引用和版本升级。各版本实际执行结果见 `docs/VALIDATION.md`。

默认采用单工作线程、1 GiB JVM 堆上限和非驻留构建进程；模拟器仅在需要设备测试时手动启动。

项目功能规划与三人分工见 `ANDROID_PROJECT_BRIEF.md`。
