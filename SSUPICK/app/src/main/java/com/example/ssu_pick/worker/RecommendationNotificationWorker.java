package com.example.ssu_pick.worker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.model.NoticePage;
import com.example.ssu_pick.model.ServerStatus;
import com.example.ssu_pick.network.ApiClient;
import com.example.ssu_pick.network.ApiService;
import com.example.ssu_pick.util.MatchScoreCalculator;
import com.example.ssu_pick.util.NotificationHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Response;

public class RecommendationNotificationWorker
        extends Worker {

    private static final String PREF_NAME =
            "SSUPICK_PREFS";

    private static final String KEY_LAST_VERSION =
            "recommendation_last_version";

    // 추천 알림 기준
    private static final int MIN_SCORE = 80;


    public RecommendationNotificationWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params
    ) {

        super(context, params);
    }


    @NonNull
    @Override
    public Result doWork() {

        Context context =
                getApplicationContext();


        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );


        // 추천 알림 OFF면 아무것도 하지 않음
        boolean enabled =
                prefs.getBoolean(
                        "recommendation_notifications",
                        false
                );


        if (!enabled) {
            return Result.success();
        }


        try {

            ApiService apiService =
                    ApiClient
                            .getClient()
                            .create(
                                    ApiService.class
                            );


            long lastVersion =
                    prefs.getLong(
                            KEY_LAST_VERSION,
                            -1
                    );


            /*
             * 최초 실행
             *
             * 지금까지 존재하던 과거 공지를
             * 전부 새 공지로 취급하면 안 되므로
             * 현재 최신 버전만 저장하고 종료.
             */
            if (lastVersion == -1) {

                Response<ServerStatus> statusResponse =
                        apiService
                                .getStatus()
                                .execute();


                if (!statusResponse.isSuccessful()
                        || statusResponse.body() == null) {

                    return Result.retry();
                }


                prefs.edit()
                        .putLong(
                                KEY_LAST_VERSION,
                                statusResponse.body()
                                        .latestVersion
                        )
                        .apply();


                return Result.success();
            }


            // 마지막 확인 이후 변경된 공지만 받기
            Response<NoticePage> response =
                    apiService
                            .getNotices(
                                    lastVersion,
                                    500,
                                    null
                            )
                            .execute();


            if (!response.isSuccessful()
                    || response.body() == null
                    || response.body().items == null) {

                return Result.retry();
            }


            NoticePage page =
                    response.body();


            List<Notice> notices =
                    page.items;


            // -------------------------
            // 사용자 프로필
            // -------------------------

            String major =
                    prefs.getString(
                            "major",
                            ""
                    );

            int grade =
                    prefs.getInt(
                            "grade",
                            0
                    );

            String region =
                    prefs.getString(
                            "region",
                            ""
                    );


            Set<String> interests =
                    prefs.getStringSet(
                            "interests",
                            new HashSet<>()
                    );


            Set<String> selectedSources =
                    prefs.getStringSet(
                            "selected_sources",
                            new HashSet<>()
                    );

            int age =
                    prefs.getInt(
                            "age",
                            0
                    );

            String studentStatus =
                    prefs.getString(
                            "student_status",
                            ""
                    );


            // -------------------------
            // 새 공지 검사
            // -------------------------

            for (Notice notice : notices) {

                if (notice.id == null) {
                    continue;
                }


                // 선택한 출처가 아니면 제외
                if (!selectedSources.isEmpty()
                        && !selectedSources.contains(
                        notice.source
                )) {

                    continue;
                }


                // 이미 마감된 공지는 제외
                if ("NONE".equals(
                        notice.deadlineStatus
                )) {

                    continue;
                }


                // 추천 점수
                notice.matchScore =
                        MatchScoreCalculator.calculate(
                                notice,
                                major,
                                grade,
                                region,
                                interests,
                                age,
                                studentStatus
                        );


                // 80점 미만은 알림 X
                if (notice.matchScore
                        < MIN_SCORE) {

                    continue;
                }


                // 같은 공지 중복 알림 방지
                String notifiedKey =
                        "recommend_notified_"
                                + notice.id;


                if (prefs.getBoolean(
                        notifiedKey,
                        false
                )) {

                    continue;
                }


                NotificationHelper
                        .showRecommendationNotification(
                                context,
                                notice
                        );


                prefs.edit()
                        .putBoolean(
                                notifiedKey,
                                true
                        )
                        .apply();
            }


            // 이번에 확인한 버전 저장
            prefs.edit()
                    .putLong(
                            KEY_LAST_VERSION,
                            page.nextVersion
                    )
                    .apply();


            return Result.success();


        } catch (Exception e) {

            return Result.retry();
        }
    }
}