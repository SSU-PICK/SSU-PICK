package com.example.ssu_pick.network;

import com.example.ssu_pick.model.Notice;
import com.example.ssu_pick.model.NoticePage;
import com.example.ssu_pick.model.ServerStatus;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // 서버 상태
    @GET("v1/status")
    Call<ServerStatus> getStatus();


    // 공지 목록
    @GET("v1/notices")
    Call<NoticePage> getNotices(
            @Query("since") long since,
            @Query("limit") int limit,
            @Query("origin") String origin
    );


    // 공지 상세
    @GET("v1/notices/{id}")
    Call<Notice> getNoticeDetail(
            @Path("id") String id
    );
}