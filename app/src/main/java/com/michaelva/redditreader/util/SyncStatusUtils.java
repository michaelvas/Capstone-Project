package com.michaelva.redditreader.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import com.michaelva.redditreader.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressLint("CommitPrefEdits")
public class SyncStatusUtils {

	// ordering is important!!!!
	// first 3 must be in this order; rest must have higher value than OK
	public static final int SYNC_STATUS_UNKNOWN = -1;
	public static final int SYNC_STATUS_IN_PROGRESS = 0;
	public static final int SYNC_STATUS_OK = 1;
	public static final int SYNC_STATUS_NO_NETWORK = 2;
	public static final int SYNC_STATUS_NO_AUTHORIZATION = 3;
	public static final int SYNC_STATUS_NO_VALID_SUBMISSIONS = 4;
	public static final int SYNC_STATUS_SERVER_INVALID = 5;
	public static final int SYNC_STATUS_NO_SUBREDDIT_SELECTED = 6;
	public static final int SYNC_STATUS_NO_VALID_SUBREDDITS = 7;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({SYNC_STATUS_UNKNOWN,
			SYNC_STATUS_IN_PROGRESS,
			SYNC_STATUS_OK,
			SYNC_STATUS_NO_NETWORK,
			SYNC_STATUS_NO_AUTHORIZATION,
			SYNC_STATUS_NO_VALID_SUBMISSIONS,
			SYNC_STATUS_SERVER_INVALID,
			SYNC_STATUS_NO_SUBREDDIT_SELECTED,
			SYNC_STATUS_NO_VALID_SUBREDDITS})
	public @interface SyncStatus {}


	@SuppressWarnings("ResourceType")
	@SyncStatus
	public static int getSyncStatus(Context ctx, String statusKey) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getInt(statusKey, SYNC_STATUS_UNKNOWN);
	}

	public static void setSyncStatus(Context ctx, String statusKey, @SyncStatus int status) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putInt(statusKey, status).commit();
	}

	public static void resetSyncStatus(Context ctx, String statusKey) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().remove(statusKey).commit();
	}

	public static @Nullable String getSyncStatusMessage(Context ctx, String statusKey) {
		return getSyncStatusMessage(ctx, getSyncStatus(ctx, statusKey));
	}

	public static @Nullable String getSyncStatusMessage(Context ctx, @SyncStatus int status) {
		switch (status) {
			case SYNC_STATUS_UNKNOWN:
				return ctx.getString(R.string.message_not_synced_yet);
			case SYNC_STATUS_IN_PROGRESS:
				return ctx.getString(R.string.message_sync_in_progress);
			case SYNC_STATUS_NO_NETWORK:
				return ctx.getString(R.string.message_no_internet);
			case SYNC_STATUS_NO_AUTHORIZATION:
				return ctx.getString(R.string.message_not_authorized);
			case SYNC_STATUS_NO_VALID_SUBREDDITS:
				return ctx.getString(R.string.message_no_valid_subreddits);
			case SYNC_STATUS_NO_VALID_SUBMISSIONS:
				return ctx.getString(R.string.message_no_valid_submissions);
			case SYNC_STATUS_SERVER_INVALID:
				return ctx.getString(R.string.message_server_invalid);
			case SYNC_STATUS_NO_SUBREDDIT_SELECTED:
				return ctx.getString(R.string.message_no_subreddits_selected);
			default: // SYNC_STATUS_OK
				return ctx.getString(R.string.message_sync_complete);
		}
	}

}
