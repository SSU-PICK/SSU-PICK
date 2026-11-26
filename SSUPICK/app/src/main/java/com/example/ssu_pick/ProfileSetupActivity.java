package com.example.ssu_pick;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileSetupActivity extends AppCompatActivity {

    private TextInputEditText editName;
    private TextInputEditText editAge;

    private Spinner spinnerMajor;
    private Spinner spinnerRegion;
    private Spinner spinnerStatus;

    private ChipGroup gradeChipGroup;

    private SharedPreferences prefs;


    // =====================================
    // 학과
    // =====================================

    private final String[] majors = {

            "학과를 선택해주세요",

            // 인문대학
            "기독교학과",
            "국어국문학과",
            "영어영문학과",
            "독어독문학과",
            "불어불문학과",
            "중어중문학과",
            "일어일문학과",
            "철학과",
            "사학과",
            "예술창작학부(문예창작전공)",
            "예술창작학부(영화예술전공)",
            "스포츠학부",

            // 자연과학대학
            "수학과",
            "물리학과",
            "화학과",
            "정보통계·보험수리학과",
            "의생명시스템학부",

            // 법과대학
            "법학과",
            "국제법무학과",

            // 사회과학대학
            "사회복지학부",
            "행정학부",
            "정치외교학과",
            "정보사회학과",
            "언론홍보학과",
            "평생교육학과",

            // 경제통상대학
            "경제학과",
            "글로벌통상학과",
            "금융경제학과",
            "국제무역학과",

            // 경영대학
            "경영학부",
            "회계학과",
            "벤처중소기업학과",
            "금융학부",

            // 공과대학
            "화학공학과",
            "신소재공학과",
            "전기공학부",
            "기계공학부",
            "산업·정보시스템공학과",
            "건축학부(건축학·건축공학전공)",
            "건축학부(실내건축전공)",

            // IT대학
            "컴퓨터학부",
            "전자정보공학부(전자공학전공)",
            "전자정보공학부(IT융합전공)",
            "지능전자공학부",
            "글로벌미디어학부",
            "디지털미디어학과",

            // AI대학
            "AI소프트웨어학부",
            "정보보호학과",

            // 독립 / 자유전공
            "차세대반도체학과",
            "자유전공학부(인문)",
            "자유전공학부(자연)"
    };


    // =====================================
    // 지역
    // =====================================

    private final String[] regions = {
            "지역을 선택해주세요",
            "서울",
            "경기",
            "인천",
            "기타"
    };


    // =====================================
    // 학적 상태
    // =====================================

    private final String[] statuses = {
            "학적 상태를 선택해주세요",
            "재학",
            "휴학",
            "졸업예정",
            "졸업"
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_profile_setup
        );


        // =====================================
        // View 연결
        // =====================================

        editName =
                findViewById(
                        R.id.editName
                );

        editAge =
                findViewById(
                        R.id.editAge
                );

        spinnerMajor =
                findViewById(
                        R.id.spinnerMajor
                );

        spinnerRegion =
                findViewById(
                        R.id.spinnerRegion
                );

        spinnerStatus =
                findViewById(
                        R.id.spinnerStatus
                );

        gradeChipGroup =
                findViewById(
                        R.id.gradeChipGroup
                );

        MaterialButton btnNext =
                findViewById(
                        R.id.btnNext
                );


        prefs =
                getSharedPreferences(
                        "SSUPICK_PREFS",
                        MODE_PRIVATE
                );


        // =====================================
        // 학과 Spinner
        // =====================================

        ArrayAdapter<String> majorAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        majors
                );

        majorAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerMajor.setAdapter(
                majorAdapter
        );


        // =====================================
        // 지역 Spinner
        // =====================================

        ArrayAdapter<String> regionAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        regions
                );

        regionAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerRegion.setAdapter(
                regionAdapter
        );


        // =====================================
        // 학적 상태 Spinner
        // =====================================

        ArrayAdapter<String> statusAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        statuses
                );

        statusAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerStatus.setAdapter(
                statusAdapter
        );


        // =====================================
        // 기존 저장값 불러오기
        // =====================================

        loadSavedProfile();


        // =====================================
        // 다음 버튼
        // =====================================

        btnNext.setOnClickListener(v -> {

            // ---------------------------------
            // 닉네임
            // ---------------------------------

            String name = "";

            if (editName.getText() != null) {

                name =
                        editName
                                .getText()
                                .toString()
                                .trim();
            }


            if (name.isEmpty()) {

                Toast.makeText(
                        this,
                        "닉네임을 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            // ---------------------------------
            // 학과
            // ---------------------------------

            if (spinnerMajor.getSelectedItemPosition()
                    == 0) {

                Toast.makeText(
                        this,
                        "학과를 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            String major =
                    spinnerMajor
                            .getSelectedItem()
                            .toString();


            // ---------------------------------
            // 학년
            // ---------------------------------

            int checkedId =
                    gradeChipGroup
                            .getCheckedChipId();


            if (checkedId == -1) {

                Toast.makeText(
                        this,
                        "학년을 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            int grade;

            if (checkedId == R.id.chipGrade1) {

                grade = 1;

            } else if (
                    checkedId == R.id.chipGrade2
            ) {

                grade = 2;

            } else if (
                    checkedId == R.id.chipGrade3
            ) {

                grade = 3;

            } else {

                grade = 4;
            }


            // ---------------------------------
            // 만 나이
            // ---------------------------------

            String ageText = "";

            if (editAge.getText() != null) {

                ageText =
                        editAge
                                .getText()
                                .toString()
                                .trim();
            }


            if (ageText.isEmpty()) {

                Toast.makeText(
                        this,
                        "만 나이를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            int age;

            try {

                age =
                        Integer.parseInt(
                                ageText
                        );

            } catch (NumberFormatException e) {

                Toast.makeText(
                        this,
                        "올바른 만 나이를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            if (age < 15 || age > 100) {

                Toast.makeText(
                        this,
                        "올바른 만 나이를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            // ---------------------------------
            // 학적 상태
            // ---------------------------------

            if (spinnerStatus.getSelectedItemPosition()
                    == 0) {

                Toast.makeText(
                        this,
                        "학적 상태를 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            String studentStatus =
                    spinnerStatus
                            .getSelectedItem()
                            .toString();


            // ---------------------------------
            // 지역
            // ---------------------------------

            if (spinnerRegion.getSelectedItemPosition()
                    == 0) {

                Toast.makeText(
                        this,
                        "지역을 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            String region =
                    spinnerRegion
                            .getSelectedItem()
                            .toString();


            // =====================================
            // 프로필 저장
            // =====================================

            prefs.edit()
                    .putString(
                            "name",
                            name
                    )
                    .putString(
                            "major",
                            major
                    )
                    .putInt(
                            "grade",
                            grade
                    )
                    .putInt(
                            "age",
                            age
                    )
                    .putString(
                            "student_status",
                            studentStatus
                    )
                    .putString(
                            "region",
                            region
                    )
                    .apply();


            // =====================================
            // 관심분야 설정 화면 이동
            // =====================================

            Intent intent =
                    new Intent(
                            ProfileSetupActivity.this,
                            InterestSetupActivity.class
                    );

            startActivity(
                    intent
            );

            finish();
        });
    }


    // =====================================
    // 기존 프로필 표시
    // =====================================

    private void loadSavedProfile() {

        String savedName =
                prefs.getString(
                        "name",
                        ""
                );


        String savedMajor =
                prefs.getString(
                        "major",
                        ""
                );


        // -------------------------------------
        // 기존 학과명 → 통합 학과명
        // -------------------------------------

        if ("AI융합학부".equals(savedMajor)
                || "소프트웨어학부".equals(savedMajor)) {

            savedMajor =
                    "AI소프트웨어학부";


            prefs.edit()
                    .putString(
                            "major",
                            savedMajor
                    )
                    .apply();
        }


        String savedRegion =
                prefs.getString(
                        "region",
                        ""
                );


        int savedGrade =
                prefs.getInt(
                        "grade",
                        0
                );


        int savedAge =
                prefs.getInt(
                        "age",
                        0
                );


        String savedStatus =
                prefs.getString(
                        "student_status",
                        ""
                );


        // =====================================
        // 닉네임
        // =====================================

        editName.setText(
                savedName
        );


        // =====================================
        // 나이
        // =====================================

        if (savedAge > 0) {

            editAge.setText(
                    String.valueOf(
                            savedAge
                    )
            );
        }


        // =====================================
        // 학과
        // =====================================

        for (int i = 0;
             i < majors.length;
             i++) {

            if (majors[i].equals(
                    savedMajor
            )) {

                spinnerMajor.setSelection(
                        i
                );

                break;
            }
        }


        // =====================================
        // 지역
        // =====================================

        for (int i = 0;
             i < regions.length;
             i++) {

            if (regions[i].equals(
                    savedRegion
            )) {

                spinnerRegion.setSelection(
                        i
                );

                break;
            }
        }


        // =====================================
        // 학적 상태
        // =====================================

        for (int i = 0;
             i < statuses.length;
             i++) {

            if (statuses[i].equals(
                    savedStatus
            )) {

                spinnerStatus.setSelection(
                        i
                );

                break;
            }
        }


        // =====================================
        // 학년
        // =====================================

        if (savedGrade == 1) {

            gradeChipGroup.check(
                    R.id.chipGrade1
            );

        } else if (
                savedGrade == 2
        ) {

            gradeChipGroup.check(
                    R.id.chipGrade2
            );

        } else if (
                savedGrade == 3
        ) {

            gradeChipGroup.check(
                    R.id.chipGrade3
            );

        } else if (
                savedGrade == 4
        ) {

            gradeChipGroup.check(
                    R.id.chipGrade4
            );
        }
    }
}