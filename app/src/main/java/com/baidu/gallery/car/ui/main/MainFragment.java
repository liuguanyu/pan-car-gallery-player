package com.baidu.gallery.car.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.baidu.gallery.car.R;
import com.baidu.gallery.car.model.AuthInfo;
import com.baidu.gallery.car.model.MediaType;
import com.baidu.gallery.car.model.PlaybackHistory;
import com.baidu.gallery.car.model.Playlist;
import com.baidu.gallery.car.model.UserInfoResponse;
import com.baidu.gallery.car.network.ApiConstants;
import com.baidu.gallery.car.network.BaiduPanService;
import com.baidu.gallery.car.network.RetrofitClient;
import com.baidu.gallery.car.repository.PlaylistRepository;
import com.baidu.gallery.car.utils.PreferenceUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 主界面Fragment
 */
public class MainFragment extends Fragment {
    
    private MainViewModel viewModel;
    private RecentTaskAdapter recentTaskAdapter;
    private PlaylistAdapter playlistAdapter;
    private PlaylistRepository playlistRepository;
    private com.baidu.gallery.car.auth.AuthRepository authRepository;
    
    private RecyclerView rvPlaylists;
    private RecyclerView rvRecentTasks;
    private TextView tvNoPlaylist;
    private TextView tvNoRecentTask;
    private TextView tvPlaylistCount;
    private TextView tvRecentCount;
    private TextView btnClearRecent;
    private LinearLayout btnBrowseFiles;
    private LinearLayout btnCreatePlaylist;
    private LinearLayout btnSettings;
    private ImageView ivUserAvatar;
    private TextView tvUsername;
    private LinearLayout llUserInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        
        initViews(view);
        
        authRepository = new com.baidu.gallery.car.auth.AuthRepository(requireContext());
        
        initViewModel();
        loadPlaylists();
        
        return view;
    }
    
    private void initViews(View view) {
        rvPlaylists = view.findViewById(R.id.rv_playlists);
        rvRecentTasks = view.findViewById(R.id.rv_recent_tasks);
        tvNoPlaylist = view.findViewById(R.id.tv_no_playlist);
        tvNoRecentTask = view.findViewById(R.id.tv_no_recent_task);
        tvPlaylistCount = view.findViewById(R.id.tv_playlist_count);
        tvRecentCount = view.findViewById(R.id.tv_recent_count);
        btnBrowseFiles = view.findViewById(R.id.btn_browse_files);
        btnCreatePlaylist = view.findViewById(R.id.btn_create_playlist);
        btnSettings = view.findViewById(R.id.btn_settings);
        ivUserAvatar = view.findViewById(R.id.iv_user_avatar);
        tvUsername = view.findViewById(R.id.tv_username);
        llUserInfo = view.findViewById(R.id.ll_user_info);

        // 设置播放列表RecyclerView为网格布局，每行显示3个
        playlistAdapter = new PlaylistAdapter(requireContext());
        rvPlaylists.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rvPlaylists.setAdapter(playlistAdapter);
        // 启用RecyclerView的焦点搜索
        rvPlaylists.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        rvPlaylists.setHasFixedSize(true);
        
        // 设置最近任务RecyclerView为网格布局，每行显示4个
        recentTaskAdapter = new RecentTaskAdapter();
        rvRecentTasks.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        rvRecentTasks.setAdapter(recentTaskAdapter);
        // 启用RecyclerView的焦点搜索
        rvRecentTasks.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        rvRecentTasks.setFocusable(false);
        
        // 清空最近播放按钮
        btnClearRecent = view.findViewById(R.id.btn_clear_recent);
        btnClearRecent.setOnClickListener(v -> showClearRecentDialog());

        // 设置点击事件
        btnBrowseFiles.setOnClickListener(v -> openFileBrowser(MediaType.ALL));
        btnCreatePlaylist.setOnClickListener(v -> openFileBrowserForPlaylist());
        btnSettings.setOnClickListener(v -> openSettings());

        // 设置播放列表点击事件
        playlistAdapter.setOnItemClickListener(this::onPlaylistClick);
        playlistAdapter.setOnItemLongClickListener(this::onPlaylistLongClick);
        playlistAdapter.setOnDeleteClickListener(this::onPlaylistDelete);
        playlistAdapter.setOnRefreshClickListener(this::onPlaylistRefresh);
        
        // 设置最近任务点击事件
        recentTaskAdapter.setOnItemClickListener(this::onRecentTaskClick);
        
        // 加载用户信息
        loadUserInfo();
        
        // 用户头像区域点击事件 - 可以跳转到设置页面查看用户信息
        llUserInfo.setOnClickListener(v -> openSettings());
    }
    
    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        
        viewModel.getRecentHistory().observe(getViewLifecycleOwner(), historyList -> {
            if (historyList == null || historyList.isEmpty()) {
                tvNoRecentTask.setVisibility(View.VISIBLE);
                rvRecentTasks.setVisibility(View.GONE);
                tvRecentCount.setText("0 个");
            } else {
                tvNoRecentTask.setVisibility(View.GONE);
                rvRecentTasks.setVisibility(View.VISIBLE);
                tvRecentCount.setText(historyList.size() + " 个");
                recentTaskAdapter.setAccessToken(authRepository.getAccessToken());
                recentTaskAdapter.setHistoryList(historyList);
            }
        });
    }
    
    /**
     * 加载播放列表
     */
    private void loadPlaylists() {
        playlistRepository = new PlaylistRepository(requireContext());
        playlistRepository.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            // 播放列表标题始终显示
            if (playlists == null || playlists.isEmpty()) {
                rvPlaylists.setVisibility(View.GONE);
                tvNoPlaylist.setVisibility(View.VISIBLE);
                tvPlaylistCount.setText("0 个");
                // 没有播放列表，默认聚焦到浏览文件按钮
                btnBrowseFiles.post(() -> btnBrowseFiles.requestFocus());
            } else {
                rvPlaylists.setVisibility(View.VISIBLE);
                tvNoPlaylist.setVisibility(View.GONE);
                tvPlaylistCount.setText(playlists.size() + " 个");
                playlistAdapter.setPlaylists(playlists);
                // 有播放列表，延迟请求第一个项的焦点
                rvPlaylists.post(() -> {
                    View firstChild = rvPlaylists.getLayoutManager().findViewByPosition(0);
                    if (firstChild != null) {
                        firstChild.requestFocus();
                    }
                });
            }
        });
    }
    
    /**
     * 打开文件浏览器（普通模式）
     */
    private void openFileBrowser(MediaType mediaType) {
        Intent intent = new Intent(requireContext(), com.baidu.gallery.car.ui.filebrowser.FileBrowserActivity.class);
        intent.putExtra("mediaType", mediaType.getValue());
        intent.putExtra("initialPath", "/");
        startActivity(intent);
    }
    
    /**
     * 打开文件浏览器（创建播放列表模式）
     */
    private void openFileBrowserForPlaylist() {
        Intent intent = new Intent(requireContext(), com.baidu.gallery.car.ui.filebrowser.FileBrowserActivity.class);
        intent.putExtra("mediaType", MediaType.ALL.getValue());
        intent.putExtra("initialPath", "/");
        intent.putExtra("multiSelectMode", true); // 多选模式
        startActivityForResult(intent, 1001); // 请求码用于标识创建播放列表操作
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 处理从FileBrowserActivity返回的结果
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
            // 播放列表创建成功，刷新播放列表显示
            loadPlaylists();
        }
    }
    
    /**
     * 播放列表点击事件
     */
    private void onPlaylistClick(Playlist playlist) {
        // 如果处于编辑模式，点击无效
        if (playlistAdapter.isEditMode()) {
            return;
        }
        
        // 启动播放器，播放该播放列表
        Intent intent = new Intent(requireContext(), com.baidu.gallery.car.ui.playback.PlaybackActivity.class);
        intent.putExtra("playlistDatabaseId", playlist.getId());
        startActivity(intent);
    }
    
    /**
     * 播放列表长按事件 - 切换编辑模式
     */
    private void onPlaylistLongClick(Playlist playlist) {
        // 切换编辑模式
        boolean newEditMode = !playlistAdapter.isEditMode();
        playlistAdapter.setEditMode(newEditMode);
        
        if (newEditMode) {
            android.widget.Toast.makeText(requireContext(),
                "点击删除按钮删除播放列表，再次长按退出编辑模式",
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 播放列表删除事件
     */
    private void onPlaylistDelete(Playlist playlist) {
        // 显示确认对话框
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("删除播放列表")
            .setMessage("确定要删除播放列表\"" + playlist.getName() + "\"吗？\n这将删除播放列表及其所有文件记录。")
            .setPositiveButton("删除", (dialog, which) -> {
                // 执行删除操作
                new Thread(() -> {
                    try {
                        playlistRepository.deletePlaylist(playlist,
                            () -> {
                                // 删除成功
                                requireActivity().runOnUiThread(() -> {
                                    android.widget.Toast.makeText(requireContext(),
                                        "播放列表已删除",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                    
                                    // 退出编辑模式
                                    playlistAdapter.setEditMode(false);
                                    
                                    // 刷新播放列表
                                    loadPlaylists();
                                });
                            },
                            () -> {
                                // 删除失败
                                requireActivity().runOnUiThread(() -> {
                                    android.widget.Toast.makeText(requireContext(),
                                        "删除播放列表失败",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                });
                            });
                    } catch (Exception e) {
                        android.util.Log.e("MainFragment", "删除播放列表失败", e);
                        requireActivity().runOnUiThread(() -> {
                            android.widget.Toast.makeText(requireContext(),
                                "删除播放列表失败: " + e.getMessage(),
                                android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 播放列表刷新事件
     */
    private void onPlaylistRefresh(Playlist playlist) {
        // 显示刷新提示
        android.widget.Toast.makeText(requireContext(),
            "正在刷新播放列表\"" + playlist.getName() + "\"...",
            android.widget.Toast.LENGTH_SHORT).show();
        
        // 执行刷新操作（BaiduAuthService会在PlaylistRepository内部处理认证）
        playlistRepository.refreshPlaylist(playlist,
            () -> {
                // 刷新成功
                requireActivity().runOnUiThread(() -> {
                    android.widget.Toast.makeText(requireContext(),
                        "播放列表刷新成功",
                        android.widget.Toast.LENGTH_SHORT).show();
                    
                    // 刷新播放列表显示
                    loadPlaylists();
                });
            },
            () -> {
                // 刷新失败
                requireActivity().runOnUiThread(() -> {
                    android.widget.Toast.makeText(requireContext(),
                        "刷新播放列表失败，请检查网络连接或重新登录",
                        android.widget.Toast.LENGTH_SHORT).show();
                });
            });
    }
    
    /**
     * 最近任务点击事件
     */
    private void onRecentTaskClick(PlaybackHistory history) {
        // 直接传递fsId和文件信息到PlaybackActivity
        Intent intent = new Intent(requireContext(), com.baidu.gallery.car.ui.playback.PlaybackActivity.class);
        intent.putExtra("historyId", history.getId());
        // 传递关键信息作为备份，防止历史记录ID加载失败
        intent.putExtra("filePath", history.getFilePath());
        intent.putExtra("fileName", history.getFileName());
        intent.putExtra("fsId", history.getFsId());
        intent.putExtra("mediaType", history.getMediaType());
        startActivity(intent);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 确保焦点在可见的元素上
        requestFocusOnVisibleElement();
        // 刷新用户信息
        loadUserInfo();
    }
    
    /**
     * 加载用户信息（头像和用户名）
     */
    private void loadUserInfo() {
        if (authRepository == null || !authRepository.isAuthenticated()) {
            // 未登录状态，显示默认
            tvUsername.setText("用户");
            ivUserAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
            ivUserAvatar.setImageTintList(android.content.res.ColorStateList.valueOf(0x80FFFFFF));
            return;
        }
        
        // 先从本地AuthInfo获取缓存的用户名
        AuthInfo authInfo = authRepository.getAuthInfo();
        if (authInfo != null && authInfo.getUsername() != null && !authInfo.getUsername().isEmpty()) {
            tvUsername.setText(authInfo.getUsername());
        }
        
        // 从网络获取最新的用户信息（包括头像）
        String accessToken = authRepository.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            return;
        }
        
        BaiduPanService service = RetrofitClient.getPanApiInstance().create(BaiduPanService.class);
        service.getUserInfo("uinfo", accessToken).enqueue(new Callback<UserInfoResponse>() {
            @Override
            public void onResponse(Call<UserInfoResponse> call, Response<UserInfoResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    UserInfoResponse userInfo = response.body();
                    
                    // 更新用户名
                    String displayName = userInfo.getNetdiskName();
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = userInfo.getBaiduName();
                    }
                    if (displayName != null && !displayName.isEmpty()) {
                        tvUsername.setText(displayName);
                    }
                    
                    // 加载用户头像
                    String avatarUrl = userInfo.getAvatarUrl();
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        // 清除tint以显示真实头像颜色
                        ivUserAvatar.setImageTintList(null);
                        
                        Glide.with(requireContext())
                            .load(avatarUrl)
                            .apply(new RequestOptions()
                                .transform(new CircleCrop())
                                .placeholder(android.R.drawable.ic_menu_myplaces)
                                .error(android.R.drawable.ic_menu_myplaces))
                            .into(ivUserAvatar);
                    }
                }
            }
            
            @Override
            public void onFailure(Call<UserInfoResponse> call, Throwable t) {
                android.util.Log.e("MainFragment", "Failed to load user info", t);
            }
        });
    }
    
    /**
     * 显示清空最近播放确认对话框
     */
    private void showClearRecentDialog() {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("清空最近播放")
            .setMessage("确定要清空所有最近播放记录吗？此操作不可撤销。")
            .setPositiveButton("清空", (dialog, which) -> {
                // 执行清空操作
                viewModel.clearAllHistory();
                android.widget.Toast.makeText(requireContext(),
                    "已清空最近播放记录",
                    android.widget.Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 打开设置界面
     */
    private void openSettings() {
        Intent intent = new Intent(requireContext(), com.baidu.gallery.car.ui.settings.SettingsActivity.class);
        startActivity(intent);
    }
    
    /**
     * 请求焦点到可见的元素
     */
    private void requestFocusOnVisibleElement() {
        // 延迟执行，确保布局已完成
        getView().post(() -> {
            // 优先聚焦到播放列表的第一个项
            if (rvPlaylists.getVisibility() == View.VISIBLE && rvPlaylists.getAdapter() != null
                && rvPlaylists.getAdapter().getItemCount() > 0) {
                View firstChild = rvPlaylists.getLayoutManager().findViewByPosition(0);
                if (firstChild != null && firstChild.requestFocus()) {
                    return;
                }
            }
            // 如果没有播放列表，聚焦到浏览文件按钮
            if (btnBrowseFiles.getVisibility() == View.VISIBLE && btnBrowseFiles.requestFocus()) {
                return;
            }
            // 最后尝试聚焦到创建播放列表按钮
            if (btnCreatePlaylist.getVisibility() == View.VISIBLE) {
                btnCreatePlaylist.requestFocus();
            }
        });
    }
}