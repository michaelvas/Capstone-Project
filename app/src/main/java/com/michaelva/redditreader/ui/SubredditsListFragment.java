package com.michaelva.redditreader.ui;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.loader.SubredditsLoader;
import com.michaelva.redditreader.sync.SubredditsSyncAdapter;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;

public class SubredditsListFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
		SharedPreferences.OnSharedPreferenceChangeListener {

	private SubredditsListAdapter mAdapter;

	private MenuItem mRefreshMenuItem;


	public SubredditsListFragment() {
		setHasOptionsMenu(true);
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
		View rootView = inflater.inflate(R.layout.fragment_subreddits_list, container, false);

		View listContaner = rootView.findViewById(R.id.recycle_view_container);

		RecyclerView list = (RecyclerView) rootView.findViewById(R.id.subreddits_list);
		list.setLayoutManager(new LinearLayoutManager(getContext()));
		list.addItemDecoration(new SimpleDividerItemDecoration(getContext()));

		ContentLoadingProgressBar progress = (ContentLoadingProgressBar) rootView.findViewById(R.id.progressbar);

		mAdapter = new SubredditsListAdapter(getContext(), listContaner, progress);

		list.setAdapter(mAdapter);

		return rootView;
    }

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getLoaderManager().initLoader(Consts.SUBREDDITS_LOADER, null, this);

	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.subreddits, menu);

		mRefreshMenuItem = menu.findItem(R.id.action_refresh);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		int syncStatus = SyncStatusUtils.getSyncStatus(getContext(), Consts.SUBREDDITS_SYNC_STATUS_PREF);

		mRefreshMenuItem.setVisible(syncStatus > SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_refresh) {
			SubredditsSyncAdapter.syncNow(getContext());
			return true;
		} else {
			return false;
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new SubredditsLoader(getContext());
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mAdapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Consts.SUBREDDITS_SYNC_STATUS_PREF)) {
			updateUi();
		}
	}

	void updateUi() {
		int syncStatus = SyncStatusUtils.getSyncStatus(getContext(), Consts.SUBREDDITS_SYNC_STATUS_PREF);

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
}
