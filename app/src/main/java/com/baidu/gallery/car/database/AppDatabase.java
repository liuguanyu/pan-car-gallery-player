package com.baidu.gallery.car.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.baidu.gallery.car.model.PlaybackHistory;
import com.baidu.gallery.car.model.Playlist;
import com.baidu.gallery.car.model.PlaylistItem;

@Database(entities = {PlaybackHistory.class, Playlist.class, PlaylistItem.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract PlaybackHistoryDao playbackHistoryDao();
    public abstract PlaylistDao playlistDao();
    public abstract PlaylistItemDao playlistItemDao();
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "baidu_tv_player.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    
    public static AppDatabase getDatabase(Context context) {
        return getInstance(context);
    }
}