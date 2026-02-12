package com.baidu.gallery.car.player;

import com.baidu.gallery.car.model.FileInfo;

/**
 * ExoPlayer策略
 * 
 * 作为默认播放器策略，处理除HEVC外的其他视频格式
 * 优先级较低 (10)
 */
public class ExoPlayerStrategy implements PlayerStrategy {
    
    @Override
    public String getName() {
        return "ExoPlayer";
    }

    @Override
    public boolean canHandle(FileInfo fileInfo, String mimeType, String codecs) {
        // ExoPlayer作为默认策略，可以处理所有视频格式
        // 但优先级低于VLC策略，所以只有在VLC策略不适用时才会被选择
        return true;
    }

    @Override
    public int getPriority() {
        return 10; // 较低优先级
    }
}