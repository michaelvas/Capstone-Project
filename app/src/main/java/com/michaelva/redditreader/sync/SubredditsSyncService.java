package com.michaelva.redditreader.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SubredditsSyncService extends Service {

    private static final Object sSyncAdapterLock = new Object();

    private static SubredditsSyncAdapter sSyncAdapter = null;

    @Override
    public void onCreate() {

        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new SubredditsSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}