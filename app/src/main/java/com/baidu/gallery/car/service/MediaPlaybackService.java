package com.baidu.gallery.car.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.baidu.gallery.car.R;
import com.baidu.gallery.car.ui.playback.PlaybackActivity;

/**
 * 媒体播放服务
 * 在后台播放媒体内容，支持通知控制
 * 继承自 MediaSessionService 以符合 Android Automotive 标准
 */
public class MediaPlaybackService extends MediaSessionService {
    
    private static final String TAG = "MediaPlaybackService";
    private static final String CHANNEL_ID = "media_playback_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private ExoPlayer player;
    private MediaSession mediaSession;
    private NotificationManager notificationManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "创建媒体播放服务");
        
        // 初始化播放器
        player = new ExoPlayer.Builder(this).build();
        
        // 初始化 MediaSession
        mediaSession = new MediaSession.Builder(this, player).build();

        // 初始化通知管理器
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        
        // 设置播放器事件监听
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateNotification();
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateNotification();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        // 启动前台服务
        if (intent != null) {
            String action = intent.getAction();
            if ("PLAY_PAUSE".equals(action)) {
                togglePlayPause();
            } else if ("NEXT".equals(action)) {
                playNext();
            }
        }
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }
    
    // MediaSessionService 要求实现的方法
    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "销毁媒体播放服务");
        
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }
        
        stopForeground(true);
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "媒体播放",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("媒体播放控制通知");
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * 创建通知
     */
    private Notification createNotification() {
        // 打开播放器Activity的Intent
        Intent intent = new Intent(this, PlaybackActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // 播放/暂停按钮Intent - Using Custom Action
        Intent playPauseIntent = new Intent(this, MediaPlaybackService.class);
        playPauseIntent.setAction("PLAY_PAUSE");
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // 下一首按钮Intent - Using Custom Action
        Intent nextIntent = new Intent(this, MediaPlaybackService.class);
        nextIntent.setAction("NEXT");
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Use standard Android resources for icons if project specific ones are missing or causing issues
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("车载媒体播放器")
                .setContentText(getMediaTitle())
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_play, "播放/暂停", playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, "下一首", nextPendingIntent);
        
        return builder.build();
    }
    
    /**
     * 更新通知
     */
    private void updateNotification() {
        Notification notification = createNotification();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
    
    /**
     * 获取当前媒体标题
     */
    private String getMediaTitle() {
        if (player != null && player.getCurrentMediaItem() != null) {
            MediaItem mediaItem = player.getCurrentMediaItem();
            return mediaItem.mediaMetadata.title != null ? 
                   mediaItem.mediaMetadata.title.toString() : "正在播放";
        }
        return "正在播放";
    }
    
    /**
     * 播放媒体
     */
    public void playMedia(MediaItem mediaItem) {
        if (player != null) {
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        }
    }
    
    /**
     * 播放/暂停切换
     */
    public void togglePlayPause() {
        if (player != null) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        }
    }
    
    /**
     * 播放下一首
     */
    public void playNext() {
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
        }
    }
    
    /**
     * 获取播放器实例
     */
    public ExoPlayer getPlayer() {
        return player;
    }
}