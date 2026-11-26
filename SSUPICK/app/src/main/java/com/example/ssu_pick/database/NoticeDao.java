package com.example.ssu_pick.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NoticeDao {

    // 서버에서 받은 공지 저장
    // 같은 id가 있으면 갱신
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<NoticeEntity> notices);


    // 공지 전체
    @Query("SELECT * FROM notices ORDER BY updatedAt DESC")
    List<NoticeEntity> getAll();


    // 특정 공지
    @Query("SELECT * FROM notices WHERE id = :noticeId LIMIT 1")
    NoticeEntity getById(String noticeId);


    // 출처
    @Query("SELECT * FROM notices WHERE source = :source")
    List<NoticeEntity> getBySource(String source);


    // 카테고리
    @Query("SELECT * FROM notices WHERE topic = :topic")
    List<NoticeEntity> getByTopic(String topic);


    // 교내 / 교외
    @Query("SELECT * FROM notices WHERE origin = :origin")
    List<NoticeEntity> getByOrigin(String origin);


    // 제목 검색
    @Query(
            "SELECT * FROM notices " +
                    "WHERE title LIKE '%' || :keyword || '%' " +
                    "OR summary LIKE '%' || :keyword || '%'"
    )
    List<NoticeEntity> search(String keyword);


    // 개수
    @Query("SELECT COUNT(*) FROM notices")
    int getCount();


    // 개발/초기화용
    @Query("DELETE FROM notices")
    void deleteAll();
}