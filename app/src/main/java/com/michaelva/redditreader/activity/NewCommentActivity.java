package com.michaelva.redditreader.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.ui.CommentsListFragment;
import com.michaelva.redditreader.ui.NewCommentFragment;

public class NewCommentActivity extends AppCompatActivity implements NewCommentFragment.Callbacks {

	public static final String EXTRA_SUBMISSION = "SUBMISSION";
	public static final String EXTRA_REDDIT_ID = "REDDIT_ID";
	public static final String EXTRA_TEXT = "TEXT";

	private Tracker mTracker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_comment);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowHomeEnabled(true);
		}

		mTracker = ((RedditReaderApp)getApplication()).getDefaultTracker();
	}

	@Override
	protected void onResume() {
		super.onResume();

		Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

		if (fragment == null) {
			fragment = NewCommentFragment.create(
					getIntent().getLongExtra(EXTRA_REDDIT_ID, -1),
					getIntent().getBooleanExtra(EXTRA_SUBMISSION, false),
					getIntent().getStringExtra(EXTRA_TEXT));

			getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
		}

		mTracker.setScreenName(getString(R.string.activity_new_comment));
		mTracker.send(new HitBuilders.ScreenViewBuilder().build());
	}

	@Override
	public void onCommentSubmitted() {
		Intent i = new Intent();
		setResult(RESULT_OK, i);
		finish();
	}
}
