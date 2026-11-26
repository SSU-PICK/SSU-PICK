package com.example.ssu_pick.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ssu_pick.R;
import com.example.ssu_pick.model.Notice;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class NoticeAdapter
        extends RecyclerView.Adapter<NoticeAdapter.NoticeViewHolder> {

    private List<Notice> notices = new ArrayList<>();

    private OnNoticeClickListener listener;


    public interface OnNoticeClickListener {
        void onNoticeClick(Notice notice);
    }


    public NoticeAdapter(OnNoticeClickListener listener) {
        this.listener = listener;
    }


    public void setNotices(List<Notice> notices) {

        this.notices = notices;

        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public NoticeViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_notice,
                        parent,
                        false
                );

        return new NoticeViewHolder(view);
    }


    @Override
    public void onBindViewHolder(
            @NonNull NoticeViewHolder holder,
            int position
    ) {

        Notice notice = notices.get(position);


        // 출처
        holder.tvSource.setText(
                formatSource(notice.source)
        );


        // 추천 점수
        holder.tvScore.setText(
                notice.matchScore + "% 일치"
        );


        // 제목
        holder.tvTitle.setText(
                notice.title != null
                        ? notice.title
                        : "제목 없음"
        );


        // 요약
        if (notice.summary != null
                && !notice.summary.isEmpty()) {

            holder.tvSummary.setVisibility(View.VISIBLE);
            holder.tvSummary.setText(notice.summary);

        } else {

            holder.tvSummary.setVisibility(View.GONE);
        }


        // 카테고리
        if (notice.topic != null
                && !notice.topic.isEmpty()) {

            holder.tvTopic.setVisibility(View.VISIBLE);
            holder.tvTopic.setText(notice.topic);

        } else {

            holder.tvTopic.setVisibility(View.GONE);
        }


        // D-day
        holder.tvDeadline.setText(
                calculateDday(notice)
        );


        // 카드 클릭
        holder.itemView.setOnClickListener(v -> {

            if (listener != null) {

                listener.onNoticeClick(notice);
            }
        });
    }


    @Override
    public int getItemCount() {

        return notices.size();
    }


    // 출처 이름 변환
    private String formatSource(String source) {

        if (source == null) {
            return "";
        }

        switch (source) {

            // 교내
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


            // 교외
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


    // D-day 계산
    private String calculateDday(Notice notice) {

        if (notice.deadlineAt == null) {

            if ("OPEN".equals(notice.deadlineStatus)) {
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


            if (days < 0) {

                return "마감";
            }


            if (days == 0) {

                return "D-DAY";
            }


            return "D-" + days;


        } catch (Exception e) {

            return "";
        }
    }


    // ViewHolder
    static class NoticeViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvSource;
        TextView tvDeadline;
        TextView tvTitle;
        TextView tvSummary;
        TextView tvTopic;
        TextView tvScore;


        public NoticeViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);


            tvSource =
                    itemView.findViewById(
                            R.id.tvSource
                    );


            tvDeadline =
                    itemView.findViewById(
                            R.id.tvDeadline
                    );


            tvTitle =
                    itemView.findViewById(
                            R.id.tvTitle
                    );


            tvSummary =
                    itemView.findViewById(
                            R.id.tvSummary
                    );


            tvTopic =
                    itemView.findViewById(
                            R.id.tvTopic
                    );


            tvScore =
                    itemView.findViewById(
                            R.id.tvScore
                    );
        }
    }
}