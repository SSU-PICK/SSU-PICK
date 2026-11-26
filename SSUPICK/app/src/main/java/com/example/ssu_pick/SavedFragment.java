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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ssu_pick.adapter.NoticeAdapter;
import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.repository.NoticeRepository;
import com.example.ssu_pick.util.BookmarkManager;
import com.example.ssu_pick.util.MatchScoreCalculator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SavedFragment extends Fragment {

    private NoticeAdapter adapter;
    private TextView tvEmptySaved;
    private RecyclerView recyclerSaved;


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        View view =
                inflater.inflate(
                        R.layout.fragment_saved,
                        container,
                        false
                );


        tvEmptySaved =
                view.findViewById(
                        R.id.tvEmptySaved
                );

        recyclerSaved =
                view.findViewById(
                        R.id.recyclerSaved
                );


        recyclerSaved.setLayoutManager(
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


        recyclerSaved.setAdapter(adapter);


        loadSavedNotices();


        return view;
    }


    @Override
    public void onResume() {

        super.onResume();

        /*
         * 상세화면에서 관심등록을 해제하고
         * 돌아왔을 때 목록 즉시 갱신
         */
        if (adapter != null) {

            loadSavedNotices();
        }
    }


    private void loadSavedNotices() {

        Set<String> bookmarkIds =
                BookmarkManager.getBookmarks(
                        requireContext()
                );


        // 저장한 공지가 하나도 없음
        if (bookmarkIds.isEmpty()) {

            adapter.setNotices(
                    new ArrayList<>()
            );

            tvEmptySaved.setVisibility(
                    View.VISIBLE
            );

            recyclerSaved.setVisibility(
                    View.GONE
            );

            return;
        }


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

                        List<Notice> savedNotices =
                                new ArrayList<>();


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


                        // 저장된 ID만 골라내기
                        for (Notice notice : notices) {

                            if (notice.id != null
                                    && bookmarkIds.contains(
                                    notice.id
                            )) {


                                // 관심 탭에서도 추천점수 계산
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


                                savedNotices.add(
                                        notice
                                );
                            }
                        }


                        /*
                         * 관심등록 목록도
                         * 추천점수 높은 순으로 정렬
                         */
                        savedNotices.sort(
                                (n1, n2) ->
                                        Integer.compare(
                                                n2.matchScore,
                                                n1.matchScore
                                        )
                        );


                        adapter.setNotices(
                                savedNotices
                        );


                        if (savedNotices.isEmpty()) {

                            tvEmptySaved.setVisibility(
                                    View.VISIBLE
                            );

                            recyclerSaved.setVisibility(
                                    View.GONE
                            );

                        } else {

                            tvEmptySaved.setVisibility(
                                    View.GONE
                            );

                            recyclerSaved.setVisibility(
                                    View.VISIBLE
                            );
                        }
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
    }
}