package com.michaelva.redditreader.task;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.michaelva.redditreader.util.Consts;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.SubmissionRequest;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

public class SubmitSubmissionVoteTask extends AbstractTask<SubmitSubmissionVoteTask.Result> {
    private final String LOG_TAG = getClass().getCanonicalName();

    public static final String ARG_SUBMISSION_ID = "SUBMISSION_ID";

	public static final String ARG_VOTE = "VOTE";

    private long mSubmissionId;
	private int mVote;

	public static class Result extends GetSubmissionLiveDataTask.Result {
	}

    public SubmitSubmissionVoteTask(Context context, Bundle args) {
        super(context, new Result());

		mSubmissionId = args.getLong(ARG_SUBMISSION_ID);
		mVote = args.getInt(ARG_VOTE);
    }

	@Override
	protected void performTask() throws Exception {
		Log.d(LOG_TAG, "Submitting vote " + mVote + " for submission " + mSubmissionId);

		String redditId = Long.toString(mSubmissionId, 36); // converting long to Reddit ID

		SubmissionRequest request = new SubmissionRequest.Builder(redditId).limit(1).depth(1).build();

		RedditClient reddit = AuthenticationManager.get().getRedditClient();

		Submission submission = reddit.getSubmission(request);

		mResult.id = Long.parseLong(submission.getId(), 36); // converting Reddit ID to long
		mResult.commentsCount = submission.getCommentCount();
		mResult.score = submission.getScore();
		mResult.readOnly = submission.isArchived() || submission.isLocked();
		mResult.hideScore = submission.data("hide_score", Boolean.class);

		VoteDirection vote;

		if (mVote > 0) {
			vote = VoteDirection.UPVOTE;
		} else if (mVote < 0) {
			vote = VoteDirection.DOWNVOTE;
		} else {
			vote = VoteDirection.NO_VOTE;
		}

		new AccountManager(reddit).vote(submission, vote);

		mResult.userVote = mVote;
    }

}
