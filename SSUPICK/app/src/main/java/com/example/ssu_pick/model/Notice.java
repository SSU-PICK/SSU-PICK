package com.example.ssu_pick.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class Notice {
    public int matchScore = 0;

    public String id;

    public String source;

    public String origin;

    @SerializedName("source_id")
    public String sourceId;

    public String url;

    public String title;

    public String topic;

    @SerializedName("posted_at")
    public String postedAt;

    @SerializedName("updated_at")
    public String updatedAt;

    @SerializedName("deadline_status")
    public String deadlineStatus;

    @SerializedName("deadline_at")
    public String deadlineAt;

    public String summary;

    @SerializedName("needs_review")
    public boolean needsReview;

    @SerializedName("content_kind")
    public String contentKind;

    public String body;

    public List<Deadline> deadlines;

    public Eligibility eligibility;

    public List<Attachment> attachments;

    public Extra extra;


    // -------------------------
    // Deadline
    // -------------------------

    public static class Deadline {

        public String kind;

        public String label;

        public String date;

        public String at;

        public String precision;
    }


    // -------------------------
    // Eligibility
    // -------------------------

    public static class Eligibility {

        public String scope;

        public List<String> colleges;

        public List<String> depts;

        public List<Integer> grades;

        public List<String> levels;

        public List<String> statuses;

        public String nationality;

        public String gender;

        @SerializedName("min_age")
        public Integer minAge;

        @SerializedName("max_age")
        public Integer maxAge;

        @SerializedName("min_gpa")
        public Double minGpa;

        public List<String> regions;

        @SerializedName("other_conditions")
        public List<String> otherConditions;

        public String confidence;

        @SerializedName("min_semester")
        public Integer minSemester;

        @SerializedName("max_semester")
        public Integer maxSemester;
    }


    // -------------------------
    // Attachment
    // -------------------------

    public static class Attachment {

        public String name;

        public String url;
    }


    // -------------------------
    // Extra
    // -------------------------

    public static class Extra {

        @SerializedName("key_points")
        public List<String> keyPoints;
    }
}