package com.michaelva.redditreader.task;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import com.michaelva.redditreader.BuildConfig;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.service.UtilityService;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.Utils;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.http.LoggingMode;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthException;
import net.dean.jraw.http.oauth.OAuthHelper;

public class UserAuthenticationTask extends AsyncTaskLoader<UserAuthenticationTask.Result> {
	private final String LOG_TAG = getClass().getCanonicalName();

	public static String ARG_URL = "URL";

	private String mUrl;

	public UserAuthenticationTask(Context context, Bundle args) {
		super(context);

		mUrl = args.getString(ARG_URL);

		onContentChanged();
	}

	@Override
	protected void onStartLoading() {
		if (takeContentChanged()) {
			forceLoad();
		}
	}

	@Override
	public Result loadInBackground() {

		Result result = new Result();
		result.success = false;

		UtilityService.startActionRemoveUserData(getContext());

		Utils.clearUserState(getContext());

		RedditClient reddit = AuthenticationManager.get().getRedditClient();

		try {

			// retrieve authorization parameters
			OAuthData data = reddit.getOAuthHelper().onUserChallenge(mUrl, Consts.APP_CREDENTIALS);

			// create new authorization
			reddit.authenticate(data);

			// done!
			result.success = true;

		} catch (OAuthException e) {
			Log.e(LOG_TAG, e.getMessage());
			if (e.getMessage().contains("access_denied")) {
				// see https://github.com/reddit/reddit/wiki/OAuth2#allowing-the-user-to-authorize-your-application
				result.error = getContext().getString(R.string.message_not_authorized);
			} else {
				result.error = getContext().getString(R.string.message_internal_error);
			}
		} catch (IllegalStateException e) {
			Log.e(LOG_TAG, e.getMessage());
			result.error = getContext().getString(R.string.message_internal_error);
		} catch (NetworkException e) {
			Log.e(LOG_TAG, e.getMessage());
			result.error = getContext().getString(R.string.message_no_internet);
		}

		return result;
	}

	public static class Result {
		public boolean success;
		public String error;
	}
}
