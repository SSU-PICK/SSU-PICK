package com.example.ssu_pick.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.ssu_pick.NoticeDetailActivity;
import com.example.ssu_pick.R;
import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.MainActivity;


public class NotificationHelper {

    private static final String CHANNEL_ID =
            "deadline_channel";

    private static final String CHANNEL_NAME =
            "마감 알림";

    public static void showRecommendationNotification(
            Context context,
            Notice notice
    ) {

        createNotificationChannel(context);

        Intent intent =
                new Intent(
                        context,
                        NoticeDetailActivity.class
                );

        intent.putExtra(
                "NOTICE_ID",
                notice.id
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );


        int requestCode =
                Math.abs(
                        (notice.id + "_recommend")
                                .hashCode()
                );


        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );


        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                R.drawable.ic_bookmark
                        )
                        .setContentTitle(
                                "새로운 맞춤 혜택이 등록됐어요"
                        )
                        .setContentText(
                                notice.title
                        )
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(
                                                notice.title
                                                        + "\n"
                                                        + notice.matchScore
                                                        + "% 일치"
                                        )
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .setContentIntent(
                                pendingIntent
                        );


        NotificationManagerCompat manager =
                NotificationManagerCompat.from(
                        context
                );


        int notificationId =
                Math.abs(
                        ("recommend_"
                                + notice.id)
                                .hashCode()
                );


        try {

            manager.notify(
                    notificationId,
                    builder.build()
            );

        } catch (SecurityException ignored) {

        }
    }


    public static void showDeadlineNotification(
            Context context,
            Notice notice,
            long daysLeft
    ) {

        createNotificationChannel(context);


        // 알림 클릭 → 해당 공지 상세
        Intent intent =
                new Intent(
                        context,
                        NoticeDetailActivity.class
                );

        intent.putExtra(
                "NOTICE_ID",
                notice.id
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );


        int requestCode =
                Math.abs(
                        (notice.id + daysLeft)
                                .hashCode()
                );


        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );


        String dDayText;

        if (daysLeft == 1) {
            dDayText = "내일 마감";
        } else {
            dDayText = "D-" + daysLeft;
        }


        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                R.drawable.ic_bookmark
                        )
                        .setContentTitle(
                                "SSU:PICK · " + dDayText
                        )
                        .setContentText(
                                notice.title
                        )
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(
                                                notice.title
                                        )
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .setContentIntent(
                                pendingIntent
                        );


        NotificationManagerCompat manager =
                NotificationManagerCompat.from(
                        context
                );


        int notificationId =
                Math.abs(
                        (notice.id
                                + "_"
                                + daysLeft)
                                .hashCode()
                );


        try {

            manager.notify(
                    notificationId,
                    builder.build()
            );

        } catch (SecurityException ignored) {

            // 알림 권한 거부 상태
        }
    }

    public static void showTestNotification(
            Context context
    ) {

        createNotificationChannel(context);


        Intent intent =
                new Intent(
                        context,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        9999,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );


        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                R.drawable.ic_bookmark
                        )
                        .setContentTitle(
                                "SSU:PICK 테스트 알림"
                        )
                        .setContentText(
                                "알림 기능이 정상적으로 작동하고 있습니다."
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .setContentIntent(
                                pendingIntent
                        );


        NotificationManagerCompat manager =
                NotificationManagerCompat.from(
                        context
                );


        try {

            manager.notify(
                    9999,
                    builder.build()
            );

        } catch (SecurityException ignored) {

        }
    }


    private static void createNotificationChannel(
            Context context
    ) {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "관심 공지의 D-7, D-3, D-1 마감 알림"
            );


            NotificationManager manager =
                    context.getSystemService(
                            NotificationManager.class
                    );

            manager.createNotificationChannel(
                    channel
            );
        }
    }
}