package com.baidu.gallery.car.player;

import com.baidu.gallery.car.model.FileInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 播放器调度器
 * 
 * 使用职责链模式管理播放器策略，根据文件信息选择最合适的播放器
 * 
 * 策略链：
 * 1. VlcFirstStrategy (优先级0) - 处理HEVC视频
 * 2. ExoPlayerStrategy (优先级10) - 处理其他视频格式
 * 
 * 如果没有策略可以处理，则返回后备策略
 */
public class PlayerScheduler {
    
    private final List<PlayerStrategy> strategies;
    private final PlayerStrategy fallbackStrategy;
    
    public PlayerScheduler() {
        strategies = new ArrayList<>();
        
        // 添加策略
        strategies.add(new VlcFirstStrategy());
        strategies.add(new ExoPlayerStrategy());
        
        // 按优先级排序
        Collections.sort(strategies, (s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()));
        
        // 找到后备策略
        PlayerStrategy fallback = null;
        for (PlayerStrategy strategy : strategies) {
            if (strategy.isFallback()) {
                fallback = strategy;
                break;
            }
        }
        
        // 如果没有明确的后备策略，使用最后一个策略作为后备
        fallbackStrategy = fallback != null ? fallback : strategies.get(strategies.size() - 1);
    }
    
    /**
     * 根据文件信息选择播放器策略
     * 
     * @param fileInfo 文件信息
     * @param mimeType MIME类型
     * @param codecs 编解码器信息
     * @return 选择的播放器策略
     */
    public PlayerStrategy selectStrategy(FileInfo fileInfo, String mimeType, String codecs) {
        // 遍历策略链，找到第一个可以处理的策略
        for (PlayerStrategy strategy : strategies) {
            if (strategy.canHandle(fileInfo, mimeType, codecs)) {
                android.util.Log.d("PlayerScheduler", "选择播放器策略: " + strategy.getName());
                return strategy;
            }
        }
        
        // 如果没有策略可以处理，使用后备策略
        android.util.Log.d("PlayerScheduler", "没有合适的策略，使用后备策略: " + fallbackStrategy.getName());
        return fallbackStrategy;
    }
    
    /**
     * 获取所有策略列表（用于调试）
     * 
     * @return 策略列表
     */
    public List<PlayerStrategy> getStrategies() {
        return new ArrayList<>(strategies);
    }
    
    /**
     * 获取后备策略
     * 
     * @return 后备策略
     */
    public PlayerStrategy getFallbackStrategy() {
        return fallbackStrategy;
    }
}