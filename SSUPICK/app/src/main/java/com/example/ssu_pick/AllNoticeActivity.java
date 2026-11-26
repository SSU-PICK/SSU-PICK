package com.example.ssu_pick;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ssu_pick.adapter.NoticeAdapter;
import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.repository.NoticeRepository;
import com.example.ssu_pick.util.MatchScoreCalculator;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AllNoticeActivity extends AppCompatActivity {

    private NoticeAdapter adapter;

    private RecyclerView recyclerAllNotices;
    private TextView tvEmpty;
    private ProgressBar progressLoading;

    private String selectedStatus = "ALL";
    private String selectedCategory = null;

    private String selectedSort = "RECOMMEND";

    // 전체 공지
    private final List<Notice> allNotices =
            new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_all_notice
        );


        // =====================================
        // View 연결
        // =====================================

        TextView tvBack =
                findViewById(R.id.tvBack);

        ChipGroup categoryChipGroup =
                findViewById(R.id.categoryChipGroup);

        ChipGroup statusChipGroup =
                findViewById(R.id.statusChipGroup);

        ChipGroup sortChipGroup =
                findViewById(R.id.sortChipGroup);

        recyclerAllNotices =
                findViewById(R.id.recyclerAllNotices);

        tvEmpty =
                findViewById(R.id.tvEmpty);

        progressLoading =
                findViewById(R.id.progressLoading);


        // =====================================
        // 뒤로가기
        // =====================================

        tvBack.setOnClickListener(
                v -> finish()
        );


        // =====================================
        // RecyclerView
        // =====================================

        recyclerAllNotices.setLayoutManager(
                new LinearLayoutManager(this)
        );


        adapter =
                new NoticeAdapter(notice -> {

                    Intent intent =
                            new Intent(
                                    AllNoticeActivity.this,
                                    NoticeDetailActivity.class
                            );

                    intent.putExtra(
                            "NOTICE_ID",
                            notice.id
                    );

                    startActivity(intent);
                });


        recyclerAllNotices.setAdapter(
                adapter
        );


        // =====================================
        // 카테고리 선택
        // =====================================

        categoryChipGroup
                .setOnCheckedStateChangeListener(
                        (group, checkedIds) -> {

                            if (checkedIds.isEmpty()) {

                                selectedCategory = null;
                                applyFilters();

                                return;
                            }


                            int checkedId =
                                    checkedIds.get(0);


                            Chip selectedChip =
                                    group.findViewById(
                                            checkedId
                                    );


                            if (selectedChip == null) {

                                selectedCategory = null;
                                applyFilters();

                                return;
                            }


                            String category =
                                    selectedChip
                                            .getText()
                                            .toString();


                            if ("전체".equals(category)) {

                                selectedCategory = null;

                            } else {

                                selectedCategory = category;
                            }


                            applyFilters();
                        }
                );


        // =====================================
        // 모집 상태 선택
        // =====================================

        statusChipGroup
                .setOnCheckedStateChangeListener(
                        (group, checkedIds) -> {

                            if (checkedIds.isEmpty()) {

                                selectedStatus = "ALL";
                                applyFilters();

                                return;
                            }


                            int id =
                                    checkedIds.get(0);


                            if (id == R.id.chipStatusOpen) {

                                selectedStatus =
                                        "OPEN";

                            } else if (
                                    id == R.id.chipStatusUrgent
                            ) {

                                selectedStatus =
                                        "URGENT";

                            } else if (
                                    id == R.id.chipStatusExpired
                            ) {

                                selectedStatus =
                                        "EXPIRED";

                            } else {

                                selectedStatus =
                                        "ALL";
                            }


                            applyFilters();
                        }
                );

        // =====================================
        // 정렬 선택
        // =====================================

        sortChipGroup
                .setOnCheckedStateChangeListener(
                        (group, checkedIds) -> {

                            if (checkedIds.isEmpty()) {

                                selectedSort = "RECOMMEND";
                                applyFilters();

                                return;
                            }


                            int id =
                                    checkedIds.get(0);


                            if (id == R.id.chipSortDeadline) {

                                selectedSort =
                                        "DEADLINE";

                            } else if (
                                    id == R.id.chipSortLatest
                            ) {

                                selectedSort =
                                        "LATEST";

                            } else {

                                selectedSort =
                                        "RECOMMEND";
                            }


                            applyFilters();
                        }
                );


        // =====================================
        // 데이터 로딩
        // =====================================

        loadAllNotices();
    }


    // =====================================
    // 전체 공지 가져오기
    // =====================================

    private void loadAllNotices() {

        progressLoading.setVisibility(
                View.VISIBLE
        );

        recyclerAllNotices.setVisibility(
                View.GONE
        );

        tvEmpty.setVisibility(
                View.GONE
        );


        NoticeRepository repository =
                new NoticeRepository(
                        AllNoticeActivity.this
                );


        repository.getAllNotices(
                new NoticeRepository.NoticeListCallback() {

                    @Override
                    public void onSuccess(
                            List<Notice> notices
                    ) {

                        progressLoading.setVisibility(
                                View.GONE
                        );


                        allNotices.clear();


                        // =====================================
                        // 사용자 정보
                        // =====================================

                        SharedPreferences prefs =
                                getSharedPreferences(
                                        "SSUPICK_PREFS",
                                        Context.MODE_PRIVATE
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


                        // =====================================
                        // 추천 점수 계산
                        // =====================================

                        for (Notice notice : notices) {

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


                            // 전체보기는 출처 제한 없이
                            // 모든 공지 표시
                            allNotices.add(
                                    notice
                            );
                        }


                        // 현재 필터 적용
                        applyFilters();
                    }


                    @Override
                    public void onError(
                            String message
                    ) {

                        progressLoading.setVisibility(
                                View.GONE
                        );

                        recyclerAllNotices.setVisibility(
                                View.GONE
                        );

                        tvEmpty.setVisibility(
                                View.VISIBLE
                        );

                        tvEmpty.setText(
                                "공지 정보를 불러오지 못했습니다."
                        );


                        Toast.makeText(
                                AllNoticeActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }


    // =====================================
    // 카테고리 + 모집상태 필터
    // =====================================

    private void applyFilters() {

        List<Notice> filtered =
                new ArrayList<>();


        for (Notice notice : allNotices) {

            // -------------------------
            // 카테고리
            // -------------------------

            boolean categoryMatch =
                    selectedCategory == null
                            || (
                            notice.topic != null
                                    && notice.topic.equals(
                                    selectedCategory
                            )
                    );


            // -------------------------
            // 모집 상태
            // -------------------------

            boolean statusMatch =
                    matchesStatus(
                            notice
                    );


            // 두 조건 모두 만족
            if (categoryMatch
                    && statusMatch) {

                filtered.add(
                        notice
                );
            }
        }


        // 추천점수 높은 순
        sortNotices(filtered);


        adapter.setNotices(
                filtered
        );


        // =====================================
        // Empty 화면
        // =====================================

        if (filtered.isEmpty()) {

            recyclerAllNotices.setVisibility(
                    View.GONE
            );

            tvEmpty.setVisibility(
                    View.VISIBLE
            );

            tvEmpty.setText(
                    "조건에 맞는 공지가 없습니다."
            );

        } else {

            recyclerAllNotices.setVisibility(
                    View.VISIBLE
            );

            tvEmpty.setVisibility(
                    View.GONE
            );
        }
    }


    // =====================================
    // 모집 상태 판단
    // =====================================

    private boolean matchesStatus(
            Notice notice
    ) {

        // 전체
        if ("ALL".equals(selectedStatus)) {

            return true;
        }


        // -------------------------
        // 마감일이 없는 경우
        // -------------------------

        if (notice.deadlineAt == null
                || notice.deadlineAt.isEmpty()) {


            // OPEN 상태면 모집중으로 포함
            return "OPEN".equals(
                    selectedStatus
            )
                    && "OPEN".equals(
                    notice.deadlineStatus
            );
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


            long days =
                    ChronoUnit.DAYS.between(
                            now.toLocalDate(),
                            deadline.toLocalDate()
                    );


            // -------------------------
            // 마감
            // -------------------------

            if ("EXPIRED".equals(
                    selectedStatus
            )) {

                return days < 0;
            }


            // -------------------------
            // 마감임박
            // 오늘 ~ 7일 이내
            // -------------------------

            if ("URGENT".equals(
                    selectedStatus
            )) {

                return days >= 0
                        && days <= 7;
            }


            // -------------------------
            // 모집중
            // -------------------------

            if ("OPEN".equals(
                    selectedStatus
            )) {

                return days >= 0;
            }


        } catch (Exception ignored) {

        }


        return false;
    }
    // =====================================
// 정렬
// =====================================

    private void sortNotices(
            List<Notice> notices
    ) {

        // -------------------------
        // 추천순
        // -------------------------

        if ("RECOMMEND".equals(
                selectedSort
        )) {

            notices.sort(
                    (n1, n2) ->
                            Integer.compare(
                                    n2.matchScore,
                                    n1.matchScore
                            )
            );

            return;
        }


        // -------------------------
        // 마감순
        // -------------------------

        if ("DEADLINE".equals(
                selectedSort
        )) {

            notices.sort(
                    (n1, n2) -> {

                        long d1 =
                                getDeadlineSortValue(
                                        n1
                                );

                        long d2 =
                                getDeadlineSortValue(
                                        n2
                                );


                        return Long.compare(
                                d1,
                                d2
                        );
                    }
            );

            return;
        }


        // -------------------------
        // 최신순
        // -------------------------

        if ("LATEST".equals(
                selectedSort
        )) {

            notices.sort(
                    (n1, n2) -> {

                        long t1 =
                                getPostedTime(
                                        n1
                                );

                        long t2 =
                                getPostedTime(
                                        n2
                                );


                        return Long.compare(
                                t2,
                                t1
                        );
                    }
            );
        }
    }


// =====================================
// 마감 정렬용 값
// =====================================

    private long getDeadlineSortValue(
            Notice notice
    ) {

        if (notice.deadlineAt == null
                || notice.deadlineAt.isEmpty()) {

            return Long.MAX_VALUE;
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


            // 이미 마감된 공지는 뒤로
            if (deadline.isBefore(now)) {

                return Long.MAX_VALUE - 1;
            }


            return deadline.toEpochSecond();


        } catch (Exception e) {

            return Long.MAX_VALUE;
        }
    }


// =====================================
// 최신순 정렬용 값
// =====================================

    private long getPostedTime(
            Notice notice
    ) {

        if (notice.postedAt == null
                || notice.postedAt.isEmpty()) {

            return Long.MIN_VALUE;
        }


        try {

            return OffsetDateTime
                    .parse(
                            notice.postedAt
                    )
                    .toEpochSecond();


        } catch (Exception e) {

            return Long.MIN_VALUE;
        }
    }
}