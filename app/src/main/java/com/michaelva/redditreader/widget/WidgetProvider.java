package com.michaelva.redditreader.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.michaelva.redditreader.service.WidgetRemoteViewsService;
import com.michaelva.redditreader.util.Consts;

public class WidgetProvider extends AppWidgetProvider {

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		context.startService(new Intent(context, WidgetRemoteViewsService.class));
	}

	@Override
	public void onReceive(@NonNull Context context, @NonNull Intent intent) {
		super.onReceive(context, intent);
		if (Consts.ACTION_SUBMISSIONS_DATA_UPDATED.equals(intent.getAction()) ||
				Consts.ACTION_SUBREDDITS_DATA_UPDATED.equals(intent.getAction())) {
			context.startService(new Intent(context, WidgetRemoteViewsService.class));
		}
	}

}
