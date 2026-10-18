<img src="app/src/main/res/drawable-nodpi/app_icon_sunrise.png" width="88" alt="Sky Wallpaper" />

# Sky Wallpaper

[English](README.md) · 简体中文

根据太阳高度切换四个时段的 Android 动态壁纸，支持自选图片与连续渐变。

- Android 14 及以上；Material 3 Expressive、Monet 动态配色和系统明暗主题。
- 清晨、白天、黄昏、夜晚可分别使用自带图片或自选图片。
- 线性、径向、角度渐变，按实际太阳时段连续过渡。
- 以 ZIP 导出、导入壁纸配置，包含自定义图片。
- 支持 Direct Boot，首次解锁前仍可显示壁纸。
- 20 种语言、21 个本地化选项，支持阿拉伯语 RTL 与大字号。
- 应用内离线查看项目许可证、第三方组件及完整许可正文。

## 下载

[下载最新正式版](https://github.com/RiversLee9312/SkyWallpaper/releases/latest)，适用于 Android 14 及以上版本。Release 附件包含 APK 和 `SHA256SUMS`。

正式版 APK 使用的签名与本地调试构建不同，Android 无法将其直接覆盖安装到调试版上。

## 使用

1. 从设置或预览页使用设备位置，或输入固定经纬度。
2. 在“设置 → 壁纸内容”选择图片或渐变，并编辑各时段。
3. 主界面点击“全屏预览”进入时间预览，或点击“设为壁纸”通过 Android 系统应用壁纸。壁纸启用后，这两个操作仍分别保留。

需要在移动后自动更新位置时，将位置权限设为“始终允许”。预览页用已配置的壁纸铺满背景，拖动时间滑块或点按日出、日落，太阳曲线和壁纸会同步更新。右上角全屏按钮统一隐藏或恢复返回键与时间卡片，系统返回先退出全屏。预览不会修改实际壁纸或自动切换设置。设置中的“固定为…”会固定最后预览的时段，并关闭自动切换。手动经纬度会保持固定，直到重新获取设备位置。

图片通过系统照片选择器导入，无需读取整个相册。应用处理方向，限制到最长边 4096 像素和 800 万像素以内，并保存本机副本，之后不依赖原相册文件。

## 渐变

四个时段共用一种渐变类型，以便连续插值。每个时段支持 2–6 个颜色及色标位置。编辑器采用相册式布局，按屏幕比例显示预览，与工具区分开。直接拖动起终点或中心，并在色带拖动色标；点按颜色通过十六进制色值和 HSV 滑块实时调色，通过“位置”精确调整坐标和色标位置。两种调节均在独立工具区展开，保留完整预览。径向渐变半径可在 10%–400% 之间调整。坐标从左上角计算，半径以屏幕短边为基准。

每种渐变类型独立保存四个时段的配置，切换类型会恢复各自的颜色、色标和坐标。编辑页始终保存到打开时的类型。从 1.0.2 升级时，现存的共用配置归入此前选中的类型，旧记录仍在本机保留。旧版本已覆盖的历史数值无法恢复。

清晨 → 白天 → 黄昏仍按实际时段中点进行 smoothstep 插值。太阳高度从 0° 下降至 −6° 时由黄昏过渡至夜晚，从 −6° 上升至 0° 时由夜晚过渡至清晨。太阳高于地平线时不混入夜色，低于 −6° 时完整使用夜晚参数。颜色、色标和几何参数一同过渡，相邻时段可以使用不同数量的色标；跨午夜和高纬度暮光保持连续。缺少位置或关闭自动切换时使用固定时段。这里采用几何太阳高度 0°，与日出、日落事件所用的 −0.833° 阈值区分。

清晨为太阳上升时的 −6° 至 +10°，黄昏为下降时的 +10° 至 −6°，其余时间按太阳高度显示白天或夜晚。太阳位置在本机计算，事件时间统一显示 UTC / GMT，不随手机时区变化。

## 导出与导入

进入“设置 → 壁纸内容 → 壁纸配置”。导出的 ZIP 包含 `config.json`，以及用到的 `images/DAWN.jpg`、`images/DAY.jpg`、`images/DUSK.jpg`、`images/NIGHT.jpg` 自定义图片；自带壁纸无需附加图片文件。JSON 包含当前图片／渐变模式、选中的渐变类型、全部 12 组渐变配置、自动切换开关和固定时段，不包含位置、应用语言或权限。

导入会先校验格式版本、渐变参数、ZIP 路径、文件大小和图片，再确认替换当前壁纸配置。取消或无效配置包不会改变当前配置。自定义图片复制到 Direct Boot 存储，导入完成后不再依赖 ZIP。通过 Android 系统文件选择器保存或选择文件，无需广泛存储权限。第 1 版配置包最大 100 MiB，图片沿用最长边 4096 像素、总计 800 万像素的限制。

## 隐私与后台运行

应用没有网络权限、账号或分析服务。位置和壁纸设置仅存本机，自选图片与位置偏好排除云备份和设备迁移。系统融合定位是否使用网络取决于系统位置服务。详见[隐私说明](PRIVACY.md)。

自动模式每 6 小时尝试通过 Android 原生 `LocationManager.FUSED_PROVIDER` 更新设备位置，可能受系统调度影响。每次请求最长 60 秒，失败保留原坐标；手动位置不进行后台刷新。无需 Google Play 服务。渐变仅在壁纸可见时每秒刷新，系统颜色最多每分钟更新一次。

## 从源码构建

需要 JDK 21、Android SDK 36。可用 Android Studio 打开项目并同步 Gradle，或在命令行运行：

```sh
./gradlew :app:assembleDebug
```

Windows：

```powershell
# JAVA_HOME 指向本机 JDK 21 或 Android Studio 的 JBR。
.\gradlew.bat :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。Material 3 Expressive 固定为 `1.5.0-alpha14`，Navigation Compose 固定为 `2.9.8`。正式版签名方式请参阅[发布签名](#发布签名)。

## 发布签名

Windows 上使用 PowerShell 7、JDK 21 和 Android SDK Build-Tools 36.0.0。新应用首次发布时，只初始化一次签名密钥：

```powershell
pwsh -File tools/build_release.ps1 -InitializeSigningKey
```

后续更新复用现有密钥：

```powershell
pwsh -File tools/build_release.ps1
```

更新时禁止重新初始化密钥，必须继续使用原签名密钥。默认情况下，PKCS12 密钥和 Windows DPAPI 加密口令保存在仓库外的 `~/.android/skywallpaper-signing/`。DPAPI 与当前 Windows 用户绑定。请分别安全备份密钥和口令；DPAPI 文件本身不能作为跨设备使用的口令备份。

其他平台可通过以下四个环境变量配置 Gradle 签名：`SKYWALLPAPER_KEYSTORE_FILE`、`SKYWALLPAPER_STORE_PASSWORD`、`SKYWALLPAPER_KEY_ALIAS` 和 `SKYWALLPAPER_KEY_PASSWORD`。然后运行：

```sh
./gradlew :app:assembleRelease --no-configuration-cache
```

## 验证与维护

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python tools\check_i18n.py

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell am instrument -w com.example.skywallpaper.test/androidx.test.runner.AndroidJUnitRunner
& $adb uninstall com.example.skywallpaper.test
```

设备测试直接运行 instrumentation，避免 Gradle 连接测试在收尾时卸载目标应用。测试覆盖太阳时段、渐变插值、图片导入、Direct Boot、状态恢复、预测式返回、语言格式和离线许可证页。真机使用一加 11，LOS 23.2 / Android 16。

- `tools/check_i18n.py`：校验语言覆盖、字符串键、占位符、换行和格式。翻译由 GPT-6 Luna（max）适配。
- `tools/update_licenses.py`：根据实际解析的依赖，更新离线许可和第三方清单；首次解析依赖需要网络。构建会检查清单是否与依赖一致。
- `tools/device_ui.py`：通过 ADB 读取、点击和截图；临时输出写入忽略的 `verification/` 目录。
- `tools/import_material_symbols.py`：导入 Material Symbols Rounded 矢量资源。

欢迎通过 [Issues](https://github.com/RiversLee9312/SkyWallpaper/issues) 报告问题。请附上 Android 版本、设备型号和复现步骤；无需提供实际经纬度。

## 许可证与素材

项目原始代码、文档和项目自有素材采用 [Apache License 2.0](LICENSE)，保留版权与声明，允许按许可条件使用、修改和分发。第三方组件保留各自的版权和许可条款，详见 [NOTICE](NOTICE) 与[第三方声明](THIRD_PARTY_NOTICES.md)。

界面图标来自 Google 的 [Material Symbols Rounded](https://github.com/google/material-design-icons)，由 SVG 转换为 Android VectorDrawable，原始许可保存在 `artwork/material-symbols-LICENSE.txt`。启动图标由 GPT-image 生成，提示词保存在 `artwork/app-icon-sunrise-prompt.txt`。

设计参考：[Material 3 Expressive](https://design.google/library/expressive-material-design-google-research)、[Apple 界面文案指导](https://developer.apple.com/videos/play/wwdc2022/10037/)。
