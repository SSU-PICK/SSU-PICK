package com.example.ssu_pick;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.repository.NoticeRepository;
import com.example.ssu_pick.util.MatchScoreCalculator;
import com.google.android.material.button.MaterialButton;
import com.example.ssu_pick.util.BookmarkManager;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoticeDetailActivity extends AppCompatActivity {

    private Notice currentNotice;

    private TextView tvSource;
    private TextView tvNeedsReview;
    private TextView tvTitle;
    private TextView tvDeadline;
    private TextView tvScore;
    private TextView tvTopic;
    private TextView tvEligibility;
    private TextView tvSummary;
    private TextView tvKeyPoints;
    private TextView tvKeyPointsTitle;
    private TextView tvBody;
    private TextView tvBodyTitle;

    private ProgressBar progressScore;

    private MaterialButton btnBookmark;
    private MaterialButton btnOriginal;

    private boolean bookmarked = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_notice_detail
        );


        // -----------------------------
        // View 연결
        // -----------------------------

        TextView tvBack =
                findViewById(R.id.tvBack);

        tvNeedsReview =
                findViewById(
                        R.id.tvNeedsReview
                );

        tvSource =
                findViewById(R.id.tvSource);

        tvTitle =
                findViewById(R.id.tvTitle);

        tvDeadline =
                findViewById(R.id.tvDeadline);

        tvScore =
                findViewById(R.id.tvScore);

        progressScore =
                findViewById(R.id.progressScore);

        tvTopic =
                findViewById(R.id.tvTopic);

        tvEligibility =
                findViewById(R.id.tvEligibility);

        tvSummary =
                findViewById(R.id.tvSummary);

        tvKeyPoints =
                findViewById(R.id.tvKeyPoints);

        tvKeyPointsTitle =
                findViewById(R.id.tvKeyPointsTitle);

        tvBody =
                findViewById(R.id.tvBody);

        tvBodyTitle =
                findViewById(R.id.tvBodyTitle);

        btnBookmark =
                findViewById(R.id.btnBookmark);

        btnOriginal =
                findViewById(R.id.btnOriginal);


        tvBack.setOnClickListener(
                v -> finish()
        );


        // -----------------------------
        // 클릭한 공지 ID 받기
        // -----------------------------

        String noticeId =
                getIntent().getStringExtra(
                        "NOTICE_ID"
                );


        if (noticeId == null) {

            Toast.makeText(
                    this,
                    "공지 정보를 불러올 수 없습니다.",
                    Toast.LENGTH_SHORT
            ).show();

            finish();

            return;
        }

        bookmarked =
                BookmarkManager.isBookmarked(
                        this,
                        noticeId
                );

        updateBookmarkButton();


        loadNoticeDetail(noticeId);


        btnBookmark.setOnClickListener(v -> {

            if (currentNotice == null) {
                return;
            }


            if (BookmarkManager.isBookmarked(
                    this,
                    currentNotice.id
            )) {

                BookmarkManager.removeBookmark(
                        this,
                        currentNotice.id
                );

                bookmarked = false;

                Toast.makeText(
                        this,
                        "관심 공지에서 삭제했습니다.",
                        Toast.LENGTH_SHORT
                ).show();

            } else {

                BookmarkManager.addBookmark(
                        this,
                        currentNotice.id
                );

                bookmarked = true;

                Toast.makeText(
                        this,
                        "관심 공지에 저장했습니다.",
                        Toast.LENGTH_SHORT
                ).show();
            }


            updateBookmarkButton();
        });


        // -----------------------------
        // 원문 보기
        // -----------------------------

        btnOriginal.setOnClickListener(v -> {

            if (currentNotice == null
                    || currentNotice.url == null
                    || currentNotice.url.isEmpty()) {

                Toast.makeText(
                        this,
                        "원문 링크가 없습니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(currentNotice.url)
                    );

            startActivity(intent);
        });
    }


    // ======================================
    // 상세 API 호출
    // ======================================

    private void loadNoticeDetail(
            String noticeId
    ) {

        NoticeRepository repository =
                new NoticeRepository(
                        NoticeDetailActivity.this
                );


        repository.getNoticeDetail(
                noticeId,
                new NoticeRepository.NoticeDetailCallback() {

                    @Override
                    public void onSuccess(
                            Notice notice
                    ) {

                        currentNotice = notice;

                        showNotice(notice);

                        if (notice.needsReview) {

                            tvNeedsReview.setVisibility(
                                    View.VISIBLE
                            );

                        } else {

                            tvNeedsReview.setVisibility(
                                    View.GONE
                            );
                        }
                    }


                    @Override
                    public void onError(
                            String message
                    ) {

                        Toast.makeText(
                                NoticeDetailActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }


    // ======================================
    // 화면 표시
    // ======================================

    private void showNotice(
            Notice notice
    ) {

        // -----------------------------
        // 출처
        // -----------------------------

        tvSource.setText(
                formatSource(
                        notice.source
                )
        );


        // -----------------------------
        // 제목
        // -----------------------------

        tvTitle.setText(
                notice.title != null
                        ? notice.title
                        : "제목 없음"
        );


        // -----------------------------
        // 카테고리
        // -----------------------------

        tvTopic.setText(
                notice.topic != null
                        ? notice.topic
                        : "기타"
        );


        // -----------------------------
        // 마감일
        // -----------------------------

        tvDeadline.setText(
                formatDeadline(notice)
        );


        // -----------------------------
        // 추천 점수
        // -----------------------------

        SharedPreferences prefs =
                getSharedPreferences(
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


        int score =
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


        notice.matchScore = score;


        tvScore.setText(
                score + "% 일치"
        );

        progressScore.setProgress(
                score
        );


        // -----------------------------
        // 지원 조건
        // -----------------------------

        tvEligibility.setText(
                buildEligibilityText(
                        notice
                )
        );


        // -----------------------------
        // AI 요약
        // -----------------------------

        if (notice.summary == null
                || notice.summary.trim().isEmpty()) {

            tvSummary.setText(
                    "요약 정보가 없습니다."
            );

            // 요약 자체가 없으면 AI 안내도 숨김
            tvNeedsReview.setVisibility(
                    View.GONE
            );

        } else {

            tvSummary.setText(
                    notice.summary
            );


            if (notice.needsReview) {

                tvNeedsReview.setVisibility(
                        View.VISIBLE
                );

            } else {

                tvNeedsReview.setVisibility(
                        View.GONE
                );
            }
        }


        // -----------------------------
        // 핵심 내용
        // -----------------------------

        if (notice.extra != null
                && notice.extra.keyPoints != null
                && !notice.extra.keyPoints.isEmpty()) {

            StringBuilder builder =
                    new StringBuilder();


            for (String point :
                    notice.extra.keyPoints) {

                builder.append("• ")
                        .append(point)
                        .append("\n\n");
            }


            tvKeyPoints.setText(
                    builder.toString().trim()
            );

            tvKeyPoints.setVisibility(
                    View.VISIBLE
            );

            tvKeyPointsTitle.setVisibility(
                    View.VISIBLE
            );

        } else {

            tvKeyPoints.setVisibility(
                    View.GONE
            );

            tvKeyPointsTitle.setVisibility(
                    View.GONE
            );
        }


        // -----------------------------
        // 본문
        // -----------------------------

        if (notice.body != null
                && !notice.body.trim().isEmpty()) {

            tvBody.setText(
                    notice.body
            );

            tvBody.setVisibility(
                    View.VISIBLE
            );

            tvBodyTitle.setVisibility(
                    View.VISIBLE
            );

        } else {

            tvBody.setVisibility(
                    View.GONE
            );

            tvBodyTitle.setVisibility(
                    View.GONE
            );
        }
    }


    // ======================================
    // 지원 조건 문자열 만들기
    // ======================================

    private String buildEligibilityText(
            Notice notice
    ) {

        if (notice.eligibility == null) {

            return "별도의 지원 자격 제한이 확인되지 않았습니다.";
        }


        Notice.Eligibility e =
                notice.eligibility;


        StringBuilder text =
                new StringBuilder();


        // 학년
        if (e.grades != null
                && !e.grades.isEmpty()) {

            text.append("학년  ·  ");

            for (int i = 0;
                 i < e.grades.size();
                 i++) {

                text.append(
                        e.grades.get(i)
                ).append("학년");

                if (i
                        < e.grades.size() - 1) {

                    text.append(", ");
                }
            }

            text.append("\n");
        }


        // 학과
        if ("ALL".equals(e.scope)) {

            text.append(
                    "학과  ·  전체 학과\n"
            );

        } else if (e.depts != null
                && !e.depts.isEmpty()) {

            text.append(
                    "학과  ·  "
            );

            text.append(
                    String.join(
                            ", ",
                            e.depts
                    )
            );

            text.append("\n");
        }


        // 단과대
        if (e.colleges != null
                && !e.colleges.isEmpty()) {

            text.append(
                    "단과대  ·  "
            );

            text.append(
                    String.join(
                            ", ",
                            e.colleges
                    )
            );

            text.append("\n");
        }


        // 지역
        if (e.regions != null
                && !e.regions.isEmpty()) {

            text.append(
                    "지역  ·  "
            );

            text.append(
                    String.join(
                            ", ",
                            e.regions
                    )
            );

            text.append("\n");
        }


        // 재학 상태
        if (e.statuses != null
                && !e.statuses.isEmpty()) {

            text.append(
                    "학적  ·  "
            );

            text.append(
                    formatStatuses(
                            e.statuses
                    )
            );

            text.append("\n");
        }


        // 나이
        if (e.minAge != null
                || e.maxAge != null) {

            text.append("나이  ·  ");

            if (e.minAge != null
                    && e.maxAge != null) {

                text.append(
                                e.minAge
                        ).append("~")
                        .append(
                                e.maxAge
                        ).append("세");

            } else if (e.minAge != null) {

                text.append(
                        e.minAge
                ).append("세 이상");

            } else {

                text.append(
                        e.maxAge
                ).append("세 이하");
            }

            text.append("\n");
        }


        // 기타조건
        if (e.otherConditions != null
                && !e.otherConditions.isEmpty()) {

            text.append(
                    "기타  ·  "
            );

            text.append(
                    String.join(
                            ", ",
                            e.otherConditions
                    )
            );

            text.append("\n");
        }


        if (text.length() == 0) {

            return "별도의 지원 자격 제한이 확인되지 않았습니다.";
        }


        return text.toString().trim();
    }


    // ======================================
    // 학적 상태 한글화
    // ======================================

    private String formatStatuses(
            List<String> statuses
    ) {

        StringBuilder result =
                new StringBuilder();


        for (int i = 0;
             i < statuses.size();
             i++) {

            String status =
                    statuses.get(i);

            switch (status) {

                case "ENROLLED":
                    result.append("재학생");
                    break;

                case "LEAVE":
                    result.append("휴학생");
                    break;

                case "GRADUATING":
                    result.append("졸업예정");
                    break;

                case "GRADUATE":
                    result.append("졸업생");
                    break;

                case "RETURNING":
                    result.append("복학예정");
                    break;

                case "JOB_SEEKER":
                    result.append("미취업");
                    break;

                default:
                    result.append(status);
                    break;
            }


            if (i < statuses.size() - 1) {

                result.append(", ");
            }
        }


        return result.toString();
    }


    // ======================================
    // 마감 표시
    // ======================================

    private String formatDeadline(
            Notice notice
    ) {

        if ("OPEN".equals(
                notice.deadlineStatus
        )) {

            return "상시 모집";
        }


        if (notice.deadlineAt == null) {

            return "마감일 정보 없음";
        }


        try {

            OffsetDateTime dateTime =
                    OffsetDateTime.parse(
                            notice.deadlineAt
                    );


            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern(
                            "yyyy.MM.dd HH:mm"
                    );


            return "마감 · "
                    + dateTime.format(
                    formatter
            );


        } catch (Exception e) {

            return "마감 · "
                    + notice.deadlineAt;
        }
    }


    // ======================================
    // source 표시명
    // ======================================

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
    private void updateBookmarkButton() {

        if (bookmarked) {

            btnBookmark.setText(
                    "♥ 관심등록됨"
            );

        } else {

            btnBookmark.setText(
                    "♡ 관심등록"
            );
        }
    }
}

