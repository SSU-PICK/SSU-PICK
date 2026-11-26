package com.example.ssu_pick.database;

import com.example.ssu_pick.model.Notice;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class NoticeMapper {

    private static final Gson gson =
            new Gson();


    public static NoticeEntity toEntity(
            Notice notice
    ) {

        return new NoticeEntity(
                notice.id,
                notice.source,
                notice.origin,
                notice.url,
                notice.title,
                notice.topic,
                notice.postedAt,
                notice.updatedAt,
                notice.deadlineStatus,
                notice.deadlineAt,
                notice.summary,
                notice.needsReview,
                notice.contentKind,

                // Notice 전체 저장
                gson.toJson(notice)
        );
    }


    public static List<NoticeEntity> toEntityList(
            List<Notice> notices
    ) {

        List<NoticeEntity> result =
                new ArrayList<>();


        if (notices == null) {
            return result;
        }


        for (Notice notice : notices) {

            if (notice != null
                    && notice.id != null) {

                result.add(
                        toEntity(notice)
                );
            }
        }


        return result;
    }


    public static Notice toNotice(
            NoticeEntity entity
    ) {

        // 전체 JSON이 있으면 원래 Notice 그대로 복원
        if (entity.rawJson != null
                && !entity.rawJson.isEmpty()) {

            try {

                Notice notice =
                        gson.fromJson(
                                entity.rawJson,
                                Notice.class
                        );

                if (notice != null) {
                    return notice;
                }

            } catch (Exception ignored) {

            }
        }


        // 혹시 JSON 복원 실패했을 때 기본 필드라도 복원
        Notice notice =
                new Notice();

        notice.id = entity.id;
        notice.source = entity.source;
        notice.origin = entity.origin;
        notice.url = entity.url;
        notice.title = entity.title;
        notice.topic = entity.topic;
        notice.postedAt = entity.postedAt;
        notice.updatedAt = entity.updatedAt;
        notice.deadlineStatus = entity.deadlineStatus;
        notice.deadlineAt = entity.deadlineAt;
        notice.summary = entity.summary;
        notice.needsReview = entity.needsReview;
        notice.contentKind = entity.contentKind;

        return notice;
    }


    public static List<Notice> toNoticeList(
            List<NoticeEntity> entities
    ) {

        List<Notice> result =
                new ArrayList<>();


        if (entities == null) {
            return result;
        }


        for (NoticeEntity entity : entities) {

            result.add(
                    toNotice(entity)
            );
        }


        return result;
    }
}