package com.example.ssu_pick.util;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.ssu_pick.worker.DeadlineNotificationWorker;

import java.util.concurrent.TimeUnit;

public class NotificationScheduler {

    private static final String WORK_NAME =
            "ssupick_deadline_check";


    public static void schedule(
            Context context
    ) {

        Constraints constraints =
                new Constraints.Builder()
                        .setRequiredNetworkType(
                                NetworkType.CONNECTED
                        )
                        .build();


        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        DeadlineNotificationWorker.class,
                        24,
                        TimeUnit.HOURS
                )
                        .setConstraints(
                                constraints
                        )
                        .build();


        WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                );
    }


    public static void cancel(
            Context context
    ) {

        WorkManager
                .getInstance(context)
                .cancelUniqueWork(
                        WORK_NAME
                );
    }
}