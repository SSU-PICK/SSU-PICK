package com.example.ssu_pick;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ssu_pick.adapter.NoticeAdapter;
import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.repository.NoticeRepository;
import com.example.ssu_pick.util.MatchScoreCalculator;
import com.google.android.material.card.MaterialCardView;
import com.example.ssu_pick.util.NoticeSortUtil;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeFragment extends Fragment {

    private Notice urgentNotice;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        View view = inflater.inflate(
                R.layout.fragment_home,
                container,
                false
        );


        // 여기부터 추가
        TextView tvGreeting =
                view.findViewById(R.id.tvGreeting);

        TextView tvProfileInfo =
                view.findViewById(R.id.tvProfileInfo);


        SharedPreferences prefs =
                requireContext().getSharedPreferences(
                        "SSUPICK_PREFS",
                        Context.MODE_PRIVATE
                );


        String name =
                prefs.getString(
                        "name",
                        "사용자"
                );

        String major =
                prefs.getString(
                        "major",
                        "학과 미설정"
                );

        int grade =
                prefs.getInt(
                        "grade",
                        0
                );


        tvGreeting.setText(
                "안녕하세요, " + name + "님"
        );

        TextView tvRecommendationTitle =
                view.findViewById(
                        R.id.tvRecommendationTitle
                );

        tvRecommendationTitle.setText(
                name + "님을 위한 추천"
        );


        if (grade > 0) {

            tvProfileInfo.setText(
                    major + " · " + grade + "학년"
            );

        } else {

            tvProfileInfo.setText(
                    major
            );
        }

        // 여기 아래부터 기존 코드 계속


        // -----------------------------
        // 전체보기
        // -----------------------------

        TextView tvAllNotice =
                view.findViewById(R.id.tvAllNotice);

        tvAllNotice.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            requireContext(),
                            AllNoticeActivity.class
                    );

            startActivity(intent);
        });


        // -----------------------------
        // 마감 임박 카드
        // -----------------------------

        TextView tvUrgentSectionTitle =
                view.findViewById(R.id.tvUrgentSectionTitle);

        MaterialCardView cardUrgentNotice =
                view.findViewById(R.id.cardUrgentNotice);

        TextView tvUrgentSource =
                view.findViewById(R.id.tvUrgentSource);

        TextView tvUrgentDeadline =
                view.findViewById(R.id.tvUrgentDeadline);

        TextView tvUrgentTitle =
                view.findViewById(R.id.tvUrgentTitle);

        TextView tvUrgentTopic =
                view.findViewById(R.id.tvUrgentTopic);

        TextView tvUrgentScore =
                view.findViewById(R.id.tvUrgentScore);


        // 데이터 불러오기 전에는 숨김
        cardUrgentNotice.setVisibility(View.GONE);


        cardUrgentNotice.setOnClickListener(v -> {

            if (urgentNotice == null) {
                return;
            }

            Intent intent =
                    new Intent(
                            requireContext(),
                            NoticeDetailActivity.class
                    );

            intent.putExtra(
                    "NOTICE_ID",
                    urgentNotice.id
            );

            startActivity(intent);
        });


        // -----------------------------
        // 추천 RecyclerView
        // -----------------------------

        RecyclerView recyclerNotices =
                view.findViewById(R.id.recyclerNotices);

        recyclerNotices.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );


        NoticeAdapter adapter =
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


        recyclerNotices.setAdapter(adapter);


        // -----------------------------
        // 서버 데이터
        // -----------------------------

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


                        Set<String> selectedSources =
                                prefs.getStringSet(
                                        "selected_sources",
                                        new HashSet<>()
                                );


                        List<Notice> filteredNotices =
                                new ArrayList<>();

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


                        // -----------------------------
                        // 출처 필터 + 추천점수
                        // -----------------------------

                        for (Notice notice : notices) {

                            boolean sourceAllowed =
                                    selectedSources.isEmpty()
                                            || selectedSources.contains(
                                            notice.source
                                    );


                            if (!sourceAllowed) {
                                continue;
                            }


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


                            filteredNotices.add(notice);
                        }


                        // -----------------------------
                        // 가장 빨리 마감되는 공지 찾기
                        // -----------------------------

                        urgentNotice =
                                findUrgentNotice(
                                        filteredNotices
                                );


                        if (urgentNotice != null) {

                            cardUrgentNotice.setVisibility(
                                    View.VISIBLE
                            );

                            tvUrgentSectionTitle.setVisibility(
                                    View.VISIBLE
                            );


                            tvUrgentSource.setText(
                                    formatSource(
                                            urgentNotice.source
                                    )
                            );


                            tvUrgentDeadline.setText(
                                    getDdayText(
                                            urgentNotice
                                    )
                            );


                            tvUrgentTitle.setText(
                                    urgentNotice.title != null
                                            ? urgentNotice.title
                                            : "제목 없음"
                            );


                            tvUrgentTopic.setText(
                                    urgentNotice.topic != null
                                            ? urgentNotice.topic
                                            : "기타"
                            );


                            tvUrgentScore.setText(
                                    urgentNotice.matchScore
                                            + "% 일치"
                            );

                        } else {

                            cardUrgentNotice.setVisibility(
                                    View.GONE
                            );

                            tvUrgentSectionTitle.setVisibility(
                                    View.GONE
                            );
                        }


                        // -----------------------------
                        // 마감 여부 + 추천점수 기준 정렬
                        // -----------------------------

                        filteredNotices.sort(
                                NoticeSortUtil::compareRecommendation
                        );

                        adapter.setNotices(
                                filteredNotices
                        );
                    }


                    @Override
                    public void onError(
                            String message
                    ) {

                        Toast.makeText(
                                requireContext(),
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );


        return view;
    }


    // =====================================
    // 가장 가까운 마감 공지 찾기
    // =====================================

    private Notice findUrgentNotice(
            List<Notice> notices
    ) {

        Notice closestNotice = null;
        OffsetDateTime closestDeadline = null;


        for (Notice notice : notices) {

            if (notice.deadlineAt == null) {
                continue;
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


                // 이미 마감된 공지는 제외
                if (deadline.isBefore(now)) {
                    continue;
                }


                if (closestDeadline == null
                        || deadline.isBefore(
                        closestDeadline
                )) {

                    closestDeadline = deadline;
                    closestNotice = notice;
                }


            } catch (Exception ignored) {

            }
        }


        return closestNotice;
    }


    // =====================================
    // D-day 표시
    // =====================================

    private String getDdayText(
            Notice notice
    ) {

        if (notice.deadlineAt == null) {

            if ("OPEN".equals(
                    notice.deadlineStatus
            )) {

                return "상시";
            }

            return "";
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


            if (days == 0) {
                return "D-DAY";
            }

            if (days > 0) {
                return "D-" + days;
            }

            return "마감";


        } catch (Exception e) {

            return "";
        }
    }


    // =====================================
    // source → 화면 표시명
    // =====================================

    private String formatSource(
            String source
    ) {

        if (source == null) {
            return "";
        }


        switch (source) {

            case "ssu_catch":
                return "슈캐치";

            case "job_ssu":
                return "잡슈";

            case "ssu_ai_college":
                return "AI대학";

            case "ssu_sw_official":
                return "SW학부";

            case "ssu_council":
                return "총학생회";

            case "ssu_aisw":
                return "AI·SW 학생회";

            case "lh":
                return "LH";

            case "myhome":
                return "마이홈";

            case "ontongyouth":
                return "온통청년";

            case "youth_mongddang":
                return "청년몽땅정보통";

            default:
                return source;
        }
    }
}