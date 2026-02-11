package com.baidu.gallery.car.utils;

import android.content.Context;
import android.util.Log;

import androidx.car.app.CarContext;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * 驾驶模式管理器
 * 负责检测车辆行驶状态，并提供驾驶模式状态
 */
public class DrivingModeManager {
    
    private static final String TAG = "DrivingModeManager";
    private static volatile DrivingModeManager instance;
    private final Context context;
    private final MutableLiveData<Boolean> isDrivingMode = new MutableLiveData<>(false);
    
    private DrivingModeManager(Context context) {
        this.context = context.getApplicationContext();
        // 初始化驾驶模式检测
        initDrivingDetection();
    }
    
    public static DrivingModeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DrivingModeManager.class) {
                if (instance == null) {
                    instance = new DrivingModeManager(context);
                }
            }
        }
        return instance;
    }
    
    private void initDrivingDetection() {
        // 在实际车载设备上，这里应该通过Car API监听车辆状态
        // 比如监听CarHardwareManager或CarPropertyManager
        // 目前为了演示，我们假设不处于驾驶模式，除非手动触发
        Log.d(TAG, "初始化驾驶模式检测");
    }
    
    /**
     * 获取驾驶模式状态LiveData
     */
    public LiveData<Boolean> getDrivingModeState() {
        return isDrivingMode;
    }
    
    /**
     * 手动设置驾驶模式（用于测试或模拟器）
     */
    public void setDrivingMode(boolean isDriving) {
        isDrivingMode.postValue(isDriving);
    }
    
    /**
     * 当前是否处于驾驶模式
     */
    public boolean isDriving() {
        Boolean value = isDrivingMode.getValue();
        return value != null && value;
    }
}