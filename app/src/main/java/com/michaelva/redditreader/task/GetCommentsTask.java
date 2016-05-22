package com.michaelva.redditreader.task;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.util.Log;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.data.RedditContract;
import com.michaelva.redditreader.service.UtilityService;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import static com.michaelva.redditreader.data.RedditContract.CommentEntry;
import static com.michaelva.redditreader.util.Consts.CommentType;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.MoreChildren;
import net.dean.jraw.models.Submission;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class GetCommentsTask extends AbstractTask<GetCommentsTask.Result> {
	private final String LOG_TAG = getClass().getCanonicalName();

	private long mSubmissionId;

	public static class Result extends AbstractTask.TaskResult {
	}

	public GetCommentsTask(Context context, long submissionId) {
		super(context, new Result());

		mSubmissionId = submissionId;

	}

	@Override
	protected void onStart() {
		SyncStatusUtils.resetSyncStatus(getContext(), Consts.COMMENTS_TASK_STATUS_REF);
	}

	@Override
	protected void onFinish(int status) {
		SyncStatusUtils.setSyncStatus(getContext(), Consts.COMMENTS_TASK_STATUS_REF, status);
	}

	@Override
	protected void performTask() throws Exception {
		String rdSubmissionId = Long.toString(mSubmissionId, 36); // converting long to Reddit ID

		Log.d(LOG_TAG, "Loading submission comments for " + mSubmissionId + " (" + rdSubmissionId + ")");

		// first we need to delete existing comments
		getContext().getContentResolver().delete(CommentEntry.CONTENT_URI, null ,null);

		// retrieve submission from Reddit
		Submission submission = AuthenticationManager.get().getRedditClient().getSubmission(rdSubmissionId);

		// 'unwrap' the comment tree into list of nodes

		List<CommentNode> comments = submission.getComments().walkTree().toList();

		// prepare inserts from list of comments
		List<ContentValues> contentValues = GetCommentsTask.processComments(1, comments);

		// check if we have more comments at the root level
		MoreChildren more = submission.getComments().getMoreChildren();

		if (more != null && more.getCount() > 0) {
			ContentValues values = new ContentValues();
			values.put(CommentEntry._ID, Long.parseLong(more.getId(), 36)); // converting Reddit ID to long
			values.put(CommentEntry.COLUMN_DISPLAY_ORDER, contentValues.size() + 1);
			values.put(CommentEntry.COLUMN_TYPE, Consts.CommentType.MORE_COMMENTS);
			values.put(CommentEntry.COLUMN_VISIBLE, 1);
			values.put(CommentEntry.COLUMN_DEPTH, 1);
			values.put(CommentEntry.COLUMN_TEXT, "More Comments (" + more.getCount() + ")");
			values.put(CommentEntry.PARENT_ID, Long.parseLong(more.getParentId().substring(3), 36)); // converting Reddit ID to long
			contentValues.add(values);
		}

		// now insert comments into DB
		getContext().getContentResolver().bulkInsert(CommentEntry.CONTENT_URI, contentValues.toArray(new ContentValues[contentValues.size()]));

	}

	public static List<ContentValues> processComments(int displayOrder, List<CommentNode> commentNodeList) {
		ArrayList<ContentValues> contentValues = new ArrayList<>();
		ContentValues values;
		int type;
		int depth;
		Comment comment;
		MoreChildren more;

		Stack<ContentValues> delayedMoreOperations = new Stack<>(); // placeholder to keep the "more comments" entries until we reach their position
		for (CommentNode node: commentNodeList) {
			depth = node.getDepth();

			// check if we have pending "more comments" entries than have higher depth than current node
			// if yes, they need to be added before this node

			while (!delayedMoreOperations.isEmpty() && delayedMoreOperations.peek().getAsInteger(CommentEntry.COLUMN_DEPTH) > depth) {
				values = delayedMoreOperations.pop();
				values.put(CommentEntry.COLUMN_DISPLAY_ORDER, displayOrder++);

				contentValues.add(values);
			}

			comment = node.getComment();
			values = new ContentValues();
			values.put(CommentEntry._ID, Long.parseLong(comment.getId(), 36)); // converting Reddit ID to long
			values.put(CommentEntry.COLUMN_DISPLAY_ORDER, displayOrder++);

			type = node.getImmediateSize() == 0 ? Consts.CommentType.NO_REPLIES_EXPANDED : Consts.CommentType.WITH_REPLIES_EXPANDED;

			if (node.hasMoreComments()) {
				type = Consts.CommentType.WITH_REPLIES_EXPANDED;
			}

			values.put(CommentEntry.COLUMN_TYPE, type);
			values.put(CommentEntry.COLUMN_VISIBLE, 1);
			values.put(CommentEntry.COLUMN_DEPTH, node.getDepth());
			values.put(CommentEntry.COLUMN_AUTHOR, comment.getAuthor());
			values.put(CommentEntry.COLUMN_SCORE, comment.getScore());
			values.put(CommentEntry.COLUMN_VOTE, comment.getVote().getValue());
			values.put(CommentEntry.COLUMN_TEXT, Utils.processRedditHtmlContent(comment.data("body_html")));
			values.put(CommentEntry.COLUMN_READ_ONLY, comment.isArchived() ? 1 : 0);

			contentValues.add(values);

			if (node.hasMoreComments()) {
				more = node.getMoreChildren();

				// prepare another entry for "more comments"
				if (more.getCount() > 0) {
					values = new ContentValues();
					values.put(CommentEntry._ID, Long.parseLong(more.getId(), 36)); // converting Reddit ID to long
					values.put(CommentEntry.PARENT_ID, Long.parseLong(comment.getId(), 36)); // converting Reddit ID to long
					values.put(CommentEntry.COLUMN_TYPE, Consts.CommentType.MORE_COMMENTS);
					values.put(CommentEntry.COLUMN_VISIBLE, 1);
					values.put(CommentEntry.COLUMN_DEPTH, node.getDepth() + 1);
					values.put(CommentEntry.COLUMN_TEXT, "More Comments (" + more.getCount() + ")");

					// push the values to the stack (entry will be created after we process all children)
					delayedMoreOperations.push(values);

				}
			}
		}

		// check id we have remaining "more comments" in the delayed stack
		while (!delayedMoreOperations.isEmpty()) {
			values = delayedMoreOperations.pop();
			values.put(CommentEntry.COLUMN_DISPLAY_ORDER, displayOrder++);

			contentValues.add(values);
		}

		return contentValues;
	}
}
