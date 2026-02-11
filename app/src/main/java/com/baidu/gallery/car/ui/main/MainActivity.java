package com.baidu.gallery.car.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import android.widget.Toast;

import com.baidu.gallery.car.R;
import com.baidu.gallery.car.auth.AuthRepository;
import com.baidu.gallery.car.ui.settings.SettingsActivity;
import com.baidu.gallery.car.utils.DrivingModeManager;
import com.baidu.gallery.car.utils.VoiceCommandManager;

/**
 * 主Activity
 */
public class MainActivity extends FragmentActivity {
    
    private DrivingModeManager drivingModeManager;
    private VoiceCommandManager voiceCommandManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏和沉浸式模式，隐藏状态栏和导航栏
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        
        setContentView(R.layout.activity_main);
        
        // 初始化车载功能管理器
        drivingModeManager = DrivingModeManager.getInstance(this);
        voiceCommandManager = VoiceCommandManager.getInstance(this);
        
        // 监听驾驶模式变化
        drivingModeManager.getDrivingModeState().observe(this, isDriving -> {
            if (isDriving) {
                // 驾驶模式下，可能需要限制某些UI交互或显示提示
                Toast.makeText(this, "进入驾驶模式，部分功能受限", Toast.LENGTH_SHORT).show();
            }
        });

        // 检查语音指令
        handleVoiceCommand(getIntent());

        // 检查是否已登录
        AuthRepository authRepository = new AuthRepository(this);
        if (!authRepository.isAuthenticated()) {
            // 未登录，跳转到登录界面
            Intent intent = new Intent(this, com.baidu.gallery.car.auth.LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_browse_fragment, new MainFragment())
                    .commitNow();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleVoiceCommand(intent);
    }

    private void handleVoiceCommand(Intent intent) {
        if (intent != null && voiceCommandManager.isVoiceCommand(intent)) {
            voiceCommandManager.handleVoiceCommand(intent);
        }
    }
    
}