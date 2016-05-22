package com.michaelva.redditreader.task;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.auth.AuthenticationState;

public abstract class AbstractTask<Result extends AbstractTask.TaskResult> extends AsyncTaskLoader<Result> {
	private final String LOG_TAG = getClass().getCanonicalName();

	protected final Result mResult;

	public static class TaskResult {
		public boolean success;
		public String error;
	}

	public AbstractTask(Context context, Result result) {
		super(context);

		mResult = result;

		onContentChanged();
	}

	@Override
	protected void onStartLoading() {
		if (takeContentChanged()) {
			forceLoad();
		}
	}

	protected void onStart() {
		// do nothing
	}

	protected void onFinish(int status) {
		// do nothing
	}

	protected abstract void performTask() throws Exception;

	@Override
	public Result loadInBackground() {
		Context ctx = getContext();

		mResult.success = false;

		onStart();

		// stop if user did not authorize access
		if (!Utils.isUserAuthenticated(ctx)) {
			Log.d(LOG_TAG, "User is not authenticated. Stopping...");
			mResult.success = false;
			mResult.error = ctx.getString(R.string.message_not_authorized);
			onFinish(SyncStatusUtils.SYNC_STATUS_NO_AUTHORIZATION);
			return mResult;
		}


		// stop if network is not available
		if (!Utils.isNetworkAvailable(ctx)) {
			Log.d(LOG_TAG, "Network is not available. Stopping...");
			mResult.success = false;
			mResult.error = ctx.getString(R.string.message_no_internet);
			onFinish(SyncStatusUtils.SYNC_STATUS_NO_NETWORK);
			return mResult;
		}

		try {
			AuthenticationManager authMngr = AuthenticationManager.get();

			// refresh access token if needed
			if (authMngr.checkAuthState() == AuthenticationState.NEED_REFRESH) {

				Log.d(LOG_TAG, "Refreshing access token");
				authMngr.refreshAccessToken(Consts.APP_CREDENTIALS);
			}

			performTask();

			mResult.success = true;

			onFinish(SyncStatusUtils.SYNC_STATUS_OK);

			return mResult;
		} catch (Exception e) {
			Log.e(LOG_TAG, "Received error from JRAW", e);

			mResult.success = false;
			String error = e.getMessage();
			if (error.contains("401 Unauthorized")) {
				mResult.error = ctx.getString(R.string.message_not_authorized);
				onFinish(SyncStatusUtils.SYNC_STATUS_NO_AUTHORIZATION);
			} else if (error.contains("RATELIMIT")) {
				try {
					String tryAgain = "try again in ";
					int start = error.indexOf(tryAgain) + tryAgain.length();
					int end = error.indexOf(".\"", start);
					mResult.error = ctx.getString(R.string.message_ratelimit_error, error.substring(start, end));
				} catch (Exception ee) {
					mResult.error = ctx.getString(R.string.message_internal_error);
				}
				onFinish(SyncStatusUtils.SYNC_STATUS_SERVER_INVALID);
			} else {
				mResult.error = ctx.getString(R.string.message_internal_error);
				onFinish(SyncStatusUtils.SYNC_STATUS_SERVER_INVALID);
			}

			return mResult;
		}
	}

}
