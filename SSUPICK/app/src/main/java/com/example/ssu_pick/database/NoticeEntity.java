package com.example.ssu_pick.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notices")
public class NoticeEntity {

    @PrimaryKey
    @NonNull
    public String id;

    public String source;
    public String origin;
    public String url;
    public String title;
    public String topic;
    public String postedAt;
    public String updatedAt;
    public String deadlineStatus;
    public String deadlineAt;
    public String summary;
    public boolean needsReview;
    public String contentKind;

    // eligibility / extra / attachments 등을 포함한
    // 서버 Notice 전체 JSON
    public String rawJson;


    public NoticeEntity(
            @NonNull String id,
            String source,
            String origin,
            String url,
            String title,
            String topic,
            String postedAt,
            String updatedAt,
            String deadlineStatus,
            String deadlineAt,
            String summary,
            boolean needsReview,
            String contentKind,
            String rawJson
    ) {

        this.id = id;
        this.source = source;
        this.origin = origin;
        this.url = url;
        this.title = title;
        this.topic = topic;
        this.postedAt = postedAt;
        this.updatedAt = updatedAt;
        this.deadlineStatus = deadlineStatus;
        this.deadlineAt = deadlineAt;
        this.summary = summary;
        this.needsReview = needsReview;
        this.contentKind = contentKind;
        this.rawJson = rawJson;
    }
}