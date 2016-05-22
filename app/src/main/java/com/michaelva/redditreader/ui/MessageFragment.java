package com.michaelva.redditreader.ui;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.michaelva.redditreader.R;

public class MessageFragment extends Fragment {

	private static final String ARG_MESSAGE = "message";

	public static MessageFragment create(String message) {
		MessageFragment fragment = new MessageFragment();
		Bundle args = new Bundle();
		args.putString(ARG_MESSAGE, message);
		fragment.setArguments(args);
		return fragment;
	}

	public MessageFragment() {
		// Required empty public constructor
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		TextView textView = (TextView) inflater.inflate(R.layout.fragment_message, container, false);

		textView.setText(getArguments().getString(ARG_MESSAGE));

		return textView;
	}

}
