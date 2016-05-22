package com.michaelva.redditreader.data;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

import static com.michaelva.redditreader.data.RedditContract.*;

public class RedditProvider extends ContentProvider {
    private static final String LOG_TAG = RedditProvider.class.getCanonicalName();

    private static final UriMatcher sUriMatcher;

    private RedditDbHelper mDbHelper;

    private static final int SUBREDDIT_LIST = 100;
    private static final int SUBREDDIT_COUNT = 110;
    private static final int SUBREDDIT = 115;
    private static final int SUBMISSION_LIST = 120;
    private static final int SUBMISSION_COUNT = 130;
    private static final int SUBMISSION = 140;
	private static final int COMMENT_LIST = 150;
	private static final int COMMENT_COUNT = 160;
	private static final int COMMENT = 170;
	private static final int COMMENT_DISPLAY_ORDER_UPDATE = 180;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_SUBREDDIT, SUBREDDIT_LIST);
        sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_SUBREDDIT + "/" + RedditContract.PATH_COUNT, SUBREDDIT_COUNT);
        sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_SUBREDDIT + "/#", SUBREDDIT);

        sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_SUBMISSION, SUBMISSION_LIST);
        sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_SUBMISSION + "/" + RedditContract.PATH_COUNT, SUBMISSION_COUNT);
        sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_SUBMISSION + "/#", SUBMISSION);

		sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_COMMENT, COMMENT_LIST);
		sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_COMMENT + "/" + RedditContract.PATH_COUNT, COMMENT_COUNT);
		sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_COMMENT + "/#", COMMENT);
		sUriMatcher.addURI(RedditContract.CONTENT_AUTHORITY, RedditContract.PATH_COMMENT + "/" + CommentEntry.PATH_UPDATE_DISPLAY_ORDER + "/#", COMMENT_DISPLAY_ORDER_UPDATE);
    }

    @Override
    public boolean onCreate() {
        mDbHelper = new RedditDbHelper(getContext());
        return false;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {

            case SUBREDDIT_LIST:
                return SubredditEntry.CONTENT_DIR_TYPE;
            case SUBREDDIT_COUNT:
            case SUBREDDIT:
                return SubredditEntry.CONTENT_ITEM_TYPE;

            case SUBMISSION_LIST:
                return SubmissionEntry.CONTENT_DIR_TYPE;
            case SUBMISSION_COUNT:
            case SUBMISSION:
                return SubmissionEntry.CONTENT_ITEM_TYPE;

			case COMMENT_LIST:
				return CommentEntry.CONTENT_DIR_TYPE;
			case COMMENT_COUNT:
			case COMMENT:
			case COMMENT_DISPLAY_ORDER_UPDATE:
				return CommentEntry.CONTENT_ITEM_TYPE;

			default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Cursor result;
        switch (sUriMatcher.match(uri)) {
            case SUBREDDIT_LIST:
                result = mDbHelper.getReadableDatabase().query(
                    SubredditEntry.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder);
                break;
            case SUBREDDIT_COUNT:
                result = mDbHelper.getReadableDatabase().query(
                    SubredditEntry.TABLE_NAME,
                    RedditContract.COUNT_PROJECTION,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null);
                break;
			case SUBREDDIT:
				String subredditId = uri.getLastPathSegment();
				result = mDbHelper.getReadableDatabase().query(
						SubredditEntry.TABLE_NAME,
						projection,
						RedditContract.BY_ID_CONDITION,
						new String[] {subredditId},
						null,
						null,
						null);
				break;

            case SUBMISSION_LIST:
                result = mDbHelper.getReadableDatabase().query(
                    SubmissionEntry.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder);
                break;
            case SUBMISSION_COUNT:
                result = mDbHelper.getReadableDatabase().query(
                    SubmissionEntry.TABLE_NAME,
                    RedditContract.COUNT_PROJECTION,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null);
                break;
            case SUBMISSION:
                String submissionId = uri.getLastPathSegment();
                result = mDbHelper.getReadableDatabase().query(
                    SubmissionEntry.TABLE_NAME,
                    projection,
					RedditContract.BY_ID_CONDITION,
                    new String[] {submissionId},
                    null,
                    null,
                    null);
                break;

			case COMMENT_LIST:
				result = mDbHelper.getReadableDatabase().query(
						CommentEntry.TABLE_NAME,
						projection,
						selection,
						selectionArgs,
						null,
						null,
						CommentEntry.COLUMN_DISPLAY_ORDER);
				break;
			case COMMENT_COUNT:
				result = mDbHelper.getReadableDatabase().query(
						CommentEntry.TABLE_NAME,
						RedditContract.COUNT_PROJECTION,
						selection,
						selectionArgs,
						null,
						null,
						null);
				break;
			case COMMENT:
				String commentId = uri.getLastPathSegment();
				result = mDbHelper.getReadableDatabase().query(
						CommentEntry.TABLE_NAME,
						projection,
						RedditContract.BY_ID_CONDITION,
						new String[] {commentId},
						null,
						null,
						null);
				break;
            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
        result.setNotificationUri(getContext().getContentResolver(), uri);
        return result;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues value) {
        Uri result;
        long _id;

        String tableName = getTableNameForInsert(uri);

        Log.v(LOG_TAG, "Inserting into " + tableName + " value: [" + value + "]");
        _id = mDbHelper.getWritableDatabase().insert(tableName, null, value);
        if ( _id > 0 ) {
            result = RedditContract.buildUri(tableName, _id);
        } else {
            throw new SQLException("Failed to insert row into " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return result;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int rowsInserted = 0;
        long _id;

        String tableName = getTableNameForInsert(uri);

        db.beginTransaction();
        try {
            for (ContentValues value : values) {
                Log.v(LOG_TAG, "Inserting into " + tableName + " value: [" + value + "]");
                _id = db.insert(tableName, null, value);
                if (_id > 0) {
                    ++rowsInserted;
                } else {
                    throw new SQLException("Failed to insert row into " + uri);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Log.d(LOG_TAG, "Inserted " + rowsInserted + " records into " + tableName);
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsInserted;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        int rowsDeleted;

        Log.v(LOG_TAG, "Deleting from " + uri + " with selection: [" + selection + "] and selectionArgs: " + Arrays.toString(selectionArgs));

		switch (sUriMatcher.match(uri)) {
			case SUBREDDIT_LIST:
				if (selection == null) {
					selection = "1"; //special value to get number of deleted rows when deleting all rows
				}
				rowsDeleted = mDbHelper.getWritableDatabase().delete(SubredditEntry.TABLE_NAME, selection, selectionArgs);
				break;
			case SUBREDDIT:
				String subredditId = uri.getLastPathSegment();
				rowsDeleted = mDbHelper.getWritableDatabase().delete(SubredditEntry.TABLE_NAME, RedditContract.BY_ID_CONDITION, new String[] {subredditId});
				break;

			case SUBMISSION_LIST:
				if (selection == null) {
					selection = "1"; //special value to get number of deleted rows when deleting all rows
				}
				rowsDeleted = mDbHelper.getWritableDatabase().delete(SubmissionEntry.TABLE_NAME, selection, selectionArgs);
				break;
			case SUBMISSION:
				String submissionId = uri.getLastPathSegment();
				rowsDeleted = mDbHelper.getWritableDatabase().delete(SubmissionEntry.TABLE_NAME, RedditContract.BY_ID_CONDITION, new String[] {submissionId});
				break;

			case COMMENT_LIST:
				if (selection == null) {
					selection = "1"; //special value to get number of deleted rows when deleting all rows
				}
				rowsDeleted = mDbHelper.getWritableDatabase().delete(CommentEntry.TABLE_NAME, selection, selectionArgs);
				break;
			case COMMENT:
				String commentId = uri.getLastPathSegment();
				rowsDeleted = mDbHelper.getWritableDatabase().delete(CommentEntry.TABLE_NAME, RedditContract.BY_ID_CONDITION, new String[] {commentId});
				break;
			default:
				throw new IllegalArgumentException("Invalid uri for update: " + uri);
		}

		Log.d(LOG_TAG, "Deleted " + rowsDeleted + " records");

		if (rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsDeleted;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int rowsUpdated = 0;

        Log.v(LOG_TAG, "Updating " + uri + " with values: [" + values + "] selection: [" + selection + "] and selectionArgs: " + Arrays.toString(selectionArgs));

		switch (sUriMatcher.match(uri)) {
			case SUBREDDIT_LIST:
				rowsUpdated = mDbHelper.getWritableDatabase().update(SubredditEntry.TABLE_NAME, values, selection, selectionArgs);
				break;
			case SUBREDDIT:
				String subredditId = uri.getLastPathSegment();
				rowsUpdated = mDbHelper.getWritableDatabase().update(SubredditEntry.TABLE_NAME, values, RedditContract.BY_ID_CONDITION, new String[] {subredditId});
				break;

			case SUBMISSION_LIST:
				rowsUpdated = mDbHelper.getWritableDatabase().update(SubmissionEntry.TABLE_NAME, values, selection, selectionArgs);
				break;
			case SUBMISSION:
				String submissionId = uri.getLastPathSegment();
				rowsUpdated = mDbHelper.getWritableDatabase().update(SubmissionEntry.TABLE_NAME, values, RedditContract.BY_ID_CONDITION, new String[] {submissionId});
				break;

			case COMMENT_LIST:
				rowsUpdated = mDbHelper.getWritableDatabase().update(CommentEntry.TABLE_NAME, values, selection, selectionArgs);
				break;
			case COMMENT:
				String commentId = uri.getLastPathSegment();
				rowsUpdated = mDbHelper.getWritableDatabase().update(CommentEntry.TABLE_NAME, values, RedditContract.BY_ID_CONDITION, new String[] {commentId});
				break;
			case COMMENT_DISPLAY_ORDER_UPDATE:
				String displayOrderIncrement = uri.getLastPathSegment();

				String sql =	"UPDATE " + CommentEntry.TABLE_NAME +
								" SET " +
								CommentEntry.COLUMN_DISPLAY_ORDER + "=" + CommentEntry.COLUMN_DISPLAY_ORDER + "+" + displayOrderIncrement +
								" WHERE " + selection;

				Cursor c = mDbHelper.getWritableDatabase().rawQuery(sql, selectionArgs);
				// without processing and closing the cursor, the table will not be updated!!!!
				c.moveToFirst();
				c.close();
				getContext().getContentResolver().notifyChange(uri, null);
				break;
			default:
				throw new IllegalArgumentException("Invalid uri for update: " + uri);
		}

		Log.d(LOG_TAG, "Updated " + rowsUpdated + " records");

        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsUpdated;
    }

	private String getTableNameForInsert(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case SUBREDDIT_LIST:
                return SubredditEntry.TABLE_NAME;
            case SUBMISSION_LIST:
                return SubmissionEntry.TABLE_NAME;
			case COMMENT_LIST:
				return CommentEntry.TABLE_NAME;
			default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
    }

}
