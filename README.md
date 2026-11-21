# 我的语音日记本 · Android V0.12

一个以本地保存为主的 Android 日记应用。项目使用 Kotlin、Jetpack Compose 和 Room；日记默认保存在手机应用的私有空间中。

## 功能

- 创建、自动保存、继续编辑和删除日记；日记按创建时间排列。
- 首页可新建长日记或随手记。随手记使用独立编辑页，无需标题；可以写几句、拍照或选择照片与视频，最多添加九个附件。手动保存后才进入日记本。
- 长日记根据最近写下的文字在本机推荐贴纸；只有点选后才会插入，不使用网络识别。
- 在日记正文中插入照片、视频和内置贴纸；图片按比例完整显示。
- 通过时间线、日历、年月档案、往年今日和搜索查看日记。
- 支持通过同一局域网与电脑同步。电脑端同步服务需单独运行；本仓库只包含 Android 客户端。

本版本不包含语音录入或语音识别。测试用的示例日记仅用于首次查看界面，可在应用内删除。

## 在 Android Studio 中运行

1. 克隆或下载此仓库，并在 Android Studio 中打开仓库根目录（包含 `settings.gradle.kts` 的目录）。
2. 使用 JDK 17，并按 Android Studio 提示安装 Android SDK 35。
3. 等待 Gradle Sync 完成，然后连接 Android 8.0（API 26）或更高版本的设备，或启动模拟器。
4. 选择 `app` 配置并点击 Run。

首次 Gradle Sync 需要网络下载依赖。仓库不包含 `local.properties`、IDE 状态、Gradle 缓存或编译生成物。

## 技术信息

- Kotlin、Jetpack Compose、Navigation Compose
- Room 本地数据库
- 包名：`com.jingwen.voicediary`
- 最低系统：Android 8.0（API 26）；目标 SDK：35
