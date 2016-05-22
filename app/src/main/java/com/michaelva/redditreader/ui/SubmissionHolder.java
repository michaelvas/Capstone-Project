package com.michaelva.redditreader.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.databinding.SubmissionItemBinding;
import com.michaelva.redditreader.loader.SubmissionLoader;
import com.michaelva.redditreader.task.GetSubmissionLiveDataTask;
import com.michaelva.redditreader.util.Consts;
import com.squareup.picasso.Picasso;

public class SubmissionHolder {
	private SubmissionItemBinding mBinding;

	private int mCurrentVote = 0;

	private Context mContext;

	private ActionCallbacks mListener;

	private int mRegularColor;

	private int mHighlightedColor;

	public interface ActionCallbacks {
		void dismissSubmission();
		void refreshSubmission();
		void upvoteSubmission();
		void downvoteSubmission();
		void unvoteSubmission();
		void viewSubmissionComments();
	}

	public SubmissionHolder(@NonNull Context ctx, @NonNull SubmissionItemBinding binding) {
		mBinding = binding;

		mContext = ctx;

		mRegularColor = ContextCompat.getColor(ctx, R.color.greyDark);

		mHighlightedColor = ContextCompat.getColor(ctx, R.color.colorPrimary);

		mBinding.toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				if (mListener == null) {
					return true;
				}

				switch (item.getItemId()) {
					case R.id.action_refresh:
						mListener.refreshSubmission();
						break;
					default: // R.id.action_dismiss
						mListener.dismissSubmission();
				}
				return true;
			}
		});

		mBinding.thumbsDownButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mListener == null) {
					return;
				}

				if (mCurrentVote < 0) {
					mListener.unvoteSubmission();
				} else {
					mListener.downvoteSubmission();
				}
			}
		});

		mBinding.thumbsUpButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mListener == null) {
					return;
				}

				if (mCurrentVote > 0) {
					mListener.unvoteSubmission();
				} else {
					mListener.upvoteSubmission();
				}
			}
		});

		mBinding.commentsButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mListener.viewSubmissionComments();
			}
		});
	}

	public void setListener(ActionCallbacks listener, boolean allowDismiss) {
		mListener = listener;

		MenuItem dismissMenuItem = mBinding.toolbar.getMenu().findItem(R.id.action_dismiss);

		dismissMenuItem.setVisible(allowDismiss);
	}

	public View getView() {
		return mBinding.getRoot();
	}

	public void applyData(@NonNull Cursor cursor) {
		int type = cursor.getInt(SubmissionLoader.COL_TYPE);

		if (type == Consts.SubmissionType.TEXT) {
			String content = cursor.getString(SubmissionLoader.COL_TEXT);

			if (content != null) {
				mBinding.title.setText(cursor.getString(SubmissionLoader.COL_TITLE));
				mBinding.title.setVisibility(View.VISIBLE);
				mBinding.contentText.setText(Html.fromHtml(content));
				mBinding.contentText.setMovementMethod(LinkMovementMethod.getInstance());
			} else {
				mBinding.title.setVisibility(View.INVISIBLE);
				mBinding.contentText.setText(cursor.getString(SubmissionLoader.COL_TITLE));
			}

			mBinding.contentPreview.setVisibility(View.GONE);
			mBinding.contentImage.setVisibility(View.GONE);
			mBinding.contentTextContainer.setVisibility(View.VISIBLE);

		} else if (type == Consts.SubmissionType.WEB) {
			mBinding.title.setText(cursor.getString(SubmissionLoader.COL_TITLE));
			mBinding.title.setVisibility(View.VISIBLE);

			// if preview image url is null (submission has no preview), picasso will present the placeholder
			Picasso.with(mContext)
				.load(cursor.getString(SubmissionLoader.COL_PREVIEW_IMAGE))
				.placeholder(R.drawable.ic_web)
				.error(R.drawable.ic_broken_image)
				.tag(cursor.getString(SubmissionLoader.COL_ID))
				.into(mBinding.contentPreview);

			mBinding.contentTextContainer.setVisibility(View.GONE);
			mBinding.contentImage.setVisibility(View.GONE);
			mBinding.contentPreview.setVisibility(View.VISIBLE);

		} else if (type == Consts.SubmissionType.IMAGE) {
			mBinding.title.setText(cursor.getString(SubmissionLoader.COL_TITLE));
			mBinding.title.setVisibility(View.VISIBLE);

			Picasso.with(mContext)
				.load(cursor.getString(SubmissionLoader.COL_URL))
				.placeholder(R.drawable.ic_web)
				.error(R.drawable.ic_broken_image)
				.tag(cursor.getString(SubmissionLoader.COL_ID))
				.into(mBinding.contentImage);

			mBinding.contentTextContainer.setVisibility(View.GONE);
			mBinding.contentPreview.setVisibility(View.GONE);
			mBinding.contentImage.setVisibility(View.VISIBLE);

		} else { // type == Consts.SubmissionType.VIDEO
			mBinding.title.setText(cursor.getString(SubmissionLoader.COL_TITLE));
			mBinding.title.setVisibility(View.VISIBLE);

			Picasso.with(mContext)
					.load(cursor.getString(SubmissionLoader.COL_PREVIEW_IMAGE))
					.placeholder(R.drawable.ic_web)
					.error(R.drawable.ic_broken_image)
					.tag(cursor.getString(SubmissionLoader.COL_ID))
					.into(mBinding.contentImage);

			mBinding.contentTextContainer.setVisibility(View.GONE);
			mBinding.contentPreview.setVisibility(View.GONE);
			mBinding.contentImage.setVisibility(View.VISIBLE);
		}

		// hide live data - will be shown when we load it
		mBinding.liveData.setVisibility(View.INVISIBLE);

	}

	public void applyLiveData(@NonNull GetSubmissionLiveDataTask.Result data) {
		mBinding.comments.setText(String.valueOf(data.commentsCount));

		if (data.hideScore) {
			mBinding.score.setVisibility(View.GONE);
		} else {
			mBinding.score.setText(String.valueOf(data.score));
			int scoreColor = mRegularColor;
			if (data.userVote != 0) {
				scoreColor = mHighlightedColor;
			}
			mBinding.score.setTextColor(scoreColor);
			mBinding.score.setVisibility(View.VISIBLE);
		}

		mCurrentVote = data.userVote;

		if (data.readOnly) {
			mBinding.thumbsDownButton.setVisibility(View.GONE);
			mBinding.thumbsUpButton.setVisibility(View.GONE);
		} else {
			Drawable thumbsUp;
			Drawable thumbsDown;
			String thumbsUpContentDescription;
			String thumbsDownContentDescription;

			if (data.userVote > 0) {
				thumbsUp = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_up_highlighted);
				thumbsDown = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_down);
				thumbsUpContentDescription = mContext.getString(R.string.submission_undo_upvote_contect_description);
				thumbsDownContentDescription = mContext.getString(R.string.submission_downvote_contect_description);
			} else if (data.userVote < 0) {
				thumbsUp = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_up);
				thumbsDown = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_down_highlighted);
				thumbsUpContentDescription = mContext.getString(R.string.submission_upvote_contect_description);
				thumbsDownContentDescription = mContext.getString(R.string.submission_undo_downvote_contect_description);
			} else {
				thumbsDown = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_down);
				thumbsUp = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_up);
				thumbsUpContentDescription = mContext.getString(R.string.submission_upvote_contect_description);
				thumbsDownContentDescription = mContext.getString(R.string.submission_downvote_contect_description);
			}

			mBinding.thumbsUpButton.setContentDescription(thumbsUpContentDescription);
			mBinding.thumbsDownButton.setContentDescription(thumbsDownContentDescription);

			mBinding.thumbsUpButton.setImageDrawable(thumbsUp);
			mBinding.thumbsDownButton.setImageDrawable(thumbsDown);

			mBinding.thumbsDownButton.setVisibility(View.VISIBLE);
			mBinding.thumbsUpButton.setVisibility(View.VISIBLE);
		}

		mBinding.liveData.setVisibility(View.VISIBLE);
	}

	public void cancelImageDownloads(@NonNull Context ctx, @NonNull Cursor cursor) {
		Picasso.with(ctx).cancelTag(cursor.getString(SubmissionLoader.COL_ID));
	}

}
