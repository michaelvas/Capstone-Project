package com.michaelva.redditreader.ui;

import android.database.Cursor;
import android.support.v4.app.Fragment;

public interface SubmissionsPresenter {
	void setSubmissionsData(Cursor data);
	void clearSubmissionsData();
}
