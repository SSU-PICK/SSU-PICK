package com.example.ssu_pick.worker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.model.NoticePage;
import com.example.ssu_pick.network.ApiClient;
import com.example.ssu_pick.network.ApiService;
import com.example.ssu_pick.util.BookmarkManager;
import com.example.ssu_pick.util.NotificationHelper;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import retrofit2.Response;

public class DeadlineNotificationWorker
        extends Worker {

    public DeadlineNotificationWorker(
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
                        "SSUPICK_PREFS",
                        Context.MODE_PRIVATE
                );


        // 사용자가 마감 알림을 껐으면 종료
        boolean enabled =
                prefs.getBoolean(
                        "deadline_notifications",
                        false
                );


        if (!enabled) {
            return Result.success();
        }


        // 관심등록한 공지 ID
        Set<String> bookmarkIds =
                BookmarkManager.getBookmarks(
                        context
                );


        if (bookmarkIds.isEmpty()) {
            return Result.success();
        }


        try {

            ApiService apiService =
                    ApiClient
                            .getClient()
                            .create(
                                    ApiService.class
                            );


            /*
             * Worker는 백그라운드 스레드에서 실행되므로
             * Retrofit execute() 사용 가능
             */
            Response<NoticePage> response =
                    apiService
                            .getNotices(
                                    0,
                                    500,
                                    null
                            )
                            .execute();


            if (!response.isSuccessful()
                    || response.body() == null
                    || response.body().items == null) {

                return Result.retry();
            }


            List<Notice> notices =
                    response.body().items;


            for (Notice notice : notices) {

                if (notice.id == null
                        || !bookmarkIds.contains(
                        notice.id
                )) {

                    continue;
                }


                long daysLeft =
                        getDaysLeft(notice);


                // 우리가 원하는 알림 시점
                if (daysLeft != 7
                        && daysLeft != 3
                        && daysLeft != 1) {

                    continue;
                }


                /*
                 * 같은 D-day 알림을 하루 동안
                 * Worker가 다시 실행해도 중복 전송하지 않음.
                 *
                 * deadlineAt까지 key에 포함해서
                 * 마감일이 바뀌면 새로운 알림 가능.
                 */
                String notificationKey =
                        "notified_"
                                + notice.id
                                + "_"
                                + notice.deadlineAt
                                + "_"
                                + daysLeft;


                boolean alreadySent =
                        prefs.getBoolean(
                                notificationKey,
                                false
                        );


                if (alreadySent) {
                    continue;
                }


                NotificationHelper
                        .showDeadlineNotification(
                                context,
                                notice,
                                daysLeft
                        );


                prefs.edit()
                        .putBoolean(
                                notificationKey,
                                true
                        )
                        .apply();
            }


            return Result.success();


        } catch (Exception e) {

            return Result.retry();
        }
    }


    private long getDaysLeft(
            Notice notice
    ) {

        if (notice.deadlineAt == null) {
            return -1;
        }


        try {

            OffsetDateTime deadline =
                    OffsetDateTime.parse(
                            notice.deadlineAt
                    );


            OffsetDateTime now =
                    OffsetDateTime.now(
                            deadline.getOffset()
                    );


            return ChronoUnit.DAYS.between(
                    now.toLocalDate(),
                    deadline.toLocalDate()
            );


        } catch (Exception e) {

            return -1;
        }
    }
}