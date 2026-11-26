package com.example.ssu_pick;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SplashScreen splashScreen =
                SplashScreen.installSplashScreen(this);

        long startTime =
                SystemClock.uptimeMillis();

        // 개발 중 확인용
        splashScreen.setKeepOnScreenCondition(() ->
                SystemClock.uptimeMillis() - startTime < 1500
        );

        super.onCreate(savedInstanceState);


        SharedPreferences prefs =
                getSharedPreferences(
                        "SSUPICK_PREFS",
                        MODE_PRIVATE
                );


        boolean onboardingCompleted =
                prefs.getBoolean(
                        "onboarding_completed",
                        false
                );


        Intent intent;


        if (onboardingCompleted) {

            // 이미 온보딩 완료
            intent =
                    new Intent(
                            LauncherActivity.this,
                            MainActivity.class
                    );

        } else {

            // 최초 실행
            intent =
                    new Intent(
                            LauncherActivity.this,
                            ProfileSetupActivity.class
                    );
        }


        startActivity(intent);
        finish();
    }
}