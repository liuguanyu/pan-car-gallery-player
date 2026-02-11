# 车载百度网盘播放器开发进度

## 已完成
- [x] 项目结构初始化 (Gradle, Manifest)
- [x] 代码迁移 (从 TV 项目迁移并重构包名)
- [x] Application 类重命名 (`CarGalleryApplication`)
- [x] 启动页 (`SplashActivity`) 及布局
- [x] 车载核心功能模块:
    - `DrivingModeManager`: 驾驶模式监听
    - `VoiceCommandManager`: 语音指令处理
    - `MediaPlaybackService`: 后台媒体服务 (集成 MediaSession)
- [x] 车载必须资源:
    - `automotive_app_desc.xml`: 声明媒体应用
    - `app_banner.xml`: 应用启动器横幅
- [x] 主界面 (`MainActivity`) 集成车载功能
- [x] 播放界面 (`PlaybackActivity`) 集成车载功能 (驾驶模式暂停)

## 待办事项 (Pending)

### 代码完善
- [ ] **PlaybackActivity**: 确认 `togglePlayPause` 方法是否存在，如不存在需实现。
- [ ] **资源清理**: 移除未使用的 TV 端特定资源或代码。
- [ ] **ExoPlayer/VLC**: 验证双播放器切换逻辑在车载环境下的表现。

### 功能验证
- [ ] **驾驶模式**: 模拟驾驶状态，验证视频是否自动暂停。
- [ ] **语音控制**: 验证 "播放", "暂停", "下一首" 等语音指令。
- [ ] **后台播放**: 验证退回主页时服务是否正常运行，通知栏控制是否有效。

### UI/UX 优化
- [ ] **字体适配**: 确认车载屏幕上的字体大小是否合适。
- [ ] **焦点控制**: 验证旋钮/方向键控制是否流畅。
- [ ] **夜间模式**: 检查深色主题在车载环境的效果。

## 已知问题
- 暂无