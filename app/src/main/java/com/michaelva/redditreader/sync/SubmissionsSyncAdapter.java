package com.michaelva.redditreader.sync;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import static com.michaelva.redditreader.data.RedditContract.SubredditEntry;
import static com.michaelva.redditreader.data.RedditContract.SubmissionEntry;
import static com.michaelva.redditreader.util.Consts.SubmissionType;

import com.michaelva.redditreader.data.RedditContract;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.SubredditPaginator;

import org.apache.commons.lang3.StringEscapeUtils;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SubmissionsSyncAdapter extends AbstractSyncAdapter<Void> {
	public final String LOG_TAG = getClass().getCanonicalName();

	private static final String AUTHORITY = "com.michaelva.redditreader.datasync.submission";

	private static final String ARG_SYNC_PENDING = "SYNC_PENDING";

	public static final int SYNC_INTERVAL = 60 * 60; // 1 hour
	public static final int SYNC_FLEXTIME = 60 * 10; // 10 minutes

	public static void initialize(Context ctx) {
		AbstractSyncAdapter.init(ctx, AUTHORITY, SYNC_INTERVAL, SYNC_FLEXTIME);
	}

	public static void syncNow(Context ctx) {
		AbstractSyncAdapter.syncNow(ctx, AUTHORITY, null);
	}

	public static void syncPending(Context ctx) {
		Bundle args = new Bundle();
		args.putBoolean(ARG_SYNC_PENDING, true);

		AbstractSyncAdapter.syncNow(ctx, AUTHORITY, args);
	}

	public SubmissionsSyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}

	@Override
	String getSyncStatusKeyName() {
		return Consts.SUBMISSIONS_SYNC_STATUS_PREF;
	}

	@Override
	String getDataUpdateAction() {
		return Consts.ACTION_SUBMISSIONS_DATA_UPDATED;
	}

	@Override
	Void removePreviousData(Bundle extras) {
		boolean syncPending = extras.getBoolean(ARG_SYNC_PENDING);

		ContentResolver cr = getContext().getContentResolver();
		if (syncPending) {
			// delete submissions for subreddits that are scheduled to be "unselected" (currently selected and have pending state change)
			Cursor data = cr.query(SubredditEntry.CONTENT_URI, new String[] {SubredditEntry._ID}, SubredditEntry.BY_SELECTED_CONDITION + " AND " + SubredditEntry.BY_PENDING_STATE_CHANGE_CONDITION, new String[] {"1", "1"}, null);
			if (data != null) {
				while (data.moveToNext()) {
					cr.delete(SubmissionEntry.CONTENT_URI, SubmissionEntry.BY_SUBREDDIT_ID_CONDITION, new String[] {data.getString(0)});
				}
				data.close();
			}

		} else {

			// delete all submissions
			getContext().getContentResolver().delete(SubmissionEntry.CONTENT_URI, null, null);
		}

		return null;
	}

	@Override
	void performSync(Bundle extras, Void previousData) {
		boolean syncPending = extras.getBoolean(ARG_SYNC_PENDING);

		if (syncPending) {
			syncPendingSubreddits();
		} else {
			syncAllSubreddits();
		}
	}

	private void syncPendingSubreddits() {
		Context ctx = getContext();

		ContentResolver cr = ctx.getContentResolver();

		HashMap<Long, String> selectedSubreddits = new HashMap<>();

		// get list of subreddits scheduled to be selected (currently not selected and have pending state change)
		Cursor data = cr.query(SubredditEntry.CONTENT_URI, new String[] {SubredditEntry._ID, SubredditEntry.COLUMN_NAME}, SubredditEntry.BY_SELECTED_CONDITION + " AND " + SubredditEntry.BY_PENDING_STATE_CHANGE_CONDITION, new String[] {"0", "1"}, null);
		if (data != null) {
			while (data.moveToNext()) {
				selectedSubreddits.put(data.getLong(0), data.getString(1));
			}
			data.close();
		}

		// Collect all operations
		ArrayList<ContentProviderOperation> ops = new ArrayList<>();

		// add inserts for every subreddit
		for (Long subredditId : selectedSubreddits.keySet()) {
			ops.addAll(processSubreddit(selectedSubreddits.get(subredditId), subredditId));
		}

		// add updates to set the new "selected" state and reset the pending state
		ContentProviderOperation update = ContentProviderOperation
				.newUpdate(SubredditEntry.CONTENT_URI)
				.withSelection(SubredditEntry.BY_SELECTED_CONDITION + " AND " + SubredditEntry.BY_PENDING_STATE_CHANGE_CONDITION, new String[] {"1", "1"})
				.withValue(SubredditEntry.COLUMN_SELECTED, "0")
				.withValue(SubredditEntry.COLUMN_PENDING_STATE_CHANGE, "0")
				.build();
		ops.add(update);

		update = ContentProviderOperation
				.newUpdate(SubredditEntry.CONTENT_URI)
				.withSelection(SubredditEntry.BY_SELECTED_CONDITION + " AND " + SubredditEntry.BY_PENDING_STATE_CHANGE_CONDITION, new String[] {"0", "1"})
				.withValue(SubredditEntry.COLUMN_SELECTED, "1")
				.withValue(SubredditEntry.COLUMN_PENDING_STATE_CHANGE, "0")
				.build();
		ops.add(update);

		try {
			cr.applyBatch(RedditContract.CONTENT_AUTHORITY, ops);
		} catch (OperationApplicationException | RemoteException e) {
			// should not happen as we're not accessing anything remote
			Log.e(LOG_TAG, "Can't update DB?", e);
		}

		// check we still have at least one submission
		boolean atLeastOneValidSubmission = false;
		data = cr.query(SubmissionEntry.buildSubmissionCountUri(), null, null, null, null);
		if (data != null) {
			data.moveToFirst();
			atLeastOneValidSubmission = data.getInt(0) > 0;
			data.close();
		}

		// we're done!!
		int status = atLeastOneValidSubmission ? SyncStatusUtils.SYNC_STATUS_OK : SyncStatusUtils.SYNC_STATUS_NO_VALID_SUBMISSIONS;

		Log.d(LOG_TAG, "Sync status: " + status);

		SyncStatusUtils.setSyncStatus(ctx, getSyncStatusKeyName(), status);

	}

	private void syncAllSubreddits() {
		Context ctx = getContext();

		ContentResolver cr = ctx.getContentResolver();

		HashMap<Long, String> selectedSubreddits = new HashMap<>();

		// get list of all selected subreddits
		Cursor data = cr.query(SubredditEntry.CONTENT_URI, new String[] {SubredditEntry._ID, SubredditEntry.COLUMN_NAME}, SubredditEntry.BY_SELECTED_CONDITION, new String[] {"1"}, null);
		if (data != null) {
			while (data.moveToNext()) {
				selectedSubreddits.put(data.getLong(0), data.getString(1));
			}
			data.close();
		}

		// Collect all operations
		ArrayList<ContentProviderOperation> ops = new ArrayList<>();

		// add inserts for every subreddit
		for (Long subredditId : selectedSubreddits.keySet()) {
			ops.addAll(processSubreddit(selectedSubreddits.get(subredditId), subredditId));
		}

		try {
			cr.applyBatch(RedditContract.CONTENT_AUTHORITY, ops);
		} catch (OperationApplicationException | RemoteException e) {
			// should not happen as we're not accessing anything remote
			Log.e(LOG_TAG, "Can't update DB?", e);
		}

		// check we still have at least one submission
		boolean atLeastOneValidSubmission = false;
		data = cr.query(SubmissionEntry.buildSubmissionCountUri(), null, null, null, null);
		if (data != null) {
			data.moveToFirst();
			atLeastOneValidSubmission = data.getInt(0) > 0;
			data.close();
		}

		// we're done!!
		int status = atLeastOneValidSubmission ? SyncStatusUtils.SYNC_STATUS_OK : SyncStatusUtils.SYNC_STATUS_NO_VALID_SUBMISSIONS;

		Log.d(LOG_TAG, "Sync status: " + status);
		SyncStatusUtils.setSyncStatus(ctx, getSyncStatusKeyName(), status);
	}

	private ArrayList<ContentProviderOperation> processSubreddit(String subredditName, long subredditId) {
		ArrayList<ContentProviderOperation> ops = new ArrayList<>();
		ContentProviderOperation operation;

		RedditClient redditClient = AuthenticationManager.get().getRedditClient();

		SubredditPaginator paginator = new SubredditPaginator(redditClient, subredditName);

		List<Submission> submissions = null;
		try {
			submissions = paginator.next();
		} catch (Exception e) {
			Log.w(LOG_TAG, "Failed to get submissions for subreddit " + subredditName + "; will try in the next sync", e);
		}

		if (submissions == null) {
			return ops;
		}

		for (Submission submission : submissions) {
			// skip NSFW submissions
			if (submission.isNsfw() || submission.getTitle().toLowerCase().contains("nsfw")) {
				continue;
			}

			// skip "sticky" submissions
			if (submission.isStickied()) {
				continue;
			}

			// skip "hidden" submissions
			if (submission.isHidden()) {
				continue;
			}

			ContentValues values = new ContentValues();

			//extract basic properties
			values.put(SubmissionEntry._ID, Long.parseLong(submission.getId(), 36)); // converting Reddit ID to long
			values.put(SubmissionEntry.COLUMN_SUBREDDIT_ID, subredditId);
			values.put(SubmissionEntry.COLUMN_TITLE, StringEscapeUtils.unescapeHtml4(submission.getTitle()));
			values.put(SubmissionEntry.COLUMN_AUTHOR, submission.getAuthor());
			values.put(SubmissionEntry.COLUMN_URL, submission.getUrl());

			boolean readOnly = submission.isArchived() || submission.isLocked();

			values.put(SubmissionEntry.COLUMN_READ_ONLY, readOnly ? 1 : 0);

			// determine submission type
			int type = SubmissionType.UNKNOWN;

			if(submission.isSelfPost()) {
				type = SubmissionType.TEXT;
			} else {
				switch (submission.getPostHint()) {
					case IMAGE:
						type = SubmissionType.IMAGE;
						break;
					case VIDEO:
						type = SubmissionType.VIDEO;
						break;
					case LINK:
						type = SubmissionType.WEB;
						break;
					default:

						// not clear what is the type, try to determine it

						// see if URL represents an image
						try {
							URLConnection con = new URL(submission.getUrl()).openConnection();
							if (con.getHeaderField("Content-Type").startsWith("image/")) {
								type = SubmissionType.IMAGE;
							}
						} catch (Exception e) {
							// OK, it is not an image
						}

						// if not an image, it may be a WEB link
						if (type != SubmissionType.IMAGE) {
							if (!submission.getUrl().startsWith("https://www.reddit.com")) {
								type = SubmissionType.WEB;
							} else {
								// we treat this as a text submission
								type = SubmissionType.TEXT;
							}
						}

				}

				// check if we have preview image
				try {
					values.put(
						SubmissionEntry.COLUMN_PREVIEW_IMAGE,
						submission.getDataNode().get("preview").get("images").get(0).get("source").get("url").textValue()
					);
				} catch (Exception e) {
					// that's fine; it just means this submission does not have preview image
				}

				// edge case: submission marked as link, but domain is "reddituploads"
				// - URL will not open outside the app; we must replace it with preview (if available)
				if (type == SubmissionType.WEB && submission.getDomain().equals("i.reddituploads.com")) {
					if (values.get(SubmissionEntry.COLUMN_PREVIEW_IMAGE) != null) {
						type = SubmissionType.IMAGE;
						values.put(SubmissionEntry.COLUMN_URL, values.getAsString(SubmissionEntry.COLUMN_PREVIEW_IMAGE));
					} else {
						// don't know what the heck this is; skipping it
						continue;
					}
				}
			}

			// try to get the content (optional) of a text submission
			if (type == SubmissionType.TEXT) {
				String selfText = submission.data("selftext_html");
				if (selfText != null && !selfText.trim().isEmpty()) {
					values.put(SubmissionEntry.COLUMN_TEXT, Utils.processRedditHtmlContent(selfText.trim()));
				}
			}

			values.put(SubmissionEntry.COLUMN_TYPE, type);

			operation = ContentProviderOperation
					.newInsert(SubmissionEntry.CONTENT_URI)
					.withValues(values)
					.build();

			ops.add(operation);
		}

		return ops;
	}

}