package com.michaelva.redditreader.ui;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatDialogFragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.michaelva.redditreader.R;
import com.michaelva.redditreader.RedditReaderApp;
import com.michaelva.redditreader.databinding.FragmentNewCommentBinding;
import com.michaelva.redditreader.service.UtilityService;
import com.michaelva.redditreader.util.Consts;

import java.util.Map;

public class NewCommentFragment extends AppCompatDialogFragment {

	public static final String FRAGMENT_TAG = NewCommentFragment.class.getSimpleName();

	private static final String ARG_SUBMISSION = "SUBMISSION";
	private static final String ARG_REPLY_TO_REDDIT_ID = "REPLY_TO_REDDIT_ID";
	private static final String ARG_REPLY_TO_TEXT = "REPLY_TO_TEXT";
	private static final String REPLY_SUBMITTED = "REPLY_SUBMITTED";

	private boolean mSubmission;

	private long mRedditId;

	private String mReplyTo;

	private boolean mReplySubmitted;

	FragmentNewCommentBinding mBinding;

	private Callbacks mHost;

	BroadcastReceiver mReceiver;

	private boolean mDialogMode = false;

	Tracker mTracker;

	public interface Callbacks {
		void onCommentSubmitted();
	}

	public NewCommentFragment() {
		// required public constructor
	}

	public static NewCommentFragment create(long redditId, boolean submission, String text) {
		NewCommentFragment fragment = new NewCommentFragment();
		Bundle args = new Bundle();
		args.putBoolean(ARG_SUBMISSION, submission);
		args.putLong(ARG_REPLY_TO_REDDIT_ID, redditId);
		args.putString(ARG_REPLY_TO_TEXT, text);
		fragment.setArguments(args);
		return fragment;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setStyle(STYLE_NO_TITLE, 0);

		mDialogMode = true;

		return super.onCreateDialog(savedInstanceState);

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
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(ARG_SUBMISSION, mSubmission);
		outState.putLong(ARG_REPLY_TO_REDDIT_ID, mRedditId);
		outState.putString(ARG_REPLY_TO_TEXT, mReplyTo);
		outState.putBoolean(REPLY_SUBMITTED, mReplySubmitted);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (savedInstanceState != null) {
			mSubmission = savedInstanceState.getBoolean(ARG_SUBMISSION);
			mRedditId = savedInstanceState.getLong(ARG_REPLY_TO_REDDIT_ID);
			mReplyTo = savedInstanceState.getString(ARG_REPLY_TO_TEXT);
			mReplySubmitted = savedInstanceState.getBoolean(REPLY_SUBMITTED);
		} else {
			mSubmission = getArguments().getBoolean(ARG_SUBMISSION);
			mRedditId = getArguments().getLong(ARG_REPLY_TO_REDDIT_ID);
			mReplyTo = getArguments().getString(ARG_REPLY_TO_TEXT);
		}

		mBinding.newCommentReplyTo.setText(Html.fromHtml(mReplyTo));
		mBinding.newCommentReplyTo.setMovementMethod(LinkMovementMethod.getInstance());

		mBinding.newCommentSubmit.setOnClickListener(new SubmitClickListener());

		if (mReplySubmitted) {

			mBinding.newComment.setVisibility(View.GONE);

			mBinding.progressbar.setVisibility(View.VISIBLE);

		}

		if (mDialogMode) {
			mBinding.adView.setVisibility(View.GONE);
		} else {
			AdRequest adRequest = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();

			mBinding.adView.loadAd(adRequest);
		}

		mTracker = ((RedditReaderApp) getActivity().getApplication()).getDefaultTracker();
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_new_comment, container, false);
		return mBinding.getRoot();
	}

	@Override
	public void onResume() {
		super.onResume();

		mReceiver = new CommentActionsReceiver();
		IntentFilter iff = new IntentFilter(UtilityService.ACTION_SUBMIT_REPLY);
		LocalBroadcastManager.getInstance(getContext()).registerReceiver(mReceiver, iff);
	}

	@Override
	public void onPause() {
		super.onPause();
		LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mReceiver);
	}

	class SubmitClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			mBinding.newComment.setVisibility(View.GONE);

			mBinding.progressbar.setVisibility(View.VISIBLE);

			mReplySubmitted = true;

			UtilityService.startActionSubmitReply(
					getContext(),
					mRedditId,
					mBinding.newCommentReply.getText().toString(),
					mSubmission);
		}
	}

	class CommentActionsReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Map<String, String> event;

			if (!intent.getBooleanExtra(UtilityService.EXTRA_RESULT_STATUS_SUCCESS, false)) {
				mBinding.newComment.setVisibility(View.VISIBLE);
				mBinding.progressbar.setVisibility(View.GONE);
				mReplySubmitted = false;
				String message = intent.getStringExtra(UtilityService.EXTRA_RESULT_MESSAGE);
				Snackbar.make(mBinding.getRoot(), message, Snackbar.LENGTH_INDEFINITE).show();

				event = new HitBuilders.ExceptionBuilder().setDescription(message).build();
			} else {
				if (mHost != null) {
					mHost.onCommentSubmitted();
				}

				event = new HitBuilders.EventBuilder()
						.setCategory(Consts.GA_CATEGORY_ACTION)
						.setAction(mSubmission ? Consts.GA_ACTION_NEW_COMMENT : Consts.GA_ACTION_REPLY_COMMENT)
						.build();
			}

			mTracker.send(event);
		}
	}
}
