package com.michaelva.redditreader.util;

import android.support.annotation.IntDef;

import com.michaelva.redditreader.BuildConfig;

import net.dean.jraw.http.oauth.Credentials;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface Consts {

    int USER_AUTHENTICATION_TASK = 1;
    int SUBREDDITS_LOADER = 2;
    int RANDOM_SUBMISSION_LOADER = 3;
	int SUBMISSION_BY_ID_LOADER = 4;
	int ALL_SUBMISSIONS_IN_RANDOM_ORDER_LOADER = 4;
	int GET_SUBMISSION_LIVE_DATA_TASK = 5;
	int SUBMIT_SUBMISSION_VOTE = 6;
	int CHANGED_SUBREDDITS_LOADER = 7;
	int GET_COMMENTS_TASK = 8;
	int COMMENTS_LOADER = 9;

    Credentials APP_CREDENTIALS = Credentials.webapp(BuildConfig.REDDIT_CLIENT_ID, BuildConfig.REDDIT_APP_SECRET, BuildConfig.REDDIT_REDIRECT_URL);

    String IS_USER_AUTHENTICATED_PREF = "is_user_authenticated";
    String SUBREDDITS_SYNC_STATUS_PREF = "subreddits_sync_status";
    String SUBMISSIONS_SYNC_STATUS_PREF = "submissions_sync_status";
    String AT_LEAST_ONE_SUBREDDIT_SELECTED_PREF = "at_least_one_subreddit_selected";
	String COMMENTS_TASK_STATUS_REF = "comments_task_status";

	String ACTION_SUBMISSIONS_DATA_UPDATED = "com.michaelva.redditreader.SUBMISSIONS_DATA_UPDATED";
	String ACTION_SUBREDDITS_DATA_UPDATED = "com.michaelva.redditreader.SUBREDDITS_DATA_UPDATED";

	String SUBMISSION_KIND = "t3_";
	String COMMENT_KIND = "t1_";

	int REPLY_SUBMISSION = 1;
	int REPLY_COMMENT = 2;

	String NOT_AUTHORIZED = "401 Unauthorized";
	String RATELIMIT = "RATELIMIT";

	interface SubmissionType {
		int UNKNOWN = -1;
		int VIDEO = 0;
		int IMAGE = 1;
		int WEB = 2;
		int TEXT = 3;
	}

	interface CommentType {
		int MORE_COMMENTS = 0;
		int NO_REPLIES_EXPANDED = 1;
		int NO_REPLIES_COLLAPSED = 2;
		int WITH_REPLIES_EXPANDED = 3;
		int WITH_REPLIES_COLLAPSED = 4;
	}

	String GA_CATEGORY_ACTION = "Action";
	String GA_ACTION_NEW_COMMENT = "Comment";
	String GA_ACTION_REPLY_COMMENT = "Reply";
	String GA_ACTION_SUBMISSION_UPVOTE = "Upvote Submission";
	String GA_ACTION_SUBMISSION_DOWNVOTE = "Downvote Submission";
	String GA_ACTION_SUBMISSION_UNVOTE = "Unvote Submission";
	String GA_ACTION_COMMENT_UPVOTE = "Upvote Comment";
	String GA_ACTION_COMMENT_DOWNVOTE = "Downvote Comment";
	String GA_ACTION_COMMENT_UNVOTE = "Unvote Comment";
	String GA_ACTION_RELOAD_COMMENTS = "Reload Comments";
	String GA_ACTION_RELOAD_SUBMISSIONS = "Reload Submissions";
	String GA_ACTION_VIEW_SUBMISSION = "Open Submission External";
}
