package com.michaelva.redditreader.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.data.RedditContract;
import com.michaelva.redditreader.loader.AllSubmissionsInRandomOrderLoader;
import com.michaelva.redditreader.loader.ChangedSubredditsLoader;
import com.michaelva.redditreader.service.UtilityService;
import com.michaelva.redditreader.sync.SubmissionsSyncAdapter;
import com.michaelva.redditreader.sync.SubredditsSyncAdapter;
import com.michaelva.redditreader.ui.AlertDialogFragment;
import com.michaelva.redditreader.ui.MessageFragment;
import com.michaelva.redditreader.ui.SubmissionFragment;
import com.michaelva.redditreader.ui.SubmissionsGridFragment;
import com.michaelva.redditreader.ui.SubmissionsPresenter;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import java.lang.ref.WeakReference;

import static com.michaelva.redditreader.ui.AlertDialogFragment.AlertType;

public class MainActivity extends AppCompatActivity implements AlertDialogFragment.Callbacks, SubmissionsGridFragment.Callbacks,
		LoaderManager.LoaderCallbacks<Cursor>, SharedPreferences.OnSharedPreferenceChangeListener {

	private static final int SYNC_SUBMISSIONS_MESSAGE = 1;

	private static final int SET_SUBMISSION_DATA_MESSAGE = 2;

	private final MyHandler mHandler = new MyHandler(this);

	private MenuItem mAuthMenuItem;

	private MenuItem mSubredditsMenuItem;

	private boolean mTabletMode;

	private Cursor mCursor;

	private Tracker mTracker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		mTabletMode = getResources().getBoolean(R.bool.tablet_mode);

		mTracker = ((RedditReaderApp)getApplication()).getDefaultTracker();
	}

	@Override
	protected void onResume() {
		super.onResume();

		int messageId = -1;
		int buttonId = -1;
		int alertType = -1;

		if (!Utils.isUserAuthenticated(this)) {
			messageId = R.string.message_not_authorized;
			alertType = AlertDialogFragment.ALERT_TYPE_LOGIN;
			buttonId = R.string.button_login;
		} else if (!Utils.atLeastOneSubredditSelected(this)) {
			messageId = R.string.message_no_subreddits_selected;
			alertType = AlertDialogFragment.ALERT_TYPE_SUBREDDITS;
			buttonId = R.string.button_select;
		}

		Fragment fragment;
		if ( messageId != -1) {
			fragment = MessageFragment.create(getString(messageId));
			getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();

			//show alert if not already shown
			DialogFragment alert = (DialogFragment) getSupportFragmentManager().findFragmentByTag("alert");
			if (alert == null) {
				alert = AlertDialogFragment.create(alertType, getString(messageId), getString(buttonId));
				alert.show(getSupportFragmentManager(), "alert");
			}
		} else {

			fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
			if (fragment == null || !(fragment instanceof SubmissionsPresenter)) {

				long selectedSubmissionId = -1;
				if (getIntent().getData() != null) {
					selectedSubmissionId = RedditContract.SubmissionEntry.getSubmissionId(getIntent().getData());
				}

				if (mTabletMode) {
					fragment = SubmissionsGridFragment.create();
				} else {
					if (selectedSubmissionId != -1) {
						fragment = SubmissionFragment.create(selectedSubmissionId);
					} else {
						fragment = SubmissionFragment.createRandom();
					}
				}
				getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();

				if (mTabletMode && selectedSubmissionId != -1) {
					onSubmissionSelected(selectedSubmissionId);
				}
			}

			getSupportLoaderManager().initLoader(Consts.ALL_SUBMISSIONS_IN_RANDOM_ORDER_LOADER, null, this);
		}

		getSupportLoaderManager().restartLoader(Consts.CHANGED_SUBREDDITS_LOADER, null, this);
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
		updateMenuItems();

		mTracker.setScreenName(getString(R.string.app_name));
		mTracker.send(new HitBuilders.ScreenViewBuilder().build());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		long selectedSubmissionId = -1;
		if (intent.getData() != null) {
			selectedSubmissionId = RedditContract.SubmissionEntry.getSubmissionId(intent.getData());
		}

		if (selectedSubmissionId != -1) {
			Fragment fragment;
			if (mTabletMode) {
				fragment = SubmissionsGridFragment.create();
			} else {
				fragment = SubmissionFragment.create(selectedSubmissionId);
			}
			getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();

			if (mTabletMode) {
				onSubmissionSelected(selectedSubmissionId);
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);

		mAuthMenuItem = menu.findItem(R.id.action_authenticate);

		mSubredditsMenuItem = menu.findItem(R.id.action_subreddits_list);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		updateMenuItems();
		return super.onPrepareOptionsMenu(menu);
	}

	void updateMenuItems() {
		if (mAuthMenuItem == null || mSubredditsMenuItem == null) {
			return;
		}

		int syncStatus = SyncStatusUtils.getSyncStatus(this, Consts.SUBMISSIONS_SYNC_STATUS_PREF);

		// set the authenticate menu item's label based on whether user is authenticated or not
		mAuthMenuItem.setTitle(Utils.isUserAuthenticated(this) ? R.string.action_change_user : R.string.action_login);

		// hide menu items if we're in the middle of sync
		mAuthMenuItem.setVisible(syncStatus != SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);

		// enable subreddits list menu item if user is authenticated and we're not in the middle of sync
		mSubredditsMenuItem.setVisible(Utils.isUserAuthenticated(this) && syncStatus != SyncStatusUtils.SYNC_STATUS_IN_PROGRESS);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_subreddits_list) {
			startSubredditsActivity();
			return true;
		} else if (id == R.id.action_authenticate) {
			startLoginActivity();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	private void startLoginActivity() {
		Intent i = new Intent(MainActivity.this, LoginActivity.class);
		startActivity(i);
	}

	private void startSubredditsActivity() {
		// if we haven't yet synced the user's subreddits, initiate immediate sync
		int syncStatus = SyncStatusUtils.getSyncStatus(this, Consts.SUBREDDITS_SYNC_STATUS_PREF);
		if (syncStatus == SyncStatusUtils.SYNC_STATUS_UNKNOWN) {
			SubredditsSyncAdapter.syncNow(this);
		}
		Intent i = new Intent(MainActivity.this, SubredditsActivity.class);
		startActivity(i);
	}

	@Override
	public void onAlertAction(@AlertType int alertType) {
		if (alertType == AlertDialogFragment.ALERT_TYPE_LOGIN) {
			startLoginActivity();
		} else if (alertType == AlertDialogFragment.ALERT_TYPE_SUBREDDITS) {
			startSubredditsActivity();
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		if (id == Consts.CHANGED_SUBREDDITS_LOADER) {
			return new ChangedSubredditsLoader(this);
		} else if (id == Consts.ALL_SUBMISSIONS_IN_RANDOM_ORDER_LOADER) {
			return new AllSubmissionsInRandomOrderLoader(this);
		} else {
			throw new IllegalArgumentException("Unknown loader ID; can't create loader");
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		int id = loader.getId();
		if (id == Consts.CHANGED_SUBREDDITS_LOADER) {
			if (data != null) {
				if (data.getCount() > 0) {
					// there are subreddits that are pending state change
					mHandler.sendEmptyMessage(SYNC_SUBMISSIONS_MESSAGE);
				}
				data.close();
			}
		} else if (id == Consts.ALL_SUBMISSIONS_IN_RANDOM_ORDER_LOADER) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

			if (fragment != null && fragment instanceof SubmissionsPresenter) {
				((SubmissionsPresenter) fragment).setSubmissionsData(data);
			}

			fragment = getSupportFragmentManager().findFragmentByTag(SubmissionFragment.FRAGMENT_TAG);
			if (fragment != null) {
				((SubmissionsPresenter) fragment).setSubmissionsData(data);
			}

			// release previous cursor if needed
			if (mCursor != null && mCursor != data) {
				mCursor.close();
			}

			mCursor = data;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		int id = loader.getId();
		if (id == Consts.ALL_SUBMISSIONS_IN_RANDOM_ORDER_LOADER) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

			if (fragment != null && fragment instanceof SubmissionsPresenter) {
				((SubmissionsPresenter) fragment).clearSubmissionsData();
			}

			fragment = getSupportFragmentManager().findFragmentByTag(SubmissionFragment.FRAGMENT_TAG);
			if (fragment != null) {
				((SubmissionsPresenter) fragment).clearSubmissionsData();
			}

			// release cursor
			if (mCursor != null) {
				mCursor.close();
				mCursor = null;
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Consts.SUBMISSIONS_SYNC_STATUS_PREF)) {
			updateMenuItems();
		}
	}

	/**
	 * Based on http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
	 */
	private static class MyHandler extends Handler {
		private final WeakReference<MainActivity> mActivity;

		public MyHandler(MainActivity activity) {
			mActivity = new WeakReference<>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			MainActivity activity = mActivity.get();

			// continue only if we still have reference to the activity
			if (activity == null) {
				return;
			}

			if (msg.what == SYNC_SUBMISSIONS_MESSAGE) {
				// start new sync if it isn't running already
				int syncStatus = SyncStatusUtils.getSyncStatus(activity, Consts.SUBMISSIONS_SYNC_STATUS_PREF);

				if ( syncStatus != SyncStatusUtils.SYNC_STATUS_IN_PROGRESS) {
					SubmissionsSyncAdapter.syncPending(activity);
					Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);

					if (fragment == null || !(fragment instanceof SubmissionsPresenter)) {
						if (activity.mTabletMode) {
							fragment = SubmissionsGridFragment.create();
						} else {
							fragment = SubmissionFragment.createRandom();
						}
						activity.getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
					}
				}
			} else if (msg.what == SET_SUBMISSION_DATA_MESSAGE) {
				Fragment dialogFragment = activity.getSupportFragmentManager().findFragmentByTag(SubmissionFragment.FRAGMENT_TAG);

				if (dialogFragment != null) {
					((SubmissionsPresenter) dialogFragment).setSubmissionsData(activity.mCursor);
				}
			}
		}
	}

	public void onSubmissionSelected(long submissionId) {
		Fragment fragment = getSupportFragmentManager().findFragmentByTag(SubmissionFragment.FRAGMENT_TAG);

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

		if (fragment != null) {
			ft.remove(fragment);
		}

		SubmissionFragment.create(submissionId).show(ft, SubmissionFragment.FRAGMENT_TAG);

		mHandler.sendEmptyMessage(SET_SUBMISSION_DATA_MESSAGE);
	}
}
