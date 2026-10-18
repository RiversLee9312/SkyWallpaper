# Sky Wallpaper

Android 动态壁纸，根据设备位置和太阳高度切换四个时段的图片，或连续过渡渐变。

## 使用

允许位置访问后，点击“设为壁纸”，在系统预览中选择主屏幕或锁屏。需要在移动后自动更新位置时，将位置权限设为“始终允许”。也可以输入固定经纬度。主页的时段选择只改变预览；设置中的“固定为…”关闭自动切换。

在“设置 → 壁纸内容”中选择：

- **图片**：为清晨、白天、黄昏、夜晚分别选择图片，或恢复自带壁纸。通过系统照片选择器导入，无需整个相册的读取权限。导入时处理方向，限制到最长边 4096 像素和 800 万像素以内，并保存为本机 JPEG 副本。
- **渐变**：四个时段共用线性、径向或角度类型。各时段支持 2–6 个颜色及色标位置，线性支持起点和终点，径向和角度支持中心，径向另支持半径。坐标从左上角计算，半径以短边为基准。编辑后点击“保存”。

自动渐变以实际太阳时段的中间时刻为节点，用 smoothstep 插值颜色、色标与几何参数，跨午夜连续，也支持不同数量的色标。极昼、极夜没有时段变化时保持对应时段；没有位置或关闭自动切换时使用固定时段。

清晨为太阳上升时的 −6° 至 +10°，黄昏为下降时的 +10° 至 −6°，其余时间按太阳高度显示白天或夜晚。太阳位置离线计算，时间统一显示 UTC / GMT，不随手机时区变化。

## 界面与语言

使用 Material 3 Expressive、Monet 动态配色和 Edge-to-Edge，支持系统明暗主题、Android 16 扩展深色模式与预测式返回。时段选择区按文字实际宽度调整列数，适配长名称和字号变化。

支持 20 种语言、21 个本地化选项：英语、简体中文、繁体中文、西班牙语、法语、德语、意大利语、巴西葡萄牙语、日语、韩语、俄语、阿拉伯语、印地语、印度尼西亚语、土耳其语、荷兰语、波兰语、乌克兰语、越南语、泰语和孟加拉语。每项覆盖全部 135 个字符串键，翻译由 GPT-6 Luna（max）适配。应用语言通过系统设置选择；阿拉伯语支持 RTL，坐标和 UTC 时间保留正确的阅读顺序。

## 存储与后台运行

位置和壁纸参数仅存本机，保存在支持 Direct Boot 的设备加密存储中，首次解锁前可继续显示壁纸。位置偏好和自选图片排除云备份及设备迁移。

自动模式下，每 6 小时尝试通过 Android 原生 LocationManager.FUSED_PROVIDER 更新设备位置，系统调度可能推迟。一次请求最长 60 秒，失败保留原坐标。手动位置不进行后台刷新，无需 Google Play 服务。渐变仅在壁纸可见时每秒刷新，系统颜色最多每分钟更新一次。

## 构建与验证

需要 JDK 21、Android SDK 36，最低 Android 14（API 34）。Material 3 Expressive 固定为 1.5.0-alpha14，Navigation Compose 固定为 2.9.8。

```powershell
# JAVA_HOME 指向本机 JDK 21 或 Android Studio 的 JBR。
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python tools\check_i18n.py

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell am instrument -w com.example.skywallpaper.test/androidx.test.runner.AndroidJUnitRunner
& $adb uninstall com.example.skywallpaper.test
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。设备测试直接运行 instrumentation，避免 Gradle 连接测试在收尾时卸载目标应用。

测试覆盖太阳时段、渐变插值、图片导入、Direct Boot 存储、编辑状态恢复、预测式返回、语言资源与数字格式。真机验证设备为一加 11，LOS 23.2 / Android 16。

2026-10-18 验证：15 项单元测试、14 项真机测试和全部语言资源校验通过；Lint 为 0 错误、20 条警告。

## 维护工具与素材

- `tools/check_i18n.py`：检查语言覆盖、字符串键、占位符、换行和格式。
- `tools/device_ui.py`：通过 ADB 读取界面、按文字点击或截图；生成内容写入忽略的 `verification/` 目录。
- `tools/import_material_symbols.py`：重新导入 Google Material Symbols Rounded 矢量图标。
- `artwork/material-symbols-LICENSE.txt`：界面图标的 Apache 2.0 许可证。
- `artwork/app-icon-sunrise-prompt.txt`：GPT-image 应用图标的生成提示词。当前图标位于 `app/src/main/res/drawable-nodpi/app_icon_sunrise.png`，另有独立单色图层。

设计参考：[Material 3 Expressive](https://design.google/library/expressive-material-design-google-research)、[Material Symbols](https://developers.google.com/fonts/docs/material_symbols)、[Apple 界面文案指导](https://developer.apple.com/videos/play/wwdc2022/10037/)。
