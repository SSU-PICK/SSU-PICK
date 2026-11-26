package com.example.ssu_pick.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.ssu_pick.database.AppDatabase;
import com.example.ssu_pick.database.NoticeEntity;
import com.example.ssu_pick.database.NoticeMapper;
import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.model.NoticePage;
import com.example.ssu_pick.network.ApiClient;
import com.example.ssu_pick.network.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NoticeRepository {

    private static final String PREF_NAME =
            "SSUPICK_PREFS";

    private static final String KEY_NOTICE_VERSION =
            "notice_last_version";

    private static final int SYNC_LIMIT =
            500;


    private final Context context;

    private final ApiService apiService;

    private final AppDatabase database;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );


    public NoticeRepository(
            Context context
    ) {

        this.context =
                context.getApplicationContext();

        apiService =
                ApiClient
                        .getClient()
                        .create(
                                ApiService.class
                        );

        database =
                AppDatabase.getInstance(
                        this.context
                );
    }


    // =====================================
    // 목록 Callback
    // =====================================

    public interface NoticeListCallback {

        void onSuccess(
                List<Notice> notices
        );

        void onError(
                String message
        );
    }


    // =====================================
    // 전체 공지
    //
    // 1. Room 캐시 즉시 제공
    // 2. AWS 증분 동기화
    // 3. 변경됐으면 최신 Room 목록 다시 제공
    // =====================================

    public void getAllNotices(
            NoticeListCallback callback
    ) {

        executor.execute(() -> {

            List<NoticeEntity> cachedEntities =
                    database
                            .noticeDao()
                            .getAll();


            List<Notice> cachedNotices =
                    NoticeMapper.toNoticeList(
                            cachedEntities
                    );


            boolean hasCache =
                    !cachedNotices.isEmpty();


            // 캐시가 있으면 먼저 즉시 화면에 표시
            if (hasCache) {

                mainHandler.post(() ->
                        callback.onSuccess(
                                cachedNotices
                        )
                );
            }


            // 그 뒤 서버와 동기화
            syncFromServer(
                    callback,
                    hasCache
            );
        });
    }


    // =====================================
    // 서버 증분 동기화
    // =====================================

    private void syncFromServer(
            NoticeListCallback callback,
            boolean hasCache
    ) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );


        long cursor =
                prefs.getLong(
                        KEY_NOTICE_VERSION,
                        0
                );


        boolean anyChanged =
                false;


        try {

            while (true) {

                long requestCursor =
                        cursor;


                Response<NoticePage> response =
                        apiService
                                .getNotices(
                                        requestCursor,
                                        SYNC_LIMIT,
                                        null
                                )
                                .execute();


                if (!response.isSuccessful()
                        || response.body() == null) {

                    if (!hasCache) {

                        postError(
                                callback,
                                "서버 응답 오류: "
                                        + response.code()
                        );
                    }

                    return;
                }


                NoticePage page =
                        response.body();


                List<Notice> changedNotices =
                        page.items != null
                                ? page.items
                                : new ArrayList<>();


                // 새 공지 / 변경 공지 Room 저장
                if (!changedNotices.isEmpty()) {

                    database
                            .noticeDao()
                            .insertAll(
                                    NoticeMapper.toEntityList(
                                            changedNotices
                                    )
                            );

                    anyChanged = true;
                }


                long nextVersion =
                        page.nextVersion;


                // 현재 버전 저장
                prefs.edit()
                        .putLong(
                                KEY_NOTICE_VERSION,
                                nextVersion
                        )
                        .apply();


                /*
                 * 서버 명세:
                 * 받은 개수가 limit과 같으면
                 * next_version으로 다음 요청
                 */
                if (changedNotices.size()
                        < SYNC_LIMIT) {

                    cursor =
                            nextVersion;

                    break;
                }


                // 혹시 서버가 같은 cursor를 반환하면
                // 무한루프 방지
                if (nextVersion
                        == requestCursor) {

                    break;
                }


                cursor =
                        nextVersion;
            }


            /*
             * 최초 실행:
             * 캐시가 없었으므로 서버 저장 후
             * Room 전체를 화면에 제공
             *
             * 기존 실행:
             * 서버에서 변경된 게 있으면
             * 최신 Room 데이터를 한 번 더 제공
             */
            if (!hasCache
                    || anyChanged) {

                List<NoticeEntity> latestEntities =
                        database
                                .noticeDao()
                                .getAll();


                List<Notice> latestNotices =
                        NoticeMapper.toNoticeList(
                                latestEntities
                        );


                mainHandler.post(() ->
                        callback.onSuccess(
                                latestNotices
                        )
                );
            }


        } catch (Exception e) {

            /*
             * 캐시가 있으면 인터넷이 실패해도
             * 기존 공지는 이미 보여줬으므로
             * 에러화면으로 바꾸지 않음.
             */
            if (!hasCache) {

                postError(
                        callback,
                        "네트워크 오류: "
                                + e.getMessage()
                );
            }
        }
    }


    private void postError(
            NoticeListCallback callback,
            String message
    ) {

        mainHandler.post(() ->
                callback.onError(
                        message
                )
        );
    }


    // =====================================
    // 상세
    // 상세는 body가 필요하므로 서버 직접 조회
    // =====================================

    public interface NoticeDetailCallback {

        void onSuccess(
                Notice notice
        );

        void onError(
                String message
        );
    }


    public void getNoticeDetail(
            String noticeId,
            NoticeDetailCallback callback
    ) {

        apiService
                .getNoticeDetail(
                        noticeId
                )
                .enqueue(
                        new Callback<Notice>() {

                            @Override
                            public void onResponse(
                                    Call<Notice> call,
                                    Response<Notice> response
                            ) {

                                if (response.isSuccessful()
                                        && response.body() != null) {

                                    callback.onSuccess(
                                            response.body()
                                    );

                                } else {

                                    callback.onError(
                                            "공지 조회 실패: "
                                                    + response.code()
                                    );
                                }
                            }


                            @Override
                            public void onFailure(
                                    Call<Notice> call,
                                    Throwable t
                            ) {

                                callback.onError(
                                        "네트워크 오류: "
                                                + t.getMessage()
                                );
                            }
                        }
                );
    }
}