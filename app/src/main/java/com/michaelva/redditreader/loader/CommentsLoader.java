package com.michaelva.redditreader.loader;

import android.content.Context;
import android.support.v4.content.CursorLoader;

import static com.michaelva.redditreader.data.RedditContract.CommentEntry.*;

public class CommentsLoader extends CursorLoader {
	// projection columns and indexes
	private static final String[] COLUMNS = {
			_ID,
			COLUMN_TYPE,
			COLUMN_DEPTH,
			COLUMN_SCORE,
			COLUMN_AUTHOR,
			COLUMN_VOTE,
			COLUMN_TEXT,
			COLUMN_READ_ONLY,
			COLUMN_LOADING
	};
	public static final int COL_ID = 0;
	public static final int COL_TYPE = 1;
	public static final int COL_DEPTH = 2;
	public static final int COL_SCORE = 3;
	public static final int COL_AUTHOR = 4;
	public static final int COL_VOTE = 5;
	public static final int COL_TEXT = 6;
	public static final int COL_READ_ONLY = 7;
	public static final int COL_LOADING = 8;

	public CommentsLoader(Context ctx) {
		super(ctx);

		setUri(CONTENT_URI);

		setProjection(COLUMNS);

		setSelection(VISIBLE_COMMENTS_CONDITION);
	}
}
