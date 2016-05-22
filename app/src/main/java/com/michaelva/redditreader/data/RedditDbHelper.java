package com.michaelva.redditreader.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static com.michaelva.redditreader.data.RedditContract.*;

public class RedditDbHelper extends SQLiteOpenHelper {

	// must be incremented every time the DB schema changes
	private static final int DATABASE_VERSION = 1;

	static final String DATABASE_NAME = "app.db";

	private static final String SQL_CREATE_SUBREDDIT_TABLE =
			"CREATE TABLE " + SubredditEntry.TABLE_NAME + " (" +
					SubredditEntry._ID + " INTEGER PRIMARY KEY," +
					SubredditEntry.COLUMN_NAME + " TEXT NOT NULL," +
					SubredditEntry.COLUMN_DESCRIPTION + " TEXT," +
					SubredditEntry.COLUMN_SELECTED + " INTEGER NOT NULL DEFAULT 0," +
					SubredditEntry.COLUMN_PENDING_STATE_CHANGE + " INTEGER NOT NULL DEFAULT 0" +
					");";

	private static final String SQL_DROP_SUBREDDIT_TABLE = "DROP TABLE IF EXISTS " + SubredditEntry.TABLE_NAME + ";";


	private static final String SQL_CREATE_SUBMISSION_TABLE =
			"CREATE TABLE " + SubmissionEntry.TABLE_NAME + " (" +
					SubmissionEntry._ID + " INTEGER PRIMARY KEY," +
					SubmissionEntry.COLUMN_SUBREDDIT_ID + " TEXT NOT NULL," +
					SubmissionEntry.COLUMN_TYPE + " INTEGER NOT NULL," +
					SubmissionEntry.COLUMN_TITLE + " TEXT NOT NULL," +
					SubmissionEntry.COLUMN_AUTHOR + " TEXT NOT NULL," +
					SubmissionEntry.COLUMN_URL + " TEXT NOT NULL," +
					SubmissionEntry.COLUMN_TEXT + " TEXT," +
					SubmissionEntry.COLUMN_PREVIEW_IMAGE + " TEXT," +
					SubmissionEntry.COLUMN_READ_ONLY + " INTEGER" +
					");";

	private static final String SQL_DROP_SUBMISSION_TABLE = "DROP TABLE IF EXISTS " + SubmissionEntry.TABLE_NAME + ";";


	private static final String SQL_CREATE_COMMENT_TABLE =
			"CREATE TABLE " + CommentEntry.TABLE_NAME + " (" +
					CommentEntry._ID + " INTEGER PRIMARY KEY," +
					CommentEntry.COLUMN_DISPLAY_ORDER + " INTEGER NOT NULL," +
					CommentEntry.COLUMN_TYPE + " INTEGER NOT NULL," +
					CommentEntry.COLUMN_DEPTH + " INTEGER NOT NULL," +
					CommentEntry.COLUMN_VISIBLE + " INTEGER NOT NULL," +
					CommentEntry.COLUMN_AUTHOR + " TEXT," +
					CommentEntry.COLUMN_SCORE + " INTEGER," +
					CommentEntry.COLUMN_VOTE + " INTEGER," +
					CommentEntry.COLUMN_TEXT + " TEXT NOT NULL," +
					CommentEntry.COLUMN_READ_ONLY + " INTEGER," +
					CommentEntry.PARENT_ID + " INTEGER," +
					CommentEntry.COLUMN_LOADING + " INTEGER" +
					");";

	private static final String SQL_CREATE_COMMENT_DISPLAY_ORDER_INDEX =
			"CREATE INDEX " + CommentEntry.TABLE_NAME + "_" + CommentEntry.COLUMN_DISPLAY_ORDER + "_idx ON " +
					CommentEntry.TABLE_NAME + "(" + CommentEntry.COLUMN_DISPLAY_ORDER + ");";


	private static final String SQL_DROP_COMMENT_TABLE = "DROP TABLE IF EXISTS " + CommentEntry.TABLE_NAME + ";";

	private static final String SQL_DROP_COMMENT_DISPLAY_ORDER_INDEX = "DROP INDEX IF EXISTS " + CommentEntry.TABLE_NAME + "_" + CommentEntry.COLUMN_DISPLAY_ORDER + "_idx";


	public RedditDbHelper(Context ctx) {
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_SUBREDDIT_TABLE);
		db.execSQL(SQL_CREATE_SUBMISSION_TABLE);
		db.execSQL(SQL_CREATE_COMMENT_TABLE);
		db.execSQL(SQL_CREATE_COMMENT_DISPLAY_ORDER_INDEX);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		//db.execSQL(SQL_DROP_SUBREDDIT_TABLE);
		//db.execSQL(SQL_CREATE_SUBREDDIT_TABLE);

		//db.execSQL(SQL_DROP_SUBMISSION_TABLE);
		//db.execSQL(SQL_CREATE_SUBMISSION_TABLE);

		db.execSQL(SQL_DROP_COMMENT_TABLE);
		db.execSQL(SQL_CREATE_COMMENT_TABLE);

		db.execSQL(SQL_DROP_COMMENT_DISPLAY_ORDER_INDEX);
		db.execSQL(SQL_CREATE_COMMENT_DISPLAY_ORDER_INDEX);

	}

}
