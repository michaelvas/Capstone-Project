package com.michaelva.redditreader.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.doubleclick.CustomRenderedAd;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.sync.SubmissionsSyncAdapter;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;

public class SubmissionsGridFragment extends Fragment implements
		SharedPreferences.OnSharedPreferenceChangeListener, SubmissionsPresenter, SubmissionsGridAdapter.ActionCallbacks {

	private SubmissionsGridAdapter mAdapter;

	private MenuItem mRefreshMenuItem;

	private Callbacks mHost;

	private Tracker mTracker;

	public interface Callbacks {
		void onSubmissionSelected(long submissionId);
	}

	public static SubmissionsGridFragment create() {
		return new SubmissionsGridFragment();
	}


	public SubmissionsGridFragment() {
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
	public void setSubmissionsData(Cursor data) {
		mAdapter.swapCursor(data);
	}

	@Override
	public void clearSubmissionsData() {
		mAdapter.swapCursor(null);
	}

	@Override
	public void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
		updateUi();
	}

	@Override
	public void onPause() {
		super.onPause();
		PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_submissions_grid, container, false);

		View gridContaner = rootView.findViewById(R.id.recycle_view_container);

		RecyclerView grid = (RecyclerView) rootView.findViewById(R.id.submissions_grid);

		int columns = getResources().getInteger(R.integer.grid_columns);
		int spacing = getResources().getDimensionPixelSize(R.dimen.submissions_grid_column_spacing);

		grid.setLayoutManager(new GridLayoutManager(getContext(), columns));

		grid.addItemDecoration(new GridSpacingItemDecoration(columns, spacing, true));

		ContentLoadingProgressBar progress = (ContentLoadingProgressBar) rootView.findViewById(R.id.progressbar);

		mAdapter = new SubmissionsGridAdapter(getContext(), gridContaner, progress, this);

		grid.setAdapter(mAdapter);

		return rootView;
    }

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		mTracker = ((RedditReaderApp) getActivity().getApplication()).getDefaultTracker();
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
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Consts.SUBMISSIONS_SYNC_STATUS_PREF)) {
			updateUi();
		}
	}

	void updateUi() {
		int syncStatus = SyncStatusUtils.getSyncStatus(getContext(), Consts.SUBMISSIONS_SYNC_STATUS_PREF);

		if (syncStatus <= SyncStatusUtils.SYNC_STATUS_IN_PROGRESS) {
			mAdapter.setListShown(false);
		} else {
			mAdapter.setEmptyText(SyncStatusUtils.getSyncStatusMessage(getContext(), syncStatus));
			mAdapter.setListShown(true);
		}

		if (mRefreshMenuItem != null) {
			mRefreshMenuItem.setVisible(syncStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);
		}
	}

	@Override
	public void onSubmissionSelected(long submissionId) {
		if (mHost != null) {
			mHost.onSubmissionSelected(submissionId);
		}
	}
}
