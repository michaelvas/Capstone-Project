package com.michaelva.redditreader.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.lang3.StringEscapeUtils;

@SuppressLint("CommitPrefEdits")
public class Utils {

	public static boolean isNetworkAvailable(Context ctx) {
		ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
	}

	public static boolean atLeastOneSubredditSelected(Context ctx) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean(Consts.AT_LEAST_ONE_SUBREDDIT_SELECTED_PREF, false);
	}

	public static void setAtLeastOneSubredditSelected(Context ctx, boolean selected) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putBoolean(Consts.AT_LEAST_ONE_SUBREDDIT_SELECTED_PREF, selected).commit();
	}

	public static boolean isUserAuthenticated(Context ctx) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean(Consts.IS_USER_AUTHENTICATED_PREF, false);
	}

	public static void setUserAuthenticated(Context ctx, boolean authenticated) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putBoolean(Consts.IS_USER_AUTHENTICATED_PREF, authenticated).commit();
	}

	public static void clearUserState(Context ctx) {
		PreferenceManager.getDefaultSharedPreferences(ctx)
		.edit()
		.putBoolean(Consts.IS_USER_AUTHENTICATED_PREF, false)
		.putInt(Consts.SUBREDDITS_SYNC_STATUS_PREF, SyncStatusUtils.SYNC_STATUS_UNKNOWN)
		.putInt(Consts.SUBMISSIONS_SYNC_STATUS_PREF, SyncStatusUtils.SYNC_STATUS_UNKNOWN)
		.putBoolean(Consts.AT_LEAST_ONE_SUBREDDIT_SELECTED_PREF, false)
		.commit();
	}

	public static String processRedditHtmlContent(String commentHtml) {
		String commentBody = StringEscapeUtils.unescapeHtml4(commentHtml);
		commentBody = commentBody.replace("href=\"/u/", "href=\"https://www.reddit.com/u/");
		commentBody = commentBody.replace("href=\"/r/", "href=\"https://www.reddit.com/r/");
		return commentBody;
	}

	public static int dpToPixel(Context ctx, int dp) {
		return (int) (ctx.getResources().getDisplayMetrics().density * dp + 0.5f);
	}

	public static void launchExternalViewer(Context ctx, int submissionType, String submissionUrl) {
		Intent i = new Intent();
		i.setAction(Intent.ACTION_VIEW);

		String mimeType = null;
		String fileExt = null;

		switch (submissionType) {
			case Consts.SubmissionType.VIDEO:
			case Consts.SubmissionType.IMAGE:
				// determine mime type
				fileExt = MimeTypeMap.getFileExtensionFromUrl(submissionUrl);
				if (!"".equals(fileExt)) {
					mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
				}

				break;
			default:
				// do nothing (will launch activity without setting mime type
		}

		// stupid animated gifs!!!!
		if (mimeType == null || ("gif".equals(fileExt) && submissionType == Consts.SubmissionType.VIDEO)) {
			i.setData(Uri.parse(submissionUrl));
			ctx.startActivity(i);
		} else {

			// first, try with specific type
			i.setDataAndType(Uri.parse(submissionUrl), mimeType);
			if (ctx.getPackageManager().resolveActivity(i, PackageManager.MATCH_ALL) != null) {
				ctx.startActivity(i);
			} else {
				// remove specific type and try again
				i.setData(Uri.parse(submissionUrl));
				ctx.startActivity(i);
			}
		}
	}
}
