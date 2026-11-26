package com.example.ssu_pick.model;

import com.google.gson.annotations.SerializedName;

public class ServerStatus {

    public int notices;

    @SerializedName("latest_version")
    public long latestVersion;

    @SerializedName("last_run")
    public String lastRun;

    @SerializedName("last_finished")
    public String lastFinished;

    @SerializedName("last_ok")
    public boolean lastOk;

    public boolean running;

    @SerializedName("last_new_or_changed")
    public int lastNewOrChanged;
}