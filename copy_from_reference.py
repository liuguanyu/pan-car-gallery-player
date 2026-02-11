import os
import shutil
import re

SOURCE_DIR = r"D:\devspace\tv-baidu-player"
TARGET_DIR = r"D:\devspace\baidu-gallery-car-player"

SOURCE_PACKAGE = "com.baidu.gallery.tv"
TARGET_PACKAGE = "com.baidu.gallery.car"

SOURCE_PATH_PACKAGE = SOURCE_PACKAGE.replace(".", os.sep)
TARGET_PATH_PACKAGE = TARGET_PACKAGE.replace(".", os.sep)

def copy_and_modify(source_file, target_file):
    os.makedirs(os.path.dirname(target_file), exist_ok=True)
    
    # Text files that need content modification
    if source_file.endswith(('.java', '.kt', '.xml', '.gradle', '.pro')):
        try:
            with open(source_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Replace package name
            content = content.replace(SOURCE_PACKAGE, TARGET_PACKAGE)
            
            # Special handling for package declaration in Java/Kotlin files
            if source_file.endswith(('.java', '.kt')):
                # Adjust imports if necessary
                pass
                
            with open(target_file, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"Copied and modified: {target_file}")
        except Exception as e:
            print(f"Error processing {source_file}: {e}")
            # Fallback to direct copy for binary files or read errors
            shutil.copy2(source_file, target_file)
    else:
        # Binary files (images, etc.)
        shutil.copy2(source_file, target_file)
        print(f"Copied binary: {target_file}")

def process_directory(relative_path):
    source_path = os.path.join(SOURCE_DIR, relative_path)
    target_path = os.path.join(TARGET_DIR, relative_path)
    
    if not os.path.exists(source_path):
        print(f"Source path does not exist: {source_path}")
        return

    for root, dirs, files in os.walk(source_path):
        for file in files:
            source_file = os.path.join(root, file)
            
            # Calculate relative path from source root
            rel_path = os.path.relpath(source_file, SOURCE_DIR)
            
            # Adjust path for Java/Kotlin files if package structure changed
            if SOURCE_PATH_PACKAGE in rel_path:
                rel_path = rel_path.replace(SOURCE_PATH_PACKAGE, TARGET_PATH_PACKAGE)
            
            target_file = os.path.join(TARGET_DIR, rel_path)
            
            copy_and_modify(source_file, target_file)

# Copy resource files
process_directory(r"app\src\main\res")

# Copy source code
process_directory(r"app\src\main\java")

print("Copy completed!")