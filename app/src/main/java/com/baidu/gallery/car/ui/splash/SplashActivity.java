package com.baidu.gallery.car.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.FragmentActivity;

import com.baidu.gallery.car.R;
import com.baidu.gallery.car.auth.BaiduAuthService;
import com.baidu.gallery.car.auth.LoginActivity;
import com.baidu.gallery.car.ui.main.MainActivity;

/**
 * 启动页Activity
 * 检查用户登录状态并跳转到相应页面
 */
public class SplashActivity extends FragmentActivity {
    
    private static final int SPLASH_DURATION = 1500; // 1.5秒
    
    private Handler handler;
    private Runnable navigateRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        handler = new Handler(Looper.getMainLooper());
        
        // 延迟跳转到主页面
        navigateRunnable = this::checkLoginStatusAndNavigate;
        handler.postDelayed(navigateRunnable, SPLASH_DURATION);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && navigateRunnable != null) {
            handler.removeCallbacks(navigateRunnable);
        }
    }
    
    /**
     * 检查登录状态并跳转到相应页面
     */
    private void checkLoginStatusAndNavigate() {
        BaiduAuthService authService = BaiduAuthService.getInstance(this);
        
        if (authService.isAuthenticated()) {
            // 已登录，跳转到主页面
            startMainActivity();
        } else {
            // 未登录，跳转到登录页面
            startLoginActivity();
        }
    }
    
    /**
     * 跳转到主页面
     */
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
    
    /**
     * 跳转到登录页面
     */
    private void startLoginActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}