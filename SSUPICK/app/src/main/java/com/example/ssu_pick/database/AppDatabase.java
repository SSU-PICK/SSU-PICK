package com.example.ssu_pick.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                NoticeEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase
        extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;


    public abstract NoticeDao noticeDao();


    public static AppDatabase getInstance(
            Context context
    ) {

        if (INSTANCE == null) {

            synchronized (AppDatabase.class) {

                if (INSTANCE == null) {

                    INSTANCE =
                            Room.databaseBuilder(
                                            context.getApplicationContext(),
                                            AppDatabase.class,
                                            "ssupick_database"
                                    )

                                    // 지금은 개발 단계라 DB 구조 변경 시
                                    // 기존 캐시를 지우고 새로 생성
                                    .fallbackToDestructiveMigration()

                                    .build();
                }
            }
        }


        return INSTANCE;
    }
}