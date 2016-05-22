package com.michaelva.redditreader.loader;

import android.content.Context;
import android.support.v4.content.CursorLoader;

import static com.michaelva.redditreader.data.RedditContract.SubmissionEntry.*;

public abstract class SubmissionLoader extends CursorLoader {
	// projection columns and indexes
	public static final String[] COLUMNS = {
			_ID,
			COLUMN_TYPE,
			COLUMN_TITLE,
			COLUMN_AUTHOR,
			COLUMN_URL,
			COLUMN_TEXT,
			COLUMN_PREVIEW_IMAGE,
			COLUMN_READ_ONLY
	};
	public static final int COL_ID = 0;
	public static final int COL_TYPE = 1;
	public static final int COL_TITLE = 2;
	public static final int COL_AUTHOR = 3;
	public static final int COL_URL = 4;
	public static final int COL_TEXT = 5;
	public static final int COL_PREVIEW_IMAGE = 6;
	public static final int COL_READ_ONLY = 7;

	public SubmissionLoader(Context context) {
		super(context);

		setUri(CONTENT_URI);

		setProjection(COLUMNS);
	}

}
