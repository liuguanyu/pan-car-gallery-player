
package com.baidu.gallery.car.ui.playback;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.baidu.gallery.car.R;
import com.baidu.gallery.car.model.FileInfo;
import com.baidu.gallery.car.effects.ImageEffectFactory;
import com.baidu.gallery.car.effects.ImageEffectStrategy;
import com.baidu.gallery.car.model.ImageEffect;
import com.baidu.gallery.car.model.PlayMode;
import com.baidu.gallery.car.model.PlaybackHistory;
import com.baidu.gallery.car.model.Playlist;
import com.baidu.gallery.car.model.PlaylistItem;
import com.baidu.gallery.car.repository.PlaybackHistoryRepository;
import com.baidu.gallery.car.repository.PlaylistRepository;
import com.baidu.gallery.car.repository.FileRepository;
import com.baidu.gallery.car.auth.AuthRepository;
import com.baidu.gallery.car.utils.LocationUtils;
import com.baidu.gallery.car.ui.view.BlindsImageView;
import com.baidu.gallery.car.utils.PlaylistCache;
import com.baidu.gallery.car.utils.ImageBackgroundUtils;
import com.baidu.gallery.car.utils.PreferenceUtils;
import com.baidu.gallery.car.utils.DrivingModeManager;
import com.baidu.gallery.car.utils.VoiceCommandManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import com.baidu.gallery.car.player.PlayerScheduler;
import com.baidu.gallery.car.player.PlayerStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 播放器Activity
 * 默认使用 ExoPlayer (主力播放器)，失败时自动切换到 VLC (备用播放器)
 *
 * 播放器策略：
 * 1. 主力使用 ExoPlayer - Google 官方推荐，性能更好，适合 Android TV
 * 2. ExoPlayer 失败时自动切换到 VLC - 支持更多格式（HEVC/H.265 等）
 * 3. 两者都失败则跳过当前文件
 */
@UnstableApi
public class PlaybackActivity extends FragmentActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    private PlaybackViewModel viewModel;
    private PlaybackHistoryRepository historyRepository;
    private com.baidu.gallery.car.auth.AuthRepository authRepository;
    
    // UI组件
    private SurfaceView surfaceView; // VLC Surface
    private PlayerView playerView;   // ExoPlayer View
    private BlindsImageView ivImageDisplay;
    private ImageView ivBackground;  // 背景图层（毛玻璃或主色调）
    private View layoutControls;
    private TextView tvFileName;
    private TextView tvLocation;
    private TextView tvPlayerIndicator; // 播放器指示器
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekbarProgress;
    private ImageView ivPlayMode;
    private ImageView ivPrev;
    private ImageView ivPlayPause;
    private ImageView ivNext;
    private ProgressBar loadingIndicator;
    
    // VLC 播放器
    private LibVLC libVLC;
    private MediaPlayer vlcMediaPlayer;
    
    // ExoPlayer 播放器 (主力播放器)
    private ExoPlayer exoPlayer;
    
    // 播放器调度器
    private PlayerScheduler playerScheduler;
    
    // 当前选择的播放器策略
    private PlayerStrategy currentPlayerStrategy;
    
    // 播放模式：true使用VLC，false使用ExoPlayer
    // 默认使用 ExoPlayer (主力播放器)，失败时切换到 VLC
    private boolean useVlc = false;
    
    // 错误重试计数
    private int vlcErrorCount = 0;
    private static final int MAX_VLC_RETRIES = 2;
    
    // ExoPlayer 错误重试计数
    private int exoErrorCount = 0;
    private static final int MAX_EXO_RETRIES = 1;
    
    // ExoPlayer 状态转换检测相关字段
    private int lastExoPlayerState = Player.STATE_IDLE;
    private long lastStateChangeTime = 0;
    private int exoBufferingToEndedCount = 0;  // BUFFERING -> ENDED 快速转换计数
    private static final int MAX_BUFFERING_TO_ENDED_RETRIES = 2;  // 最大重试次数
    private static final long STATE_CHANGE_THRESHOLD = 1000; // 状态转换时间阈值(毫秒)
    
    // 当前播放的URL
    private String currentMediaUrl = null;
    
    // 记录最后一次prepare的时间，用于性能分析
    private long lastPrepareTime = 0;

    // 防止ExoPlayer.prepare()循环调用的标志位
    private boolean isPreparingExoPlayer = false;
    private boolean hasSwitchedToVlc = false; // 标记是否已经切换到VLC

    // 是否为模拟器环境
    private boolean isEmulator = false;
    
    // Activity是否已销毁标志位，用于防止在销毁后继续执行重试逻辑
    private boolean isActivityDestroyed = false;
    
    // 图片播放相关
    private Handler imageHandler;
    private Runnable imageRunnable;
    
    // 控制栏显示相关
    private Handler controlsHandler;
    private Runnable controlsRunnable;
    private static final int CONTROLS_HIDE_DELAY = 3000; // 3秒后隐藏控制栏
    
    // 进度更新相关
    private Handler progressHandler;
    private Runnable progressRunnable;
    
    // 地点显示相关
    private Handler locationHandler;
    private Runnable locationRunnable;
    private static final int LOCATION_HIDE_DELAY = 10000; // 10秒后隐藏地点

    // 车载功能相关
    private DrivingModeManager drivingModeManager;
    private VoiceCommandManager voiceCommandManager;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏和沉浸式模式
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        
        setContentView(R.layout.activity_playback);
        
        // 检测是否为模拟器
        isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                     android.os.Build.FINGERPRINT.contains("vbox") ||
                     android.os.Build.PRODUCT.contains("sdk") ||
                     android.os.Build.MODEL.contains("Emulator");
        android.util.Log.d("PlaybackActivity", "设备类型: " + (isEmulator ? "模拟器" : "真机"));

        initViews();
        initVLC();
        initExoPlayer();
        initViewModel();
        initData();
        setupClickListeners();
        initCarFeatures();
        initScheduler();
    }

    private void initScheduler() {
        playerScheduler = new PlayerScheduler();
    }
    
    private void initCarFeatures() {
        drivingModeManager = DrivingModeManager.getInstance(this);
        voiceCommandManager = VoiceCommandManager.getInstance(this);

        // 监听驾驶模式变化
        drivingModeManager.getDrivingModeState().observe(this, isDriving -> {
            if (isDriving) {
                // 如果正在播放视频，且处于驾驶模式，暂停播放
                FileInfo currentFile = viewModel.getCurrentFile();
                if (currentFile != null && currentFile.isVideo()) {
                    Boolean isPlaying = viewModel.getIsPlaying().getValue();
                    if (isPlaying != null && isPlaying) {
                        viewModel.setPlaying(false); // 暂停
                        Toast.makeText(this, "为了您的安全，驾驶模式下视频已暂停", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        // 处理可能的语音指令
        handleVoiceCommand(getIntent());
    }

    @Override
    protected void onNewIntent(@Nullable Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleVoiceCommand(intent);
    }

    private void handleVoiceCommand(@Nullable Intent intent) {
        if (intent != null && voiceCommandManager.isVoiceCommand(intent)) {
            // 这里可以处理更复杂的语音指令逻辑，例如快进快退等
            // 目前简单实现播放暂停切换
            voiceCommandManager.handleVoiceCommand(intent);
            // 简单的播放/暂停控制
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_BUTTON.equals(action) || "android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(action)) {
                // 如果是按键或搜索播放，这里可能需要补充逻辑
                // 目前VoiceCommandManager主要通过广播或Context启动，这里主要响应直接的指令
            }
        }
    }

    // Removed duplicate methods: togglePlayPause, updatePlayPauseButton, startProgressUpdate, stopProgressUpdate, updateProgress, handleVlcError, hideControls, startImageDisplayTimer, playImageWithUrl, setupClickListeners, initData, initViewModel

    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("PlaybackActivity", "onResume called");
        
        // 从设置页返回时重新加载配置
        viewModel.reloadSettings();
        
        FileInfo currentFile = viewModel.getCurrentFile();
        if (currentFile == null) {
            android.util.Log.d("PlaybackActivity", "onResume: currentFile is null");
            return;
        }
        
        Boolean isPlaying = viewModel.getIsPlaying().getValue();
        if (isPlaying != null && isPlaying) {
            if (currentFile.isVideo()) {
                // 视频：恢复播放
                android.util.Log.d("PlaybackActivity", "onResume: 恢复视频播放");
                if (useVlc && vlcMediaPlayer != null && !vlcMediaPlayer.isPlaying()) {
                    vlcMediaPlayer.play();
                } else if (exoPlayer != null && !exoPlayer.isPlaying()) {
                    exoPlayer.setPlayWhenReady(true);
                }
            } else if (currentFile.isImage()) {
                // 图片：需要重新显示图片（因为可能已经被清空或隐藏）
                android.util.Log.d("PlaybackActivity", "onResume: 重新显示图片");
                
                // 确保图片显示View可见
                ivImageDisplay.setVisibility(View.VISIBLE);
                surfaceView.setVisibility(View.GONE);
                playerView.setVisibility(View.GONE);
                
                // 如果图片当前不可见或为空，重新加载
                String mediaUrl = viewModel.getPreparedMediaUrl().getValue();
                if (mediaUrl != null && !mediaUrl.isEmpty()) {
                    // 如果ImageView中没有图片，或者我们想确保它被刷新
                    if (ivImageDisplay.getDrawable() == null) {
                        android.util.Log.d("PlaybackActivity", "onResume: 重新加载图片 URL");
                        playImageWithUrl(mediaUrl);
                    }
                }
                
                // 重新启动定时器以应用新的显示时长
                startImageDisplayTimer();
            }
        }
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surface_view);
        playerView = findViewById(R.id.player_view);
        ivImageDisplay = findViewById(R.id.iv_image_display);
        ivBackground = findViewById(R.id.iv_background);
        layoutControls = findViewById(R.id.layout_controls);
        tvFileName = findViewById(R.id.tv_file_name);
        tvLocation = findViewById(R.id.tv_location);
        tvPlayerIndicator = findViewById(R.id.tv_player_indicator);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        seekbarProgress = findViewById(R.id.seekbar_progress);
        ivPlayMode = findViewById(R.id.iv_play_mode);
        ivPrev = findViewById(R.id.iv_prev);
        ivPlayPause = findViewById(R.id.iv_play_pause);
        ivNext = findViewById(R.id.iv_next);
        loadingIndicator = findViewById(R.id.loading_indicator);
        
        // 强制设置SurfaceView布局参数
        android.view.ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        surfaceView.setLayoutParams(params);
        
        // 隐藏控制栏
        hideControls();
    }
    
    private void initVLC() {
        try {
            ArrayList<String> options = new ArrayList<>();
            // 增加网络缓存以提高稳定性
            options.add("--network-caching=1000");
            // 禁用硬件加速，强制使用软解以解决HEVC花屏问题
            options.add("--avcodec-hw=none");
            // 启用详细日志
            options.add("-vvv");
            android.util.Log.d("PlaybackActivity", "[DECODER] VLC: 禁用硬件加速，强制使用软解");
            
            libVLC = new LibVLC(this, options);
            vlcMediaPlayer = new MediaPlayer(libVLC);
            
            IVLCVout vout = vlcMediaPlayer.getVLCVout();
            vout.setVideoView(surfaceView);
            vout.setWindowSize(surfaceView.getWidth(), surfaceView.getHeight());
            
            // 添加布局监听器
            vout.addCallback(new IVLCVout.Callback() {
                @Override
                public void onSurfacesCreated(IVLCVout vout) {
                    android.util.Log.d("PlaybackActivity", "VLC Surface created");
                }

                @Override
                public void onSurfacesDestroyed(IVLCVout vout) {
                    android.util.Log.d("PlaybackActivity", "VLC Surface destroyed");
                }

                public void onNewLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
                    if (width * height == 0) return;
                    
                    // 计算视频实际宽高比（考虑SAR）
                    double videoRatio = (double) width / height;
                    if (sarNum > 0 && sarDen > 0) {
                        videoRatio = videoRatio * sarNum / sarDen;
                    }
                    final double finalVideoRatio = videoRatio;

                    android.util.Log.d("PlaybackActivity", String.format(
                        "视频源尺寸: video=%dx%d, sar=%d/%d, ratio=%.4f",
                        width, height, sarNum, sarDen, finalVideoRatio));
                    
                    runOnUiThread(() -> {
                        // 获取屏幕尺寸
                        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                        int screenWidth = dm.widthPixels;
                        int screenHeight = dm.heightPixels;
                        
                        double screenRatio = (double) screenWidth / screenHeight;
                        
                        // 判断视频是横屏还是竖屏 (使用最终显示比例判断)
                        boolean isLandscape = finalVideoRatio >= 1.0;
                        
                        android.widget.FrameLayout.LayoutParams lp =
                            (android.widget.FrameLayout.LayoutParams) surfaceView.getLayoutParams();
                        
                        if (isLandscape) {
                            // 横屏视频：尽量充满全屏 (CenterCrop 模式)
                            // 计算能够填满屏幕的 SurfaceView 尺寸，同时保持视频比例
                            int surfaceWidth, surfaceHeight;
                            
                            if (finalVideoRatio > screenRatio) {
                                // 视频比屏幕更宽 (例如 21:9 在 16:9 屏幕)
                                // 以高度为基准填满屏幕，宽度超出屏幕 (裁剪左右)
                                surfaceHeight = screenHeight;
                                surfaceWidth = (int) (screenHeight * finalVideoRatio);
                            } else {
                                // 视频比屏幕更窄 (例如 4:3 在 16:9 屏幕)
                                // 以宽度为基准填满屏幕，高度超出屏幕 (裁剪上下)
                                surfaceWidth = screenWidth;
                                surfaceHeight = (int) (screenWidth / finalVideoRatio);
                            }
                            
                            lp.width = surfaceWidth;
                            lp.height = surfaceHeight;
                            lp.gravity = android.view.Gravity.CENTER;
                            surfaceView.setLayoutParams(lp);
                            
                            // 不设置宽高比，让VLC自动适应SurfaceView
                            // 由于SurfaceView的比例已调整为和视频一致，VLC会自然充满SurfaceView
                            vlcMediaPlayer.setAspectRatio(null);
                            vlcMediaPlayer.setScale(0);
                            
                            android.util.Log.d("PlaybackActivity", String.format(
                                "横屏视频 - CenterCrop模式: SurfaceView=%dx%d (Screen=%dx%d)",
                                surfaceWidth, surfaceHeight, screenWidth, screenHeight));
                        } else {
                            // 竖屏视频：等比占满纵轴 (FitCenter 模式)
                            // SurfaceView保持全屏
                            lp.width = screenWidth;
                            lp.height = screenHeight;
                            lp.gravity = android.view.Gravity.CENTER;
                            surfaceView.setLayoutParams(lp);
                            
                            // 计算目标宽高比字符串，保持视频比例
                            String targetAspectRatio;
                            if (finalVideoRatio > screenRatio) {
                                // 视频比屏幕宽（罕见情况）：宽度占满
                                targetAspectRatio = screenWidth + ":" + (int)(screenWidth / finalVideoRatio);
                            } else {
                                // 视频比屏幕窄：高度占满
                                targetAspectRatio = (int)(screenHeight * finalVideoRatio) + ":" + screenHeight;
                            }
                            
                            vlcMediaPlayer.setAspectRatio(targetAspectRatio);
                            vlcMediaPlayer.setScale(0);
                            
                            android.util.Log.d("PlaybackActivity", String.format(
                                "竖屏视频 - FitCenter模式: AspectRatio=%s, SurfaceView=%dx%d",
                                targetAspectRatio, screenWidth, screenHeight));
                        }
                    });
                }
            });
            
            // 确保SurfaceView已准备好再附加
            surfaceView.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull android.view.SurfaceHolder holder) {
                    android.util.Log.d("PlaybackActivity", "SurfaceHolder created");
                    if (!vout.areViewsAttached()) {
                        vout.attachViews();
                    }
                }

                @Override
                public void surfaceChanged(@NonNull android.view.SurfaceHolder holder, int format, int width, int height) {
                    android.util.Log.d("PlaybackActivity", "SurfaceHolder changed: " + width + "x" + height);
                    if (vout.areViewsAttached()) {
                        vout.setWindowSize(width, height);
                    }
                }

                @Override
                public void surfaceDestroyed(@NonNull android.view.SurfaceHolder holder) {
                    android.util.Log.d("PlaybackActivity", "SurfaceHolder destroyed");
                }
            });
            
            vlcMediaPlayer.setEventListener(event -> {
                switch (event.type) {
                    case MediaPlayer.Event.Buffering:
                        if (event.getBuffering() == 100.0f) {
                            loadingIndicator.setVisibility(View.GONE);
                        } else {
                            if (loadingIndicator.getVisibility() != View.VISIBLE) {
                                loadingIndicator.setVisibility(View.VISIBLE);
                            }
                        }
                        break;
                    case MediaPlayer.Event.Playing:
                        loadingIndicator.setVisibility(View.GONE);
                        updatePlayPauseButton(true);
                        startProgressUpdate();
                        break;
                    case MediaPlayer.Event.Paused:
                        updatePlayPauseButton(false);
                        stopProgressUpdate();
                        break;
                    case MediaPlayer.Event.Stopped:
                        updatePlayPauseButton(false);
                        stopProgressUpdate();
                        break;
                    case MediaPlayer.Event.EndReached:
                        android.util.Log.d("PlaybackActivity", "[VLC] EndReached事件触发，准备播放下一个");
                        stopProgressUpdate();
                        viewModel.playNext();
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        loadingIndicator.setVisibility(View.GONE);
                        handleVlcError();
                        break;
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "VLC初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            useVlc = false; // 回退到ExoPlayer
        }
    }

    private void initExoPlayer() {
        if (exoPlayer == null) {
            // 初始化ExoPlayer，配置更好的解码器和渲染策略
            // 针对模拟器和真机使用不同的缓冲策略
            androidx.media3.exoplayer.DefaultLoadControl loadControl;
            
            if (isEmulator) {
                // 模拟器：极致激进的启动策略
                loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        5000,   // minBufferMs: 最小缓冲5秒
                        15000,  // maxBufferMs: 最大缓冲15秒
                        200,    // bufferForPlaybackMs: 仅缓冲0.2秒即开始播放
                        800     // bufferForPlaybackAfterRebufferMs: 重新缓冲0.8秒
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBackBuffer(0, false) // 禁用后向缓冲
                    .build();
                android.util.Log.d("PlaybackActivity", "[PLAYBACK] 使用模拟器优化配置：超低延迟启动");
            } else {
                // 真机：平衡的策略
                loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        10000,  // minBufferMs: 最小缓冲10秒
                        30000,  // maxBufferMs: 最大缓冲30秒
                        500,    // bufferForPlaybackMs: 开始播放前缓冲0.5秒
                        1500    // bufferForPlaybackAfterRebufferMs: 重新缓冲后需要1.5秒
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            }
            
            // 配置解码器选择器：智能选择策略
            // 核心改进：对于HEVC(H.265)，优先使用c2.android.hevc.decoder软件解码器
            // c2.android.hevc.decoder支持广泛的HEVC profile，兼容性好
            // 硬件解码器经常不支持某些profile level，VLC作为最终后备方案
            androidx.media3.exoplayer.mediacodec.MediaCodecSelector mediaCodecSelector =
                (mimeType, requiresSecureDecoder, audioContentType) -> {
                    List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> decoderInfos =
                        androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                            .getDecoderInfos(mimeType, requiresSecureDecoder, audioContentType);
                    
                    if (decoderInfos.isEmpty()) {
                        android.util.Log.w("PlaybackActivity", "[DECODER] 解码器选择: 没有找到 " + mimeType + " 的解码器");
                        return decoderInfos;
                    }
                    
                    // 打印所有可用解码器
                    android.util.Log.d("PlaybackActivity", "[DECODER] === 解码器选择开始 (" + mimeType + ") ===");
                    for (int i = 0; i < decoderInfos.size(); i++) {
                        androidx.media3.exoplayer.mediacodec.MediaCodecInfo info = decoderInfos.get(i);
                        android.util.Log.d("PlaybackActivity", "[DECODER]   可用解码器[" + i + "]: " + info.name +
                            " (软解=" + isSoftwareDecoder(info.name) + ", 硬解=" + isHardwareDecoder(info.name) + ")");
                    }
                    
                    // 判断是否为HEVC/H.265格式
                    boolean isHevc = mimeType != null &&
                        (mimeType.contains("hevc") || mimeType.contains("hev1") || mimeType.contains("hvc1"));
                    
                    ArrayList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> reordered = new ArrayList<>(decoderInfos);
                    
                    if (isHevc) {
                        // HEVC策略：c2.android.hevc.decoder软解优先，然后是硬件解码器
                        // 原因：c2.android.hevc.decoder支持广泛的HEVC profile
                        // 硬件解码器经常不支持某些profile level
                        // VLC作为最终后备方案（在ExoPlayer失败时自动切换）
                        java.util.Collections.sort(reordered, (d1, d2) -> {
                            boolean d1IsC2Android = d1.name.toLowerCase(java.util.Locale.US).startsWith("c2.android.");
                            boolean d2IsC2Android = d2.name.toLowerCase(java.util.Locale.US).startsWith("c2.android.");
                            boolean d1IsSoftware = isSoftwareDecoder(d1.name);
                            boolean d2IsSoftware = isSoftwareDecoder(d2.name);
                            
                            // c2.android.hevc.decoder优先（支持广泛的HEVC profile）
                            if (d1IsC2Android && !d2IsC2Android) return -1;
                            if (!d1IsC2Android && d2IsC2Android) return 1;
                            
                            // 其他软件解码器次之
                            if (d1IsSoftware && !d2IsSoftware) return -1;
                            if (!d1IsSoftware && d2IsSoftware) return 1;
                            
                            // 同类型按名称排序
                            return d1.name.compareTo(d2.name);
                        });
                        android.util.Log.d("PlaybackActivity", "[DECODER] HEVC解码器策略: c2.android软解优先 > 硬解 > VLC后备");
                    } else {
                        // 非HEVC策略：硬解优先，软解备用
                        java.util.Collections.sort(reordered, (d1, d2) -> {
                            boolean d1IsSoftware = isSoftwareDecoder(d1.name);
                            boolean d2IsSoftware = isSoftwareDecoder(d2.name);
                            
                            // 硬解优先
                            if (!d1IsSoftware && d2IsSoftware) return -1;
                            if (d1IsSoftware && !d2IsSoftware) return 1;
                            
                            // 同类型按名称排序
                            return d1.name.compareTo(d2.name);
                        });
                        android.util.Log.d("PlaybackActivity", "[DECODER] 非HEVC解码器策略: 硬解优先");
                    }
                    
                    // 打印重排后的解码器顺序
                    android.util.Log.d("PlaybackActivity", "[DECODER] 重排后解码器顺序:");
                    for (int i = 0; i < reordered.size(); i++) {
                        android.util.Log.d("PlaybackActivity", "[DECODER]   [" + i + "] " + reordered.get(i).name);
                    }
                    android.util.Log.d("PlaybackActivity", "[DECODER] === 解码器选择结束 ===");
                        
                    return reordered;
                };
            android.util.Log.d("PlaybackActivity", "[DECODER] ExoPlayer: 智能解码器选择策略已启用 (HEVC: c2.android软解优先)");

            // 优化渲染器工厂
            // 使用ON模式，当扩展解码器可用时使用，否则回退到内置解码器
            androidx.media3.exoplayer.DefaultRenderersFactory renderersFactory =
                new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                    // 使用ON模式，启用扩展解码器但不强制优先
                    .setExtensionRendererMode(
                        androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    )
                    .setMediaCodecSelector(mediaCodecSelector)
                    .setEnableDecoderFallback(true)  // 关键：启用解码器降级
                    .setAllowedVideoJoiningTimeMs(2000); // 进一步减少视频连接时间

            // 在真机上启用异步队列可以提高性能，但在模拟器上可能导致问题
            if (!isEmulator) {
                renderersFactory.forceEnableMediaCodecAsynchronousQueueing();
            }
            
            // 配置自定义 DataSource.Factory 以添加百度网盘所需的请求头
            // 关键修复：使用与RetrofitClient一致的User-Agent "pan.baidu.com"
            // 百度网盘API会对User-Agent进行校验，不一致会导致403 Forbidden
            String userAgent = "pan.baidu.com";
            
            android.util.Log.d("PlaybackActivity", "ExoPlayer User-Agent set to: " + userAgent);
            
            androidx.media3.datasource.DefaultHttpDataSource.Factory httpDataSourceFactory =
                new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setConnectTimeoutMs(15000) // 减少连接超时到15秒
                    .setReadTimeoutMs(15000)    // 减少读取超时到15秒
                    .setAllowCrossProtocolRedirects(true)
                    .setKeepPostFor302Redirects(true);
            
            // 额外添加默认请求头，进一步确保兼容性
            java.util.Map<String, String> defaultRequestProperties = new java.util.HashMap<>();
            defaultRequestProperties.put("User-Agent", userAgent);
            // 有些情况下可能还需要Referer
            // defaultRequestProperties.put("Referer", "https://pan.baidu.com/");
            httpDataSourceFactory.setDefaultRequestProperties(defaultRequestProperties);
            
            // 使用带带宽测量的DataSource，有助于ExoPlayer调整缓冲策略
            androidx.media3.exoplayer.upstream.DefaultBandwidthMeter bandwidthMeter =
                new androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(this).build();
                
            androidx.media3.datasource.DefaultDataSource.Factory dataSourceFactory =
                new androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
                    .setTransferListener(bandwidthMeter);
            
            exoPlayer = new ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                    new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(dataSourceFactory)
                )
                .build();
            
            playerView.setPlayer(exoPlayer);
            
            // 设置视频缩放模式为自适应
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            
            // 不保持内容在播放器重置时不变，确保切换视频时清除上一帧
            playerView.setKeepContentOnPlayerReset(false);
            
            // 监听播放器状态变化
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    // 重置准备标志位
                    isPreparingExoPlayer = false;
                    
                    android.util.Log.e("PlaybackActivity", "[ERROR] ======== ExoPlayer 错误 ========");
                    android.util.Log.e("PlaybackActivity", "[ERROR] 错误信息: " + error.getMessage());
                    android.util.Log.e("PlaybackActivity", "[ERROR] 错误代码: " + error.errorCode);
                    
                    // 获取根本原因
                    Throwable cause = error.getCause();
                    if (cause != null) {
                        android.util.Log.e("PlaybackActivity", "[ERROR] 根本原因: " + cause.getClass().getName() + ": " + cause.getMessage());
                        
                        // 检查是否为MediaCodecRenderer.DecoderInitializationException
                        // 这个错误表示解码器初始化失败，通常是profile level不支持
                        String causeStr = cause.toString().toLowerCase();
                        if (causeStr.contains("nosupport") ||
                            causeStr.contains("profilelevel") ||
                            causeStr.contains("codec.profilelevel") ||
                            causeStr.contains("hev1") ||
                            causeStr.contains("hevc")) {
                            android.util.Log.e("PlaybackActivity", "[ERROR] ⚠️ 检测到HEVC Profile Level不支持错误！");
                            android.util.Log.e("PlaybackActivity", "[ERROR] ⚠️ 直接切换到VLC播放器（不重试）");
                            // 直接切换到VLC，不重试
                            switchToVlcImmediately();
                            return;
                        }
                    }
                    
                    // 特别处理解码器错误
                    if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                        android.util.Log.e("PlaybackActivity", "[ERROR] ⚠️ 解码器错误，可能是不支持的视频格式");
                        // 对于解码器错误，直接切换VLC而不是重试
                        switchToVlcImmediately();
                        return;
                    }
                    
                    android.util.Log.e("PlaybackActivity", "[ERROR] ================================");
                    handleExoPlayerError();
                }
                
                @Override
                public void onVideoSizeChanged(androidx.media3.common.VideoSize videoSize) {
                    int width = videoSize.width;
                    int height = videoSize.height;
                    android.util.Log.d("PlaybackActivity", "[PLAYBACK] ExoPlayer 视频尺寸: " + width + "x" + height);
                    
                    // 根据视频是横屏还是竖屏设置不同的缩放模式
                    if (width >= height) {
                        // 横屏视频：使用 ZOOM 模式填满屏幕 (CenterCrop)
                        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                        android.util.Log.d("PlaybackActivity", "[PLAYBACK] 横屏视频，使用 RESIZE_MODE_ZOOM (CenterCrop)");
                    } else {
                        // 竖屏视频：使用 FIT 模式保持比例 (FitCenter，高度占满)
                        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
                        android.util.Log.d("PlaybackActivity", "[PLAYBACK] 竖屏视频，使用 RESIZE_MODE_FIT (FitCenter)");
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLastState = currentTime - lastStateChangeTime;
                    
                    // 当ExoPlayer进入READY或ENDED状态时，重置准备标志位
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        isPreparingExoPlayer = false;
                    }
                    
                    String stateName;
                    switch (playbackState) {
                        case Player.STATE_IDLE:
                            stateName = "IDLE";
                            break;
                        case Player.STATE_BUFFERING:
                            stateName = "BUFFERING";
                            break;
                        case Player.STATE_READY:
                            stateName = "READY";
                            break;
                        case Player.STATE_ENDED:
                            stateName = "ENDED";
                            break;
                        default:
                            stateName = "UNKNOWN";
                    }
                    android.util.Log.d("PlaybackActivity", "[PLAYBACK] ExoPlayer state changed: " + stateName +
                        " (from " + lastExoPlayerState + " after " + timeSinceLastState + "ms)");
                    
                    // 检测异常状态转换：BUFFERING -> ENDED (中间没有 READY)
                    if (lastExoPlayerState == Player.STATE_BUFFERING && playbackState == Player.STATE_ENDED) {
                        // 如果状态转换非常快，或者在BUFFERING后立即ENDED而没有经历READY状态
                        android.util.Log.w("PlaybackActivity", "[ERROR] ⚠️ 检测到异常状态转换: BUFFERING -> ENDED (可能解码失败)");
                        
                        exoBufferingToEndedCount++;
                        android.util.Log.w("PlaybackActivity", "[ERROR] 异常转换计数: " + exoBufferingToEndedCount +
                            "/" + MAX_BUFFERING_TO_ENDED_RETRIES);
                        
                        if (exoBufferingToEndedCount >= MAX_BUFFERING_TO_ENDED_RETRIES) {
                            android.util.Log.e("PlaybackActivity", "[ERROR] ❌ 连续多次解码失败，判定为格式不支持，切换到VLC");
                            switchToVlcImmediately();
                            return; // 阻止后续的 playNext() 调用
                        } else {
                            // 如果次数还不够，尝试重新prepare一次，或者让它继续playNext（如果是真的结束）
                            // 这里我们做一个检查：如果是真正的播放结束，应该播放了比较长的时间
                            // 如果是刚开始播放就ENDED，那肯定是异常
                            long duration = exoPlayer.getDuration();
                            long position = exoPlayer.getCurrentPosition();
                            
                            android.util.Log.d("PlaybackActivity", "[PLAYBACK] 播放位置: " + position + "/" + duration);
                            
                            // 如果播放位置接近0，说明还没开始播放就结束了
                            if (position < 1000 && duration > 5000) {
                                android.util.Log.w("PlaybackActivity", "[ERROR] ⚠️ 视频未播放即结束，可能是解码器兼容性问题");
                                // 这种情况下，不要立即调用playNext，而是增加计数并尝试切换VLC
                                // 如果是第一次，我们可以让它重试一次（也许是网络波动）
                                // 但根据日志，这种情况通常是解码失败
                                if (exoBufferingToEndedCount == 1) {
                                    // 第一次，记录警告，但如果只有一个文件，playNext会导致无限循环
                                    // 检查播放列表大小
                                    int playlistSize = 0;
                                    if (viewModel.getPlayList().getValue() != null) {
                                        playlistSize = viewModel.getPlayList().getValue().size();
                                    }
                                    
                                    if (playlistSize <= 1) {
                                        // 只有一个文件，必须防止死循环
                                        android.util.Log.e("PlaybackActivity", "[ERROR] 单文件播放列表，防止无限重试，直接切换VLC");
                                        switchToVlcImmediately();
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    
                    // 更新上一次状态
                    lastExoPlayerState = playbackState;
                    lastStateChangeTime = currentTime;
                    
                    if (playbackState == Player.STATE_READY) {
                        long readyTime = System.currentTimeMillis();
                        android.util.Log.d("PlaybackActivity", "[PLAYBACK] ExoPlayer is ready, hiding loading indicator");
                        android.util.Log.d("PlaybackActivity", "[PLAYBACK] 从prepare到READY的总耗时: " +
                            (readyTime - lastPrepareTime) + "ms");
                        
                        // 成功进入READY状态，重置异常转换计数
                        exoBufferingToEndedCount = 0;
                        
                        // 检查视频轨道信息
                        if (exoPlayer != null) {
                            androidx.media3.common.Tracks tracks = exoPlayer.getCurrentTracks();
                            boolean hasVideo = false;
                            boolean hasAudio = false;
                            
                            for (androidx.media3.common.Tracks.Group trackGroup : tracks.getGroups()) {
                                if (trackGroup.getType() == androidx.media3.common.C.TRACK_TYPE_VIDEO && trackGroup.isSelected()) {
                                    hasVideo = true;
                                    android.util.Log.d("PlaybackActivity", "[PLAYBACK] ✓ 检测到视频轨道");
                                }
                                if (trackGroup.getType() == androidx.media3.common.C.TRACK_TYPE_AUDIO && trackGroup.isSelected()) {
                                    hasAudio = true;
                                    android.util.Log.d("PlaybackActivity", "[PLAYBACK] ✓ 检测到音频轨道");
                                }
                            }
                            
                            android.util.Log.d("PlaybackActivity", "[PLAYBACK] 视频轨道: " + hasVideo + ", 音频轨道: " + hasAudio);
                            
                            // 如果有音频但没有视频，可能是渲染问题
                            if (hasAudio && !hasVideo) {
                                android.util.Log.w("PlaybackActivity", "[PLAYBACK] ⚠️ 警告：检测到音频但没有视频轨道");
                            }
                        }
                        
                        loadingIndicator.setVisibility(View.GONE);
                        updatePlayPauseButton(true);
                        updateProgress();
                        // ExoPlayer 成功播放，重置错误计数
                        exoErrorCount = 0;
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        android.util.Log.d("PlaybackActivity", "[PLAYBACK] ExoPlayer is buffering, showing loading indicator");
                        loadingIndicator.setVisibility(View.VISIBLE);
                    } else if (playbackState == Player.STATE_ENDED) {
                        // 只有在没有触发异常处理的情况下才播放下一个
                        android.util.Log.d("PlaybackActivity", "[PLAYBACK] ExoPlayer playback ended, playing next");
                        viewModel.playNext();
                    }
                }
                
                @Override
                public void onRenderedFirstFrame() {
                    android.util.Log.d("PlaybackActivity", "[PLAYBACK] ✓ ExoPlayer 渲染了第一帧视频");
                }
            });
        }
    }

    /**
     * 判断是否为软件解码器
     *
     * 修正后的判断逻辑：
     * - c2.android.* 开头的解码器是软件解码器
     * - FFmpeg 解码器是软件解码器
     * - OMX.google.* 开头的解码器是硬件解码器（不是软件解码器）
     */
    private boolean isSoftwareDecoder(String name) {
        if (name == null) return false;
        String lowerName = name.toLowerCase(java.util.Locale.US);
        // c2.android.* 开头的是 Android 软件解码器
        if (lowerName.startsWith("c2.android.")) {
            return true;
        }
        // FFmpeg 解码器是纯软件解码
        if (lowerName.contains("ffmpeg")) {
            return true;
        }
        // OMX.google.* 开头的是硬件解码器（不是软件解码器）
        // 注意：OMX.google.* 是 Google 提供的硬件解码器接口
        return false;
    }

    /**
     * 判断是否为硬件解码器
     *
     * 硬件解码器包括：
     * - OMX.google.* 开头的解码器（Google 硬件解码器）
     * - 包含芯片厂商标识的解码器（qcom, mtk, hisi, exynos, qti）
     */
    private boolean isHardwareDecoder(String name) {
        if (name == null) return false;
        String lowerName = name.toLowerCase(java.util.Locale.US);
        // OMX.google.* 开头的是 Google 硬件解码器
        if (lowerName.startsWith("omx.google.")) {
            return true;
        }
        // 硬件解码器通常包含芯片厂商标识
        if (lowerName.contains("qcom") ||    // 高通
            lowerName.contains("mtk") ||     // 联发科
            lowerName.contains("hisi") ||    // 华为海思
            lowerName.contains("exynos") ||  // 三星
            lowerName.contains("qti")) {     // 高通新一代
            return true;
        }
        return false;
    }

    /**
     * 检测视频文件是否为HEVC/H.265格式
     *
     * HEVC视频优先使用VLC播放器，因为VLC内置了FFmpeg解码器
     * 支持更广泛的HEVC profile和level
     *
     * @param fileInfo 文件信息
     * @return true表示是HEVC格式，false表示不是或无法确定
     */
    private boolean isHevcVideo(@Nullable FileInfo fileInfo) {
        if (fileInfo == null) {
            return false;
        }
        
        // 方法1：通过文件扩展名判断（快速判断）
        String fileName = fileInfo.getServerFilename();
        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase(java.util.Locale.US);
            // HEVC/H.265常见扩展名
            if (lowerFileName.endsWith(".hevc") ||
                lowerFileName.endsWith(".h265") ||
                lowerFileName.endsWith(".265")) {
                android.util.Log.d("PlaybackActivity", "[HEVC] 通过文件扩展名检测到HEVC视频: " + fileName);
                return true;
            }
        }
        
        // 方法2：通过文件路径判断
        String filePath = fileInfo.getPath();
        if (filePath != null) {
            String lowerPath = filePath.toLowerCase(java.util.Locale.US);
            if (lowerPath.contains(".hevc") ||
                lowerPath.contains(".h265") ||
                lowerPath.contains(".265")) {
                android.util.Log.d("PlaybackActivity", "[HEVC] 通过文件路径检测到HEVC视频: " + filePath);
                return true;
            }
        }
        
        // 方法3：通过MediaMetadataRetriever获取实际编码格式（更准确但较慢）
        // 注意：这个方法需要访问文件，对于网络URL可能不适用
        // 在实际播放时，ExoPlayer会提供mimeType信息，这里只做初步判断
        android.util.Log.d("PlaybackActivity", "[HEVC] 未检测到HEVC视频: " + fileName);
        return false;
    }

    /**
     * 立即切换到VLC播放器（用于解码器不兼容的情况）
     * 这个方法跳过重试逻辑，因为解码器不兼容重试没有意义
     */
    private void switchToVlcImmediately() {
        // 检查Activity是否已销毁，如果已销毁则不执行重试逻辑
        if (isActivityDestroyed) {
            android.util.Log.d("PlaybackActivity", "[SWITCH] switchToVlcImmediately: Activity已销毁，跳过切换");
            return;
        }
        
        // 防止重复切换
        if (hasSwitchedToVlc) {
            android.util.Log.w("PlaybackActivity", "[SWITCH] 已经切换到VLC，跳过重复切换");
            return;
        }
        
        android.util.Log.d("PlaybackActivity", "[SWITCH] === 立即切换到VLC播放器 ===");
        loadingIndicator.setVisibility(View.GONE);
        
        // 设置标志位，阻止后续的ExoPlayer准备
        hasSwitchedToVlc = true;
        isPreparingExoPlayer = false;
        
        // 停止 ExoPlayer
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        
        // 切换到 VLC
        useVlc = true;
        exoErrorCount = 0;
        updatePlayerIndicator();
        
        Toast.makeText(this, "ExoPlayer不支持此视频格式，切换到VLC", Toast.LENGTH_SHORT).show();
        
        // 重新尝试播放
        if (currentMediaUrl != null) {
            android.util.Log.d("PlaybackActivity", "[SWITCH] 使用VLC重新播放: " + currentMediaUrl);
            playVideoWithUrl(currentMediaUrl);
        } else {
            android.util.Log.w("PlaybackActivity", "[SWITCH] 没有可用的媒体URL，跳到下一个");
            viewModel.playNext();
        }
    }

    /**
     * 处理 ExoPlayer 播放错误，尝试切换到 VLC
     * 注意：解码器错误（如HEVC profile不支持）应该使用 switchToVlcImmediately()
     */
    private void handleExoPlayerError() {
        // 检查Activity是否已销毁，如果已销毁则不执行重试逻辑
        if (isActivityDestroyed) {
            android.util.Log.d("PlaybackActivity", "[ERROR] handleExoPlayerError: Activity已销毁，跳过错误处理");
            return;
        }
        
        // 防止重复切换
        if (hasSwitchedToVlc) {
            android.util.Log.w("PlaybackActivity", "[ERROR] 已经切换到VLC，跳过重复错误处理");
            return;
        }
        
        exoErrorCount++;
        loadingIndicator.setVisibility(View.GONE);
        
        android.util.Log.d("PlaybackActivity", "[ERROR] ExoPlayer错误处理, 错误次数: " + exoErrorCount);
        
        // 不再重试，直接切换到VLC
        // 原因：大多数ExoPlayer错误重试没有意义，浪费用户时间
        android.util.Log.d("PlaybackActivity", "[SWITCH] ExoPlayer失败，直接切换到VLC");
        Toast.makeText(this, "ExoPlayer播放失败，切换到VLC播放器", Toast.LENGTH_SHORT).show();
        
        // 设置标志位，阻止后续的ExoPlayer准备
        hasSwitchedToVlc = true;
        isPreparingExoPlayer = false;
        
        // 释放 ExoPlayer
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        
        // 切换到 VLC
        useVlc = true;
        exoErrorCount = 0;
        updatePlayerIndicator();
        
        // 重新尝试播放
        if (currentMediaUrl != null) {
            playVideoWithUrl(currentMediaUrl);
        } else {
            viewModel.playNext();
        }
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        
        // 初始化播放器指示器（在 viewModel 创建后）
        updatePlayerIndicator();
        
        // 观察播放列表
        viewModel.getPlayList().observe(this, files -> {
            if (files != null && !files.isEmpty()) {
                playCurrentFile();
            }
        });
        
        // 观察当前索引
        viewModel.getCurrentIndex().observe(this, index -> {
            if (index != null) {
                playCurrentFile();
            }
        });
        
        // 观察播放模式
        viewModel.getPlayMode().observe(this, mode -> {
            if (mode != null) {
                updatePlayModeIcon();
            }
        });
        
        // 观察播放状态
        viewModel.getIsPlaying().observe(this, isPlaying -> {
            if (isPlaying != null) {
                handlePlayPause(isPlaying);
            }
        });
        
        // 观察地点信息
        viewModel.getCurrentLocation().observe(this, location -> {
            android.util.Log.d("PlaybackActivity", "CurrentLocation观察者触发: " + location);
            Boolean showLocation = viewModel.getShowLocation().getValue();
            android.util.Log.d("PlaybackActivity", "ShowLocation值: " + showLocation);
            
            if (showLocation != null && showLocation && location != null && !location.isEmpty()) {
                android.util.Log.d("PlaybackActivity", "显示位置信息: " + location);
                showLocationToast(location);
            } else {
                android.util.Log.d("PlaybackActivity", "隐藏位置信息");
                tvLocation.setVisibility(View.GONE);
            }
        });
        
        // 观察是否显示地点
        viewModel.getShowLocation().observe(this, showLocation -> {
            android.util.Log.d("PlaybackActivity", "ShowLocation观察者触发: " + showLocation);
            if (showLocation != null && !showLocation) {
                android.util.Log.d("PlaybackActivity", "设置关闭，隐藏位置");
                tvLocation.setVisibility(View.GONE);
            } else if (showLocation != null && showLocation) {
                // 当设置打开时，检查是否有位置信息
                String location = viewModel.getCurrentLocation().getValue();
                if (location != null && !location.isEmpty()) {
                    android.util.Log.d("PlaybackActivity", "设置打开，显示位置: " + location);
                    showLocationToast(location);
                }
            }
        });
        
        // 观察准备好的媒体URL
        viewModel.getPreparedMediaUrl().observe(this, url -> {
            if (url != null) {
                // 停止之前的加载指示器
                loadingIndicator.setVisibility(View.GONE);
                
                FileInfo currentFile = viewModel.getCurrentFile();
                if (currentFile == null) return;
                
                if (currentFile.isVideo()) {
                    playVideoWithUrl(url);
                } else if (currentFile.isImage()) {
                    playImageWithUrl(url);
                }
                
                // 获取地点信息（使用实际的媒体URL）
                getLocationForFile(currentFile, url);
                
                // 临时添加：测试反向地理编码功能
                // LocationUtils.testReverseGeocode(this);
                
                // 保存到播放历史（存储单个文件信息）
                new Thread(() -> {
                    PlaybackHistory history = new PlaybackHistory();
                    history.setFilePath(currentFile.getPath()); // 文件完整路径
                    history.setFileName(currentFile.getServerFilename()); // 文件名
                    history.setFsId(currentFile.getFsId()); // 文件ID
                    
                    // 设置媒体类型
                    int mediaType = 3; // 默认混合
                    if (currentFile.isVideo()) {
                        mediaType = 2; // 视频
                    } else if (currentFile.isImage()) {
                        mediaType = 1; // 图片
                    }
                    history.setMediaType(mediaType);
                    
                    history.setLastPlayTime(System.currentTimeMillis());
                    history.setCreateTime(System.currentTimeMillis());
                    
                    // 保存缩略图URL
                    if (currentFile.getThumbs() != null) {
                        // 优先使用url3（最大尺寸），其次url2，最后url1
                        String thumbnailUrl = currentFile.getThumbs().getUrl3();
                        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                            thumbnailUrl = currentFile.getThumbs().getUrl2();
                        }
                        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                            thumbnailUrl = currentFile.getThumbs().getUrl1();
                        }
                        history.setThumbnailUrl(thumbnailUrl);
                    }
                    
                    historyRepository.insert(history);
                }).start();
            } else {
                // 加载失败
                loadingIndicator.setVisibility(View.GONE);
                // 可以显示错误提示
            }
        });
        
        // 初始化Handler
        imageHandler = new Handler();
        controlsHandler = new Handler();
        progressHandler = new Handler();
        
        locationHandler = new Handler();
        locationRunnable = () -> {
            // 10秒后淡出地点信息
            tvLocation.animate()
                    .alpha(0f)
                    .setDuration(1000)
                    .withEndAction(() -> tvLocation.setVisibility(View.GONE))
                    .start();
        };
    }

    private void initData() {
        historyRepository = new PlaybackHistoryRepository(getApplication());
        authRepository = new com.baidu.gallery.car.auth.AuthRepository(this);
        
        // 检查是否有传入的历史记录ID
        long historyId = getIntent().getLongExtra("historyId", -1);
        if (historyId != -1) {
            // 优先尝试使用直接传递的fsId（如果有）
            long directFsId = getIntent().getLongExtra("fsId", 0);
            String directFilePath = getIntent().getStringExtra("filePath");
            String directFileName = getIntent().getStringExtra("fileName");
            
            if (directFsId > 0 && directFilePath != null) {
                android.util.Log.d("PlaybackActivity", "直接使用Intent传递的文件信息: " + directFileName + ", fsId=" + directFsId);
                playSingleFileFallback(directFilePath, directFileName, directFsId);
                return;
            }

            // 从历史记录加载
            historyRepository.getHistoryById(historyId).observe(this, history -> {
                if (history != null) {
                    String filePath = history.getFilePath();
                    long fsId = history.getFsId();
                    android.util.Log.d("PlaybackActivity", "从历史记录加载单个文件: " + filePath + ", fsId=" + fsId);
                    
                    // 获取访问令牌
                    String accessToken = authRepository.getAccessToken();
                    if (accessToken == null || accessToken.isEmpty()) {
                        android.util.Log.e("PlaybackActivity", "访问令牌为空，无法加载历史记录");
                        finish();
                        return;
                    }
                    
                    // 从文件路径提取文件名
                    int lastSlashIndex = filePath.lastIndexOf('/');
                    String targetFileName;
                    if (lastSlashIndex >= 0) {
                        targetFileName = filePath.substring(lastSlashIndex + 1);
                    } else {
                        targetFileName = filePath.startsWith("/") ? filePath.substring(1) : filePath;
                    }

                    // 直接播放单个文件
                    android.util.Log.d("PlaybackActivity", "从历史记录直接播放文件: " + targetFileName + ", fsId=" + fsId);
                    
                    runOnUiThread(() -> playSingleFileFallback(filePath, targetFileName, fsId));
                } else {
                    android.util.Log.e("PlaybackActivity", "未找到ID为 " + historyId + " 的历史记录");
                    Toast.makeText(this, "未找到播放记录", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        } else {
            // 检查是否有传入的播放列表ID
            long playlistDatabaseId = getIntent().getLongExtra("playlistDatabaseId", -1);
            if (playlistDatabaseId != -1) {
                // 从数据库加载播放列表（异步）
                PlaylistRepository playlistRepository = new PlaylistRepository(getApplication());
                
                // 在后台线程中执行数据库操作
                new Thread(() -> {
                    try {
                        Playlist playlist = playlistRepository.getPlaylistByIdSync(playlistDatabaseId);
                        
                        if (playlist != null) {
                            // 更新最后播放时间
                            playlist.setLastPlayedAt(System.currentTimeMillis());
                            playlistRepository.updatePlaylist(playlist, null, null);
                            
                            // 从数据库获取播放列表项
                            List<PlaylistItem> playlistItems = playlistRepository.getPlaylistItemsSync(playlistDatabaseId);
                            
                            // 转换为FileInfo列表（需要实时获取dlink）
                            List<FileInfo> files = new ArrayList<>();
                            for (PlaylistItem item : playlistItems) {
                                FileInfo fileInfo = new FileInfo();
                                fileInfo.setPath(item.getFilePath());
                                fileInfo.setServerFilename(item.getFileName());
                                fileInfo.setFsId(item.getFsId());
                                fileInfo.setSize(item.getFileSize());
                                fileInfo.setDlink(null); // 显式置空，强制使用prepareMediaUrl获取
                                
                                // 根据文件扩展名判断媒体类型
                                String fileName = item.getFileName().toLowerCase();
                                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                                    fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                                    fileName.endsWith(".bmp") || fileName.endsWith(".webp")) {
                                    fileInfo.setCategory(3); // 图片
                                } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                                           fileName.endsWith(".mkv") || fileName.endsWith(".mov") ||
                                           fileName.endsWith(".wmv") || fileName.endsWith(".flv") ||
                                           fileName.endsWith(".webm") || fileName.endsWith(".m4v")) {
                                    fileInfo.setCategory(1); // 视频
                                }
                                
                                files.add(fileInfo);
                            }
                            
                            android.util.Log.d("PlaybackActivity", "从数据库加载播放列表: " + files.size() + " 个文件");
                            
                            if (!files.isEmpty()) {
                                // 切换回主线程更新UI
                                runOnUiThread(() -> {
                                    // 保存播放列表ID到ViewModel，用于更新播放进度
                                    viewModel.setPlaylistDatabaseId(playlistDatabaseId);
                                    
                                    // 设置播放列表，并强制根据当前播放模式重置初始索引
                                    viewModel.setPlayList(files, true);
                                    
                                    // 注意：不再手动设置startIndex，让setPlayList()根据播放模式自动处理
                                    // 倒序模式会自动从最后一个开始，顺序/随机模式会从第一个开始
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            android.widget.Toast.makeText(PlaybackActivity.this,
                                "加载播放列表失败", android.widget.Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }).start();
            } else {
                // 优先从缓存加载播放列表
                String playlistId = getIntent().getStringExtra("playlistId");
                List<FileInfo> files = null;
                
                if (playlistId != null) {
                    // 从缓存中获取并移除播放列表（一次性使用）
                    files = PlaylistCache.getInstance().getAndRemove(playlistId);
                }
                
                // 如果缓存中没有，则尝试从Intent中获取（兼容旧版本）
                if (files == null) {
                    files = getIntent().getParcelableArrayListExtra("files");
                }
                
                if (files != null && !files.isEmpty()) {
                    viewModel.setPlayList(files);
                    
                    // 设置起始播放位置
                    int startIndex = getIntent().getIntExtra("startIndex", 0);
                    if (startIndex >= 0 && startIndex < files.size()) {
                        viewModel.setCurrentIndex(startIndex);
                    }
                }
            }
        }
    }

    private void setupClickListeners() {
        // 播放模式切换
        ivPlayMode.setOnClickListener(v -> viewModel.togglePlayMode());
        
        // 上一个
        ivPrev.setOnClickListener(v -> viewModel.playPrevious());
        
        // 播放/暂停
        ivPlayPause.setOnClickListener(v -> viewModel.togglePlayPause());
        
        // 下一个
        ivNext.setOnClickListener(v -> viewModel.playNext());
        
        // 控制栏显示/隐藏
        View.OnClickListener controlsClickListener = v -> showControls();
        playerView.setOnClickListener(controlsClickListener);
        surfaceView.setOnClickListener(controlsClickListener);
        ivImageDisplay.setOnClickListener(controlsClickListener);
        
        // 进度条拖动
        seekbarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (isCurrentFileVideo()) {
                        long newTime = (long) progress;
                        if (useVlc && vlcMediaPlayer != null) {
                            vlcMediaPlayer.setTime(newTime);
                        } else if (exoPlayer != null) {
                            exoPlayer.seekTo(newTime);
                        }
                        tvCurrentTime.setText(DateUtils.formatElapsedTime(newTime / 1000));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopProgressUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startProgressUpdate();
            }
        });
    }

    /**
     * 播放当前文件
     *
     * 解码器优先级策略：
     * 1. 对于HEVC视频：VLC播放器（内置FFmpeg，最高优先级）
     * 2. 对于其他视频：ExoPlayer（使用c2.android.hevc.decoder软解作为备选）
     * 3. 失败时自动切换到VLC作为后备方案
     */
    private void playCurrentFile() {
        FileInfo currentFile = viewModel.getCurrentFile();
        if (currentFile == null) {
            android.util.Log.e("PlaybackActivity", "playCurrentFile: currentFile is null");
            return;
        }
        
        // 重置错误计数
        exoErrorCount = 0;
        vlcErrorCount = 0;
        
        // 重置状态转换检测
        lastExoPlayerState = Player.STATE_IDLE;
        exoBufferingToEndedCount = 0;
        
        // 重置ExoPlayer准备标志位（新文件重新开始）
        isPreparingExoPlayer = false;
        hasSwitchedToVlc = false;
        
        // 检测视频格式，决定播放器策略
        if (currentFile.isVideo()) {
            // 使用调度器选择策略
            currentPlayerStrategy = playerScheduler.selectStrategy(currentFile, null, null);
            
            if (currentPlayerStrategy.getName().equals("VLC")) {
                android.util.Log.d("PlaybackActivity", "[HEVC] ★★★ 调度器选择使用VLC播放器 ★★★");
                useVlc = true;
                hasSwitchedToVlc = true; // 标记已使用VLC，防止后续误切换
            } else {
                android.util.Log.d("PlaybackActivity", "[DECODER] 调度器选择使用ExoPlayer");
                useVlc = false;
            }
        }

        android.util.Log.d("PlaybackActivity", "playCurrentFile: " + currentFile.getServerFilename() +
            ", path=" + currentFile.getPath() +
            ", fsId=" + currentFile.getFsId() +
            ", category=" + currentFile.getCategory() +
            ", hasDlink=" + (currentFile.getDlink() != null) +
            ", useVlc=" + useVlc +
            ", strategy=" + (currentPlayerStrategy != null ? currentPlayerStrategy.getName() : "null"));

        // 更新文件名
        tvFileName.setText(currentFile.getServerFilename());
        
        // 停止之前的播放
        stopCurrentPlayback();
        
        // 立即隐藏之前的地点信息
        tvLocation.setVisibility(View.GONE);
        tvLocation.setAlpha(1.0f); // 重置透明度
        if (locationHandler != null && locationRunnable != null) {
            locationHandler.removeCallbacks(locationRunnable);
        }
        
        // 显示加载指示器
        loadingIndicator.setVisibility(View.VISIBLE);
        
        // 准备媒体URL
        String accessToken = authRepository.getAccessToken();
        android.util.Log.d("PlaybackActivity", "准备获取媒体URL, accessToken=" +
            (accessToken != null ? accessToken.substring(0, 10) + "..." : "null"));
        viewModel.prepareMediaUrl(accessToken, currentFile);
    }

    /**
     * 使用URL播放视频
     * 会记录当前播放的URL，用于播放器切换时重试
     */
    private void playVideoWithUrl(String videoUrl) {
        // 保存当前播放的URL，用于在播放器之间切换时重新播放
        currentMediaUrl = videoUrl;
        
        // 双重保险：强制使用最新的Access Token更新URL
        // 这能解决因Token过期或ViewModel缓存导致的403权限错误
        if (authRepository != null) {
            String accessToken = authRepository.getAccessToken();
            if (accessToken != null && !accessToken.isEmpty()) {
                if (videoUrl.contains("access_token=")) {
                    // 替换现有的token
                    videoUrl = videoUrl.replaceAll("access_token=[^&]*", "access_token=" + accessToken);
                    android.util.Log.d("PlaybackActivity", "已强制更新视频URL中的Access Token");
                } else {
                    // 追加token
                    videoUrl += (videoUrl.contains("?") ? "&" : "?") + "access_token=" + accessToken;
                    android.util.Log.d("PlaybackActivity", "已追加Access Token到视频URL");
                }
            }
        }

        android.util.Log.d("PlaybackActivity", "playVideoWithUrl (Final): " + videoUrl);
        
        // 隐藏图片显示
        ivImageDisplay.setVisibility(View.GONE);
        
        // 重置背景为黑色（视频播放时不需要背景）
        ivBackground.setBackgroundColor(android.graphics.Color.BLACK);
        ivBackground.setImageBitmap(null);
        ivBackground.setVisibility(View.VISIBLE);
        
        updatePlayerIndicator();
        
        // 如果是HEVC视频，确保使用VLC
        FileInfo currentFile = viewModel.getCurrentFile();
        if (currentFile != null && isHevcVideo(currentFile) && !useVlc) {
             android.util.Log.w("PlaybackActivity", "[HEVC] 发现HEVC视频但未启用VLC，强制切换到VLC模式");
             useVlc = true;
             hasSwitchedToVlc = true;
             updatePlayerIndicator();
        }
        
        if (useVlc) {
            // 使用 VLC 播放
            surfaceView.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.GONE);
            
            if (vlcMediaPlayer != null && videoUrl != null && !videoUrl.isEmpty()) {
                android.util.Log.d("PlaybackActivity", "开始VLC播放: " + videoUrl);
                
                // 确保VLCVout已附加
                IVLCVout vout = vlcMediaPlayer.getVLCVout();
                if (!vout.areViewsAttached()) {
                    android.util.Log.d("PlaybackActivity", "VLCVout未附加，重新附加");
                    vout.setVideoView(surfaceView);
                    vout.setWindowSize(surfaceView.getWidth(), surfaceView.getHeight());
                    vout.attachViews();
                }
                
                Media media = new Media(libVLC, Uri.parse(videoUrl));
                // 启用硬件解码以提高性能，同时保留软件解码作为备选
                media.setHWDecoderEnabled(true, true);
                // 添加媒体选项
                media.addOption(":network-caching=1500"); // 减少网络缓存到1.5秒
                
                vlcMediaPlayer.setMedia(media);
                
                // 设置缩放为0，让VLC自动适应SurfaceView
                vlcMediaPlayer.setScale(0);
                // 不设置宽高比，让onNewLayout回调处理
                vlcMediaPlayer.setAspectRatio(null);
                
                android.util.Log.d("PlaybackActivity", "VLC Scale=0 (自动适应), AspectRatio=null");
                
                media.release();
                
                // 延迟播放，确保Surface准备完成
                new Handler().postDelayed(() -> {
                    // 检查Activity是否已销毁
                    if (isActivityDestroyed) {
                        android.util.Log.d("PlaybackActivity", "VLC延迟播放: Activity已销毁，跳过播放");
                        return;
                    }
                    if (vlcMediaPlayer != null) {
                        vlcMediaPlayer.play();
                        viewModel.setPlaying(true);
                        android.util.Log.d("PlaybackActivity", "VLC开始播放");

                        // 启动进度更新
                        startProgressUpdate();
                    }
                }, 100);
            }
        } else {
            // 使用 ExoPlayer 播放
            surfaceView.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            
            if (videoUrl != null && !videoUrl.isEmpty()) {
                // 检查是否已经切换到VLC，如果是则跳过ExoPlayer准备
                if (hasSwitchedToVlc) {
                    android.util.Log.w("PlaybackActivity", "已经切换到VLC，跳过ExoPlayer准备");
                    return;
                }
                
                // 检查是否正在准备中，防止重复调用
                if (isPreparingExoPlayer) {
                    android.util.Log.w("PlaybackActivity", "ExoPlayer正在准备中，跳过重复调用");
                    return;
                }
                
                long startTime = System.currentTimeMillis();
                android.util.Log.d("PlaybackActivity", "开始ExoPlayer准备");
                android.util.Log.d("PlaybackActivity", "完整视频URL: " + videoUrl);
                
                // 检查URL中是否包含access_token
                if (videoUrl.contains("access_token=")) {
                    android.util.Log.d("PlaybackActivity", "✓ URL中包含access_token参数");
                } else {
                    android.util.Log.e("PlaybackActivity", "✗ 警告：URL中缺少access_token参数！");
                }
                
                // 设置准备标志位
                isPreparingExoPlayer = true;
                
                // 清除之前的媒体项，防止上一个视频的帧残留
                exoPlayer.clearMediaItems();
                
                // 使用Builder方式构建MediaItem，确保URL参数正确传递
                // 这对于Media3 1.5.0很重要，直接fromUri可能导致参数丢失
                MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(android.net.Uri.parse(videoUrl))
                    .build();
                    
                exoPlayer.setMediaItem(mediaItem);
                
                // 记录prepare开始时间
                lastPrepareTime = System.currentTimeMillis();
                long prepareStartTime = lastPrepareTime;
                
                // 重置状态检测变量
                lastExoPlayerState = Player.STATE_IDLE;
                lastStateChangeTime = System.currentTimeMillis();
                
                exoPlayer.prepare();
                android.util.Log.d("PlaybackActivity", "ExoPlayer.prepare() 调用完成，耗时: " +
                    (System.currentTimeMillis() - prepareStartTime) + "ms");
                
                // 设置播放状态为true，确保自动播放
                viewModel.setPlaying(true);
                exoPlayer.setPlayWhenReady(true);
                
                android.util.Log.d("PlaybackActivity", "ExoPlayer准备完成，总耗时: " +
                    (System.currentTimeMillis() - startTime) + "ms");
                
                // 启动进度更新
                startProgressUpdate();
            }
        }
    }

    /**
     * 使用URL播放图片
     */
    private void playImageWithUrl(String imageUrl) {
        android.util.Log.d("PlaybackActivity", "playImageWithUrl: " + imageUrl);
        
        // 显示图片显示，隐藏视频播放器
        surfaceView.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        ivImageDisplay.setVisibility(View.VISIBLE);
        updatePlayerIndicator();
        
        // 加载图片
        if (imageUrl != null && !imageUrl.isEmpty()) {
            android.util.Log.d("PlaybackActivity", "开始加载图片: " + imageUrl);
            // 根据设置的特效加载图片
            ImageEffect effect = viewModel.getImageEffect().getValue();
            if (effect == null) {
                effect = ImageEffect.FADE;
            }
            
            // 如果是随机特效，每次显示图片时随机选择一种特效
            ImageEffect actualEffect = effect.getActualEffect();
            android.util.Log.d("PlaybackActivity", "图片特效: " + effect.getName() +
                (effect == ImageEffect.RANDOM ? " -> 实际特效: " + actualEffect.getName() : ""));

            // 取消当前正在进行的动画，避免与新动画冲突
            ivImageDisplay.animate().cancel();
            
            // 对于FADE效果使用Glide的CrossFade，其他效果不使用CrossFade避免冲突
            DrawableTransitionOptions transitionOptions;
            if (actualEffect == ImageEffect.FADE) {
                transitionOptions = DrawableTransitionOptions.withCrossFade(800);
            } else {
                // 其他效果使用更快的CrossFade或不使用
                transitionOptions = DrawableTransitionOptions.withCrossFade(300);
            }
            
            Glide.with(this)
                    .load(imageUrl)
                    .transition(transitionOptions)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, @Nullable Object model, @NonNull Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull android.graphics.drawable.Drawable resource, @NonNull Object model, @NonNull Target<android.graphics.drawable.Drawable> target, @NonNull com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            // 图片加载完成后应用动画
                            // 对于非FADE效果，需要先设置初始状态再开始动画
                            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                applyImageEffect(actualEffect);
                            }, actualEffect == ImageEffect.FADE ? 0 : 150);
                            
                            // 更新背景（主色调或毛玻璃效果）
                            updateImageBackground(resource);
                            return false;
                        }
                    })
                    .into(ivImageDisplay);
        }
        
        // 启动图片定时播放
        startImageDisplayTimer();
    }

    /**
     * 应用图片特效
     * 使用策略模式和工厂模式替代原来的switch-case语句
     * @param effect 图片特效类型
     */
    private void applyImageEffect(ImageEffect effect) {
        // 确保视图可见
        ivImageDisplay.setVisibility(View.VISIBLE);
        
        // 先重置到中性状态，避免残留的变换影响新动画
        ivImageDisplay.setScaleX(1.0f);
        ivImageDisplay.setScaleY(1.0f);
        ivImageDisplay.setTranslationX(0);
        ivImageDisplay.setTranslationY(0);
        ivImageDisplay.setRotation(0);
        ivImageDisplay.setAlpha(1.0f);
        
        // 使用工厂方法创建特效策略并应用
        // 注意：这里的effect已经是从ImageEffect.getActualEffect()获取的实际特效
        ImageEffectStrategy strategy = ImageEffectFactory.createEffectStrategy(effect);
        strategy.applyEffect(ivImageDisplay);
    }
    
    /**
     * 更新图片背景（主色调或毛玻璃效果）
     * 在图片加载完成后调用
     * 使用策略模式实现，方便扩展新的背景效果
     */
    private void updateImageBackground(@Nullable android.graphics.drawable.Drawable imageDrawable) {
        if (imageDrawable == null || ivBackground == null) {
            return;
        }
        
        // 获取当前图片URL用于缓存
        String imageUrl = viewModel.getPreparedMediaUrl().getValue();
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = "unknown";
        }
        
        // 从设置中获取背景模式
        // 0: 纯黑色背景, 1: 主色调背景, 2: 毛玻璃背景
        int backgroundMode = PreferenceUtils.getBackgroundMode(this);
        
        // 使用工厂方法创建背景策略并应用
        com.baidu.gallery.car.background.ImageBackgroundStrategy strategy =
            com.baidu.gallery.car.background.ImageBackgroundFactory.getStrategy(backgroundMode);
        strategy.applyBackground(this, ivBackground, imageUrl, imageDrawable);
    }
    

    /**
     * 启动图片显示定时器
     */
    private void startImageDisplayTimer() {
        // 检查Activity是否已销毁
        if (isActivityDestroyed) {
            android.util.Log.d("PlaybackActivity", "startImageDisplayTimer: Activity已销毁，跳过定时器启动");
            return;
        }
        
        // 移除之前的回调
        if (imageRunnable != null && imageHandler != null) {
            imageHandler.removeCallbacks(imageRunnable);
        }
        
        // 创建新的回调
        imageRunnable = () -> {
            // 检查Activity是否已销毁，如果已销毁则不执行
            if (isActivityDestroyed) {
                android.util.Log.d("PlaybackActivity", "startImageDisplayTimer: Activity已销毁，跳过播放下一张");
                return;
            }
            // 播放下一张图片
            viewModel.playNext();
        };
        
        // 获取显示时长
        Integer duration = viewModel.getImageDisplayDuration().getValue();
        if (duration == null) {
            duration = 5000; // 默认5秒
        }
        
        // 延迟执行
        if (imageHandler != null) {
            imageHandler.postDelayed(imageRunnable, duration);
        }
    }
    
    private void stopCurrentPlayback() {
        // 停止VLC
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.stop();
        }
        
        // 停止ExoPlayer
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems(); // 确保完全清除
        }
        
        // 重置准备标志位
        isPreparingExoPlayer = false;
        
        // 确保PlayerView不显示旧内容
        if (playerView != null) {
            // 这会触发快门显示，遮挡旧视频帧
            playerView.setPlayer(null);
            playerView.setPlayer(exoPlayer);
        }
        
        // 停止图片定时器
        if (imageRunnable != null) {
            imageHandler.removeCallbacks(imageRunnable);
        }
        
        stopProgressUpdate();
    }
    
    private void handlePlayPause(boolean isPlaying) {
        if (isCurrentFileVideo()) {
            if (useVlc) {
                if (vlcMediaPlayer != null) {
                    if (isPlaying) {
                        if (!vlcMediaPlayer.isPlaying()) vlcMediaPlayer.play();
                    } else {
                        if (vlcMediaPlayer.isPlaying()) vlcMediaPlayer.pause();
                    }
                }
            } else {
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(isPlaying);
                }
            }
        }
    }
    
    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) {
            ivPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            ivPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    private void updatePlayPauseButton() {
        boolean isPlaying = false;
        if (useVlc && vlcMediaPlayer != null) {
            isPlaying = vlcMediaPlayer.isPlaying();
        } else if (exoPlayer != null) {
            isPlaying = exoPlayer.isPlaying();
        }
        updatePlayPauseButton(isPlaying);
    }
    
    private void updatePlayModeIcon() {
        PlayMode mode = viewModel.getPlayMode().getValue();
        if (mode == null) mode = PlayMode.SEQUENTIAL;
        
        switch (mode) {
            case SINGLE:
                ivPlayMode.setImageResource(android.R.drawable.ic_menu_revert);
                break;
            case RANDOM:
                ivPlayMode.setImageResource(android.R.drawable.ic_menu_sort_alphabetically);
                break;
            case SEQUENTIAL:
            default:
                ivPlayMode.setImageResource(android.R.drawable.ic_menu_sort_by_size);
                break;
        }
    }
    
    private void showControls() {
        layoutControls.setVisibility(View.VISIBLE);
        
        // 移除之前的隐藏回调
        if (controlsRunnable != null) {
            controlsHandler.removeCallbacks(controlsRunnable);
        }
        
        // 创建新的隐藏回调
        controlsRunnable = this::hideControls;
        
        // 延迟隐藏
        controlsHandler.postDelayed(controlsRunnable, CONTROLS_HIDE_DELAY);
    }
    
    private void hideControls() {
        layoutControls.setVisibility(View.GONE);
    }

    /**
     * 显示浮动地点信息，并在几秒后自动隐藏
     */
    private void showLocationToast(String location) {
        // 移除之前的隐藏任务
        locationHandler.removeCallbacks(locationRunnable);
        
        // 设置内容并显示
        tvLocation.setText(location);
        tvLocation.setAlpha(1f);
        tvLocation.setVisibility(View.VISIBLE);
        
        // 延迟隐藏
        locationHandler.postDelayed(locationRunnable, LOCATION_HIDE_DELAY);
    }
    
    private void startProgressUpdate() {
        // 检查Activity是否已销毁
        if (isActivityDestroyed) {
            android.util.Log.d("PlaybackActivity", "startProgressUpdate: Activity已销毁，跳过进度更新启动");
            return;
        }
        
        if (progressRunnable != null && progressHandler != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查Activity是否已销毁，如果已销毁则停止更新
                if (isActivityDestroyed) {
                    android.util.Log.d("PlaybackActivity", "startProgressUpdate: Activity已销毁，停止进度更新");
                    return;
                }
                updateProgress();
                if (progressHandler != null) {
                    progressHandler.postDelayed(this, 1000);
                }
            }
        };
        
        if (progressHandler != null) {
            progressHandler.post(progressRunnable);
        }
    }
    
    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }
    
    private void updateProgress() {
        if (!isCurrentFileVideo()) return;
        
        long currentTime = 0;
        long totalTime = 0;
        
        if (useVlc && vlcMediaPlayer != null) {
            currentTime = vlcMediaPlayer.getTime();
            totalTime = vlcMediaPlayer.getLength();
        } else if (exoPlayer != null) {
            currentTime = exoPlayer.getCurrentPosition();
            totalTime = exoPlayer.getDuration();
            if (totalTime == androidx.media3.common.C.TIME_UNSET) {
                totalTime = 0;
            }
        }
        
        if (totalTime > 0) {
            seekbarProgress.setMax((int) totalTime);
            seekbarProgress.setProgress((int) currentTime);
            
            tvCurrentTime.setText(DateUtils.formatElapsedTime(currentTime / 1000));
            tvTotalTime.setText(DateUtils.formatElapsedTime(totalTime / 1000));
        }
    }
    
    private void updatePlayerIndicator() {
        if (tvPlayerIndicator == null) return;
        
        // 始终隐藏 UI 指示器，只在日志中记录当前使用的播放器
        tvPlayerIndicator.setVisibility(View.GONE);
        
        // 如果 viewModel 还未初始化，直接返回
        if (viewModel == null) return;
        
        if (!isCurrentFileVideo()) return;
        
        String playerName = currentPlayerStrategy != null ?
            currentPlayerStrategy.getName() :
            (useVlc ? "VLC Player" : "ExoPlayer");
        
        // 检查是否为HEVC视频，添加额外日志
        FileInfo currentFile = viewModel.getCurrentFile();
        boolean isHevc = currentFile != null && isHevcVideo(currentFile);
        
        if (isHevc) {
            android.util.Log.d("PlaybackActivity", "[SWITCH] 当前使用的播放器: " + playerName + " (HEVC视频 - VLC优先策略)");
        } else {
            android.util.Log.d("PlaybackActivity", "[SWITCH] 当前使用的播放器: " + playerName);
        }
    }

    private boolean isCurrentFileVideo() {
        FileInfo currentFile = viewModel.getCurrentFile();
        return currentFile != null && currentFile.isVideo();
    }
    
    private void getLocationForFile(@Nullable FileInfo file, @Nullable String mediaUrl) {
        if (file == null || mediaUrl == null) {
            android.util.Log.d("PlaybackActivity", "getLocationForFile: file or mediaUrl is null");
            return;
        }
        
        Boolean showLocation = viewModel.getShowLocation().getValue();
        if (showLocation == null || !showLocation) {
            android.util.Log.d("PlaybackActivity", "getLocationForFile: showLocation is false or null");
            return;
        }
        
        // 检查位置权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("PlaybackActivity", "getLocationForFile: 位置权限未授予，请求权限");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        
        // 使用独立进程的服务获取地点信息（避免GPS提取崩溃影响主进程）
        android.util.Log.d("PlaybackActivity", "开始获取地点信息（使用独立进程服务）: " + mediaUrl);
        // 记录请求时的文件ID，用于验证结果是否匹配当前文件
        final long requestFsId = file.getFsId();
        
        android.os.ResultReceiver receiver = new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, android.os.Bundle resultData) {
                // 检查当前文件是否仍然是请求时的文件
                FileInfo currentFile = viewModel.getCurrentFile();
                if (currentFile == null || currentFile.getFsId() != requestFsId) {
                    android.util.Log.d("PlaybackActivity", "忽略过期的地点信息回调 (文件已切换)");
                    return;
                }
                
                if (resultCode == com.baidu.gallery.car.service.LocationExtractionService.RESULT_CODE_SUCCESS) {
                    String location = resultData.getString(com.baidu.gallery.car.service.LocationExtractionService.RESULT_LOCATION);
                    android.util.Log.d("PlaybackActivity", "地点信息获取成功: " + location);
                    if (location != null && !location.isEmpty()) {
                        viewModel.setCurrentLocation(location);
                    } else {
                        viewModel.setCurrentLocation(null);
                    }
                } else {
                    android.util.Log.d("PlaybackActivity", "地点信息获取失败");
                    viewModel.setCurrentLocation(null);
                }
            }
        };
        
        boolean isVideo = file.isVideo();
        com.baidu.gallery.car.service.LocationExtractionService.startExtraction(this, mediaUrl, isVideo, receiver);
    }
    
    /**
     * 处理VLC播放错误，实现智能降级策略
     * 针对H.265/HEVC（特别是苹果设备拍摄）和高帧率视频的兼容性问题
     */
    private void handleVlcError() {
        // 检查Activity是否已销毁，如果已销毁则不执行重试逻辑
        if (isActivityDestroyed) {
            android.util.Log.d("PlaybackActivity", "[ERROR] handleVlcError: Activity已销毁，跳过错误处理");
            return;
        }
        
        vlcErrorCount++;
        android.util.Log.w("PlaybackActivity", "[ERROR] VLC播放错误 (错误次数: " + vlcErrorCount + "/" + MAX_VLC_RETRIES + ")");
        
        FileInfo currentFile = viewModel.getCurrentFile();
        if (currentFile == null) {
            Toast.makeText(this, "播放错误", Toast.LENGTH_SHORT).show();
            viewModel.playNext();
            return;
        }
        
        String fileName = currentFile.getServerFilename();
        boolean isHevc = fileName.toLowerCase().contains("hevc") ||
                        fileName.toLowerCase().contains("h265") ||
                        fileName.toLowerCase().contains("h.265");
        
        if (vlcErrorCount >= MAX_VLC_RETRIES) {
            // 达到最大重试次数，切换到ExoPlayer或跳过
            if (!useVlc) {
                // 已经在使用ExoPlayer，跳过此文件
                Toast.makeText(this,
                    "无法播放此文件 (" + (isHevc ? "H.265编码可能不受支持" : "格式不兼容") + ")，跳过",
                    Toast.LENGTH_LONG).show();
                vlcErrorCount = 0; // 重置计数器
                viewModel.playNext();
            } else {
                // 尝试切换到ExoPlayer
                Toast.makeText(this,
                    "VLC播放失败，尝试使用ExoPlayer" + (isHevc ? " (H.265)" : ""),
                    Toast.LENGTH_SHORT).show();
                vlcErrorCount = 0; // 重置计数器
                useVlc = false;
                android.util.Log.d("PlaybackActivity", "[SWITCH] VLC播放失败，切换到ExoPlayer");
                updatePlayerIndicator();
                
                // 重新播放当前文件
                if (currentMediaUrl != null) {
                    playVideoWithUrl(currentMediaUrl);
                } else {
                    viewModel.playNext();
                }
            }
        } else {
            // 还未达到最大重试次数，尝试软解码
            Toast.makeText(this,
                "播放错误，尝试软解码" + (isHevc ? " (H.265视频)" : ""),
                Toast.LENGTH_SHORT).show();
            
            // 重新初始化VLC，使用软解码
            try {
                if (vlcMediaPlayer != null) {
                    vlcMediaPlayer.stop();
                    vlcMediaPlayer.release();
                }
                if (libVLC != null) {
                    libVLC.release();
                }
                
                // 使用软解码选项重新初始化
                ArrayList<String> options = new ArrayList<>();
                options.add("-vvv");
                options.add("--avcodec-hw=none"); // 强制软解码
                options.add("--network-caching=2000");
                
                libVLC = new LibVLC(this, options);
                vlcMediaPlayer = new MediaPlayer(libVLC);
                
                IVLCVout vout = vlcMediaPlayer.getVLCVout();
                vout.setVideoView(surfaceView);
                vout.attachViews();
                
                // 重新设置事件监听
                vlcMediaPlayer.setEventListener(event -> {
                    switch (event.type) {
                        case MediaPlayer.Event.Playing:
                            loadingIndicator.setVisibility(View.GONE);
                            updatePlayPauseButton(true);
                            startProgressUpdate();
                            break;
                        case MediaPlayer.Event.EncounteredError:
                            loadingIndicator.setVisibility(View.GONE);
                            handleVlcError();
                            break;
                    }
                });
                
                // 重新播放
                if (currentMediaUrl != null) {
                    playVideoWithUrl(currentMediaUrl);
                }
            } catch (Exception e) {
                android.util.Log.e("PlaybackActivity", "[ERROR] 重新初始化VLC失败", e);
                Toast.makeText(this, "播放器初始化失败，跳过此文件", Toast.LENGTH_SHORT).show();
                vlcErrorCount = 0;
                viewModel.playNext();
            }
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        // 全局快捷键
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                viewModel.togglePlayPause();
                showControls();
                return true;
                
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                viewModel.playNext();
                showControls();
                return true;
                
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                viewModel.playPrevious();
                showControls();
                return true;
                
            case KeyEvent.KEYCODE_BACK:
                if (layoutControls.getVisibility() == View.VISIBLE) {
                    hideControls();
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }
    
    /**
     * 快进/快退
     * @param offsetMs 偏移量（毫秒）
     */
    private void seekBy(long offsetMs) {
        long currentTime = 0;
        long totalTime = 0;
        
        if (useVlc && vlcMediaPlayer != null) {
            currentTime = vlcMediaPlayer.getTime();
            totalTime = vlcMediaPlayer.getLength();
            
            long newTime = Math.max(0, Math.min(totalTime, currentTime + offsetMs));
            vlcMediaPlayer.setTime(newTime);
            
            // 更新UI
            tvCurrentTime.setText(DateUtils.formatElapsedTime(newTime / 1000));
            seekbarProgress.setProgress((int) newTime);
            
        } else if (exoPlayer != null) {
            currentTime = exoPlayer.getCurrentPosition();totalTime = exoPlayer.getDuration();
            
            if (totalTime != androidx.media3.common.C.TIME_UNSET) {
                long newTime = Math.max(0, Math.min(totalTime, currentTime + offsetMs));
                exoPlayer.seekTo(newTime);
                
                // 更新UI
                tvCurrentTime.setText(DateUtils.formatElapsedTime(newTime / 1000));
                seekbarProgress.setProgress((int) newTime);
            }
        }
        
        // 显示快进/快退提示
        String text = offsetMs > 0 ? "+" + (offsetMs/1000) + "s" : (offsetMs/1000) + "s";
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
    
    
    @Override
    protected void onPause() {
        super.onPause();
        android.util.Log.d("PlaybackActivity", "onPause called");
        
        // 停止播放
        if (useVlc && vlcMediaPlayer != null && vlcMediaPlayer.isPlaying()) {
            vlcMediaPlayer.pause();
            android.util.Log.d("PlaybackActivity", "onPause: VLC paused");
        } else if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            android.util.Log.d("PlaybackActivity", "onPause: ExoPlayer paused");
        }
        
        // 停止图片定时器
        if (imageRunnable != null && imageHandler != null) {
            imageHandler.removeCallbacks(imageRunnable);
            android.util.Log.d("PlaybackActivity", "onPause: Image timer stopped");
        }
        
        // 停止进度更新
        stopProgressUpdate();
        
        // 停止控制栏隐藏定时器
        if (controlsRunnable != null && controlsHandler != null) {
            controlsHandler.removeCallbacks(controlsRunnable);
        }
        
        // 停止地点信息隐藏定时器
        if (locationRunnable != null && locationHandler != null) {
            locationHandler.removeCallbacks(locationRunnable);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.util.Log.d("PlaybackActivity", "onDestroy called - 开始清理资源");
        
        // 设置销毁标志位，防止后续重试逻辑继续执行
        isActivityDestroyed = true;
        android.util.Log.d("PlaybackActivity", "onDestroy: isActivityDestroyed标志已设置");
        
        // 停止并释放VLC资源
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.stop();
            vlcMediaPlayer.getVLCVout().detachViews();
            vlcMediaPlayer.release();
            vlcMediaPlayer = null;
            android.util.Log.d("PlaybackActivity", "onDestroy: VLC播放器已释放");
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
            android.util.Log.d("PlaybackActivity", "onDestroy: LibVLC已释放");
        }
        
        // 停止并释放ExoPlayer资源
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.release();
            exoPlayer = null;
            android.util.Log.d("PlaybackActivity", "onDestroy: ExoPlayer已释放");
        }
        
        // 清理所有Handler的回调
        if (imageHandler != null) {
            imageHandler.removeCallbacksAndMessages(null);
            imageHandler = null;
            android.util.Log.d("PlaybackActivity", "onDestroy: ImageHandler已清理");
        }
        if (controlsHandler != null) {
            controlsHandler.removeCallbacksAndMessages(null);
            controlsHandler = null;
            android.util.Log.d("PlaybackActivity", "onDestroy: ControlsHandler已清理");
        }
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
            progressHandler = null;
            android.util.Log.d("PlaybackActivity", "onDestroy: ProgressHandler已清理");
        }
        if (locationHandler != null) {
            locationHandler.removeCallbacksAndMessages(null);
            locationHandler = null;
            android.util.Log.d("PlaybackActivity", "onDestroy: LocationHandler已清理");
        }
        
        // 清理Runnable引用
        imageRunnable = null;
        controlsRunnable = null;
        progressRunnable = null;
        locationRunnable = null;
        
        android.util.Log.d("PlaybackActivity", "onDestroy: 所有资源清理完成");
    }

    /**
     * 当目录加载失败或找不到文件时，尝试直接播放单个文件
     */
    private void playSingleFileFallback(String filePath, String fileName, long fsId) {
        android.util.Log.w("PlaybackActivity", "使用单个文件回退策略播放: " + fileName);
        
        FileInfo fallbackFile = new FileInfo();
        fallbackFile.setPath(filePath);
        fallbackFile.setServerFilename(fileName);
        fallbackFile.setFsId(fsId);
        
        // 尝试从文件名推断类型
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
            lowerName.endsWith(".bmp") || lowerName.endsWith(".webp") ||
            lowerName.endsWith(".heic")) {
            fallbackFile.setCategory(3); // 图片
        } else {
            fallbackFile.setCategory(1); // 视频
        }
        
        List<FileInfo> mediaFiles = new ArrayList<>();
        mediaFiles.add(fallbackFile);
        
        viewModel.setPlayList(mediaFiles);
        viewModel.setCurrentIndex(0);
    }
}
