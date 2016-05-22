package com.michaelva.redditreader.loader;

import android.content.Context;

public class AllSubmissionsInRandomOrderLoader extends SubmissionLoader {
	public AllSubmissionsInRandomOrderLoader(Context context) {
		super(context);

		setSortOrder("RANDOM()");
	}

}
