package com.michaelva.redditreader.ui;

import android.content.Context;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.data.RedditContract;
import com.michaelva.redditreader.databinding.SubredditsListItemBinding;
import com.michaelva.redditreader.loader.SubredditsLoader;
import com.michaelva.redditreader.service.UtilityService;

public class SubredditsListAdapter extends RecyclerView.Adapter<SubredditsListAdapter.SubredditViewHolder> {

	private Cursor mCursor;

	private final TextView mEmptyText;

	private final View mListContaner;

	private final ContentLoadingProgressBar mProgress;

	private final Context mContext;

	private boolean mShown = true;

	public SubredditsListAdapter(Context ctx, View listContainer, ContentLoadingProgressBar progress) {
		mContext = ctx;
		mListContaner = listContainer;
		mEmptyText = (TextView) listContainer.findViewById(R.id.empty);
		mProgress = progress;

	}

	@Override
	public SubredditViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		SubredditsListItemBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.subreddits_list_item, parent, false);

		return new SubredditViewHolder(binding);
	}

	@Override
	public void onBindViewHolder(SubredditViewHolder holder, int position) {
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
			mListContaner.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
			mProgress.setVisibility(View.GONE);
			mListContaner.setVisibility(View.VISIBLE);
		} else {
			mProgress.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
			mListContaner.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
			mProgress.setVisibility(View.VISIBLE);
			mListContaner.setVisibility(View.GONE);
		}
	}

	public class SubredditViewHolder extends RecyclerView.ViewHolder {

		private final SubredditsListItemBinding mBinding;

		public SubredditViewHolder(SubredditsListItemBinding binding) {
			super(binding.getRoot());

			mBinding = binding;
		}

		public void bindData(Cursor cursor) {
			String name = cursor.getString(SubredditsLoader.COL_NAME);

			mBinding.subredditsListItemName.setText(mContext.getString(R.string.subreddit_name, name));

			mBinding.subredditsListItemDescription.setText(cursor.getString(SubredditsLoader.COL_DESCRIPTION));

			mBinding.subredditsListItemSelected.setContentDescription(mContext.getString(R.string.subreddit_selection_contect_description, name));

			// must remove any previously attached listener; otherwise it may fire when we call setChecked method
			mBinding.subredditsListItemSelected.setOnCheckedChangeListener(null);

			// setting the checkbox state
			boolean currentlySelected = cursor.getInt(SubredditsLoader.COL_SELECTED) != 0;
			boolean scheduledStateChange = cursor.getInt(SubredditsLoader.COL_PENDING_STATE_CHANGE) != 0;

			boolean checked;

			if (scheduledStateChange) {
				checked = !currentlySelected;
			} else {
				checked = currentlySelected;
			}
			mBinding.subredditsListItemSelected.setChecked(checked);

			final Uri subredditUri = RedditContract.SubredditEntry.buildSubredditUri(cursor.getLong(SubredditsLoader.COL_ID));

			// now attaching new listener (after state has been set)
			mBinding.subredditsListItemSelected.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					UtilityService.startActionUpdateSubredditSelectionStatus(mContext, subredditUri, isChecked);
				}
			});
		}
	}
}
