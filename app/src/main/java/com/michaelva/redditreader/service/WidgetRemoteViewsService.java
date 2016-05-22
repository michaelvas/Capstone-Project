package com.michaelva.redditreader.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.text.Html;
import android.view.View;
import android.widget.RemoteViews;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.activity.MainActivity;
import com.michaelva.redditreader.loader.SubmissionLoader;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;
import com.michaelva.redditreader.widget.WidgetProvider;
import com.squareup.picasso.Picasso;

import static com.michaelva.redditreader.data.RedditContract.SubmissionEntry;


public class WidgetRemoteViewsService extends IntentService {

	public WidgetRemoteViewsService() {
		super("WidgetRemoteViewsService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		AppWidgetManager widgetsManager = AppWidgetManager.getInstance(this);

		int[] widgetIds = widgetsManager.getAppWidgetIds(new ComponentName(this, WidgetProvider.class));

		// check whether we have data to show

		String message = null;
		if (!Utils.isUserAuthenticated(this)) {
			message = getString(R.string.message_not_authorized);
		} else if (!Utils.atLeastOneSubredditSelected(this)) {
			message = getString(R.string.message_no_subreddits_selected);
		} else {
			int syncStatus = SyncStatusUtils.getSyncStatus(this, Consts.SUBMISSIONS_SYNC_STATUS_PREF);
			if (syncStatus <= SyncStatusUtils.SYNC_STATUS_IN_PROGRESS || syncStatus > SyncStatusUtils.SYNC_STATUS_OK) {
				message = SyncStatusUtils.getSyncStatusMessage(this, syncStatus);
			}
		}

		// load list of random rows so we have different submission in each widget instance
		Cursor cursor = null;
		if (message == null) {
			String sort = "RANDOM() LIMIT " + widgetIds.length;
			cursor = getContentResolver().query(SubmissionEntry.CONTENT_URI, SubmissionLoader.COLUMNS, null, null, sort);
			if (cursor == null) {
				message = getString(R.string.message_internal_error);
			} else if (!cursor.moveToFirst()) {
				message = getString(R.string.message_no_valid_submissions);
			}
		}

		// go over the widgets and populate them with data (or message)
		for (int widgetId : widgetIds) {
			RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);

			// prepare the application launching intent
			Intent i = new Intent(this, MainActivity.class);

			if (message != null) {
				message = getString(R.string.app_name) + ": " + message;
				views.setTextViewText(R.id.widget_empty, message);
				views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
				views.setViewVisibility(R.id.widget_title, View.GONE);
				views.setViewVisibility(R.id.widget_content_image, View.GONE);
				views.setViewVisibility(R.id.widget_content_text, View.GONE);

			} else {
				views.setViewVisibility(R.id.widget_empty, View.GONE);

				long id = cursor.getLong(SubmissionLoader.COL_ID);
				int type = cursor.getInt(SubmissionLoader.COL_TYPE);
				String title = cursor.getString(SubmissionLoader.COL_TITLE);

				// add submission id to the application launching intent
				i.setData(SubmissionEntry.buildSubmissionUri(id));


				if (type == Consts.SubmissionType.TEXT) {
					String content = cursor.getString(SubmissionLoader.COL_TEXT);

					if (content != null) {
						views.setTextViewText(R.id.widget_title, title);
						views.setViewVisibility(R.id.widget_title, View.VISIBLE);
						views.setTextViewText(R.id.widget_content_text, Html.fromHtml(content));
					} else {
						views.setViewVisibility(R.id.widget_title, View.GONE);
						views.setTextViewText(R.id.widget_content_text, title);
					}

					views.setViewVisibility(R.id.widget_content_text, View.VISIBLE);
					views.setViewVisibility(R.id.widget_content_image, View.GONE);

				} else {
					views.setTextViewText(R.id.widget_title, title);
					views.setViewVisibility(R.id.widget_title, View.VISIBLE);
					views.setViewVisibility(R.id.widget_content_text, View.GONE);
					views.setViewVisibility(R.id.widget_content_image, View.VISIBLE);

					String imageUrl = (type == Consts.SubmissionType.IMAGE) ?
							cursor.getString(SubmissionLoader.COL_URL) :
							cursor.getString(SubmissionLoader.COL_PREVIEW_IMAGE);

					if (imageUrl != null) {
						try {
							Bitmap bitmap = Picasso.with(this)
									.load(imageUrl)
									.resizeDimen(R.dimen.widget_default_width, R.dimen.widget_default_height)
									.centerCrop()
									.get();
							views.setImageViewBitmap(R.id.widget_content_image, bitmap);
						} catch (Exception e) {
							views.setImageViewResource(R.id.widget_content_image, R.drawable.ic_broken_image);
						}
						views.setContentDescription(R.id.widget_content_image, title);
					} else {
						views.setImageViewResource(R.id.widget_content_image, R.drawable.ic_web);
						views.setContentDescription(R.id.widget_content_image, getString(R.string.weblink_submission_contect_description));
					}
				}

				// only moving the cursor if we didn't reach last row
				// in case there are more widgets than rows in the cursor
				// the last row will be used for remaining widgets
				if (!cursor.isLast()) {
					cursor.moveToNext();
				}
			}

			// set the application launching intent
			PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
			views.setOnClickPendingIntent(R.id.widget, pi);

			widgetsManager.updateAppWidget(widgetId, views);
		}

		if (cursor != null) {
			cursor.close();
		}
	}

}
