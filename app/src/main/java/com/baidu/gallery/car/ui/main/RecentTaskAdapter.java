package com.baidu.gallery.car.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.baidu.gallery.car.R;
import com.baidu.gallery.car.model.MediaType;
import com.baidu.gallery.car.model.PlaybackHistory;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近任务适配器
 */
public class RecentTaskAdapter extends RecyclerView.Adapter<RecentTaskAdapter.ViewHolder> {
    
    private List<PlaybackHistory> historyList = new ArrayList<>();
    private OnItemClickListener listener;
    private String accessToken;
    
    public interface OnItemClickListener {
        void onItemClick(PlaybackHistory history);
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    public void setHistoryList(List<PlaybackHistory> historyList) {
        this.historyList = historyList != null ? historyList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_task, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaybackHistory history = historyList.get(position);
        holder.bind(history, accessToken); // Pass accessToken to bind method
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(history);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return historyList.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBackground;
        TextView tvFileName;
        TextView tvFilePath;
        TextView tvMediaType;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBackground = itemView.findViewById(R.id.iv_background);
            tvFileName = itemView.findViewById(R.id.tv_folder_name); // 复用这个TextView显示文件名
            tvFilePath = itemView.findViewById(R.id.tv_folder_path); // 复用这个TextView显示文件路径
            tvMediaType = itemView.findViewById(R.id.tv_media_type);
        }
        
        void bind(PlaybackHistory history, String accessToken) {
            tvFileName.setText(history.getFileName());
            tvFilePath.setText(history.getFilePath());
            
            MediaType mediaType = MediaType.fromCode(history.getMediaType());
            tvMediaType.setText(mediaType.getName());
            
            // 加载缩略图
            String thumbnailUrl = history.getThumbnailUrl();
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                // 处理Access Token
                if (accessToken != null && !accessToken.isEmpty()) {
                    if (thumbnailUrl.contains("access_token=")) {
                        thumbnailUrl = thumbnailUrl.replaceAll("access_token=[^&]*", "access_token=" + accessToken);
                    } else {
                        thumbnailUrl += (thumbnailUrl.contains("?") ? "&" : "?") + "access_token=" + accessToken;
                    }
                }

                // 添加User-Agent头
                GlideUrl glideUrl = new GlideUrl(thumbnailUrl, new LazyHeaders.Builder()
                        .addHeader("User-Agent", "pan.baidu.com")
                        .build());

                Glide.with(itemView.getContext())
                        .load(glideUrl)
                        .centerCrop()
                        .placeholder(android.R.color.darker_gray)
                        .error(android.R.color.darker_gray)
                        .fallback(android.R.color.darker_gray)
                        .into(ivBackground);
            } else {
                // 如果没有缩略图，显示默认背景
                ivBackground.setImageResource(android.R.color.darker_gray);
            }
        }
    }
}