# 车载媒体播放器项目设置总结

## 已完成工作

### 1. 项目基础设置 ✅
- ✅ 创建 `settings.gradle`
- ✅ 创建根目录 `build.gradle`
- ✅ 创建 `app/build.gradle`（包含所有必需依赖）
- ✅ 创建 `AndroidManifest.xml`（包含车载专用配置）

### 2. 从TV项目复制代码 ✅
- ✅ 使用Python脚本从 `D:\devspace\tv-baidu-player` 复制代码
- ✅ 自动替换包名从 `com.baidu.gallery.tv` 到 `com.baidu.gallery.car`
- ✅ 复制所有源代码文件（Java）
- ✅ 复制所有资源文件（res目录）

### 3. 核心类创建 ✅
- ✅ `CarGalleryApplication.java` - 主Application类
- ✅ `SplashActivity.java` - 启动页，检查登录状态
- ✅ `DrivingModeManager.java` - 驾驶模式管理器
- ✅ `VoiceCommandManager.java` - 语音指令管理器
- ✅ `MediaPlaybackService.java` - 后台媒体播放服务

### 4. 已复制的TV项目核心模块 ✅
- ✅ 用户认证模块（LoginActivity, AuthRepository, AuthViewModel, BaiduAuthService）
- ✅ 文件浏览模块（FileBrowserActivity, FileBrowserFragment, FileBrowserViewModel, FileAdapter）
- ✅ 播放列表管理模块（PlaylistRepository, PlaylistDao, PlaylistItemDao）
- ✅ 播放器模块（PlaybackActivity, PlaybackViewModel）
- ✅ 设置模块（SettingsActivity）
- ✅ 数据库模块（AppDatabase, PlaybackHistoryDao）
- ✅ 网络模块（BaiduPanService, RetrofitClient）
- ✅ 工具类（QRCodeUtils, PreferenceUtils, LocationUtils, ImageBackgroundUtils等）
- ✅ 图片特效（9种特效策略）
- ✅ 地理编码（3种策略：Android原生、高德、OpenStreetMap）

## 需要完成的工作

### 1. 修复Activity路径问题 ⚠️
AndroidManifest中引用了一些不存在的Activity：
- `app/src/main/java/com/baidu/gallery/car/ui/auth/AuthActivity.java` 不存在
  - 实际存在：`app/src/main/java/com/baidu/gallery/car/auth/LoginActivity.java`
- `app/src/main/java/com/baidu/gallery/car/ui/player/PlayerActivity.java` 不存在
  - 实际存在：`app/src/main/java/com/baidu/gallery/car/ui/playback/PlaybackActivity.java`

需要：
- 修改 `AndroidManifest.xml` 中的Activity引用路径
- 或创建缺失的Activity类作为代理

### 2. 创建必要的布局文件 📱
需要创建的布局：
- `activity_splash.xml` - 启动页布局
- 其他布局文件已从TV项目复制

### 3. 创建车载特定配置文件 🚗
- `app/src/main/res/xml/automotive_app_desc.xml` - 车载应用描述文件
- `app/src/main/res/xml/data_extraction_rules.xml` - 数据提取规则
- `app/src/main/res/xml/backup_rules.xml` - 备份规则

### 4. 创建drawable资源 🎨
- `app_banner.png` - 车载Launcher Banner

### 5. 更新AndroidManifest.xml 📝
修复以下问题：
- 将 `android:name=".ui.auth.AuthActivity"` 改为 `.auth.LoginActivity`
- 将 `android:name=".ui.player.PlayerActivity"` 改为 `.ui.playback.PlaybackActivity`
- 确保所有Activity路径正确

### 6. 车载功能集成 🚗
需要在MainActivity和PlaybackActivity中集成：
- DrivingModeManager - 监听驾驶状态
- VoiceCommandManager - 处理语音指令
- MediaPlaybackService - 后台播放服务

### 7. 测试和调试 🧪
- 确保编译通过
- 测试登录流程
- 测试文件浏览
- 测试播放功能
- 测试车载专属功能

## 项目架构

```
com.baidu.gallery.car/
├── CarGalleryApplication          # 主Application
├── auth/                           # 认证模块
│   ├── LoginActivity              # 登录页面
│   ├── AuthRepository             # 认证仓库
│   ├── AuthViewModel              # 认证ViewModel
│   └── BaiduAuthService           # 百度认证服务
├── ui/
│   ├── splash/
│   │   └── SplashActivity         # 启动页
│   ├── main/
│   │   ├── MainActivity           # 主页面
│   │   ├── MainFragment
│   │   └── MainViewModel
│   ├── playback/
│   │   ├── PlaybackActivity       # 播放器页面
│   │   └── PlaybackViewModel
│   ├── filebrowser/
│   │   ├── FileBrowserActivity
│   │   ├── FileBrowserFragment
│   │   └── FileBrowserViewModel
│   └── settings/
│       └── SettingsActivity        # 设置页面
├── service/
│   ├── MediaPlaybackService       # 媒体播放服务
│   └── LocationExtractionService  # 地点提取服务
├── database/
│   ├── AppDatabase                # Room数据库
│   ├── PlaybackHistoryDao
│   ├── PlaylistDao
│   └── PlaylistItemDao
├── repository/                     # 数据仓库层
│   ├── FileRepository
│   ├── PlaylistRepository
│   └── PlaybackHistoryRepository
├── network/                        # 网络层
│   ├── BaiduPanService
│   ├── RetrofitClient
│   └── ApiConstants
├── model/                          # 数据模型
│   ├── FileInfo
│   ├── Playlist
│   ├── PlaylistItem
│   ├── PlaybackHistory
│   └── ...
├── utils/                          # 工具类
│   ├── DrivingModeManager         # 驾驶模式管理器（车载专属）
│   ├── VoiceCommandManager        # 语音指令管理器（车载专属）
│   ├── QRCodeUtils
│   ├── PreferenceUtils
│   ├── LocationUtils
│   └── ...
├── effects/                        # 图片特效
│   └── 9种特效策略
└── geocoding/                      # 地理编码
    └── 3种策略
```

## 技术栈

- **开发语言**: Java 1.8
- **最低SDK**: Android 9.0 (API 28)
- **目标SDK**: Android 14 (API 35)
- **架构模式**: MVVM
- **UI框架**: Android TV Leanback + Car App Library
- **数据库**: Room 2.5.0
- **网络请求**: Retrofit 2.9.0 + OkHttp 4.10.0
- **图片加载**: Glide 4.15.1
- **视频播放**: Media3 (ExoPlayer) 1.5.0 + VLC 3.5.1
- **二维码**: ZXing 3.5.1

## 下一步行动

1. 修复AndroidManifest.xml中的Activity路径
2. 创建activity_splash.xml布局文件
3. 创建automotive_app_desc.xml等配置文件
4. 测试编译
5. 集成车载功能到主要Activity
6. 进行功能测试