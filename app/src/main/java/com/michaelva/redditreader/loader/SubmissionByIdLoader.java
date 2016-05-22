package com.michaelva.redditreader.loader;

import android.content.Context;

import static com.michaelva.redditreader.data.RedditContract.SubmissionEntry;

public class SubmissionByIdLoader extends SubmissionLoader {

	public SubmissionByIdLoader(Context context, long submissionId) {
		super(context);

		setUri(SubmissionEntry.buildSubmissionUri(submissionId));

	}

}
