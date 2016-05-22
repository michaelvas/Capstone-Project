package com.michaelva.redditreader.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.service.UtilityService;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.auth.AuthenticationState;

public abstract class AbstractSyncAdapter<T> extends AbstractThreadedSyncAdapter {
	private final String LOG_TAG = getClass().getCanonicalName();

	private static final String ACCOUNT_TYPE = "redditreader.michaelva.com";

	public AbstractSyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}

	public AbstractSyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
		super(context, autoInitialize, allowParallelSyncs);
	}

	/**
	 * Triggers immediate sync for input content authority and optional extra arguments
	 */
	static void syncNow(Context ctx, String authority, Bundle extras) {
		if (extras == null) {
			extras = new Bundle();
		}
		extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
		ContentResolver.requestSync(getSyncAccount(ctx, authority), authority, extras);
	}

	/**
	 * Initializes the sync adapter for input content authority
	 */
	static void init(Context ctx, String authority, int syncInterval, int syncFlexTime) {
		Account account = getSyncAccount(ctx, authority);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			// enable inexact timers
			SyncRequest request = new SyncRequest.Builder()
					.syncPeriodic(syncInterval, syncFlexTime)
					.setSyncAdapter(account, authority)
					.setExtras(Bundle.EMPTY)
					.build();
			ContentResolver.requestSync(request);
		} else {
			ContentResolver.addPeriodicSync(account, authority, Bundle.EMPTY, syncInterval);
		}

		ContentResolver.setSyncAutomatically(account, authority, true);
	}

	/**
	 * Returns a stub account to be used with the sync service. Will trigger immediate sync if
	 * account does not exist yet (in addition to creating a new account)
	 */
	static Account getSyncAccount(Context ctx, String authority) {
		AccountManager accountManager = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);

		Account newAccount = new Account(ctx.getString(R.string.app_name), ACCOUNT_TYPE);

		// If the password doesn't exist, the account doesn't exist
		if (accountManager.getPassword(newAccount) == null) {

			// create new account
			if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
				return null;
			}

			syncNow(ctx, authority, null);
		}

		return newAccount;
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		Log.d(LOG_TAG, "Starting sync");

		Context ctx = getContext();

		String statusKey = getSyncStatusKeyName();

		SyncStatusUtils.setSyncStatus(ctx, statusKey, SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);

		// reset previously retrieved data
		T previousData = removePreviousData(extras);

		// stop if user did not authorize access
		if (!Utils.isUserAuthenticated(ctx)) {
			Log.d(LOG_TAG, "User is not authenticated. Stopping...");
			SyncStatusUtils.setSyncStatus(ctx, statusKey, SyncStatusUtils.SYNC_STATUS_NO_AUTHORIZATION);
			return;
		}

		// stop if network is not available
		if (!Utils.isNetworkAvailable(ctx)) {
			Log.d(LOG_TAG, "Network is not available. Stopping...");
			SyncStatusUtils.setSyncStatus(ctx, statusKey, SyncStatusUtils.SYNC_STATUS_NO_NETWORK);
			return;
		}

		AuthenticationManager authMngr = AuthenticationManager.get();

		if (authMngr.checkAuthState() == AuthenticationState.NEED_REFRESH) {
			// refresh access token
			try {
				Log.d(LOG_TAG, "Refreshing access token");
				authMngr.refreshAccessToken(Consts.APP_CREDENTIALS);
			} catch (Exception e) {
				checkError(ctx, e);
				return;
			}
		}

		performSync(extras, previousData);

		// send broadcast so other components could react

		Intent dataUpdatedIntent = new Intent(getDataUpdateAction()).setPackage(ctx.getPackageName());
		ctx.sendBroadcast(dataUpdatedIntent);

		Log.d(LOG_TAG, "Sync finished");
	}

	abstract String getSyncStatusKeyName();

	abstract T removePreviousData(Bundle extras);

	abstract void performSync(Bundle extras, T previousData);

	abstract String getDataUpdateAction();

	protected void checkError(Context ctx, Exception e) {
		Log.e(LOG_TAG, "Received error from JRAW", e);

		String message = e.getMessage();

		if (message.contains(Consts.NOT_AUTHORIZED)) {
			Log.w(LOG_TAG, "Looks like user is no longer authorized. Will have to re-authenticate");
			UtilityService.startActionRemoveUserData(ctx);
			Utils.clearUserState(ctx);
		} else {
			Log.d(LOG_TAG, "Sync status: " + SyncStatusUtils.SYNC_STATUS_SERVER_INVALID + "(" + e.getMessage() + ")");
			SyncStatusUtils.setSyncStatus(ctx, getSyncStatusKeyName(), SyncStatusUtils.SYNC_STATUS_SERVER_INVALID);
		}
	}
}
