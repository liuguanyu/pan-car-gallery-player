package com.baidu.gallery.car.player;

import com.baidu.gallery.car.model.FileInfo;
import java.util.Locale;

/**
 * VLC优先策略
 * 
 * 主要用于处理HEVC/H.265视频，因为VLC内置FFmpeg解码器，对HEVC的支持通常比Android原生更好
 * 优先级最高 (0)
 */
public class VlcFirstStrategy implements PlayerStrategy {
    
    @Override
    public String getName() {
        return "VLC";
    }

    @Override
    public boolean canHandle(FileInfo fileInfo, String mimeType, String codecs) {
        if (fileInfo == null) {
            android.util.Log.d("PlayerStrategy", "[VLC] fileInfo is null, cannot handle");
            return false;
        }
        
        android.util.Log.d("PlayerStrategy", "[VLC] canHandle called - fileName=" + fileInfo.getServerFilename() +
            ", mimeType=" + mimeType + ", codecs=" + codecs);
        
        // 1. 通过MIME类型判断HEVC
        if (mimeType != null) {
            if (mimeType.contains("hevc") || mimeType.contains("hev1") || mimeType.contains("hvc1")) {
                android.util.Log.d("PlayerStrategy", "[VLC] ✓ 通过MIME类型检测到HEVC: " + mimeType);
                return true;
            }
        } else {
            android.util.Log.d("PlayerStrategy", "[VLC] mimeType is null, skip MIME check");
        }
        
        // 2. 通过Codecs字符串判断HEVC
        if (codecs != null) {
            if (codecs.contains("hev") || codecs.contains("hvc") || codecs.contains("h265")) {
                android.util.Log.d("PlayerStrategy", "[VLC] ✓ 通过Codecs检测到HEVC: " + codecs);
                return true;
            }
        } else {
            android.util.Log.d("PlayerStrategy", "[VLC] codecs is null, skip codecs check");
        }
        
        // 3. 通过文件扩展名判断HEVC (快速检测)
        String fileName = fileInfo.getServerFilename();
        if (fileName != null) {
            String lowerName = fileName.toLowerCase(Locale.US);
            if (lowerName.endsWith(".hevc") ||
                lowerName.endsWith(".h265") ||
                lowerName.endsWith(".265")) {
                android.util.Log.d("PlayerStrategy", "[VLC] ✓ 通过文件名检测到HEVC: " + fileName);
                return true;
            }
            android.util.Log.d("PlayerStrategy", "[VLC] 文件扩展名不是HEVC: " + fileName);
        } else {
            android.util.Log.d("PlayerStrategy", "[VLC] fileName is null");
        }
        
        // 4. 通过文件路径判断HEVC
        String filePath = fileInfo.getPath();
        if (filePath != null) {
            String lowerPath = filePath.toLowerCase(Locale.US);
            if (lowerPath.contains("hevc") ||
                lowerPath.contains("h265") ||
                lowerPath.contains("265")) {
                android.util.Log.d("PlayerStrategy", "[VLC] ✓ 通过文件路径检测到HEVC: " + filePath);
                return true;
            }
            android.util.Log.d("PlayerStrategy", "[VLC] 文件路径不包含HEVC关键词: " + filePath);
        } else {
            android.util.Log.d("PlayerStrategy", "[VLC] filePath is null");
        }
        
        // 5. 对于常见视频容器格式，优先使用VLC
        // 原因：VLC内置FFmpeg解码器，支持更多编码格式（包括HEVC/H.265）
        // 当无法确定具体编码格式时，使用VLC作为默认选择更安全
        if (fileName != null) {
            String lowerName = fileName.toLowerCase(Locale.US);
            // 常见视频容器扩展名
            if (lowerName.endsWith(".mp4") ||
                lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".avi") ||
                lowerName.endsWith(".mov") ||
                lowerName.endsWith(".flv") ||
                lowerName.endsWith(".wmv") ||
                lowerName.endsWith(".webm") ||
                lowerName.endsWith(".m4v")) {
                android.util.Log.d("PlayerStrategy", "[VLC] ✓ 检测到视频容器格式，优先使用VLC: " + fileName);
                return true;
            }
        }
        
        // 其他情况默认不使用VLC优先，让后续策略处理
        android.util.Log.d("PlayerStrategy", "[VLC] ✗ 未检测到视频格式，返回false");
        return false;
    }

    @Override
    public int getPriority() {
        return 0; // 最高优先级
    }
    
    @Override
    public boolean isFallback() {
        return true; // VLC也作为最终后备方案
    }
}