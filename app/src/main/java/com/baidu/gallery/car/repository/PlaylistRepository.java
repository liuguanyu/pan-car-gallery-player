package com.baidu.gallery.car.repository;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.baidu.gallery.car.auth.BaiduAuthService;
import com.baidu.gallery.car.database.AppDatabase;
import com.baidu.gallery.car.database.PlaylistDao;
import com.baidu.gallery.car.database.PlaylistItemDao;
import com.baidu.gallery.car.model.Playlist;
import com.baidu.gallery.car.model.PlaylistItem;
import com.baidu.gallery.car.model.FileInfo;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * 播放列表数据仓库
 */
public class PlaylistRepository {
    private static final String TAG = "PlaylistRepository";
    
    private final Context context;
    private final PlaylistDao playlistDao;
    private final PlaylistItemDao playlistItemDao;
    private final ExecutorService executorService;
    
    public PlaylistRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(context);
        playlistDao = database.playlistDao();
        playlistItemDao = database.playlistItemDao();
        executorService = Executors.newFixedThreadPool(4);
    }
    
    /**
     * 获取所有播放列表
     */
    public LiveData<List<Playlist>> getAllPlaylists() {
        return playlistDao.getAllPlaylists();
    }
    
    /**
     * 同步获取所有播放列表
     */
    public List<Playlist> getAllPlaylistsSync() {
        return playlistDao.getAllPlaylistsSync();
    }
    
    /**
     * 根据ID获取播放列表
     */
    public LiveData<Playlist> getPlaylistById(long id) {
        return playlistDao.getPlaylistById(id);
    }
    
    /**
     * 同步根据ID获取播放列表
     */
    public Playlist getPlaylistByIdSync(long id) {
        return playlistDao.getPlaylistByIdSync(id);
    }
    
    /**
     * 插入播放列表
     */
    public interface InsertCallback {
        void onSuccess(long id);
        void onError(Exception e);
    }

    public void insertPlaylist(Playlist playlist, InsertCallback callback) {
        executorService.execute(() -> {
            try {
                long id = playlistDao.insert(playlist);
                Log.d(TAG, "播放列表插入成功, ID: " + id);
                if (callback != null) {
                    // 在主线程回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onSuccess(id));
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表插入失败", e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(e));
                }
            }
        });
    }
    
    /**
     * 更新播放列表
     */
    public void updatePlaylist(Playlist playlist, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistDao.update(playlist);
                Log.d(TAG, "播放列表更新成功, ID: " + playlist.getId());
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表更新失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 删除播放列表
     */
    public void deletePlaylist(Playlist playlist, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistDao.delete(playlist);
                Log.d(TAG, "播放列表删除成功, ID: " + playlist.getId());
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表删除失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 获取播放列表的所有项
     */
    public LiveData<List<PlaylistItem>> getPlaylistItems(long playlistId) {
        return playlistItemDao.getItemsByPlaylistId(playlistId);
    }
    
    /**
     * 同步获取播放列表的所有项
     */
    public List<PlaylistItem> getPlaylistItemsSync(long playlistId) {
        return playlistItemDao.getItemsByPlaylistIdSync(playlistId);
    }
    
    /**
     * 插入播放列表项
     */
    public void insertPlaylistItems(List<PlaylistItem> items, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistItemDao.insertAll(items);
                Log.d(TAG, "播放列表项插入成功, 数量: " + items.size());
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表项插入失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 删除播放列表的所有项
     */
    public void deletePlaylistItems(long playlistId, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistItemDao.deleteByPlaylistId(playlistId);
                Log.d(TAG, "播放列表项删除成功, playlistId: " + playlistId);
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表项删除失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 获取播放列表项数量
     */
    public int getPlaylistItemCount(long playlistId) {
        return playlistItemDao.getItemCount(playlistId);
    }

    /**
     * 刷新播放列表
     * @param playlist 要刷新的播放列表
     * @param onSuccess 成功回调
     * @param onError 失败回调
     */
    public void refreshPlaylist(Playlist playlist, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "开始刷新播放列表: " + playlist.getName());
                
                // 获取访问令牌
                String accessToken = BaiduAuthService.getInstance(context).getAccessToken();
                if (accessToken == null || accessToken.isEmpty()) {
                    Log.e(TAG, "未获取到访问令牌，请先登录");
                    if (onError != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                    }
                    return;
                }
                
                // 1. 尝试从 sourcePaths 获取（兼容旧逻辑，如果有明确的sourcePaths则优先使用）
                List<String> sourcePaths = new java.util.ArrayList<>();
                String sourcePathsJson = playlist.getSourcePaths();
                boolean hasExplicitSourcePaths = false;
                
                if (sourcePathsJson != null && !sourcePathsJson.isEmpty()) {
                    try {
                        org.json.JSONArray jsonArray = new org.json.JSONArray(sourcePathsJson);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            sourcePaths.add(jsonArray.getString(i));
                        }
                        if (!sourcePaths.isEmpty()) {
                            hasExplicitSourcePaths = true;
                        }
                    } catch (org.json.JSONException e) {
                        Log.w(TAG, "解析源目录路径失败，尝试使用智能推断", e);
                    }
                }
                
                // 2. 如果没有明确的 sourcePaths，使用智能推断算法（参考项目算法）
                if (!hasExplicitSourcePaths) {
                    List<PlaylistItem> currentItems = playlistItemDao.getItemsByPlaylistIdSync(playlist.getId());
                    if (currentItems == null || currentItems.isEmpty()) {
                        Log.w(TAG, "播放列表为空且无源目录信息，无法刷新");
                        if (onError != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                        }
                        return;
                    }
                    
                    Set<String> calculatedRoots = calculateSyncRoots(currentItems);
                    sourcePaths.addAll(calculatedRoots);
                    
                    if (sourcePaths.isEmpty()) {
                        Log.w(TAG, "无法推断扫描路径");
                        if (onError != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                        }
                        return;
                    }
                    
                    // 更新 playlist 的 sourcePaths，以便下次直接使用（可选）
                    // JSONArray jsonArray = new JSONArray(sourcePaths);
                    // playlist.setSourcePaths(jsonArray.toString());
                    // playlistDao.update(playlist);
                }
                
                Log.d(TAG, "确定扫描路径: " + sourcePaths);
                
                // 3. 递归获取所有文件
                List<com.baidu.gallery.car.model.FileInfo> allFiles = new java.util.ArrayList<>();
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(sourcePaths.size());
                final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);
                
                for (String path : sourcePaths) {
                    FileRepository.getInstance().fetchFilesRecursive(accessToken, path, new FileRepository.FileListCallback() {
                        @Override
                        public void onSuccess(List<com.baidu.gallery.car.model.FileInfo> files) {
                            synchronized (allFiles) {
                                allFiles.addAll(files);
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(String error) {
                            Log.e(TAG, "获取目录文件失败: " + path + ", error: " + error);
                            // 即使某个目录失败，也尝试继续处理其他目录
                            latch.countDown();
                        }
                    });
                }
                
                try {
                    // 等待所有文件获取完成，最长等待5分钟
                    latch.await(5, java.util.concurrent.TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Log.e(TAG, "等待文件获取被中断", e);
                    hasError.set(true);
                }
                
                if (hasError.get()) {
                    if (onError != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                    }
                    return;
                }
                
                Log.d(TAG, "获取到新文件列表，总数: " + allFiles.size());
                
                // 4. 过滤并转换为播放列表项
                List<PlaylistItem> newItems = new java.util.ArrayList<>();
                long totalDuration = 0;
                int videoCount = 0;
                int imageCount = 0;
                
                // 检查播放列表类型
                int targetMediaType = playlist.getMediaType();
                
                for (int i = 0; i < allFiles.size(); i++) {
                    com.baidu.gallery.car.model.FileInfo fileInfo = allFiles.get(i);
                    
                    // 根据播放列表类型过滤
                    boolean shouldAdd = false;
                    if (targetMediaType == 0) { // 混合
                        shouldAdd = fileInfo.isVideo() || fileInfo.isImage();
                    } else if (targetMediaType == 1) { // 视频
                        shouldAdd = fileInfo.isVideo();
                    } else if (targetMediaType == 2) { // 图片
                        shouldAdd = fileInfo.isImage();
                    }
                    
                    if (shouldAdd) {
                        PlaylistItem item = new PlaylistItem();
                        item.setPlaylistId(playlist.getId());
                        item.setFsId(fileInfo.getFsId());
                        item.setFilePath(fileInfo.getPath());
                        item.setFileName(fileInfo.getServerFilename());
                        item.setFileSize(fileInfo.getSize());
                        item.setSortOrder(i);
                        
                        if (fileInfo.isVideo()) {
                            item.setMediaType(1);
                            item.setDuration(0); // FileInfo通常没有时长
                            videoCount++;
                        } else if (fileInfo.isImage()) {
                            item.setMediaType(2);
                            imageCount++;
                        }
                        
                        newItems.add(item);
                    }
                }
                
                // 5. 更新数据库
                AppDatabase.getInstance(null).runInTransaction(() -> {
                    // 删除旧项
                    playlistItemDao.deleteByPlaylistId(playlist.getId());
                    
                    // 插入新项
                    playlistItemDao.insertAll(newItems);
                    
                    // 更新播放列表统计信息
                    playlist.setTotalItems(newItems.size());
                    
                    // 如果列表原本没有封面，且有新项，尝试设置封面（简单逻辑）
                    if (playlist.getCoverImagePath() == null || playlist.getCoverImagePath().isEmpty()) {
                        if (!newItems.isEmpty()) {
                            // 这里可以考虑设置一个标志位或者默认封面逻辑
                        }
                    }
                    
                    playlistDao.update(playlist);
                });
                
                Log.d(TAG, "播放列表刷新完成，新文件数: " + newItems.size());
                
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "刷新播放列表失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }

    /**
     * 核心算法：推导最小扫描根集合
     * 策略：
     * 1. 提取所有歌曲的直接父文件夹路径
     * 2. 相互比较，如果路径A包含路径B（即A是B的父级），则只保留A
     * 3. 最终留下的就是最顶层的文件夹集合
     */
    private Set<String> calculateSyncRoots(List<PlaylistItem> items) {
        // 1. 收集所有唯一的父路径
        Set<String> parentPaths = new HashSet<>();
        for (PlaylistItem item : items) {
            String parent = getParentPath(item.getFilePath());
            if (!TextUtils.isEmpty(parent)) {
                parentPaths.add(parent);
            }
        }

        // 2. 转换为List以便排序和比较
        List<String> sortedPaths = new ArrayList<>(parentPaths);
        // 按长度排序，短的在前（父目录通常比子目录短）
        Collections.sort(sortedPaths);

        // 3. 过滤被包含的路径
        Set<String> roots = new HashSet<>();
        for (String path : sortedPaths) {
            boolean isChild = false;
            // 检查当前path是否是roots中某个路径的子路径
            for (String root : roots) {
                // 判断逻辑：root是path的前缀，且path的下一个字符是'/'（或者是完全相等）
                if (path.startsWith(root)) {
                    if (path.length() == root.length() || path.charAt(root.length()) == '/') {
                        isChild = true;
                        break;
                    }
                }
            }
            
            if (!isChild) {
                roots.add(path);
            }
        }

        return roots;
    }

    private String getParentPath(String path) {
        if (TextUtils.isEmpty(path) || path.equals("/")) return null;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == 0) return "/"; // 父路径是根
        if (lastSlash > 0) return path.substring(0, lastSlash);
        return null;
    }
}