package com.michaelva.redditreader.loader;

import android.content.Context;
import android.support.v4.content.CursorLoader;

import static com.michaelva.redditreader.data.RedditContract.SubredditEntry;

public class ChangedSubredditsLoader extends SubredditsLoader {

	public ChangedSubredditsLoader(Context ctx) {
		super(ctx);

		setSelection(SubredditEntry.BY_PENDING_STATE_CHANGE_CONDITION);

		setSelectionArgs(new String[] {"1"});
	}
}
