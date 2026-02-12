package com.baidu.gallery.car.player;

import com.baidu.gallery.car.model.FileInfo;

/**
 * 播放器策略接口
 * 
 * 使用职责链模式，每个策略负责判断是否可以处理特定的视频格式
 * 如果可以处理，返回true；否则返回false，让下一个策略处理
 * 
 * @see VlcPlayerStrategy
 * @see ExoPlayerStrategy
 * @see PlayerScheduler
 */
public interface PlayerStrategy {
    
    /**
     * 获取策略名称，用于日志和UI显示
     * @return 策略名称，如 "VLC" 或 "ExoPlayer"
     */
    String getName();
    
    /**
     * 判断此策略是否可以处理指定的文件
     * 
     * @param fileInfo 文件信息
     * @param mimeType MIME类型，可能为null
     * @param codecs 编解码器信息，可能为null
     * @return true表示此策略可以处理该文件，false表示不能处理
     */
    boolean canHandle(FileInfo fileInfo, String mimeType, String codecs);
    
    /**
     * 获取策略优先级，数值越小优先级越高
     * 用于排序策略链
     * 
     * @return 优先级值，如 0 表示最高优先级
     */
    int getPriority();
    
    /**
     * 判断是否应该作为后备策略使用
     * 后备策略在其他策略都失败时使用
     * 
     * @return true表示是后备策略
     */
    default boolean isFallback() {
        return false;
    }
}