package com.example.ssu_pick.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class NoticePage {

    public List<Notice> items;

    @SerializedName("next_version")
    public long nextVersion;
}