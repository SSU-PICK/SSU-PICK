package com.example.ssu_pick.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class BookmarkManager {

    private static final String PREF_NAME = "SSUPICK_PREFS";
    private static final String KEY_BOOKMARKS = "bookmarked_notices";


    public static Set<String> getBookmarks(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        return new HashSet<>(
                prefs.getStringSet(
                        KEY_BOOKMARKS,
                        new HashSet<>()
                )
        );
    }


    public static boolean isBookmarked(
            Context context,
            String noticeId
    ) {

        if (noticeId == null) {
            return false;
        }

        return getBookmarks(context)
                .contains(noticeId);
    }


    public static void addBookmark(
            Context context,
            String noticeId
    ) {

        if (noticeId == null) {
            return;
        }

        Set<String> bookmarks =
                getBookmarks(context);

        bookmarks.add(noticeId);

        save(context, bookmarks);
    }


    public static void removeBookmark(
            Context context,
            String noticeId
    ) {

        if (noticeId == null) {
            return;
        }

        Set<String> bookmarks =
                getBookmarks(context);

        bookmarks.remove(noticeId);

        save(context, bookmarks);
    }


    private static void save(
            Context context,
            Set<String> bookmarks
    ) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        prefs.edit()
                .putStringSet(
                        KEY_BOOKMARKS,
                        new HashSet<>(bookmarks)
                )
                .apply();
    }
}