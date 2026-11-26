package com.example.ssu_pick;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.HashSet;
import java.util.Set;

public class InterestSetupActivity
        extends AppCompatActivity {

    private SharedPreferences prefs;

    private Chip chipScholarship;
    private Chip chipAcademic;
    private Chip chipEmployment;
    private Chip chipPartTime;
    private Chip chipExtra;
    private Chip chipExchange;
    private Chip chipVolunteer;
    private Chip chipContest;
    private Chip chipInternational;
    private Chip chipTeacher;
    private Chip chipHousing;
    private Chip chipYouth;
    private Chip chipEtc;


    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_interest_setup
        );


        prefs =
                getSharedPreferences(
                        "SSUPICK_PREFS",
                        MODE_PRIVATE
                );


        // =====================================
        // View 연결
        // =====================================

        chipScholarship =
                findViewById(
                        R.id.chipScholarship
                );

        chipAcademic =
                findViewById(
                        R.id.chipAcademic
                );

        chipEmployment =
                findViewById(
                        R.id.chipEmployment
                );

        chipPartTime =
                findViewById(
                        R.id.chipPartTime
                );

        chipExtra =
                findViewById(
                        R.id.chipExtra
                );

        chipExchange =
                findViewById(
                        R.id.chipExchange
                );

        chipVolunteer =
                findViewById(
                        R.id.chipVolunteer
                );

        chipContest =
                findViewById(
                        R.id.chipContest
                );

        chipInternational =
                findViewById(
                        R.id.chipInternational
                );

        chipTeacher =
                findViewById(
                        R.id.chipTeacher
                );

        chipHousing =
                findViewById(
                        R.id.chipHousing
                );

        chipYouth =
                findViewById(
                        R.id.chipYouth
                );

        chipEtc =
                findViewById(
                        R.id.chipEtc
                );


        MaterialButton btnNext =
                findViewById(
                        R.id.btnNext
                );


        // =====================================
        // 기존 관심분야 복원
        // =====================================

        loadSavedInterests();


        // =====================================
        // 다음
        // =====================================

        btnNext.setOnClickListener(v -> {

            Set<String> selectedInterests =
                    new HashSet<>();


            if (chipScholarship.isChecked()) {

                selectedInterests.add(
                        "장학"
                );
            }


            if (chipAcademic.isChecked()) {

                selectedInterests.add(
                        "학사"
                );
            }


            if (chipEmployment.isChecked()) {

                selectedInterests.add(
                        "채용·인턴"
                );
            }


            if (chipPartTime.isChecked()) {

                selectedInterests.add(
                        "아르바이트"
                );
            }


            if (chipExtra.isChecked()) {

                selectedInterests.add(
                        "비교과·프로그램"
                );
            }


            if (chipExchange.isChecked()) {

                selectedInterests.add(
                        "해외·교환"
                );
            }


            if (chipVolunteer.isChecked()) {

                selectedInterests.add(
                        "봉사"
                );
            }


            if (chipContest.isChecked()) {

                selectedInterests.add(
                        "공모전·대외활동"
                );
            }


            if (chipInternational.isChecked()) {

                selectedInterests.add(
                        "유학생"
                );
            }


            if (chipTeacher.isChecked()) {

                selectedInterests.add(
                        "교원채용"
                );
            }


            if (chipHousing.isChecked()) {

                selectedInterests.add(
                        "주거"
                );
            }


            if (chipYouth.isChecked()) {

                selectedInterests.add(
                        "청년지원"
                );
            }


            if (chipEtc.isChecked()) {

                selectedInterests.add(
                        "기타"
                );
            }


            // 최소 1개
            if (selectedInterests.isEmpty()) {

                Toast.makeText(
                        InterestSetupActivity.this,
                        "관심 분야를 하나 이상 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            // 저장
            prefs.edit()
                    .putStringSet(
                            "interests",
                            selectedInterests
                    )
                    .apply();


            // 출처 선택으로 이동
            Intent intent =
                    new Intent(
                            InterestSetupActivity.this,
                            SourceSetupActivity.class
                    );

            startActivity(
                    intent
            );

            finish();
        });
    }


    // =====================================
    // 기존 선택값 복원
    // =====================================

    private void loadSavedInterests() {

        Set<String> saved =
                prefs.getStringSet(
                        "interests",
                        new HashSet<>()
                );


        chipScholarship.setChecked(
                saved.contains(
                        "장학"
                )
        );

        chipAcademic.setChecked(
                saved.contains(
                        "학사"
                )
        );

        chipEmployment.setChecked(
                saved.contains(
                        "채용·인턴"
                )
        );

        chipPartTime.setChecked(
                saved.contains(
                        "아르바이트"
                )
        );

        chipExtra.setChecked(
                saved.contains(
                        "비교과·프로그램"
                )
        );

        chipExchange.setChecked(
                saved.contains(
                        "해외·교환"
                )
        );

        chipVolunteer.setChecked(
                saved.contains(
                        "봉사"
                )
        );

        chipContest.setChecked(
                saved.contains(
                        "공모전·대외활동"
                )
        );

        chipInternational.setChecked(
                saved.contains(
                        "유학생"
                )
        );

        chipTeacher.setChecked(
                saved.contains(
                        "교원채용"
                )
        );

        chipHousing.setChecked(
                saved.contains(
                        "주거"
                )
        );

        chipYouth.setChecked(
                saved.contains(
                        "청년지원"
                )
        );

        chipEtc.setChecked(
                saved.contains(
                        "기타"
                )
        );
    }
}