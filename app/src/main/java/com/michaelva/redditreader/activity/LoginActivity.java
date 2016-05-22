package com.michaelva.redditreader.activity;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.ui.AlertDialogFragment;
import com.michaelva.redditreader.ui.LoginFragment;
import com.michaelva.redditreader.R;

import static com.michaelva.redditreader.ui.AlertDialogFragment.AlertType;

public class LoginActivity extends AppCompatActivity implements LoginFragment.Callbacks, AlertDialogFragment.Callbacks {

	private Tracker mTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowHomeEnabled(true);
		}

		Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

		if (fragment == null) {
			doLogin();
		}

		mTracker = ((RedditReaderApp)getApplication()).getDefaultTracker();
    }

	@Override
	protected void onResume() {
		super.onResume();

		mTracker.setScreenName(getString(R.string.activity_login));
		mTracker.send(new HitBuilders.ScreenViewBuilder().build());
	}

	@Override
    public void onUserAuthenticated() {
        finish();
    }

    @Override
    public void onAuthenticationFailed(String error) {
		DialogFragment alert = AlertDialogFragment.create(AlertDialogFragment.ALERT_TYPE_LOGIN, error, getString(R.string.button_try_again));
		alert.show(getSupportFragmentManager(), "alert");
    }

	@Override
	public void onAlertAction(@AlertType int alertType) {
		// no need to check alert type - only login alert could be shown in this activity
		doLogin();
	}

	private void doLogin() {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

		ft.replace(R.id.fragment_container, new LoginFragment()).commit();
	}
}
