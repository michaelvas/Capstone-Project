package com.michaelva.redditreader.loader;

import android.content.Context;
import android.support.v4.content.CursorLoader;

import static com.michaelva.redditreader.data.RedditContract.SubredditEntry.*;

public class SubredditsLoader extends CursorLoader {
	// projection columns and indexes
	private static final String[] COLUMNS = {
		_ID,
		COLUMN_NAME,
		COLUMN_DESCRIPTION,
		COLUMN_SELECTED,
		COLUMN_PENDING_STATE_CHANGE
	};
	public static final int COL_ID = 0;
	public static final int COL_NAME = 1;
	public static final int COL_DESCRIPTION = 2;
	public static final int COL_SELECTED = 3;
	public static final int COL_PENDING_STATE_CHANGE = 4;

	public SubredditsLoader(Context ctx) {
		super(ctx);

		setUri(CONTENT_URI);

		setProjection(COLUMNS);

		setSortOrder(COLUMN_NAME + " ASC");
	}
}
