package com.example.ssu_pick;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.example.ssu_pick.util.NotificationScheduler;

import java.util.Collections;
import java.util.Set;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.example.ssu_pick.util.NotificationHelper;
import com.google.android.material.button.MaterialButton;
import android.widget.Toast;
import com.example.ssu_pick.util.RecommendationScheduler;

public class SettingsFragment extends Fragment {

    private TextView tvProfileSummary;
    private TextView tvInterestSummary;
    private TextView tvSourceSummary;

    private SwitchMaterial switchDeadline;
    private SwitchMaterial switchRecommendation;

    private SharedPreferences prefs;


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        View view = inflater.inflate(
                R.layout.fragment_settings,
                container,
                false
        );


        tvProfileSummary =
                view.findViewById(R.id.tvProfileSummary);

        tvInterestSummary =
                view.findViewById(R.id.tvInterestSummary);

        tvSourceSummary =
                view.findViewById(R.id.tvSourceSummary);

        switchDeadline =
                view.findViewById(R.id.switchDeadline);

        switchRecommendation =
                view.findViewById(R.id.switchRecommendation);

        MaterialButton btnEditPersonalization =
                view.findViewById(
                        R.id.btnEditPersonalization
                );
        MaterialButton btnTestNotification =
                view.findViewById(
                        R.id.btnTestNotification
                );


        prefs =
                requireContext().getSharedPreferences(
                        "SSUPICK_PREFS",
                        Context.MODE_PRIVATE
                );


        // -------------------------
        // 현재 알림 설정
        // -------------------------

        switchDeadline.setChecked(
                prefs.getBoolean(
                        "deadline_notifications",
                        true
                )
        );

        switchRecommendation.setChecked(
                prefs.getBoolean(
                        "recommendation_notifications",
                        true
                )
        );


        // -------------------------
        // 알림 설정 저장
        // -------------------------

        switchDeadline.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    prefs.edit()
                            .putBoolean(
                                    "deadline_notifications",
                                    isChecked
                            )
                            .apply();


                    if (isChecked) {

                        NotificationScheduler
                                .schedule(
                                        requireContext()
                                );

                    } else {

                        NotificationScheduler
                                .cancel(
                                        requireContext()
                                );
                    }
                }
        );


        switchRecommendation
                .setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {

                            prefs.edit()
                                    .putBoolean(
                                            "recommendation_notifications",
                                            isChecked
                                    )
                                    .apply();


                            if (isChecked) {

                                RecommendationScheduler
                                        .schedule(
                                                requireContext()
                                        );

                            } else {

                                RecommendationScheduler
                                        .cancel(
                                                requireContext()
                                        );
                            }
                        }
                );

        btnTestNotification.setOnClickListener(v -> {

            // Android 13 이상에서 알림 권한 확인
            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.TIRAMISU) {

                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(
                            requireContext(),
                            "알림 권한이 허용되어 있지 않습니다.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }
            }


            NotificationHelper.showTestNotification(
                    requireContext()
            );
        });


        // -------------------------
        // 맞춤 설정 다시 하기
        // -------------------------

        btnEditPersonalization.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            requireContext(),
                            ProfileSetupActivity.class
                    );

            startActivity(intent);
        });


        updateSummary();

        return view;
    }


    @Override
    public void onResume() {

        super.onResume();

        if (prefs != null) {

            updateSummary();
        }
    }


    // ====================================
    // 현재 사용자 설정 표시
    // ====================================

    private void updateSummary() {

        String major =
                prefs.getString(
                        "major",
                        "미설정"
                );

        int grade =
                prefs.getInt(
                        "grade",
                        0
                );

        String region =
                prefs.getString(
                        "region",
                        "미설정"
                );


        String gradeText =
                grade == 0
                        ? "미설정"
                        : grade + "학년";


        tvProfileSummary.setText(
                major
                        + " · "
                        + gradeText
                        + " · "
                        + region
        );


        // 관심분야
        Set<String> interests =
                prefs.getStringSet(
                        "interests",
                        Collections.emptySet()
                );


        if (interests.isEmpty()) {

            tvInterestSummary.setText(
                    "관심분야 · 미설정"
            );

        } else {

            tvInterestSummary.setText(
                    "관심분야 · "
                            + String.join(
                            ", ",
                            interests
                    )
            );
        }


        // 선택 출처
        Set<String> sources =
                prefs.getStringSet(
                        "selected_sources",
                        Collections.emptySet()
                );


        tvSourceSummary.setText(
                "선택 출처 · "
                        + sources.size()
                        + "개"
        );
    }
}