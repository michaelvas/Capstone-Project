package com.michaelva.redditreader.ui;


import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.activity.CommentsActivity;
import com.michaelva.redditreader.databinding.SubmissionItemBinding;
import com.michaelva.redditreader.loader.SubmissionLoader;
import com.michaelva.redditreader.sync.SubmissionsSyncAdapter;
import com.michaelva.redditreader.task.GetSubmissionLiveDataTask;
import com.michaelva.redditreader.task.SubmitSubmissionVoteTask;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

public class SubmissionFragment extends AppCompatDialogFragment implements LoaderManager.LoaderCallbacks,
		SubmissionHolder.ActionCallbacks, SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener,
		SubmissionsPresenter {

	public static final String FRAGMENT_TAG = SubmissionFragment.class.getCanonicalName();

	private static final String ARG_SUBMISSION_ID = "SUBMISSION_ID";

	private boolean mSingleSubmissionMode = false;

	private long mCurrentSubmissionId = -1;

	private Cursor mCursor;
	private int mCurrentPosition;
	private int mNextPosition;

	private FrameLayout mSubmissionsPager;

	private SubmissionHolder mCurrent;
	private SubmissionHolder mNext;

	private boolean mDialogMode = false;

	private TextView mEmptyText;
	private ContentLoadingProgressBar mProgress;

	private MenuItem mRefreshMenuItem;

	private FloatingActionButton mFab;

	private Tracker mTracker;

	public static SubmissionFragment createRandom() {
		return new SubmissionFragment();
	}

	public static SubmissionFragment create(long submissionId) {
		SubmissionFragment fragment = new SubmissionFragment();
		Bundle args = new Bundle();
		args.putLong(ARG_SUBMISSION_ID, submissionId);
		fragment.setArguments(args);
		return fragment;
	}

	public SubmissionFragment() {
		setHasOptionsMenu(true);
	}

	@Override
	public void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
		updateUi();

		// fragment is shown as dialog only on tablets!
		if (mDialogMode) {

			// no menu when in dialog mode
			setHasOptionsMenu(false);

			// limit fragment's height device is in portrait orientation
			if (getResources().getBoolean(R.bool.portrait_mode)) {

				int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.66);

				Window window = getDialog().getWindow();

				WindowManager.LayoutParams lp = window.getAttributes();

				lp.height = height;

				window.setAttributes(lp);
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.submissions, menu);

		mRefreshMenuItem = menu.findItem(R.id.action_refresh);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		int syncStatus = SyncStatusUtils.getSyncStatus(getContext(), Consts.SUBMISSIONS_SYNC_STATUS_PREF);

		mRefreshMenuItem.setVisible(syncStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_refresh) {
			SubmissionsSyncAdapter.syncNow(getContext());
			mTracker.send(new HitBuilders.EventBuilder(Consts.GA_CATEGORY_ACTION, Consts.GA_ACTION_RELOAD_SUBMISSIONS).build());
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (savedInstanceState != null) {
			mCurrentSubmissionId = savedInstanceState.getLong(ARG_SUBMISSION_ID, -1);
		} else if (getArguments() != null){
			mCurrentSubmissionId = getArguments().getLong(ARG_SUBMISSION_ID);
		}

		mTracker = ((RedditReaderApp) getActivity().getApplication()).getDefaultTracker();

	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setStyle(STYLE_NO_TITLE, 0);

		mDialogMode = true;

		return super.onCreateDialog(savedInstanceState);

	}


	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_submission, container, false);

		mSubmissionsPager = (FrameLayout) rootView.findViewById(R.id.submissions_pager);

		mEmptyText = (TextView) rootView.findViewById(R.id.empty);

		mProgress = (ContentLoadingProgressBar) rootView.findViewById(R.id.progressbar);

		mFab = (FloatingActionButton) rootView.findViewById(R.id.fab);
		mFab.setOnClickListener(this);

		SubmissionItemBinding viewBinding = DataBindingUtil.inflate(inflater, R.layout.submission_item, mSubmissionsPager, false);
		viewBinding.toolbar.inflateMenu(R.menu.submission);
		mNext = new SubmissionHolder(getContext(), viewBinding);

		viewBinding = DataBindingUtil.inflate(inflater, R.layout.submission_item, mSubmissionsPager, false);
		viewBinding.toolbar.inflateMenu(R.menu.submission);
		mCurrent = new SubmissionHolder(getContext(), viewBinding);

		return rootView;
	}

	void showNext() {
		// sanity check (action will be blocked from UI)
		if (mSingleSubmissionMode) {
			return;
		}

		// cancel pending image downloads
		mCursor.moveToPosition(mCurrentPosition);
		mCurrent.cancelImageDownloads(getContext(), mCursor);

		// move currentPosition to the next one and get the submission ID of the "new" current submission
		mCurrentPosition = mNextPosition;
		mCursor.moveToPosition(mCurrentPosition);
		mCurrentSubmissionId = mCursor.getLong(SubmissionLoader.COL_ID);

		// re-insert the next submission's view so it is drawn on top of current submission's view
		mSubmissionsPager.removeView(mNext.getView());
		mSubmissionsPager.addView(mNext.getView());

		// switch view holders
		SubmissionHolder temp = mNext;
		mNext = mCurrent;
		mCurrent = temp;

		/**** now current/next view holders are pointing to the "new" current/next ****/

		// update next position
		if (mCursor.isLast()) {
			mNextPosition = 0;
		} else {
			mNextPosition = mCurrentPosition + 1;
		}

		// update the next submission's data
		mCursor.moveToPosition(mNextPosition);
		mNext.applyData(mCursor);

		updateActionListeners();

		// load live data for the current submission
		loadSubmissionLiveData();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Consts.SUBMISSIONS_SYNC_STATUS_PREF)) {
			updateUi();
		}
	}

	void updateUi() {
		int syncStatus = SyncStatusUtils.getSyncStatus(getContext(), Consts.SUBMISSIONS_SYNC_STATUS_PREF);

		if (syncStatus <= SyncStatusUtils.SYNC_STATUS_IN_PROGRESS) {
			mSubmissionsPager.setVisibility(View.GONE);
			mEmptyText.setVisibility(View.GONE);
			mProgress.setVisibility(View.VISIBLE);
		} else {
			if (syncStatus == SyncStatusUtils.SYNC_STATUS_OK) {
				mProgress.setVisibility(View.GONE);
				mEmptyText.setVisibility(View.GONE);
				mSubmissionsPager.setVisibility(View.VISIBLE);
			} else {
				mEmptyText.setText(SyncStatusUtils.getSyncStatusMessage(getContext(), syncStatus));
				mProgress.setVisibility(View.GONE);
				mSubmissionsPager.setVisibility(View.GONE);
				mEmptyText.setVisibility(View.VISIBLE);
			}
		}

		if (mRefreshMenuItem != null) {
			mRefreshMenuItem.setVisible(syncStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);
		}

		mFab.setVisibility(syncStatus == SyncStatusUtils.SYNC_STATUS_OK ? View.VISIBLE : View.GONE);
	}

	void setupViews(Cursor data) {
		mCursor = data;

		// setup the submission cards if data is available

		if (mCursor.getCount() == 1) {
			mSingleSubmissionMode = true;
			mCurrentPosition = 0;
			mCursor.moveToFirst();
			mCurrentSubmissionId = mCursor.getLong(SubmissionLoader.COL_ID);

			// remove any views from the container
			mSubmissionsPager.removeAllViews();

			// prepare the submission card

			mCurrent.applyData(mCursor);

			mSubmissionsPager.addView(mCurrent.getView());

			updateActionListeners();

			loadSubmissionLiveData();

			Snackbar.make(mSubmissionsPager, R.string.message_only_single_submission, Snackbar.LENGTH_SHORT).show();
		} else if (mCursor.getCount() > 1) {
			mSingleSubmissionMode = false;

			// sync current position with the current submission
			// if there is no current submission or it could not
			// be located in the cursor, set current position
			// to be 0 and current submission accordingly
			if (mCurrentSubmissionId == -1) {
				mCurrentPosition = 0;

				mCursor.moveToFirst();
				mCurrentSubmissionId = mCursor.getLong(SubmissionLoader.COL_ID);

			} else {
				boolean positionFound = false;

				mCurrentPosition = 0;
				mCursor.moveToFirst();
				long submissionId;
				while (!positionFound && !mCursor.isAfterLast()) {
					submissionId = mCursor.getLong(SubmissionLoader.COL_ID);
					if (mCurrentSubmissionId ==submissionId) {
						positionFound = true;
					} else {
						++mCurrentPosition;
						mCursor.moveToNext();
					}
				}

				if (!positionFound) {
					mCurrentPosition = 0;

					mCursor.moveToFirst();
					mCurrentSubmissionId = mCursor.getLong(SubmissionLoader.COL_ID);
				}
			}

			// setup the position of the next submission
			if (mCursor.isLast()) {
				mNextPosition = 0;
			} else {
				mNextPosition = mCurrentPosition + 1;
			}

			// remove any views from the container
			mSubmissionsPager.removeAllViews();

			// prepare the current and next submission cards

			mCursor.moveToPosition(mCurrentPosition);

			mCurrent.applyData( mCursor);

			mCursor.moveToPosition(mNextPosition);

			mNext.applyData(mCursor);

			// add the views (next must be added first - this way it is behind the current)

			mSubmissionsPager.addView(mNext.getView());

			mSubmissionsPager.addView(mCurrent.getView());

			updateActionListeners();

			loadSubmissionLiveData();

		} else {
			// empty cursor - update empty message text
			mEmptyText.setText(SyncStatusUtils.getSyncStatusMessage(getContext(), Consts.SUBMISSIONS_SYNC_STATUS_PREF));
		}
	}

	void updateActionListeners() {
		mNext.setListener(null, !mSingleSubmissionMode);
		mCurrent.setListener(this, !mSingleSubmissionMode);
	}

	void loadSubmissionLiveData() {
		Bundle args = new Bundle();
		args.putLong(GetSubmissionLiveDataTask.ARG_SUBMISSION_ID, mCurrentSubmissionId);
		getLoaderManager().restartLoader(Consts.GET_SUBMISSION_LIVE_DATA_TASK, args, this);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putLong(ARG_SUBMISSION_ID, mCurrentSubmissionId);
	}

	@Override
	public Loader onCreateLoader(int id, Bundle args) {
		 if (id == Consts.SUBMIT_SUBMISSION_VOTE) {
			return new SubmitSubmissionVoteTask(getContext(), args);
		} else if (id == Consts.GET_SUBMISSION_LIVE_DATA_TASK) {
			return new GetSubmissionLiveDataTask(getContext(), args);
		} else {
			 throw new IllegalArgumentException("Unknown loader ID!!!");
		 }
	}

	@Override
	public void onLoadFinished(Loader loader, Object data) {
		int id = loader.getId();

		if (id == Consts.GET_SUBMISSION_LIVE_DATA_TASK || id == Consts.SUBMIT_SUBMISSION_VOTE) {
			GetSubmissionLiveDataTask.Result result = (GetSubmissionLiveDataTask.Result) data;
			if (result.success) {
				if (result.id == mCurrentSubmissionId) {
					mCurrent.applyLiveData(result);
				}

				if (id == Consts.SUBMIT_SUBMISSION_VOTE) {
					String action;
					if (result.userVote > 0) {
						action = Consts.GA_ACTION_SUBMISSION_UPVOTE;
					} else if (result.userVote < 0) {
						action = Consts.GA_ACTION_SUBMISSION_DOWNVOTE;
					} else {
						action = Consts.GA_ACTION_SUBMISSION_UNVOTE;
					}
					mTracker.send(new HitBuilders.EventBuilder(Consts.GA_CATEGORY_ACTION, action).build());
				}

			} else {
				Snackbar.make(mSubmissionsPager, result.error, Snackbar.LENGTH_LONG).show();
				mTracker.send(new HitBuilders.ExceptionBuilder().setDescription(result.error).build());
			}
		}
	}

	@Override
	public void onLoaderReset(Loader loader) {
	}

	@Override
	public void setSubmissionsData(Cursor data) {
		setupViews(data);
		updateUi();
	}

	@Override
	public void clearSubmissionsData() {
		mCursor = null;
	}

	/**
	 * Invoked when FAB is clicked
	 */
	@Override
	public void onClick(View v) {
		if (mCursor == null) {
			return;
		}
		mCursor.moveToPosition(mCurrentPosition);
		if (mCursor.isBeforeFirst() || mCursor.isAfterLast()) {
			return;
		}

		String url = mCursor.getString(SubmissionLoader.COL_URL);

		int submissionType = mCursor.getInt(SubmissionLoader.COL_TYPE);

		Context ctx = getContext();

		Utils.launchExternalViewer(ctx, submissionType, url);

		mTracker.send(new HitBuilders.EventBuilder(Consts.GA_CATEGORY_ACTION, Consts.GA_ACTION_VIEW_SUBMISSION).build());
	}

	@Override
	public void dismissSubmission() {
		showNext();
	}

	@Override
	public void refreshSubmission() {
		loadSubmissionLiveData();
	}

	@Override
	public void upvoteSubmission() {
		submitVote(1);
	}

	@Override
	public void downvoteSubmission() {
		submitVote(-1);
	}

	@Override
	public void unvoteSubmission() {
		submitVote(0);
	}

	@Override
	public void viewSubmissionComments() {
		Intent i = new Intent(getContext(), CommentsActivity.class);
		i.putExtra(CommentsActivity.EXTRA_SUBMISSION_ID, mCurrentSubmissionId);

		startActivity(i);
	}

	void submitVote(int vote) {
		Bundle args = new Bundle();
		args.putLong(SubmitSubmissionVoteTask.ARG_SUBMISSION_ID, mCurrentSubmissionId);
		args.putInt(SubmitSubmissionVoteTask.ARG_VOTE, vote);
		getLoaderManager().restartLoader(Consts.SUBMIT_SUBMISSION_VOTE, args, this);
	}
}
