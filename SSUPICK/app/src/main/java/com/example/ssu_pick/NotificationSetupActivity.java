package com.example.ssu_pick;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.ssu_pick.util.NotificationScheduler;
import com.example.ssu_pick.util.RecommendationScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class NotificationSetupActivity
        extends AppCompatActivity {

    private SwitchMaterial switchDeadline;
    private SwitchMaterial switchRecommendation;

    private SharedPreferences prefs;

    private ActivityResultLauncher<String>
            notificationPermissionLauncher;


    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_notification_setup
        );


        // =====================================
        // View 연결
        // =====================================

        switchDeadline =
                findViewById(
                        R.id.switchDeadline
                );

        switchRecommendation =
                findViewById(
                        R.id.switchRecommendation
                );

        MaterialButton btnStart =
                findViewById(
                        R.id.btnStart
                );

        android.widget.TextView tvSkip =
                findViewById(
                        R.id.tvSkip
                );


        prefs =
                getSharedPreferences(
                        "SSUPICK_PREFS",
                        MODE_PRIVATE
                );


        // =====================================
        // 기존 설정값 불러오기
        // =====================================

        boolean onboardingCompleted =
                prefs.getBoolean(
                        "onboarding_completed",
                        false
                );


        if (onboardingCompleted) {

            switchDeadline.setChecked(
                    prefs.getBoolean(
                            "deadline_notifications",
                            false
                    )
            );

            switchRecommendation.setChecked(
                    prefs.getBoolean(
                            "recommendation_notifications",
                            false
                    )
            );


            // 설정에서 다시 들어온 경우
            btnStart.setText(
                    "설정 저장하기"
            );

            tvSkip.setText(
                    "알림 모두 끄기"
            );

        } else {

            // 최초 가입 시 기본값
            switchDeadline.setChecked(
                    true
            );

            switchRecommendation.setChecked(
                    true
            );
        }


        // =====================================
        // Android 13+ 알림 권한 요청 결과
        // =====================================

        notificationPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts
                                .RequestPermission(),

                        granted -> {

                            if (granted) {

                                saveSettingsAndSchedule();

                            } else {

                                // OS 권한 자체가 거부된 경우
                                prefs.edit()
                                        .putBoolean(
                                                "deadline_notifications",
                                                false
                                        )
                                        .putBoolean(
                                                "recommendation_notifications",
                                                false
                                        )
                                        .apply();

                                NotificationScheduler
                                        .cancel(this);

                                RecommendationScheduler
                                        .cancel(this);
                            }


                            openHome();
                        }
                );


        // =====================================
        // 시작 / 설정 저장
        // =====================================

        btnStart.setOnClickListener(v -> {

            boolean wantsNotification =
                    switchDeadline.isChecked()
                            || switchRecommendation.isChecked();


            // 알림을 하나라도 사용할 경우
            if (wantsNotification
                    && Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.TIRAMISU) {

                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

                    notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                    );

                    return;
                }
            }


            saveSettingsAndSchedule();

            openHome();
        });


        // =====================================
        // 알림 모두 끄기
        // =====================================

        tvSkip.setOnClickListener(v -> {

            prefs.edit()
                    .putBoolean(
                            "deadline_notifications",
                            false
                    )
                    .putBoolean(
                            "recommendation_notifications",
                            false
                    )
                    .apply();


            NotificationScheduler.cancel(
                    this
            );

            RecommendationScheduler.cancel(
                    this
            );


            openHome();
        });
    }


    // =====================================
    // 설정 저장 + Worker 관리
    // =====================================

    private void saveSettingsAndSchedule() {

        boolean deadlineEnabled =
                switchDeadline.isChecked();

        boolean recommendationEnabled =
                switchRecommendation.isChecked();


        prefs.edit()
                .putBoolean(
                        "deadline_notifications",
                        deadlineEnabled
                )
                .putBoolean(
                        "recommendation_notifications",
                        recommendationEnabled
                )
                .apply();


        // -------------------------
        // 마감 알림
        // -------------------------

        if (deadlineEnabled) {

            NotificationScheduler.schedule(
                    this
            );

        } else {

            NotificationScheduler.cancel(
                    this
            );
        }


        // -------------------------
        // 추천 알림
        // -------------------------

        if (recommendationEnabled) {

            RecommendationScheduler.schedule(
                    this
            );

        } else {

            RecommendationScheduler.cancel(
                    this
            );
        }
    }


    // =====================================
    // 홈으로 이동
    // =====================================

    private void openHome() {

        prefs.edit()
                .putBoolean(
                        "onboarding_completed",
                        true
                )
                .apply();


        Intent intent =
                new Intent(
                        NotificationSetupActivity.this,
                        MainActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );

        finish();
    }
}