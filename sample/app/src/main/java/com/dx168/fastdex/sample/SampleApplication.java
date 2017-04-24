package com.dx168.fastdex.sample;

import android.content.Context;
import android.support.multidex.MultiDex;

import java.io.IOException;

/**
 * Created by tong on 17/10/3.
 */
public class SampleApplication extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            getAssets().open("ass.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
    }
}
