package com.michaelva.redditreader.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.databinding.FragmentCommentsListBinding;
import com.michaelva.redditreader.loader.CommentsLoader;
import com.michaelva.redditreader.service.UtilityService;
import com.michaelva.redditreader.task.GetCommentsTask;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommentsListFragment extends Fragment implements LoaderManager.LoaderCallbacks,
		SharedPreferences.OnSharedPreferenceChangeListener, CommentsListAdapter.ActionCallbacks {

	public static final String ARG_SUBMISSION_ID = "SUBMISSION_ID";
	public static final String ARG_SUBMISSION_TITLE = "SUBMISSION_TITLE";
	public static final String ARG_SUBMISSION_READ_ONLY = "SUBMISSION_READ_ONLY";


	private long mSubmissionId;
	private String mSubmissionTitle;
	private boolean mSubmissionReadOnly;

	private CommentsListAdapter mAdapter;

	private MenuItem mRefreshMenuItem;

	private MenuItem mReplyMenuItem;

	private Callbacks mHost;

	private FragmentCommentsListBinding mBinding;

	List<BroadcastReceiver> mReceivers = new ArrayList<>();

	private Tracker mTracker;

	public interface Callbacks {
		void onSubmissionReply(long submissionId, String submissionText);
		void onCommentReply(long commentId, String commentText);
	}

	public static CommentsListFragment create(long submissionId, String submissionTitle, boolean submissionReadOnly) {
		CommentsListFragment fragment = new CommentsListFragment();

		Bundle args = new Bundle();
		args.putLong(ARG_SUBMISSION_ID, submissionId);
		args.putString(ARG_SUBMISSION_TITLE, submissionTitle);
		args.putBoolean(ARG_SUBMISSION_READ_ONLY, submissionReadOnly);

		fragment.setArguments(args);

		return fragment;
	}

	public CommentsListFragment() {
		setHasOptionsMenu(true);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		if (context instanceof Callbacks) {
			mHost = (Callbacks) context;
		} else {
			throw new ClassCastException(context.getClass().getCanonicalName() + " must implement " + Callbacks.class.getCanonicalName());
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mHost = null;
	}

	@Override
	public void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);

		BroadcastReceiver receiver = new CommentActionsReceiver();
		IntentFilter iff = new IntentFilter(UtilityService.ACTION_LOAD_MORE_COMMENTS);
		LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver, iff);
		mReceivers.add(receiver);

		receiver = new CommentActionsReceiver();
		iff = new IntentFilter(UtilityService.ACTION_EXPAND_COLLAPSE_COMMENT);
		LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver, iff);
		mReceivers.add(receiver);

	}

	@Override
	public void onPause() {
		super.onPause();
		PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
		for (BroadcastReceiver receiver : mReceivers) {
			LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
		}
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			mSubmissionId = savedInstanceState.getLong(ARG_SUBMISSION_ID);
			mSubmissionTitle = savedInstanceState.getString(ARG_SUBMISSION_TITLE);
			mSubmissionReadOnly = savedInstanceState.getBoolean(ARG_SUBMISSION_READ_ONLY);
		} else {
			mSubmissionId = getArguments().getLong(ARG_SUBMISSION_ID);
			mSubmissionTitle = getArguments().getString(ARG_SUBMISSION_TITLE);
			mSubmissionReadOnly = getArguments().getBoolean(ARG_SUBMISSION_READ_ONLY);
		}

		mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_comments_list, container, false);

		mBinding.submissionTitle.setText(Html.fromHtml(mSubmissionTitle));

		mBinding.commentsList.setLayoutManager(new LinearLayoutManager(getContext()));

		mAdapter = new CommentsListAdapter(
				getContext(),
				mBinding.listContainer,
				mBinding.progressbar,
				mBinding.submissionTitle,
				this);

		mBinding.commentsList.setAdapter(mAdapter);

		return mBinding.getRoot();
    }

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getLoaderManager().initLoader(Consts.COMMENTS_LOADER, null, this);

		Bundle args = new Bundle();
		args.putLong(ARG_SUBMISSION_ID, mSubmissionId);
		getLoaderManager().initLoader(Consts.GET_COMMENTS_TASK, args, this);

		AdRequest adRequest = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();

		mBinding.adView.loadAd(adRequest);

		if (savedInstanceState == null) {
			mAdapter.setListShown(false, true);
		} else {
			updateUi();
		}

		mTracker = ((RedditReaderApp) getActivity().getApplication()).getDefaultTracker();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(ARG_SUBMISSION_ID, mSubmissionId);
		outState.putString(ARG_SUBMISSION_TITLE, mSubmissionTitle);
		outState.putBoolean(ARG_SUBMISSION_READ_ONLY, mSubmissionReadOnly);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.comments, menu);

		mRefreshMenuItem = menu.findItem(R.id.action_refresh);
		mReplyMenuItem = menu.findItem(R.id.action_reply);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		int taskStatus = SyncStatusUtils.getSyncStatus(getContext(), Consts.COMMENTS_TASK_STATUS_REF);

		mRefreshMenuItem.setVisible(taskStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);

		mReplyMenuItem.setVisible(taskStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS && !mSubmissionReadOnly);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_refresh) {
			reloadComments();
			return true;
		} else if (item.getItemId() == R.id.action_reply) {
			mHost.onSubmissionReply(mSubmissionId, mBinding.submissionTitle.getText().toString());
			return true;
		} else {
			return false;
		}
	}

	public void reloadComments() {
		Bundle args = new Bundle();
		args.putLong(ARG_SUBMISSION_ID, mSubmissionId);
		getLoaderManager().restartLoader(Consts.GET_COMMENTS_TASK, args, this);

		mTracker.send(new HitBuilders.EventBuilder(Consts.GA_CATEGORY_ACTION, Consts.GA_ACTION_RELOAD_COMMENTS).build());
	}

	@Override
	public Loader onCreateLoader(int id, Bundle args) {
		if (id == Consts.COMMENTS_LOADER) {
			return new CommentsLoader(getContext());
		} else if (id == Consts.GET_COMMENTS_TASK) {
			return new GetCommentsTask(getContext(), args.getLong(ARG_SUBMISSION_ID));
		} else {
			throw new IllegalArgumentException("unknown loader ID!");
		}
	}

	@Override
	public void onLoadFinished(Loader loader, Object data) {
		if (loader.getId() == Consts.COMMENTS_LOADER) {
			mAdapter.swapCursor((Cursor) data);
		}
	}

	@Override
	public void onLoaderReset(Loader loader) {
		if (loader.getId() == Consts.COMMENTS_LOADER) {
			mAdapter.swapCursor(null);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Consts.COMMENTS_TASK_STATUS_REF)) {
			updateUi();
		}
	}

	void updateUi() {
		int taskStatus = SyncStatusUtils.getSyncStatus(getContext(), Consts.COMMENTS_TASK_STATUS_REF);

		if (taskStatus <= SyncStatusUtils.SYNC_STATUS_IN_PROGRESS) {
			mAdapter.setListShown(false);
		} else {
			mAdapter.setEmptyText(SyncStatusUtils.getSyncStatusMessage(getContext(), taskStatus));
			mAdapter.setListShown(true);
		}

		if (mRefreshMenuItem != null) {
			mRefreshMenuItem.setVisible(taskStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);
		}
		if (mReplyMenuItem != null) {
			mReplyMenuItem.setVisible(taskStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS && !mSubmissionReadOnly);
		}

	}

	@Override
	public void onCommentReply(long commentId, String commentText) {
		mHost.onCommentReply(commentId, commentText);
	}

	@Override
	public void onCommentLoadMore(long commentId) {
		UtilityService.startActionLoadMoreComments(getContext(), mSubmissionId, commentId);
	}

	@Override
	public void onCommentExpandCollapse(long commentId) {
		UtilityService.startActionExpandCollapseComment(getContext(), commentId);
	}

	@Override
	public void onCommentUpvote(long commentId) {
		submitVote(commentId, 1);
	}

	@Override
	public void onCommentDownvote(long commentId) {
		submitVote(commentId, -1);
	}

	@Override
	public void onCommentUnvote(long commentId) {
		submitVote(commentId, 0);
	}

	void submitVote(long commentId, int vote) {
		UtilityService.startActionSubmitCommentVote(getContext(), mSubmissionId, commentId, vote);
	}

	class CommentActionsReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Map<String, String> event;

			if (!intent.getBooleanExtra(UtilityService.EXTRA_RESULT_STATUS_SUCCESS, false)) {
				String message = intent.getStringExtra(UtilityService.EXTRA_RESULT_MESSAGE);
				Snackbar.make(mBinding.getRoot(), message, Snackbar.LENGTH_LONG).show();
				event = new HitBuilders.ExceptionBuilder().setDescription(message).build();
			} else {
				int vote = intent.getIntExtra(UtilityService.EXTRA_RESULT_VOTE, 0);
				String action;
				if (vote > 0) {
					action = Consts.GA_ACTION_COMMENT_UPVOTE;
				} else if (vote < 0) {
					action = Consts.GA_ACTION_COMMENT_DOWNVOTE;
				} else {
					action = Consts.GA_ACTION_COMMENT_UNVOTE;
				}
				event = new HitBuilders.EventBuilder(Consts.GA_CATEGORY_ACTION, action).build();
			}

			mTracker.send(event);
		}
	}
}
