package com.michaelva.redditreader.loader;

import android.content.Context;

public class RandomSubmissionLoader extends SubmissionLoader {
	public RandomSubmissionLoader(Context context) {
		super(context);

		setSortOrder("RANDOM() LIMIT 1");
	}

}
