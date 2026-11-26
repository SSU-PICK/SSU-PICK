package com.example.ssu_pick;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.HashSet;
import java.util.Set;

public class SourceSetupActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    private MaterialCheckBox checkSsuCatch;
    private MaterialCheckBox checkJobSsu;
    private MaterialCheckBox checkAiCollege;
    private MaterialCheckBox checkSwOfficial;
    private MaterialCheckBox checkCouncil;
    private MaterialCheckBox checkAisw;

    private MaterialCheckBox checkLh;
    private MaterialCheckBox checkMyhome;
    private MaterialCheckBox checkOntongYouth;
    private MaterialCheckBox checkYouthMongddang;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_source_setup
        );


        prefs =
                getSharedPreferences(
                        "SSUPICK_PREFS",
                        MODE_PRIVATE
                );


        // =====================================
        // View 연결
        // =====================================

        checkSsuCatch =
                findViewById(
                        R.id.checkSsuCatch
                );

        checkJobSsu =
                findViewById(
                        R.id.checkJobSsu
                );

        checkAiCollege =
                findViewById(
                        R.id.checkAiCollege
                );

        checkSwOfficial =
                findViewById(
                        R.id.checkSwOfficial
                );

        checkCouncil =
                findViewById(
                        R.id.checkCouncil
                );

        checkAisw =
                findViewById(
                        R.id.checkAisw
                );

        checkLh =
                findViewById(
                        R.id.checkLh
                );

        checkMyhome =
                findViewById(
                        R.id.checkMyhome
                );

        checkOntongYouth =
                findViewById(
                        R.id.checkOntongYouth
                );

        checkYouthMongddang =
                findViewById(
                        R.id.checkYouthMongddang
                );


        MaterialButton btnNext =
                findViewById(
                        R.id.btnNext
                );


        // =====================================
        // 기존 선택값 불러오기
        // =====================================

        loadSavedSources();


        // =====================================
        // 다음 버튼
        // =====================================

        btnNext.setOnClickListener(v -> {

            Set<String> selectedSources =
                    new HashSet<>();


            if (checkSsuCatch.isChecked()) {

                selectedSources.add(
                        "ssu_catch"
                );
            }


            if (checkJobSsu.isChecked()) {

                selectedSources.add(
                        "job_ssu"
                );
            }


            if (checkAiCollege.isChecked()) {

                selectedSources.add(
                        "ssu_ai_college"
                );
            }


            if (checkSwOfficial.isChecked()) {

                selectedSources.add(
                        "ssu_sw_official"
                );
            }


            if (checkCouncil.isChecked()) {

                selectedSources.add(
                        "ssu_council"
                );
            }


            if (checkAisw.isChecked()) {

                selectedSources.add(
                        "ssu_aisw"
                );
            }


            if (checkLh.isChecked()) {

                selectedSources.add(
                        "lh"
                );
            }


            if (checkMyhome.isChecked()) {

                selectedSources.add(
                        "myhome"
                );
            }


            if (checkOntongYouth.isChecked()) {

                selectedSources.add(
                        "ontongyouth"
                );
            }


            if (checkYouthMongddang.isChecked()) {

                selectedSources.add(
                        "youth_mongddang"
                );
            }


            // 최소 1개
            if (selectedSources.isEmpty()) {

                Toast.makeText(
                        SourceSetupActivity.this,
                        "출처를 하나 이상 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            // 저장
            prefs.edit()
                    .putStringSet(
                            "selected_sources",
                            selectedSources
                    )
                    .apply();


            // 다음 화면
            Intent intent =
                    new Intent(
                            SourceSetupActivity.this,
                            NotificationSetupActivity.class
                    );

            startActivity(intent);

            finish();
        });
    }


    // =====================================
    // 기존 출처 선택 복원
    // =====================================

    private void loadSavedSources() {

        boolean hasSavedSources =
                prefs.contains(
                        "selected_sources"
                );


        /*
         * 최초 가입이면 기존값이 없으므로
         * 전체 출처 기본 체크
         */
        if (!hasSavedSources) {

            checkAllSources(true);

            return;
        }


        Set<String> saved =
                prefs.getStringSet(
                        "selected_sources",
                        new HashSet<>()
                );


        checkSsuCatch.setChecked(
                saved.contains(
                        "ssu_catch"
                )
        );

        checkJobSsu.setChecked(
                saved.contains(
                        "job_ssu"
                )
        );

        checkAiCollege.setChecked(
                saved.contains(
                        "ssu_ai_college"
                )
        );

        checkSwOfficial.setChecked(
                saved.contains(
                        "ssu_sw_official"
                )
        );

        checkCouncil.setChecked(
                saved.contains(
                        "ssu_council"
                )
        );

        checkAisw.setChecked(
                saved.contains(
                        "ssu_aisw"
                )
        );

        checkLh.setChecked(
                saved.contains(
                        "lh"
                )
        );

        checkMyhome.setChecked(
                saved.contains(
                        "myhome"
                )
        );

        checkOntongYouth.setChecked(
                saved.contains(
                        "ontongyouth"
                )
        );

        checkYouthMongddang.setChecked(
                saved.contains(
                        "youth_mongddang"
                )
        );
    }


    // =====================================
    // 전체 체크
    // =====================================

    private void checkAllSources(
            boolean checked
    ) {

        checkSsuCatch.setChecked(
                checked
        );

        checkJobSsu.setChecked(
                checked
        );

        checkAiCollege.setChecked(
                checked
        );

        checkSwOfficial.setChecked(
                checked
        );

        checkCouncil.setChecked(
                checked
        );

        checkAisw.setChecked(
                checked
        );

        checkLh.setChecked(
                checked
        );

        checkMyhome.setChecked(
                checked
        );

        checkOntongYouth.setChecked(
                checked
        );

        checkYouthMongddang.setChecked(
                checked
        );
    }
}