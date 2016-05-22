package com.michaelva.redditreader.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Taken from Sunshine application:
 * https://github.com/udacity/Advanced_Android_Development
 *
 * The service which allows the sync adapter framework to access the authenticator.
 */
public class StubAuthenticatorService extends Service {
    // Instance field that stores the authenticator object
    private StubAuthenticator mAuthenticator;

    @Override
    public void onCreate() {
        // Create a new authenticator object
        mAuthenticator = new StubAuthenticator(this);
    }

    /*
     * When the system binds to this Service to make the RPC call
     * return the authenticator's IBinder.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }
}
