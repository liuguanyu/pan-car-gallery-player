package com.baidu.gallery.car;

import android.app.Application;
import com.baidu.gallery.car.database.AppDatabase;

/**
 * 车载应用主Application类
 */
public class CarGalleryApplication extends Application {
    
    private static CarGalleryApplication instance;
    private AppDatabase database;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // 初始化数据库
        database = AppDatabase.getInstance(this);
    }
    
    public static CarGalleryApplication getInstance() {
        return instance;
    }
    
    public AppDatabase getDatabase() {
        return database;
    }
}