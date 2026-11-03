# 验证记录

日期：2026-11-03。Android 版本 0.3.0。

| 检查 | 结果 |
| --- | --- |
| Android JVM单元测试 | 28项通过，0失败、0错误 |
| SQLite数据库升级检查 | 4项通过 |
| 桌面单元与回归测试 | 5项通过 |
| Android APK构建 | 通过 |
| Android Lint | 通过，无错误 |
| Android 模拟器测试 | 10 项通过 |

## 覆盖内容

- 地图记录查询与日期边界。
- 密码校验、草稿照片序列化、私有图片清理。
- 初始化重试、操作状态和账号切换。
- 观察手记字段校验、搜索和账号数据隔离。
- Room升级过程中的记录、互动及草稿保留。
- 桌面记录按ID编辑删除、更新结果处理、文本展示与页面脚本。

## 验证范围

在 Android 15 模拟器上测试了登录错误提示、注册、手记增删改查、草稿恢复、地图选点、发布、评论和删除。手动检查了地图标记和无照片的详情页，并选中周末日期应用筛选，核对了日期范围和结果数。

相机、真机定位和跨设备共享待验证。

## 运行命令

Android：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python scripts/check_migration.py
```

桌面：

```powershell
node --test test/*.test.js
```

连接Android设备后执行交互测试：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

APK位于 `app/build/outputs/apk/debug/app-debug.apk`。

构建采用单工作线程、1GiB JVM堆和非驻留进程。
