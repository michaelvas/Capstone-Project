package com.michaelva.redditreader.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AlertDialogFragment extends DialogFragment {

	private static final String TYPE = "TYPE";
	private static final String MESSAGE = "MESSAGE";
	private static final String BUTTON = "BUTTON";

	@IntDef({ALERT_TYPE_LOGIN, ALERT_TYPE_SUBREDDITS})
	@Retention(RetentionPolicy.SOURCE)
	public @interface AlertType {
	}

	public static final int ALERT_TYPE_LOGIN = 0;
	public static final int ALERT_TYPE_SUBREDDITS = 1;

	private Callbacks mListener;

	private int mAlertType;

	public interface Callbacks {
		void onAlertAction(@AlertType int alertType);
	}

	public static AlertDialogFragment create(@AlertType int alertType, String message, String button) {
		AlertDialogFragment fragment = new AlertDialogFragment();

		Bundle args = new Bundle();
		args.putString(MESSAGE, message);
		args.putString(BUTTON, button);
		args.putInt(TYPE, alertType);
		fragment.setArguments(args);

		return fragment;
	}

	@AlertType
	public int getAlertType() {
		return mAlertType;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			mListener = (Callbacks) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement " + Callbacks.class.getCanonicalName());
		}

		mAlertType = getArguments().getInt(TYPE);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Dialog dialog = new AlertDialog.Builder(getActivity())
				.setMessage(getArguments().getString(MESSAGE))
				.setPositiveButton(getArguments().getString(BUTTON), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						mListener.onAlertAction(mAlertType);
						dismiss();
					}
				})
				.setCancelable(false)
				.create();
		return dialog;
	}
}
