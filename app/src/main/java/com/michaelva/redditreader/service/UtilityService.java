package com.michaelva.redditreader.service;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.fasterxml.jackson.databind.node.NullNode;
import com.google.android.gms.analytics.Tracker;
import com.google.common.base.Predicate;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.data.RedditContract;
import com.michaelva.redditreader.task.GetCommentsTask;
import static com.michaelva.redditreader.util.Consts.CommentType;

import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.auth.AuthenticationState;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.SubmissionRequest;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.MoreChildren;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thing;
import net.dean.jraw.models.VoteDirection;
import net.dean.jraw.models.attr.Votable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static com.michaelva.redditreader.data.RedditContract.CommentEntry;
import static com.michaelva.redditreader.data.RedditContract.SubredditEntry;
import static com.michaelva.redditreader.data.RedditContract.SubmissionEntry;

public class UtilityService extends IntentService {
	private final String LOG_TAG = getClass().getCanonicalName();

	private static final String ACTION_UPDATE_SUBREDDIT_SELECTION_STATUS = UtilityService.class.getCanonicalName() + ".action.UPDATE_SUBREDDIT_SELECTION_STATUS";
	private static final String EXTRA_SELECTION_STATUS = UtilityService.class.getCanonicalName() + ".extra.SELECTION_STATUS";

	private static final String ACTION_REMOVE_USER_DATA = UtilityService.class.getCanonicalName() + ".action.REMOVE_USER_DATA";

	public static final String ACTION_LOAD_MORE_COMMENTS = UtilityService.class.getCanonicalName() + ".action.LOAD_MORE_COMMENTS";
	private static final String EXTRA_SUBMISSION_ID = UtilityService.class.getCanonicalName() + ".extra.SUBMISSION_ID";
	private static final String EXTRA_COMMENT_ID = UtilityService.class.getCanonicalName() + ".extra.COMMENT_ID";

	public static final String ACTION_EXPAND_COLLAPSE_COMMENT = UtilityService.class.getCanonicalName() + ".action.EXPAND_COLLAPSE_COMMENT";

	public static final String ACTION_SUBMIT_COMMENT_VOTE = UtilityService.class.getCanonicalName() + ".action.SUBMIT_COMMENT_VOTE";
	private static final String EXTRA_VOTE = UtilityService.class.getCanonicalName() + ".extra.VOTE";

	public static final String ACTION_SUBMIT_REPLY = UtilityService.class.getCanonicalName() + ".action.SUBMIT_REPLY";
	private static final String EXTRA_REDDIT_ID = UtilityService.class.getCanonicalName() + ".extra.REDDIT_ID";
	private static final String EXTRA_REPLY_TEXT = UtilityService.class.getCanonicalName() + ".extra.REPLY_TEXT";
	private static final String EXTRA_SUBMISSION = UtilityService.class.getCanonicalName() + ".extra.SUBMISSION";
	public static final String EXTRA_RESULT_VOTE = UtilityService.class.getCanonicalName() + ".extra.RESULT_VOTE";


	public static final String EXTRA_RESULT_STATUS_SUCCESS = UtilityService.class.getCanonicalName() + ".extra.RESULT_STATUS_SUCCESS";
	public static final String EXTRA_RESULT_MESSAGE = UtilityService.class.getCanonicalName() + ".extra.RESULT_MESSAGE";

	public static void startActionUpdateSubredditSelectionStatus(Context context, Uri subredditUri, boolean selected) {
		Intent intent = new Intent(context, UtilityService.class);
		intent.setAction(ACTION_UPDATE_SUBREDDIT_SELECTION_STATUS);
		intent.setData(subredditUri);
		intent.putExtra(EXTRA_SELECTION_STATUS, selected);
		context.startService(intent);
	}

	public static void startActionRemoveUserData(Context context) {
		Intent intent = new Intent(context, UtilityService.class);
		intent.setAction(ACTION_REMOVE_USER_DATA);
		context.startService(intent);
	}

	public static void startActionLoadMoreComments(Context context, long submissionId, long commentId) {
		Intent intent = new Intent(context, UtilityService.class);
		intent.setAction(ACTION_LOAD_MORE_COMMENTS);
		intent.putExtra(EXTRA_SUBMISSION_ID, submissionId);
		intent.putExtra(EXTRA_COMMENT_ID, commentId);
		context.startService(intent);
	}

	public static void startActionExpandCollapseComment(Context context, long commentId) {
		Intent intent = new Intent(context, UtilityService.class);
		intent.setAction(ACTION_EXPAND_COLLAPSE_COMMENT);
		intent.putExtra(EXTRA_COMMENT_ID, commentId);
		context.startService(intent);
	}

	public static void startActionSubmitCommentVote(Context context, long submissionId, long commentId, int vote) {
		Intent intent = new Intent(context, UtilityService.class);
		intent.setAction(ACTION_SUBMIT_COMMENT_VOTE);
		intent.putExtra(EXTRA_COMMENT_ID, commentId);
		intent.putExtra(EXTRA_SUBMISSION_ID, submissionId);
		intent.putExtra(EXTRA_VOTE, vote);
		context.startService(intent);
	}

	public static void startActionSubmitReply(Context context, long redditId, String replyText, boolean submission) {
		Intent intent = new Intent(context, UtilityService.class);
		intent.setAction(ACTION_SUBMIT_REPLY);
		intent.putExtra(EXTRA_REDDIT_ID, redditId);
		intent.putExtra(EXTRA_REPLY_TEXT, replyText);
		intent.putExtra(EXTRA_SUBMISSION, submission);
		context.startService(intent);
	}

	public UtilityService() {
		super(UtilityService.class.getSimpleName());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			final String action = intent.getAction();
			if (ACTION_UPDATE_SUBREDDIT_SELECTION_STATUS.equals(action)) {
				final Uri subredditUri = intent.getData();
				final boolean selected = intent.getBooleanExtra(EXTRA_SELECTION_STATUS, false);
				handleActionUpdateSubreddit(subredditUri, selected);
			} else if (ACTION_REMOVE_USER_DATA.equals(action)) {
				handleActionRemoveUserData();
			} else if (ACTION_LOAD_MORE_COMMENTS.equals(action)) {
				final long submissionId = intent.getLongExtra(EXTRA_SUBMISSION_ID, 0);
				final long commentId = intent.getLongExtra(EXTRA_COMMENT_ID, 0);
				handleActionLoadMoreComments(submissionId, commentId);
			} else if (ACTION_EXPAND_COLLAPSE_COMMENT.equals(action)) {
				final long commentId = intent.getLongExtra(EXTRA_COMMENT_ID, 0);
				handleActionExpandCollapseComment(commentId);
			} else if (ACTION_SUBMIT_COMMENT_VOTE.equals(action)) {
				final long commentId = intent.getLongExtra(EXTRA_COMMENT_ID, 0);
				final long submissionId = intent.getLongExtra(EXTRA_SUBMISSION_ID, 0);
				final int vote = intent.getIntExtra(EXTRA_VOTE, 0);
				handleActionSubmitCommentVote(submissionId, commentId, vote);
			} else if (ACTION_SUBMIT_REPLY.equals(action)) {
				final long redditId = intent.getLongExtra(EXTRA_REDDIT_ID, 0);
				final String replyText = intent.getStringExtra(EXTRA_REPLY_TEXT);
				final boolean submission = intent.getBooleanExtra(EXTRA_SUBMISSION, false);
				handleActionSubmitReply(redditId, replyText, submission);
			}
		}
	}

	private void sendResultBroadcast(Intent result, boolean success, String error) {
		result.putExtra(EXTRA_RESULT_STATUS_SUCCESS, success);
		result.putExtra(EXTRA_RESULT_MESSAGE, error);

		LocalBroadcastManager.getInstance(this).sendBroadcast(result);
	}

	private void checkAccessAndToken() throws Exception {
		// stop if user did not authorize access
		if (!Utils.isUserAuthenticated(this)) {
			Log.d(LOG_TAG, "User is not authenticated. Stopping...");
			throw new Exception(getString(R.string.message_not_authorized));
		}

		// stop if network is not available
		if (!Utils.isNetworkAvailable(this)) {
			Log.d(LOG_TAG, "Network is not available. Stopping...");
			throw new Exception(getString(R.string.message_no_internet));
		}

		AuthenticationManager authMngr = AuthenticationManager.get();

		// refresh access token if needed
		if (authMngr.checkAuthState() == AuthenticationState.NEED_REFRESH) {
			try {
				Log.d(LOG_TAG, "Refreshing access token");
				authMngr.refreshAccessToken(Consts.APP_CREDENTIALS);
			} catch (Exception e) {
				Log.e(LOG_TAG, "Failed to refresh access token!!!!", e);
				throw new Exception(getString(R.string.message_server_invalid));
			}
		}
	}

	private String getError(Exception e) {
		Log.e(LOG_TAG, "Caught error while communicating with Reddit: ", e);
		String error = e.getMessage();
		String message;
		if (error.contains(Consts.NOT_AUTHORIZED)) {
			message = getString(R.string.message_not_authorized);
		} else if (error.contains(Consts.RATELIMIT)) {
			try {
				String tryAgain = "try again in ";
				int start = error.indexOf(tryAgain) + tryAgain.length();
				int end = error.indexOf(".\"", start);
				message = getString(R.string.message_ratelimit_error, error.substring(start, end));
			} catch (Exception ee) {
				message = getString(R.string.message_internal_error);
			}
		} else {
			message = getString(R.string.message_internal_error);
		}
		return message;
	}

	private void handleActionSubmitReply(long redditId, String replyText, boolean submission) {
		String rdId = Long.toString(redditId, 36); // converting long to Reddit ID

		Intent result = new Intent(ACTION_SUBMIT_REPLY);

		Log.d(LOG_TAG, "Submitting reply on " + (submission ? "submission " : "comment ") + redditId + " (" + rdId + ")");

		try {
			checkAccessAndToken();
		} catch (Exception e) {
			sendResultBroadcast(result, false, e.getMessage());
			return;
		}

		RedditClient reddit = AuthenticationManager.get().getRedditClient();

		// submit reply to the server
		try {
			new AccountManager(reddit).reply(new Contribution(rdId, submission), replyText);
		} catch (Exception e) {
			sendResultBroadcast(result, false, getError(e));
			return;
		}
		sendResultBroadcast(result, true, null);
	}

	private void handleActionLoadMoreComments(long submissionId, long commentId) {
		String rdSubmissionId = Long.toString(submissionId, 36);
		String rdCommentId = Long.toString(commentId, 36);

		Intent result = new Intent(ACTION_LOAD_MORE_COMMENTS);

		Log.d(LOG_TAG, "Loading more comments for " + commentId + " (" + rdCommentId + ")");

		ContentResolver cr = getContentResolver();

		Cursor data = cr.query(
				CommentEntry.CONTENT_URI,
				new String[] {CommentEntry.COLUMN_DISPLAY_ORDER, CommentEntry.PARENT_ID},
				RedditContract.BY_ID_CONDITION + " and " + CommentEntry.BY_TYPE_CONDITION,
				new String[] {String.valueOf(commentId), String.valueOf(Consts.CommentType.MORE_COMMENTS)},
				null);

		if (data == null) {
			Log.d(LOG_TAG, "Can't load comment from DB????");
			sendResultBroadcast(result, false, getString(R.string.message_internal_error));
			return;
		}

		if (!data.moveToNext()) {
			Log.d(LOG_TAG, "Comment " + commentId + " (" + rdCommentId + ") is no longer in the DB or already processed. Stopping...");
			sendResultBroadcast(result, true, null);
			return;
		}

		// update comment's state to 'loading'
		setCommentLoading(commentId, true);

		int displayOrder = data.getInt(0);
		String rdParentCommentId = Long.toString(data.getLong(1), 36);

		data.close();

		try {
			checkAccessAndToken();
		} catch (Exception e) {
			setCommentLoading(commentId, false);
			sendResultBroadcast(result, false, e.getMessage());
			return;
		}

		List<CommentNode> allComments;

		RedditClient reddit = AuthenticationManager.get().getRedditClient();

		// retrieve submission from Reddit, and find the parent comment
		CommentNode root;

		try {
			root = reddit.getSubmission(rdSubmissionId).getComments();
		} catch (Exception e) {
			setCommentLoading(commentId, false);
			sendResultBroadcast(result, false, getError(e));
			return;
		}

		CommentNode parent = root.findChild(Consts.COMMENT_KIND + rdParentCommentId).orNull();

		if (parent == null || !parent.hasMoreComments()) {
			Log.w(LOG_TAG, "Parent comment " + rdParentCommentId + " could not be found. Stopping...");
			setCommentLoading(commentId, false);
			sendResultBroadcast(result, false, getString(R.string.message_local_comments_out_of_sync));
			return;
		}

		try {
			// retrieve 'more comments' from Reddit into parent's node and 'unwrap' the parent node into list of nodes
			parent.loadMoreComments(reddit);
		} catch (Exception e) {
			Log.e(LOG_TAG, "Caught error while loading data from Reddit: ", e);
			setCommentLoading(commentId, false);
			sendResultBroadcast(result, false, getError(e));
			return;
		}

		allComments = parent.walkTree().toList();

		// keep only the 'target' comment and everything after it
		List<CommentNode> filteredList = new ArrayList<>();
		boolean targetCommentFound = false;
		for (CommentNode comment : allComments) {

			if (targetCommentFound) {
				filteredList.add(comment);
			} else if (comment.getComment().getId().equals(rdCommentId)) {
				targetCommentFound = true;
				filteredList.add(comment);
			}
		}

		ArrayList<ContentProviderOperation> ops = new ArrayList<>();

		// first, we need to delete the 'more comments' record as it will be replaced with a 'real' comment record
		ContentProviderOperation operation = ContentProviderOperation.newDelete(CommentEntry.buildCommentUri(commentId)).build();
		ops.add(operation);

		if (filteredList.size() > 1) {
			// need to increment display order to accommodate the insertion of the new records
			operation = ContentProviderOperation
					.newUpdate(CommentEntry.buildUpdateDisplayOrderUri(filteredList.size() - 1))
					.withSelection(CommentEntry.DISPLAY_ORDER_ABOVE_CONDITION, new String[] {String.valueOf(displayOrder)})
					.withValue(CommentEntry.COLUMN_TEXT, "") // just to avoid exception from build(); it will not actually be updated
					.build();
			ops.add(operation);
		}

		// go over list of retrieved comments and prepare insert operations
		List<ContentValues> contentValues = GetCommentsTask.processComments(displayOrder, filteredList);
		for (ContentValues values : contentValues) {
			operation = ContentProviderOperation.newInsert(CommentEntry.CONTENT_URI).withValues(values).build();
			ops.add(operation);
		}

		// execute operations
		try {
			cr.applyBatch(RedditContract.CONTENT_AUTHORITY, ops);
		} catch (OperationApplicationException | RemoteException e) {
			// should not happen as we're not accessing anything remote
			Log.e(LOG_TAG, "Can't update DB?", e);
		}

		sendResultBroadcast(result, true, null);
	}

	private void handleActionExpandCollapseComment(long commentId) {
		Log.d(LOG_TAG, "Expanding/collapsing comment " + commentId);

		Intent result = new Intent(ACTION_EXPAND_COLLAPSE_COMMENT);

		ContentResolver cr = getContentResolver();

		// load comment attributes

		Cursor data = cr.query(
				CommentEntry.buildCommentUri(commentId),
				new String[] {CommentEntry.COLUMN_TYPE, CommentEntry.COLUMN_DEPTH, CommentEntry.COLUMN_DISPLAY_ORDER},
				null,
				null,
				null);
		if (data == null) {
			Log.d(LOG_TAG, "Can't load comment from DB????");
			sendResultBroadcast(result, false, getString(R.string.message_internal_error));
			return;
		}

		data.moveToFirst();
		int type = data.getInt(0);
		int depth = data.getInt(1);
		int displayOrder = data.getInt(2);

		data.close();

		if (type == CommentType.MORE_COMMENTS) {
			Log.d(LOG_TAG, "Comment is a 'more comments' node???");
			sendResultBroadcast(result, false, getString(R.string.message_internal_error));
		} else if (type == CommentType.NO_REPLIES_EXPANDED || type == CommentType.NO_REPLIES_COLLAPSED) {

			// simple comment without any replies, just update from expanded to collapsed (or vice versa)

			ContentValues values = new ContentValues();
			values.put(CommentEntry.COLUMN_TYPE, type == CommentType.NO_REPLIES_EXPANDED ? CommentType.NO_REPLIES_COLLAPSED : CommentType.NO_REPLIES_EXPANDED);

			cr.update(CommentEntry.buildCommentUri(commentId), values, null, null);

		} else {

			ArrayList<ContentProviderOperation> ops = new ArrayList<>();

			// update the comment's state: expanded/collapsed
			ContentValues values = new ContentValues();
			values.put(CommentEntry.COLUMN_TYPE, type == CommentType.WITH_REPLIES_EXPANDED ? CommentType.WITH_REPLIES_COLLAPSED : CommentType.WITH_REPLIES_EXPANDED);

			ContentProviderOperation operation = ContentProviderOperation
					.newUpdate(CommentEntry.buildCommentUri(commentId))
					.withValues(values)
					.build();

			ops.add(operation);

			//go over subsequent comments until we find comment with same or lower depth
			//all those comments will be hidden/shown
			int stopDisplayOrder = displayOrder;

			data = cr.query(
					CommentEntry.CONTENT_URI,
					new String[]{CommentEntry.COLUMN_DISPLAY_ORDER, CommentEntry.COLUMN_DEPTH},
					CommentEntry.DISPLAY_ORDER_ABOVE_CONDITION,
					new String[] {String.valueOf(displayOrder)},
					null);
			if (data == null) {
				Log.d(LOG_TAG, "Can't load comments from DB???");
				sendResultBroadcast(result, false, getString(R.string.message_internal_error));
				return;
			}

			boolean stop = false; // we stop when we get to the comment with same or lower depth
			while (!stop && data.moveToNext()) {
				stopDisplayOrder = data.getInt(0);
				if (data.getInt(1) <= depth) {
					stop = true;
				}
			}
			data.close();

			// now we need to hide/show all comments between this comment's displayOrder and stopDisplayOrder

			values = new ContentValues();
			values.put(CommentEntry.COLUMN_VISIBLE, type == CommentType.WITH_REPLIES_EXPANDED ? 0 : 1);

			operation = ContentProviderOperation
					.newUpdate(CommentEntry.CONTENT_URI)
					.withValues(values)
					.withSelection(
							CommentEntry.DISPLAY_ORDER_BETWEEN_CONDITION,
							new String[] {String.valueOf(displayOrder), String.valueOf(stopDisplayOrder)})
					.build();

			ops.add(operation);

			// execute operations
			try {
				cr.applyBatch(RedditContract.CONTENT_AUTHORITY, ops);
			} catch (OperationApplicationException | RemoteException e) {
				// should not happen as we're not accessing anything remote
				Log.e(LOG_TAG, "Can't update DB?", e);
			}

			sendResultBroadcast(result, true, null);
		}
	}

	private void handleActionSubmitCommentVote(long submissionId, long commentId, int vote) {
		final String rdCommentId = Long.toString(commentId, 36); // converting long to Reddit ID

		Log.d(LOG_TAG, "Submitting vote " + vote + " for comment " + commentId + "(" + rdCommentId + ")");

		Intent result = new Intent(ACTION_SUBMIT_COMMENT_VOTE);

		VoteDirection voteDirection;

		if (vote > 0) {
			voteDirection = VoteDirection.UPVOTE;
		} else if (vote < 0) {
			voteDirection = VoteDirection.DOWNVOTE;
		} else {
			voteDirection = VoteDirection.NO_VOTE;
		}

		try {
			checkAccessAndToken();
		} catch (Exception e) {
			sendResultBroadcast(result, false, e.getMessage());
			return;
		}

		RedditClient reddit = AuthenticationManager.get().getRedditClient();

		// submit vote to the server
		try {
			new AccountManager(reddit).vote(new Comment(rdCommentId), voteDirection);
		} catch (Exception e) {
			sendResultBroadcast(result, false, getError(e));
			return;
		}

		// reload comment from server to get updated score
		CommentNode comment;

		try {
			String reSubmissionId = Long.toString(submissionId, 36); // converting long to Reddit ID

			SubmissionRequest request = new SubmissionRequest.Builder(reSubmissionId).focus(rdCommentId).build();

			// find the comment
			comment = reddit.getSubmission(request).getComments().walkTree()
					.filter(new Predicate<CommentNode>() {
						@Override
						public boolean apply(CommentNode input) {
							return input.getComment().getId().equals(rdCommentId);
						}
					}).first().get();
		} catch (Exception e) {
			sendResultBroadcast(result, false, getError(e));
			return;
		}

		// update the comment's record in the DB

		ContentValues values = new ContentValues();
		values.put(RedditContract.CommentEntry.COLUMN_VOTE, comment.getComment().getVote().getValue());
		values.put(RedditContract.CommentEntry.COLUMN_SCORE, comment.getComment().getScore());

		Uri commentUri = RedditContract.CommentEntry.buildCommentUri(commentId);

		getContentResolver().update(commentUri, values, null, null);

		result.putExtra(EXTRA_RESULT_VOTE, vote);

		sendResultBroadcast(result, true, null);
	}

	private void setCommentLoading(long commentId, boolean loading) {
		ContentValues values = new ContentValues();
		values.put(CommentEntry.COLUMN_LOADING, loading ? "1" : "");
		getContentResolver().update(CommentEntry.buildCommentUri(commentId), values, null, null);
	}

	private void handleActionRemoveUserData() {
		Log.d(LOG_TAG, "Removing User's Data");
		ContentResolver cr = getContentResolver();
		cr.delete(SubredditEntry.CONTENT_URI, null, null);
		cr.delete(SubmissionEntry.CONTENT_URI, null, null);
	}

	private void handleActionUpdateSubreddit(Uri subredditUri, boolean selected) {
		Log.d(LOG_TAG, "Changing subreddit's selection state");

		ContentResolver cr = getContentResolver();

		Cursor data = cr.query(subredditUri, new String[] {SubredditEntry.COLUMN_SELECTED, SubredditEntry.COLUMN_PENDING_STATE_CHANGE}, null, null, null);

		if (data == null) {
			return;
		}

		data.moveToFirst();

		if (data.isAfterLast()) {
			return;
		}

		boolean currentlySelected = data.getInt(0) > 0;
		boolean pendingStateChange = data.getInt(1) > 0;

		data.close();

		boolean newPendingStateChange = selected != currentlySelected;

		// update pending state change if needed
		if (newPendingStateChange != pendingStateChange) {
			ContentValues values = new ContentValues();
			values.put(SubredditEntry.COLUMN_PENDING_STATE_CHANGE, newPendingStateChange ? 1 : 0);
			cr.update(subredditUri, values, null, null);
		}


		// check that at least one subreddit will be selected after all pending state changes are applied
		boolean atLeastOneSubredditSelected = false;

		data = cr.query(SubredditEntry.CONTENT_URI, new String[] {SubredditEntry.COLUMN_SELECTED, SubredditEntry.COLUMN_PENDING_STATE_CHANGE}, null, null, null);

		if (data != null) {
			while (data.moveToNext()) {
				currentlySelected = data.getInt(0) > 0;
				pendingStateChange = data.getInt(1) > 0;

				if ((currentlySelected && !pendingStateChange) || (!currentlySelected && pendingStateChange)) {
					atLeastOneSubredditSelected = true;
					break;
				}
			}
			data.close();
		}

		Utils.setAtLeastOneSubredditSelected(this, atLeastOneSubredditSelected);

	}

	class Comment extends Thing implements Votable {
		final String mId;

		public Comment(String id) {
			super(NullNode.getInstance());
			mId = Consts.COMMENT_KIND + id;
		}

		@Override
		public Integer getScore() {
			return null;
		}

		@Override
		public VoteDirection getVote() {
			return null;
		}

		@Override
		public String getFullName() {
			return mId;
		}
	}

	class Contribution extends net.dean.jraw.models.Contribution {
		final String mId;

		public Contribution(String id, boolean submission) {
			super(NullNode.getInstance());
			mId = (submission ? Consts.SUBMISSION_KIND : Consts.COMMENT_KIND) + id;
		}

		@Override
		public String getFullName() {
			return mId;
		}
	}
}
