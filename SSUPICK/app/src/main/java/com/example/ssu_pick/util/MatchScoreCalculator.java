package com.example.ssu_pick.util;

import com.example.ssu_pick.model.Notice;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

public class MatchScoreCalculator {

    public static int calculate(
            Notice notice,
            String major,
            int grade,
            String region,
            Set<String> interests,
            int age,
            String studentStatus
    ) {

        if (notice == null) {
            return 0;
        }


        int score = 0;
        int maxScore = 100;


        // =====================================
        // 1. 관심분야 - 40점
        //
        // 관심분야가 아니면 최종 최대 60점
        // =====================================

        boolean interestMatch =
                notice.topic != null
                        && interests != null
                        && interests.contains(
                        notice.topic
                );


        if (interestMatch) {

            score += 40;

        } else {

            maxScore =
                    Math.min(
                            maxScore,
                            60
                    );
        }


        // eligibility 자체가 없는 경우
        if (notice.eligibility == null) {

            // 조건 없는 공지는
            // 학년/학과/지역 절반점수
            score += 12;   // 학년
            score += 10;   // 학과
            score += 5;    // 지역

            score += getUrgencyScore(
                    notice
            );

            return Math.min(
                    score,
                    maxScore
            );
        }


        // =====================================
        // 2. 학년 - 최대 25점
        //
        // 조건 없음: 12점
        // 조건 있고 일치: 25점
        // 조건 있고 불일치: 최대 40%
        // =====================================

        if (notice.eligibility.grades == null
                || notice.eligibility.grades.isEmpty()) {

            score += 12;

        } else if (isGradeMatch(
                notice.eligibility.grades,
                grade
        )) {

            score += 25;

        } else {

            maxScore =
                    Math.min(
                            maxScore,
                            40
                    );
        }


        // =====================================
        // 3. 학과 - 최대 20점
        //
        // 조건 없음: 10점
        // 조건 있고 일치: 20점
        // 조건 있고 불일치: 최대 40%
        // =====================================

        if (notice.eligibility.depts == null
                || notice.eligibility.depts.isEmpty()) {

            score += 10;

        } else if (isMajorMatch(
                major,
                notice.eligibility.depts
        )) {

            score += 20;

        } else {

            maxScore =
                    Math.min(
                            maxScore,
                            40
                    );
        }


        // =====================================
        // 4. 지역 - 최대 10점
        //
        // 조건 없음: 5점
        // 조건 있고 일치: 10점
        // 불일치: 최대 50%
        // =====================================

        if (notice.eligibility.regions == null
                || notice.eligibility.regions.isEmpty()) {

            score += 5;

        } else if (isRegionMatch(
                notice.eligibility.regions,
                region
        )) {

            score += 10;

        } else {

            maxScore =
                    Math.min(
                            maxScore,
                            50
                    );
        }


        // =====================================
        // 5. 나이 조건
        //
        // min_age / max_age 불일치 → 최대 40%
        // =====================================

        if (hasAgeRestriction(notice)) {

            if (!isAgeMatch(
                    notice,
                    age
            )) {

                maxScore =
                        Math.min(
                                maxScore,
                                40
                        );
            }
        }


        // =====================================
        // 6. 학적 상태
        //
        // statuses 불일치 → 최대 40%
        // =====================================

        if (notice.eligibility.statuses != null
                && !notice.eligibility.statuses.isEmpty()) {

            if (!isStatusMatch(
                    notice.eligibility.statuses,
                    studentStatus
            )) {

                maxScore =
                        Math.min(
                                maxScore,
                                40
                        );
            }
        }


        // =====================================
        // 7. 마감 임박 - 최대 5점
        // =====================================

        score += getUrgencyScore(
                notice
        );


        // =====================================
        // 최종 상한 적용
        // =====================================

        return Math.min(
                score,
                maxScore
        );
    }


    // =====================================
    // 학년
    // =====================================

    private static boolean isGradeMatch(
            List<?> targetGrades,
            int userGrade
    ) {

        if (userGrade <= 0) {
            return false;
        }


        for (Object target : targetGrades) {

            if (target == null) {
                continue;
            }


            String value =
                    String.valueOf(target)
                            .replace("학년", "")
                            .trim();


            if (value.equals(
                    String.valueOf(userGrade)
            )) {

                return true;
            }
        }


        return false;
    }


    // =====================================
    // 학과
    // =====================================

    private static boolean isMajorMatch(
            String userMajor,
            List<String> targetDepts
    ) {

        if (userMajor == null
                || userMajor.isEmpty()) {

            return false;
        }


        for (String dept : targetDepts) {

            if (dept == null) {
                continue;
            }


            // 정확히 일치
            if (dept.equals(userMajor)) {
                return true;
            }


            // 어느 한쪽에 전체 학과명이 포함된 경우
            if (dept.contains(userMajor)
                    || userMajor.contains(dept)) {

                return true;
            }
        }


        // -------------------------------------
        // AI소프트웨어학부 개편 전 이름 호환
        // -------------------------------------

        if ("AI소프트웨어학부".equals(
                userMajor
        )) {

            return targetDepts.contains(
                    "AI소프트웨어학부"
            )
                    || targetDepts.contains(
                    "AI융합학부"
            )
                    || targetDepts.contains(
                    "소프트웨어학부"
            );
        }


        return false;
    }


    // =====================================
    // 지역
    // =====================================

    private static boolean isRegionMatch(
            List<String> targetRegions,
            String userRegion
    ) {

        if (userRegion == null
                || userRegion.isEmpty()) {

            return false;
        }


        for (String target : targetRegions) {

            if (target == null) {
                continue;
            }


            if (target.equals(userRegion)
                    || target.contains(userRegion)
                    || userRegion.contains(target)) {

                return true;
            }
        }


        return false;
    }


    // =====================================
    // 나이
    // =====================================

    private static boolean hasAgeRestriction(
            Notice notice
    ) {

        return notice.eligibility.minAge != null
                || notice.eligibility.maxAge != null;
    }


    private static boolean isAgeMatch(
            Notice notice,
            int age
    ) {

        // 사용자가 나이를 아직 입력하지 않았다면
        // 매칭으로 간주하지 않음
        if (age <= 0) {
            return false;
        }


        if (notice.eligibility.minAge != null
                && age < notice.eligibility.minAge) {

            return false;
        }


        if (notice.eligibility.maxAge != null
                && age > notice.eligibility.maxAge) {

            return false;
        }


        return true;
    }


    // =====================================
    // 학적
    // =====================================

    private static boolean isStatusMatch(
            List<String> targetStatuses,
            String userStatus
    ) {

        if (userStatus == null
                || userStatus.isEmpty()) {

            return false;
        }


        String normalizedUser =
                normalizeStatus(
                        userStatus
                );


        for (String status : targetStatuses) {

            if (status == null) {
                continue;
            }


            String normalizedTarget =
                    normalizeStatus(
                            status
                    );


            if (normalizedTarget.equals(
                    normalizedUser
            )
                    || normalizedTarget.contains(
                    normalizedUser
            )
                    || normalizedUser.contains(
                    normalizedTarget
            )) {

                return true;
            }
        }


        return false;
    }


    private static String normalizeStatus(
            String value
    ) {

        return value
                .replace(" ", "")
                .replace("학생", "")
                .trim();
    }


    // =====================================
    // 마감 임박 보너스
    // =====================================

    private static int getUrgencyScore(
            Notice notice
    ) {

        if (notice.deadlineAt == null
                || notice.deadlineAt.isEmpty()) {

            return 0;
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
                return 0;
            }

            if (days <= 3) {
                return 5;
            }

            if (days <= 7) {
                return 4;
            }

            if (days <= 14) {
                return 2;
            }

            if (days <= 30) {
                return 1;
            }


        } catch (Exception ignored) {

        }


        return 0;
    }
}