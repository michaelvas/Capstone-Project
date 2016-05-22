package com.michaelva.redditreader.activity;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.loader.SubmissionByIdLoader;
import com.michaelva.redditreader.loader.SubmissionLoader;
import com.michaelva.redditreader.service.UtilityService;
import com.michaelva.redditreader.ui.CommentsListFragment;
import com.michaelva.redditreader.ui.MessageFragment;
import com.michaelva.redditreader.ui.NewCommentFragment;
import com.michaelva.redditreader.util.Consts;

public class CommentsActivity extends AppCompatActivity implements
		CommentsListFragment.Callbacks,
		NewCommentFragment.Callbacks,
		LoaderManager.LoaderCallbacks<Cursor> {

	public static final String EXTRA_SUBMISSION_ID = CommentsActivity.class.getCanonicalName() + ".extra.SUBMISSION_ID";

	private boolean mTabletMode;

	private Handler mHandler = new Handler();

	private long mSubmissionId;

	private Tracker mTracker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_comments);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowHomeEnabled(true);
		}

		mTabletMode = getResources().getBoolean(R.bool.tablet_mode);

		if (savedInstanceState == null) {
			mSubmissionId = getIntent().getLongExtra(EXTRA_SUBMISSION_ID, -1);
		} else {
			mSubmissionId = savedInstanceState.getLong(EXTRA_SUBMISSION_ID, -1);
		}

		mTracker = ((RedditReaderApp)getApplication()).getDefaultTracker();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(EXTRA_SUBMISSION_ID, mSubmissionId);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// if fragment is not loaded, we start submission loader
		// fragment will be instantiated when loader finishes retrieving data
		if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
			Bundle args = new Bundle();
			args.putLong(EXTRA_SUBMISSION_ID, mSubmissionId);
			getSupportLoaderManager().initLoader(Consts.SUBMISSION_BY_ID_LOADER, args, this);
		}

		mTracker.setScreenName(getString(R.string.activity_comments_list));
		mTracker.send(new HitBuilders.ScreenViewBuilder().build());
	}

	@Override
	public void onSubmissionReply(long submissionId, String submissionText) {
		if (mTabletMode) {
			NewCommentFragment fragment = NewCommentFragment.create(submissionId, true, submissionText);
			fragment.show(getSupportFragmentManager(), NewCommentFragment.FRAGMENT_TAG);
		} else {
			Intent i = new Intent(this, NewCommentActivity.class);
			i.putExtra(NewCommentActivity.EXTRA_SUBMISSION, true);
			i.putExtra(NewCommentActivity.EXTRA_REDDIT_ID, submissionId);
			i.putExtra(NewCommentActivity.EXTRA_TEXT, submissionText);
			startActivityForResult(i, Consts.REPLY_SUBMISSION);
		}
	}

	@Override
	public void onCommentReply(long commentId, String commentText) {
		if (mTabletMode) {
			NewCommentFragment fragment = NewCommentFragment.create(commentId, false, commentText);
			fragment.show(getSupportFragmentManager(), NewCommentFragment.FRAGMENT_TAG);
		} else {
			Intent i = new Intent(this, NewCommentActivity.class);
			i.putExtra(NewCommentActivity.EXTRA_SUBMISSION, false);
			i.putExtra(NewCommentActivity.EXTRA_REDDIT_ID, commentId);
			i.putExtra(NewCommentActivity.EXTRA_TEXT, commentText);
			startActivityForResult(i, Consts.REPLY_COMMENT);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Consts.REPLY_SUBMISSION || requestCode == Consts.REPLY_COMMENT) {
			if (resultCode == Activity.RESULT_OK) {
				CommentsListFragment fragment = (CommentsListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
				fragment.reloadComments();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		if (id == Consts.SUBMISSION_BY_ID_LOADER) {
			return new SubmissionByIdLoader(this, args.getLong(EXTRA_SUBMISSION_ID));
		} else {
			throw new IllegalArgumentException("unknown loader ID!");
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if (data == null || !data.moveToFirst()) {

			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Fragment fragment = MessageFragment.create(getString(R.string.message_internal_error));
					getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
				}
			});
		} else {
			final long submissionId = data.getLong(SubmissionLoader.COL_ID);
			final String submissionTitle = data.getString(SubmissionLoader.COL_TITLE);
			final boolean submissionReadOnly = data.getInt(SubmissionLoader.COL_READ_ONLY) == 1;
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Fragment fragment = CommentsListFragment.create(submissionId, submissionTitle, submissionReadOnly);
					getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
				}
			});
		}

		if (data != null) {
			data.close();
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {}

	@Override
	public void onCommentSubmitted() {
		Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
		if (fragment != null && fragment instanceof CommentsListFragment) {
			((CommentsListFragment) fragment).reloadComments();
		}
	}
}
