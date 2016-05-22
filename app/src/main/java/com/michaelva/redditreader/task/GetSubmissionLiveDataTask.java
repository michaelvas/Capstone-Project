package com.michaelva.redditreader.task;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.michaelva.redditreader.util.Consts;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.SubmissionRequest;
import net.dean.jraw.models.Submission;

public class GetSubmissionLiveDataTask extends AbstractTask<GetSubmissionLiveDataTask.Result> {
    private final String LOG_TAG = getClass().getCanonicalName();

    public static final String ARG_SUBMISSION_ID = "SUBMISSION_ID";

	private long mSubmissionId;

	public static class Result extends AbstractTask.TaskResult {
		public long id;
		public int commentsCount;
		public int score;
		public int userVote;
		public boolean readOnly;
		public boolean hideScore;
	}

    public GetSubmissionLiveDataTask(Context context, Bundle args) {
        super(context, new Result());

		mSubmissionId = args.getLong(ARG_SUBMISSION_ID);
    }

	@Override
	protected void performTask() throws Exception {
		Log.d(LOG_TAG, "Loading submission " + mSubmissionId);

		String redditId = Long.toString(mSubmissionId, 36); // converting long to Reddit ID

		SubmissionRequest request = new SubmissionRequest.Builder(redditId).limit(1).depth(1).build();

		RedditClient reddit = AuthenticationManager.get().getRedditClient();

		Submission submission = reddit.getSubmission(request);

		mResult.id = Long.parseLong(submission.getId(), 36); // converting Reddit ID to long
		mResult.commentsCount = submission.getCommentCount();
		mResult.score = submission.getScore();
		mResult.userVote = submission.getVote().getValue();
		mResult.readOnly = submission.isArchived() || submission.isLocked();
		mResult.hideScore = submission.data("hide_score", Boolean.class);
    }

}
