package com.example.ssu_pick.util;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.ssu_pick.worker.RecommendationNotificationWorker;

import java.util.concurrent.TimeUnit;

public class RecommendationScheduler {

    private static final String WORK_NAME =
            "ssupick_recommendation_check";


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
                        RecommendationNotificationWorker.class,
                        3,
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