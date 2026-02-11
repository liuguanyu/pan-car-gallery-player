package com.baidu.gallery.car.effects;

import android.view.animation.DecelerateInterpolator;
import com.baidu.gallery.car.ui.view.BlindsImageView;

/**
 * 旋转特效策略
 * 从旋转状态恢复
 */
public class RotateEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        imageView.setRotation(180f);
        imageView.animate()
                .rotation(0f)
                .setDuration(getDuration())
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}