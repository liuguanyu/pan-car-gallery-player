import os
import shutil

SOURCE_DIR = r"D:\devspace\baidu-gallery-car-player\app\src\main\java\com\baidu\tv\player"
TARGET_DIR = r"D:\devspace\baidu-gallery-car-player\app\src\main\java\com\baidu\gallery\car"

OLD_PACKAGE = "com.baidu.tv.player"
NEW_PACKAGE = "com.baidu.gallery.car"

def move_and_rename_files():
    if not os.path.exists(SOURCE_DIR):
        print(f"Source directory does not exist: {SOURCE_DIR}")
        return
    
    # Create target directory
    os.makedirs(TARGET_DIR, exist_ok=True)
    
    # Move all files from source to target
    for root, dirs, files in os.walk(SOURCE_DIR):
        for file in files:
            source_file = os.path.join(root, file)
            rel_path = os.path.relpath(source_file, SOURCE_DIR)
            target_file = os.path.join(TARGET_DIR, rel_path)
            
            # Create target subdirectories
            os.makedirs(os.path.dirname(target_file), exist_ok=True)
            
            # Read and modify file content
            if file.endswith(('.java', '.kt', '.xml')):
                try:
                    with open(source_file, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    # Replace package name
                    content = content.replace(OLD_PACKAGE, NEW_PACKAGE)
                    
                    with open(target_file, 'w', encoding='utf-8') as f:
                        f.write(content)
                    print(f"Moved and modified: {target_file}")
                except Exception as e:
                    print(f"Error processing {source_file}: {e}")
                    shutil.copy2(source_file, target_file)
            else:
                shutil.copy2(source_file, target_file)
                print(f"Moved binary: {target_file}")
    
    # Remove old directory
    try:
        shutil.rmtree(SOURCE_DIR)
        print(f"Removed old directory: {SOURCE_DIR}")
    except Exception as e:
        print(f"Error removing old directory: {e}")

if __name__ == "__main__":
    move_and_rename_files()
    print("Package structure fixed!")