package com.baidu.gallery.car.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.baidu.gallery.car.database.AppDatabase;
import com.baidu.gallery.car.model.PlaybackHistory;

import java.util.List;

/**
 * 主界面视图模型
 */
public class MainViewModel extends AndroidViewModel {
    
    private final AppDatabase database;
    private final LiveData<List<PlaybackHistory>> recentHistory;
    
    public MainViewModel(@NonNull Application application) {
        super(application);
        database = AppDatabase.getInstance(application);
        // 修改为获取所有历史记录，而不仅仅是前4条
        recentHistory = database.playbackHistoryDao().getAllHistory();
    }
    
    public LiveData<List<PlaybackHistory>> getRecentHistory() {
        return recentHistory;
    }
    
    /**
     * 清空所有播放历史记录
     */
    public void clearAllHistory() {
        new Thread(() -> {
            database.playbackHistoryDao().deleteAll();
        }).start();
    }
}