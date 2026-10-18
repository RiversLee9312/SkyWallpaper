# Privacy

Sky Wallpaper stores location coordinates, wallpaper preferences, gradient parameters and imported images on the device. The app has no Internet permission, accounts, advertising SDK or analytics service. It does not transmit location or imported images to the developer.

## Location

Location is optional. You may enter fixed coordinates instead of granting access. Automatic device location uses Android's native fused location provider to calculate solar periods, sunrise and sunset. The provider may use network services according to the device's system settings; Sky Wallpaper does not control those services.

Background updates require background location permission and are attempted every six hours. Android may delay them. Each request lasts up to 60 seconds, and a failed update keeps the saved coordinates. A manually entered location is not refreshed in the background.

## Images and storage

Custom images are selected with Android's photo picker. The app reads only the selected image, handles its orientation and saves a resized local copy. It does not request broad access to the photo library.

Preferences and imported wallpaper copies are kept in device-protected storage so the live wallpaper can work before the first unlock after a restart. Location preferences and imported images are excluded from the app's cloud backup and device transfer rules. Clearing the app's storage or uninstalling the app removes this local data.

## External links

The Source code action opens this project's GitHub repository in the system browser. That website is governed by GitHub's privacy policy. Reading the in-app license pages works offline.

## 隐私说明

Sky Wallpaper 仅在本机保存经纬度、壁纸设置、渐变参数和导入的图片。应用没有网络权限、账号、广告 SDK 或分析服务，不向开发者传输位置或图片。

定位为可选项，也可以输入固定经纬度。自动定位使用 Android 原生融合定位服务；该系统服务是否使用网络取决于设备设置。后台定位需单独授权，每 6 小时尝试一次，可能受系统调度影响；每次最多 60 秒，失败保留原位置。手动位置不在后台刷新。

自选图片通过系统照片选择器导入，应用只读取所选图片并保存调整尺寸后的本机副本，不请求整个相册的访问权限。偏好和图片副本保存在支持 Direct Boot 的设备加密存储中。位置偏好和自选图片排除应用云备份及设备迁移；清除应用数据或卸载应用会删除这些本机数据。

“源代码”按钮会在系统浏览器中打开本项目的 GitHub 页面，该网站适用 GitHub 的隐私政策。应用内许可证页面可离线查看。
