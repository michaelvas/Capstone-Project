package com.michaelva.redditreader.ui;

import android.content.Context;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.databinding.SubmissionGridItemBinding;
import com.michaelva.redditreader.loader.SubmissionLoader;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.Utils;
import com.squareup.picasso.Picasso;

public class SubmissionsGridAdapter extends RecyclerView.Adapter<SubmissionsGridAdapter.SubmissionViewHolder> {

	private Cursor mCursor;

	private final TextView mEmptyText;

	private final View mGridContaner;

	private final ContentLoadingProgressBar mProgress;

	private final Context mContext;

	private boolean mShown = true;

	private final ActionCallbacks mListener;

	public interface ActionCallbacks {
		void onSubmissionSelected(long submissionId);
	}

	public SubmissionsGridAdapter(@NonNull Context ctx, @NonNull View gridContainer, @NonNull ContentLoadingProgressBar progress, @NonNull ActionCallbacks listener) {
		mContext = ctx;
		mGridContaner = gridContainer;
		mEmptyText = (TextView) gridContainer.findViewById(R.id.empty);
		mProgress = progress;
		mListener = listener;
	}

	@Override
	public SubmissionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		SubmissionGridItemBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.submission_grid_item, parent, false);

		return new SubmissionViewHolder(binding);
	}

	@Override
	public void onBindViewHolder(SubmissionViewHolder holder, int position) {
		mCursor.moveToPosition(position);

		holder.bindData(mCursor);
	}

	@Override
	public int getItemCount() {
		return mCursor != null ? mCursor.getCount() : 0;
	}

	public void swapCursor(Cursor newCursor) {
		mCursor = newCursor;
		notifyDataSetChanged();
		mEmptyText.setVisibility(getItemCount() == 0 ? View.VISIBLE : View.GONE);
	}

	public void setEmptyText(String text) {
		mEmptyText.setText(text);
	}

	public void setListShown(boolean shown) {
		if (mShown == shown) {
			return;
		}

		mShown = shown;

		if (shown) {
			mProgress.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
			mGridContaner.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
			mProgress.setVisibility(View.GONE);
			mGridContaner.setVisibility(View.VISIBLE);
		} else {
			mProgress.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
			mGridContaner.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
			mProgress.setVisibility(View.VISIBLE);
			mGridContaner.setVisibility(View.GONE);
		}
	}

	public class SubmissionViewHolder extends RecyclerView.ViewHolder {

		private final SubmissionGridItemBinding mBinding;

		public SubmissionViewHolder(SubmissionGridItemBinding binding) {
			super(binding.getRoot());

			mBinding = binding;
		}

		public void bindData(Cursor cursor) {
			long id = cursor.getLong(SubmissionLoader.COL_ID);
			int type = cursor.getInt(SubmissionLoader.COL_TYPE);
			String url = cursor.getString(SubmissionLoader.COL_URL);

			mBinding.getRoot().setOnClickListener(new SubmissionClickListener(id));

			mBinding.openExternalButton.setOnClickListener(new LaunchButtonClickListener(mContext, type, url));

			if (type == Consts.SubmissionType.TEXT) {
				String content = cursor.getString(SubmissionLoader.COL_TEXT);

				if (content != null) {
					mBinding.title.setText(cursor.getString(SubmissionLoader.COL_TITLE));
					mBinding.title.setVisibility(View.VISIBLE);
					mBinding.contentText.setText(Html.fromHtml(content));
				} else {
					mBinding.title.setVisibility(View.INVISIBLE);
					mBinding.contentText.setText(cursor.getString(SubmissionLoader.COL_TITLE));
				}

				mBinding.contentPreview.setVisibility(View.GONE);
				mBinding.contentImage.setVisibility(View.GONE);
				mBinding.contentText.setVisibility(View.VISIBLE);

			} else if (type == Consts.SubmissionType.WEB) {
				mBinding.title.setText(cursor.getString(SubmissionLoader.COL_TITLE));
				mBinding.title.setVisibility(View.VISIBLE);

				// if preview image url is null (submission has no preview), picasso will present the placeholder
				Picasso.with(mContext)
						.load(cursor.getString(SubmissionLoader.COL_PREVIEW_IMAGE))
						.placeholder(R.drawable.ic_web)
						.error(R.drawable.ic_broken_image)
						.tag(String.valueOf(id))
						.into(mBinding.contentPreview);

				mBinding.contentText.setVisibility(View.GONE);
				mBinding.contentImage.setVisibility(View.GONE);
				mBinding.contentPreview.setVisibility(View.VISIBLE);

				if (cursor.getString(SubmissionLoader.COL_PREVIEW_IMAGE) == null) {
					mBinding.contentPreview.setContentDescription(mContext.getString(R.string.weblink_submission_contect_description));
				} else {
					mBinding.contentPreview.setContentDescription(cursor.getString(SubmissionLoader.COL_TITLE));
				}

			} else { // type == Consts.SubmissionType.IMAGE || type == Consts.SubmissionType.VIDEO
				mBinding.title.setText(cursor.getString(SubmissionLoader.COL_TITLE));
				mBinding.title.setVisibility(View.VISIBLE);

				String imageUrl = (type == Consts.SubmissionType.IMAGE) ?
						cursor.getString(SubmissionLoader.COL_URL) :
						cursor.getString(SubmissionLoader.COL_PREVIEW_IMAGE);

				Picasso.with(mContext)
						.load(imageUrl)
						.placeholder(R.drawable.ic_web)
						.error(R.drawable.ic_broken_image)
						.tag(String.valueOf(id))
						.into(mBinding.contentImage);

				mBinding.contentText.setVisibility(View.GONE);
				mBinding.contentPreview.setVisibility(View.GONE);
				mBinding.contentImage.setVisibility(View.VISIBLE);

				mBinding.contentPreview.setContentDescription(cursor.getString(SubmissionLoader.COL_TITLE));
			}
		}
	}

	class SubmissionClickListener implements View.OnClickListener {
		final long submissionId;

		SubmissionClickListener(long submissionId) {
			this.submissionId = submissionId;
		}

		@Override
		public void onClick(View v) {
			mListener.onSubmissionSelected(submissionId);
		}
	}

	class LaunchButtonClickListener implements View.OnClickListener {
		final Context ctx;
		final int type;
		final String url;


		LaunchButtonClickListener(Context ctx, int type, String url) {
			this.ctx = ctx;
			this.type = type;
			this.url = url;
		}

		@Override
		public void onClick(View v) {
			Utils.launchExternalViewer(ctx, type, url);
		}
	}
}
