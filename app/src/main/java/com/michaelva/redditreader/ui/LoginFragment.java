package com.michaelva.redditreader.ui;

import android.app.Dialog;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.michaelva.redditreader.BuildConfig;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.databinding.FragmentLoginBinding;
import com.michaelva.redditreader.task.UserAuthenticationTask;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.Utils;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.http.LoggingMode;
import net.dean.jraw.http.oauth.OAuthHelper;

import java.lang.ref.WeakReference;
import java.net.URL;

/**
 * Reddit authentication code is based on
 * https://github.com/thatJavaNerd/JRAW-Android/blob/master/example-app/src/main/java/net/dean/jrawandroidexample/activities/LoginActivity.java
 */
public class LoginFragment extends AppCompatDialogFragment implements LoaderManager.LoaderCallbacks<UserAuthenticationTask.Result> {

	private static final int MSG_SUCCESS = 0;
	private static final int MSG_ERROR = 1;

	private final MyHandler mHandler = new MyHandler(this);

    private FragmentLoginBinding mBinding;

	private Callbacks mHost;

    public interface Callbacks {
        void onUserAuthenticated();
        void onAuthenticationFailed(String error);
    }

    public LoginFragment() {}

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
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		setStyle(STYLE_NO_TITLE, 0);

		return super.onCreateDialog(savedInstanceState);
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_login, container, false);

		mBinding.webview.setWebViewClient(new WebViewClient() {

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				if (url.startsWith(BuildConfig.REDDIT_REDIRECT_URL)) {
					mBinding.progressbar.show();

					Bundle bundle = new Bundle();
					bundle.putString(UserAuthenticationTask.ARG_URL, url);
					getLoaderManager().restartLoader(Consts.USER_AUTHENTICATION_TASK, bundle, LoginFragment.this);

					return true;
				} else {
					return false;
				}
			}

		});

		return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        startRedditAuthentication();
    }

    @Override
    public Loader<UserAuthenticationTask.Result> onCreateLoader(int id, Bundle args) {
        return new UserAuthenticationTask(getContext(), args);
    }

    @Override
    public void onLoaderReset(Loader<UserAuthenticationTask.Result> loader) {
        // no need to do anything
    }

    @Override
    public void onLoadFinished(Loader<UserAuthenticationTask.Result> loader, UserAuthenticationTask.Result result) {
        mBinding.progressbar.hide();

		Utils.setUserAuthenticated(getContext(), result.success);

		Message msg;
        if (result.success) {
			msg = mHandler.obtainMessage(MSG_SUCCESS);
        } else {
			Bundle data = new Bundle();
			data.putString("error", result.error);
			msg = mHandler.obtainMessage(MSG_ERROR);
			msg.setData(data);
        }
		mHandler.sendMessage(msg);
    }

    private void startRedditAuthentication() {

		// clean-up any data related to previous user
		// from the web view
		if (Utils.isUserAuthenticated(getContext())) {
			CookieManager cookieManager = CookieManager.getInstance();
			//noinspection deprecation
			cookieManager.removeAllCookie();
			mBinding.webview.clearHistory();
			mBinding.webview.clearFormData();
		}

		// Create our RedditClient
        OAuthHelper authHelper = AuthenticationManager.get().getRedditClient().getOAuthHelper();

		// OAuth2 scopes to request. See https://www.reddit.com/dev/api/oauth for a full list
        String[] scopes = {"identity", "read", "mysubreddits", "vote", "submit"};

        URL authorizationUrl = authHelper.getAuthorizationUrl(Consts.APP_CREDENTIALS, true, true, scopes);

		// Load the authorization URL into the browser
		mBinding.webview.loadUrl(authorizationUrl.toExternalForm());
    }

	/**
	 * Based on http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
	 */
	private static class MyHandler extends Handler {
		private final WeakReference<LoginFragment> mFragment;

		public MyHandler(LoginFragment fragment) {
			mFragment = new WeakReference<>(fragment);
		}

		@Override
		public void handleMessage(Message msg) {
			LoginFragment fragment = mFragment.get();

			// continue only if we still have reference
			if (fragment == null) {
				return;
			}

			if (msg.what == MSG_SUCCESS) {
				fragment.mHost.onUserAuthenticated();
			} else if (msg.what == MSG_ERROR){
				String error = msg.getData().getString("error");
				fragment.mHost.onAuthenticationFailed(error);
			}
		}
	}
}
