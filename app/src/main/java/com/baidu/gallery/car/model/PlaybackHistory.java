package com.baidu.gallery.car.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 播放历史记录实体
 * 存储单个文件的播放历史，而非文件夹
 */
@Entity(tableName = "playback_history",
        indices = {@Index(value = {"filePath"}, unique = true)})
public class PlaybackHistory {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String filePath; // 文件完整路径
    private String fileName; // 文件名
    private long fsId; // 文件ID（用于获取下载链接）
    private int mediaType; // 1: 图片, 2: 视频
    private long lastPlayTime; // 最后播放时间
    private long createTime; // 创建时间
    private String thumbnailUrl; // 缩略图URL（云盘缩略图）

    public PlaybackHistory() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFsId() {
        return fsId;
    }

    public void setFsId(long fsId) {
        this.fsId = fsId;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public long getLastPlayTime() {
        return lastPlayTime;
    }

    public void setLastPlayTime(long lastPlayTime) {
        this.lastPlayTime = lastPlayTime;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}