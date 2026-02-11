# -*- coding: utf-8 -*-
"""
从参考项目复制应用图标到当前项目
"""
import os
import shutil

# 源项目路径
SOURCE_PROJECT = r"D:\devspace\tv-baidu-player"
# 目标项目路径  
TARGET_PROJECT = r"D:\devspace\baidu-gallery-car-player"

# 需要复制的mipmap目录
MIPMAP_DIRS = [
    "mipmap-hdpi",
    "mipmap-mdpi", 
    "mipmap-xhdpi",
    "mipmap-xxhdpi",
    "mipmap-xxxhdpi",
    "mipmap-anydpi-v26"
]

# 需要复制的图标文件
ICON_FILES = [
    "ic_launcher.png",
    "ic_launcher_background.png",
    "ic_launcher_foreground.png",
    "ic_launcher.xml"
]

def copy_icons():
    """复制图标资源"""
    source_res = os.path.join(SOURCE_PROJECT, "app", "src", "main", "res")
    target_res = os.path.join(TARGET_PROJECT, "app", "src", "main", "res")
    
    copied_count = 0
    
    for mipmap_dir in MIPMAP_DIRS:
        source_dir = os.path.join(source_res, mipmap_dir)
        target_dir = os.path.join(target_res, mipmap_dir)
        
        if not os.path.exists(source_dir):
            print(f"[SKIP] Source directory not found: {mipmap_dir}")
            continue
            
        # 确保目标目录存在
        os.makedirs(target_dir, exist_ok=True)
        
        for icon_file in ICON_FILES:
            source_file = os.path.join(source_dir, icon_file)
            target_file = os.path.join(target_dir, icon_file)
            
            if os.path.exists(source_file):
                shutil.copy2(source_file, target_file)
                print(f"[COPIED] {mipmap_dir}/{icon_file}")
                copied_count += 1
    
    # 复制drawable目录中的前景图标（如果存在）
    source_drawable = os.path.join(source_res, "drawable")
    target_drawable = os.path.join(target_res, "drawable")
    
    drawable_files = [
        "ic_launcher_foreground.xml"
    ]
    
    for drawable_file in drawable_files:
        source_file = os.path.join(source_drawable, drawable_file)
        target_file = os.path.join(target_drawable, drawable_file)
        
        if os.path.exists(source_file):
            shutil.copy2(source_file, target_file)
            print(f"[COPIED] drawable/{drawable_file}")
            copied_count += 1
    
    print(f"\nTotal files copied: {copied_count}")
    return copied_count

if __name__ == "__main__":
    print("Copying icons from reference project...")
    print(f"Source: {SOURCE_PROJECT}")
    print(f"Target: {TARGET_PROJECT}")
    print("-" * 50)
    copy_icons()
    print("-" * 50)
    print("Done!")