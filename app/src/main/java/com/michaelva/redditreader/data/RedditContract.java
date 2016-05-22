package com.michaelva.redditreader.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.net.Uri;
import android.provider.BaseColumns;

public class RedditContract {

	public static final String CONTENT_AUTHORITY = "com.michaelva.redditreader";

	public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

	public static final String PATH_SUBREDDIT = "subreddit";
	public static final String PATH_SUBMISSION = "submission";
	public static final String PATH_COMMENT = "comment";
	public static final String PATH_COUNT = "count";

	// special projection argument for count queries
	public static final String[] COUNT_PROJECTION = new String[]{"count(*) count"};

	// condition to query/update record by ID
	public static final String BY_ID_CONDITION = BaseColumns._ID + " = ?";

	public static Uri buildUri(String tableName, long id) {
		if (SubredditEntry.TABLE_NAME.equals(tableName)) {
			return SubredditEntry.buildSubredditUri(id);
		} else if (SubmissionEntry.TABLE_NAME.equals(tableName)) {
			return SubmissionEntry.buildSubmissionUri(id);
		} else if (CommentEntry.TABLE_NAME.equals(tableName)) {
			return CommentEntry.buildCommentUri(id);
		} else {
			throw new IllegalArgumentException("Invalid tableName " + tableName);
		}
	}

	public static final class SubredditEntry implements BaseColumns {
		public static final String TABLE_NAME = PATH_SUBREDDIT;

		public static final String COLUMN_NAME = "name";
		public static final String COLUMN_DESCRIPTION = "description";
		public static final String COLUMN_SELECTED = "selected";
		public static final String COLUMN_PENDING_STATE_CHANGE = "pending_state_change";

		public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(TABLE_NAME).build();

		public static final String CONTENT_DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + TABLE_NAME;
		public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + TABLE_NAME;

		public static final String BY_SELECTED_CONDITION = COLUMN_SELECTED + " = ?";
		public static final String BY_PENDING_STATE_CHANGE_CONDITION = COLUMN_PENDING_STATE_CHANGE + " = ?";

		public static Uri buildSubredditUri(long id) {
			return ContentUris.withAppendedId(CONTENT_URI, id);
		}

		public static Uri buildSubredditCountUri() {
			return CONTENT_URI.buildUpon().appendPath(PATH_COUNT).build();
		}

		public static long getSubredditId(Uri uri) {
			return ContentUris.parseId(uri);
		}
	}

	public static final class SubmissionEntry implements BaseColumns {
		public static final String TABLE_NAME = PATH_SUBMISSION;

		public static final String COLUMN_SUBREDDIT_ID = "subreddit_id";
		public static final String COLUMN_TYPE = "type";
		public static final String COLUMN_TITLE = "title";
		public static final String COLUMN_AUTHOR = "author";
		public static final String COLUMN_URL = "url";
		public static final String COLUMN_TEXT = "text";
		public static final String COLUMN_PREVIEW_IMAGE = "preview_image";
		public static final String COLUMN_READ_ONLY = "read_only";

		public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(TABLE_NAME).build();

		public static final String CONTENT_DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + TABLE_NAME;
		public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + TABLE_NAME;

		public static final String BY_SUBREDDIT_ID_CONDITION = COLUMN_SUBREDDIT_ID + " = ?";

		public static Uri buildSubmissionUri(long id) {
			return ContentUris.withAppendedId(CONTENT_URI, id);
		}

		public static Uri buildSubmissionCountUri() {
			return CONTENT_URI.buildUpon().appendPath(PATH_COUNT).build();
		}

		public static long getSubmissionId(Uri uri) {
			return ContentUris.parseId(uri);
		}
	}


	public static final class CommentEntry implements BaseColumns {
		public static final String TABLE_NAME = PATH_COMMENT;

		public static final String PATH_UPDATE_DISPLAY_ORDER = "update_display_order";

		public static final String COLUMN_DISPLAY_ORDER = "display_order";
		public static final String COLUMN_TYPE = "type";
		public static final String COLUMN_DEPTH = "depth";
		public static final String COLUMN_VISIBLE = "visible";
		public static final String COLUMN_SCORE = "score";
		public static final String COLUMN_AUTHOR = "author";
		public static final String COLUMN_VOTE = "vote";
		public static final String COLUMN_TEXT = "text";
		public static final String COLUMN_READ_ONLY = "read_only";
		public static final String PARENT_ID = "parent_id";
		public static final String COLUMN_LOADING = "loading";

		public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(TABLE_NAME).build();

		public static final String CONTENT_DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + TABLE_NAME;
		public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + TABLE_NAME;

		public static final String BY_TYPE_CONDITION = COLUMN_TYPE + "=?";
		public static final String VISIBLE_COMMENTS_CONDITION = COLUMN_VISIBLE + "=1";
		public static final String DISPLAY_ORDER_ABOVE_CONDITION = COLUMN_DISPLAY_ORDER + ">?";
		public static final String DISPLAY_ORDER_BETWEEN_CONDITION = COLUMN_DISPLAY_ORDER + ">? and " + COLUMN_DISPLAY_ORDER + "<?";

		public static Uri buildCommentUri(long id) {
			return ContentUris.withAppendedId(CONTENT_URI, id);
		}

		public static Uri buildUpdateDisplayOrderUri(int increment) {
			return CONTENT_URI.buildUpon().appendPath(PATH_UPDATE_DISPLAY_ORDER).appendPath(String.valueOf(increment)).build();
		}
	}
}
