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

import com.michaelva.redditreader.data.RedditContract;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.paginators.UserSubredditsPaginator;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static com.michaelva.redditreader.data.RedditContract.SubredditEntry;
import static com.michaelva.redditreader.data.RedditContract.SubmissionEntry;

public class SubredditsSyncAdapter extends AbstractSyncAdapter<Set<Long>> {
    private final String LOG_TAG = getClass().getCanonicalName();

	private static final String AUTHORITY = "com.michaelva.redditreader.datasync.subreddit";

    public static final int SYNC_INTERVAL = 60 * 60 * 24; // 24 hours
    public static final int SYNC_FLEXTIME = 60 * 60; // 1 hour

    public static void initialize(Context ctx) {
        AbstractSyncAdapter.init(ctx, AUTHORITY, SYNC_INTERVAL, SYNC_FLEXTIME);
    }

    public static void syncNow(Context ctx) {
        AbstractSyncAdapter.syncNow(ctx, AUTHORITY, null);
    }

    public SubredditsSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

	@Override
	String getSyncStatusKeyName() {
		return Consts.SUBREDDITS_SYNC_STATUS_PREF;
	}

	@Override
	String getDataUpdateAction() {
		return Consts.ACTION_SUBREDDITS_DATA_UPDATED;
	}

	@Override
	Set<Long> removePreviousData(Bundle extras) {
		ContentResolver cr = getContext().getContentResolver();

		Set<Long> selectedSubreddits = new HashSet<>();

		Cursor data = cr.query(SubredditEntry.CONTENT_URI, new String[] {SubredditEntry._ID}, SubredditEntry.BY_SELECTED_CONDITION, new String [] {"1"}, null);
		if (data != null) {
			while (data.moveToNext()) {
				selectedSubreddits.add(data.getLong(0));
			}
			data.close();
		}

		cr.delete(SubredditEntry.CONTENT_URI, null ,null);

		return selectedSubreddits;
	}

	@Override
    void performSync(Bundle extras, Set<Long> selectedSubreddits) {
		Context ctx = getContext();

        RedditClient redditClient = AuthenticationManager.get().getRedditClient();

        // get all subreddits that user is subscribed to excluding NSFW subreddits

        UserSubredditsPaginator paginator = new UserSubredditsPaginator(redditClient, "subscriber");

        HashMap<String, Subreddit> latestSubreddits = new HashMap<>();
        try {
            while (paginator.hasNext()) {
                Listing<Subreddit> subreddits = paginator.next();
                for (Subreddit subreddit: subreddits) {
                    if (!subreddit.isNsfw()) {
                        latestSubreddits.put(subreddit.getId(), subreddit);
                    }
                }
            }
        } catch (Exception e) {
			checkError(ctx, e);
			return;
        }

		// check that we have at least one valid subreddit
		if (latestSubreddits.isEmpty()) {
			Log.d(LOG_TAG, "All user's subreddits are NSFW. Stopping...");
			SyncStatusUtils.setSyncStatus(ctx, getSyncStatusKeyName(), SyncStatusUtils.SYNC_STATUS_NO_VALID_SUBREDDITS);
			return;
		}

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
		ContentProviderOperation operation;

		// go over list of retrieved subreddits and prepare insert operations
		ContentValues values;
		Long subredditId;
		int selected;
		for (Subreddit subreddit: latestSubreddits.values()) {
			values = new ContentValues();
			subredditId = Long.parseLong(subreddit.getId(), 36); // converting Reddit ID to long
			values.put(SubredditEntry._ID, subredditId);
			values.put(SubredditEntry.COLUMN_NAME, subreddit.getDisplayName());
			values.put(SubredditEntry.COLUMN_DESCRIPTION, StringEscapeUtils.unescapeHtml4(subreddit.getPublicDescription()));

			selected = 0;
			if (selectedSubreddits.contains(subredditId)) {
				selected = 1;
				selectedSubreddits.remove(subredditId); // remove from the set so we could later process remaining subreddits
			}
			values.put(SubredditEntry.COLUMN_SELECTED, selected);

			operation = ContentProviderOperation.newInsert(SubredditEntry.CONTENT_URI).withValues(values).build();

			ops.add(operation);
		}

		// go over selected subreddits that are no longer available (were not present in the response)
		// and prepare delete operations for related submissions
		for (Long subreddit : selectedSubreddits) {
			operation = ContentProviderOperation
					.newDelete(SubmissionEntry.CONTENT_URI)
					.withSelection(SubmissionEntry.BY_SUBREDDIT_ID_CONDITION, new String [] {subreddit.toString()})
					.build();
			ops.add(operation);
		}

		ContentResolver cr = ctx.getContentResolver();

		// execute operations
		try {
			cr.applyBatch(RedditContract.CONTENT_AUTHORITY, ops);
		} catch (OperationApplicationException | RemoteException e) {
			// should not happen as we're not accessing anything remote
			Log.e(LOG_TAG, "Can't update DB?", e);
		}


		// check that at least one subreddit is still selected

		Cursor data = cr.query(SubredditEntry.buildSubredditCountUri(), null, SubredditEntry.BY_SELECTED_CONDITION, new String[] {"1"}, null, null);
		if (data != null) {
			data.moveToFirst();
			Utils.setAtLeastOneSubredditSelected(ctx, data.getInt(0) > 0);
			data.close();
		}

		Log.d(LOG_TAG, "Sync status: " + SyncStatusUtils.SYNC_STATUS_OK);

        // we're done!!
		SyncStatusUtils.setSyncStatus(ctx, getSyncStatusKeyName(), SyncStatusUtils.SYNC_STATUS_OK);

    }

}