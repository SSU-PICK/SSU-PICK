package com.example.ssu_pick;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ssu_pick.adapter.NoticeAdapter;
import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.repository.NoticeRepository;
import com.example.ssu_pick.util.MatchScoreCalculator;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class ExploreFragment extends Fragment {

    private final List<Notice> allNotices =
            new ArrayList<>();

    private NoticeAdapter adapter;

    private TextView tvResultCount;
    private TextView tvEmptyExplore;

    private RecyclerView recyclerExplore;

    private ProgressBar progressExplore;

    private TextInputEditText editSearch;


    private String selectedOrigin =
            "ALL";

    private String selectedCategory =
            null;

    private String selectedStatus = "ACTIVE";


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        View view =
                inflater.inflate(
                        R.layout.fragment_explore,
                        container,
                        false
                );


        // =====================================
        // View 연결
        // =====================================

        editSearch =
                view.findViewById(
                        R.id.editSearch
                );

        ChipGroup originChipGroup =
                view.findViewById(
                        R.id.originChipGroup
                );

        ChipGroup categoryChipGroup =
                view.findViewById(
                        R.id.categoryChipGroup
                );

        ChipGroup statusChipGroup =
                view.findViewById(
                        R.id.exploreStatusChipGroup
                );

        tvResultCount =
                view.findViewById(
                        R.id.tvResultCount
                );

        tvEmptyExplore =
                view.findViewById(
                        R.id.tvEmptyExplore
                );

        progressExplore =
                view.findViewById(
                        R.id.progressExplore
                );

        recyclerExplore =
                view.findViewById(
                        R.id.recyclerExplore
                );


        // =====================================
        // RecyclerView
        // =====================================

        recyclerExplore.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );


        adapter =
                new NoticeAdapter(notice -> {

                    Intent intent =
                            new Intent(
                                    requireContext(),
                                    NoticeDetailActivity.class
                            );

                    intent.putExtra(
                            "NOTICE_ID",
                            notice.id
                    );

                    startActivity(intent);
                });


        recyclerExplore.setAdapter(
                adapter
        );


        // =====================================
        // 검색어
        // =====================================

        editSearch.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {

                    }


                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {

                        applyFilters();
                    }


                    @Override
                    public void afterTextChanged(
                            Editable s
                    ) {

                    }
                }
        );


        // =====================================
        // 교내 / 교외
        // =====================================

        originChipGroup
                .setOnCheckedStateChangeListener(
                        (group, checkedIds) -> {

                            if (checkedIds.isEmpty()) {

                                selectedOrigin =
                                        "ALL";

                                applyFilters();

                                return;
                            }


                            int id =
                                    checkedIds.get(0);


                            if (id == R.id.chipCampus) {

                                selectedOrigin =
                                        "CAMPUS";

                            } else if (
                                    id == R.id.chipExternal
                            ) {

                                selectedOrigin =
                                        "EXTERNAL";

                            } else {

                                selectedOrigin =
                                        "ALL";
                            }


                            applyFilters();
                        }
                );


        // =====================================
        // 카테고리
        // =====================================

        categoryChipGroup
                .setOnCheckedStateChangeListener(
                        (group, checkedIds) -> {

                            if (checkedIds.isEmpty()) {

                                selectedCategory =
                                        null;

                                applyFilters();

                                return;
                            }


                            int id =
                                    checkedIds.get(0);


                            Chip chip =
                                    group.findViewById(
                                            id
                                    );


                            if (chip == null) {

                                selectedCategory =
                                        null;

                            } else {

                                String text =
                                        chip
                                                .getText()
                                                .toString();


                                if ("전체".equals(text)) {

                                    selectedCategory =
                                            null;

                                } else {

                                    selectedCategory =
                                            text;
                                }
                            }


                            applyFilters();
                        }
                );

        // =====================================
// 모집 상태 필터
// =====================================

        statusChipGroup.setOnCheckedStateChangeListener(
                (group, checkedIds) -> {

                    if (checkedIds.isEmpty()) {

                        selectedStatus = "ACTIVE";
                        applyFilters();
                        return;
                    }

                    int id = checkedIds.get(0);

                    if (id == R.id.chipExploreStatusAll) {

                        selectedStatus = "ALL";

                    } else if (id == R.id.chipExploreStatusOpen) {

                        selectedStatus = "OPEN";

                    } else if (id == R.id.chipExploreStatusUrgent) {

                        selectedStatus = "URGENT";

                    } else if (id == R.id.chipExploreStatusExpired) {

                        selectedStatus = "EXPIRED";

                    } else {

                        selectedStatus = "ACTIVE";
                    }

                    applyFilters();
                }
        );


        // =====================================
        // 데이터 로딩
        // =====================================

        loadNotices();


        return view;
    }


    // =====================================
    // 공지 데이터
    // =====================================

    private void loadNotices() {

        progressExplore.setVisibility(
                View.VISIBLE
        );

        recyclerExplore.setVisibility(
                View.GONE
        );

        tvEmptyExplore.setVisibility(
                View.GONE
        );


        NoticeRepository repository =
                new NoticeRepository(
                        requireContext()
                );


        repository.getAllNotices(
                new NoticeRepository.NoticeListCallback() {

                    @Override
                    public void onSuccess(
                            List<Notice> notices
                    ) {

                        progressExplore.setVisibility(
                                View.GONE
                        );


                        allNotices.clear();


                        SharedPreferences prefs =
                                requireContext()
                                        .getSharedPreferences(
                                                "SSUPICK_PREFS",
                                                Context.MODE_PRIVATE
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


                            allNotices.add(
                                    notice
                            );
                        }


                        applyFilters();
                    }


                    @Override
                    public void onError(
                            String message
                    ) {

                        progressExplore.setVisibility(
                                View.GONE
                        );

                        recyclerExplore.setVisibility(
                                View.GONE
                        );

                        tvEmptyExplore.setVisibility(
                                View.VISIBLE
                        );

                        tvEmptyExplore.setText(
                                "공지 정보를 불러오지 못했습니다."
                        );


                        Toast.makeText(
                                requireContext(),
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }


    // =====================================
    // 검색 + 출처 + 카테고리 필터
    // =====================================

    private void applyFilters() {

        List<Notice> filtered =
                new ArrayList<>();


        String keyword =
                editSearch.getText() != null
                        ? editSearch.getText()
                        .toString()
                        .trim()
                        .toLowerCase()
                        : "";


        for (Notice notice : allNotices) {

            // 교내 / 교외
            boolean originMatch =
                    "ALL".equals(selectedOrigin)
                            || selectedOrigin.equals(
                            notice.origin
                    );


            // 카테고리
            boolean categoryMatch =
                    selectedCategory == null
                            || selectedCategory.equals(
                            notice.topic
                    );


            // 검색어
            boolean keywordMatch =
                    keyword.isEmpty()
                            || containsKeyword(
                            notice,
                            keyword
                    );


            // 모집 상태
            boolean statusMatch =
                    matchesStatus(notice);


            // 네 가지 조건을 모두 만족해야 표시
            if (originMatch
                    && categoryMatch
                    && keywordMatch
                    && statusMatch) {

                filtered.add(notice);
            }
        }


        // 추천점수 높은 순 정렬
        filtered.sort(
                (n1, n2) ->
                        Integer.compare(
                                n2.matchScore,
                                n1.matchScore
                        )
        );


        adapter.setNotices(filtered);


        // 검색 결과 개수
        tvResultCount.setText(
                filtered.size() + "개의 공지"
        );


        // 검색 결과가 없는 경우
        if (filtered.isEmpty()) {

            recyclerExplore.setVisibility(
                    View.GONE
            );

            tvEmptyExplore.setText(
                    "조건에 맞는 공지가 없습니다."
            );

            tvEmptyExplore.setVisibility(
                    View.VISIBLE
            );

        } else {

            recyclerExplore.setVisibility(
                    View.VISIBLE
            );

            tvEmptyExplore.setVisibility(
                    View.GONE
            );
        }
    }


    // =====================================
    // 검색 대상
    // =====================================

    private boolean containsKeyword(
            Notice notice,
            String keyword
    ) {

        if (notice.title != null
                && notice.title
                .toLowerCase()
                .contains(keyword)) {

            return true;
        }


        if (notice.summary != null
                && notice.summary
                .toLowerCase()
                .contains(keyword)) {

            return true;
        }


        if (notice.topic != null
                && notice.topic
                .toLowerCase()
                .contains(keyword)) {

            return true;
        }


        if (notice.source != null
                && notice.source
                .toLowerCase()
                .contains(keyword)) {

            return true;
        }


        // AI key points까지 검색
        if (notice.extra != null
                && notice.extra.keyPoints != null) {

            for (String keyPoint
                    : notice.extra.keyPoints) {

                if (keyPoint != null
                        && keyPoint
                        .toLowerCase()
                        .contains(keyword)) {

                    return true;
                }
            }
        }


        return false;
    }
    // =====================================
// 모집 상태 판단
// =====================================

    private boolean matchesStatus(Notice notice) {

        // 전체 공지 표시
        if ("ALL".equals(selectedStatus)) {
            return true;
        }


        // 마감일이 없거나 확인되지 않은 공지
        if (notice.deadlineAt == null
                || notice.deadlineAt.trim().isEmpty()) {

            // 마감 제외에서는 날짜 미확인 공지도 표시
            if ("ACTIVE".equals(selectedStatus)) {
                return true;
            }

            // 상시 모집은 모집중에 포함
            return "OPEN".equals(selectedStatus)
                    && "OPEN".equals(notice.deadlineStatus);
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


            // 날짜뿐 아니라 실제 마감 시각까지 비교
            boolean expired =
                    !deadline.isAfter(now);


            long days =
                    ChronoUnit.DAYS.between(
                            now.toLocalDate(),
                            deadline.toLocalDate()
                    );


            switch (selectedStatus) {

                // 이미 마감된 공지만 제외
                case "ACTIVE":
                    return !expired;


                // 모집중
                case "OPEN":
                    return !expired;


                // 오늘부터 7일 이내 마감
                case "URGENT":
                    return !expired
                            && days >= 0
                            && days <= 7;


                // 이미 마감된 공지
                case "EXPIRED":
                    return expired;


                default:
                    return true;
            }


        } catch (Exception e) {

            // 날짜 파싱 실패 시 전체 및 마감 제외에서만 표시
            return "ACTIVE".equals(selectedStatus);
        }
    }
}