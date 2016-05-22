package com.michaelva.redditreader.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.sync.SubredditsSyncAdapter;

public class SubredditsActivity extends AppCompatActivity {

	private Tracker mTracker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_subreddits);

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

		mTracker.setScreenName(getString(R.string.activity_subreddits_list));
		mTracker.send(new HitBuilders.ScreenViewBuilder().build());
	}
}
