# -*- coding: utf-8 -*-
import os
import shutil

SOURCE_DIR = r"D:\devspace\tv-baidu-player"
TARGET_DIR = r"D:\devspace\baidu-gallery-car-player"

# List of mipmap directories to copy
MIPMAP_DIRS = [
    r"app\src\main\res\mipmap-hdpi",
    r"app\src\main\res\mipmap-mdpi",
    r"app\src\main\res\mipmap-xhdpi",
    r"app\src\main\res\mipmap-xxhdpi",
    r"app\src\main\res\mipmap-xxxhdpi",
    r"app\src\main\res\mipmap-anydpi-v26",
]

# Drawable files to copy (icons only)
DRAWABLE_ICONS = [
    r"app\src\main\res\drawable\ic_launcher_foreground.xml",
    r"app\src\main\res\drawable\ic_launcher_background.xml",
]

def copy_file(source_file, target_file):
    """Copy a single file"""
    if os.path.exists(source_file):
        os.makedirs(os.path.dirname(target_file), exist_ok=True)
        shutil.copy2(source_file, target_file)
        print(f"Copied: {target_file}")
    else:
        print(f"Source file not found: {source_file}")

def copy_directory(relative_path):
    """Copy all files from a directory"""
    source_path = os.path.join(SOURCE_DIR, relative_path)
    target_path = os.path.join(TARGET_DIR, relative_path)
    
    if not os.path.exists(source_path):
        print(f"Source directory not found: {source_path}")
        return
    
    # Create target directory
    os.makedirs(target_path, exist_ok=True)
    
    # Copy all files in the directory
    for file in os.listdir(source_path):
        source_file = os.path.join(source_path, file)
        target_file = os.path.join(target_path, file)
        
        if os.path.isfile(source_file):
            shutil.copy2(source_file, target_file)
            print(f"Copied: {target_file}")

print("Starting to copy icon resources from reference project...")
print(f"Source: {SOURCE_DIR}")
print(f"Target: {TARGET_DIR}")
print()

# Copy mipmap directories
print("=== Copying mipmap directories ===")
for mipmap_dir in MIPMAP_DIRS:
    print(f"\nProcessing: {mipmap_dir}")
    copy_directory(mipmap_dir)

# Copy drawable icons
print("\n=== Copying drawable icons ===")
for drawable_file in DRAWABLE_ICONS:
    source_file = os.path.join(SOURCE_DIR, drawable_file)
    target_file = os.path.join(TARGET_DIR, drawable_file)
    copy_file(source_file, target_file)

print("\n=== Copy completed! ===")