package com.baidu.gallery.car.utils;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;

/**
 * 语音指令管理器
 * 处理来自车载语音助手的指令
 */
public class VoiceCommandManager {
    
    private static final String TAG = "VoiceCommandManager";
    private static volatile VoiceCommandManager instance;
    private final Context context;
    
    // 语音指令事件
    public enum CommandType {
        PLAY,
        PAUSE,
        NEXT,
        PREVIOUS,
        SEARCH,
        OPEN_PLAYLIST,
        UNKNOWN
    }
    
    public static class VoiceCommand {
        public final CommandType type;
        public final String extra;
        
        public VoiceCommand(CommandType type, String extra) {
            this.type = type;
            this.extra = extra;
        }
    }
    
    private final MutableLiveData<VoiceCommand> commandLiveData = new MutableLiveData<>();
    
    private VoiceCommandManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static VoiceCommandManager getInstance(Context context) {
        if (instance == null) {
            synchronized (VoiceCommandManager.class) {
                if (instance == null) {
                    instance = new VoiceCommandManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 检查Intent是否为语音指令
     */
    public boolean isVoiceCommand(Intent intent) {
        if (intent == null) return false;
        
        String action = intent.getAction();
        return RecognizerIntent.ACTION_RECOGNIZE_SPEECH.equals(action) ||
               Intent.ACTION_SEARCH.equals(action) ||
               "android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(action);
    }
    
    /**
     * 处理语音指令Intent
     */
    public void handleVoiceCommand(Intent intent) {
        handleVoiceSearch(intent);
    }
    
    /**
     * 处理语音搜索Intent
     */
    public void handleVoiceSearch(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        if (RecognizerIntent.ACTION_RECOGNIZE_SPEECH.equals(action) ||
            Intent.ACTION_SEARCH.equals(action) ||
            "android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(action)) {
            
            String query = intent.getStringExtra("query"); // MEDIA_PLAY_FROM_SEARCH
            if (query == null) {
                query = intent.getStringExtra(SearchManager.QUERY); // 常规搜索
            }
            
            if (query != null && !query.isEmpty()) {
                Log.d(TAG, "收到语音搜索: " + query);
                parseAndExecute(query);
            }
        }
    }
    
    /**
     * 解析语音文本并执行相应指令
     */
    private void parseAndExecute(String query) {
        query = query.toLowerCase();
        
        if (query.contains("播放") || query.contains("play")) {
            if (query.contains("照片") || query.contains("视频") || query.contains("相册")) {
                // 可能是播放特定内容
                String keyword = extractKeyword(query);
                if (!keyword.isEmpty()) {
                    commandLiveData.postValue(new VoiceCommand(CommandType.SEARCH, keyword));
                } else {
                    commandLiveData.postValue(new VoiceCommand(CommandType.PLAY, null));
                }
            } else {
                commandLiveData.postValue(new VoiceCommand(CommandType.PLAY, null));
            }
        } else if (query.contains("暂停") || query.contains("pause") || query.contains("停止")) {
            commandLiveData.postValue(new VoiceCommand(CommandType.PAUSE, null));
        } else if (query.contains("下一") || query.contains("next")) {
            commandLiveData.postValue(new VoiceCommand(CommandType.NEXT, null));
        } else if (query.contains("上一") || query.contains("previous")) {
            commandLiveData.postValue(new VoiceCommand(CommandType.PREVIOUS, null));
        } else if (query.contains("搜索") || query.contains("查找") || query.contains("找一下")) {
            String keyword = extractKeyword(query);
            commandLiveData.postValue(new VoiceCommand(CommandType.SEARCH, keyword));
        } else {
            // 默认为搜索
            commandLiveData.postValue(new VoiceCommand(CommandType.SEARCH, query));
        }
    }
    
    private String extractKeyword(String query) {
        // 简单的关键词提取，实际项目中可能需要更复杂的NLP
        String[] prefixes = {"搜索", "查找", "播放", "找一下", "看"};
        for (String prefix : prefixes) {
            if (query.startsWith(prefix)) {
                return query.substring(prefix.length()).trim();
            }
        }
        return query;
    }
    
    public LiveData<VoiceCommand> getCommandLiveData() {
        return commandLiveData;
    }
}