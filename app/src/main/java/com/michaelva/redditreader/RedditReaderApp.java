package com.michaelva.redditreader;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.webkit.CookieManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.sync.SubmissionsSyncAdapter;
import com.michaelva.redditreader.sync.SubredditsSyncAdapter;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.auth.NoSuchTokenException;
import net.dean.jraw.auth.RefreshTokenHandler;
import net.dean.jraw.auth.TokenStore;
import net.dean.jraw.http.LoggingMode;
import net.dean.jraw.http.UserAgent;

/**
 * Reddit authentication code is based on the following:
 * https://github.com/thatJavaNerd/JRAW-Android/blob/master/library/src/main/java/net/dean/jraw/android/AndroidRedditClient.java
 * https://github.com/thatJavaNerd/JRAW-Android/blob/master/library/src/main/java/net/dean/jraw/android/AndroidTokenStore.java
 */
public class RedditReaderApp extends Application {

	private Tracker mTracker;

	@Override
	public void onCreate() {
		super.onCreate();

		RedditClient reddit = new RedditClient(UserAgent.of("android", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.REDDIT_APP_OWNER));
		reddit.setLoggingMode(BuildConfig.BUILD_TYPE.equals("debug") ? LoggingMode.ALWAYS : LoggingMode.ON_FAIL);
		reddit.getHttpLogger().setResponseBodyAlwaysFull(BuildConfig.BUILD_TYPE.equals("debug"));

		TokenStore tokenStore = new TokenStore() {
			@Override
			public boolean isStored(String key) {
				return PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).contains(key);
			}

			@Override
			public String readToken(String key) throws NoSuchTokenException {
				String value = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(key, null);
				if (value == null) {
					throw new NoSuchTokenException(key);
				}
				return value;
			}

			@Override
			public void writeToken(String key, String token) {
				PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString(key, token).apply();
			}
		};

		AuthenticationManager.get().init(reddit, new RefreshTokenHandler(tokenStore, reddit));

		SubredditsSyncAdapter.initialize(this);

		SubmissionsSyncAdapter.initialize(this);
	}

	synchronized public Tracker getDefaultTracker() {
		if (mTracker == null) {
			GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
			// To enable debug logging use: adb shell setprop log.tag.GAv4 DEBUG
			mTracker = analytics.newTracker(R.xml.global_tracker);
		}
		return mTracker;
	}
}
