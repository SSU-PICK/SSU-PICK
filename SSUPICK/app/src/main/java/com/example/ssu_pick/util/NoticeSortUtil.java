package com.example.ssu_pick.util;

import com.example.ssu_pick.model.Notice;

import java.time.OffsetDateTime;

public class NoticeSortUtil {

    public static boolean isExpired(
            Notice notice
    ) {

        if (notice == null
                || notice.deadlineAt == null
                || notice.deadlineAt.isEmpty()) {

            return false;
        }


        try {

            OffsetDateTime deadline =
                    OffsetDateTime.parse(
                            notice.deadlineAt
                    );

            return deadline.isBefore(
                    OffsetDateTime.now(
                            deadline.getOffset()
                    )
            );

        } catch (Exception e) {

            return false;
        }
    }


    public static int compareRecommendation(
            Notice a,
            Notice b
    ) {

        boolean aExpired =
                isExpired(a);

        boolean bExpired =
                isExpired(b);


        if (aExpired != bExpired) {

            return aExpired ? 1 : -1;
        }


        return Integer.compare(
                b.matchScore,
                a.matchScore
        );
    }
}