package com.michaelva.redditreader.ui;

import android.content.Context;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.michaelva.redditreader.R;
import com.michaelva.redditreader.databinding.CommentsListItemBinding;
import com.michaelva.redditreader.loader.CommentsLoader;
import com.michaelva.redditreader.util.Consts;
import com.michaelva.redditreader.util.SyncStatusUtils;
import com.michaelva.redditreader.util.Utils;

import static com.michaelva.redditreader.util.Consts.CommentType;

public class CommentsListAdapter extends RecyclerView.Adapter<CommentsListAdapter.CommentViewHolder> {

	private Cursor mCursor;

	private final TextView mTitle;

	private final TextView mEmptyText;

	private final View mListContaner;

	private final ProgressBar mProgress;

	private final Context mContext;

	private boolean mShown = false;

	private int mHighlightedColor;

	private final ActionCallbacks mListener;

	private boolean mTabletMode;

	private Animation mRotateAnimation;

	private Handler mHandler = new Handler();

	public interface ActionCallbacks {

		void onCommentExpandCollapse(long commentId);

		void onCommentUpvote(long commentId);

		void onCommentDownvote(long commentId);

		void onCommentUnvote(long commentId);

		void onCommentReply(long commentId, String commentText);

		void onCommentLoadMore(long commentId);
	}

	public CommentsListAdapter(@NonNull Context ctx, @NonNull View listContainer, @NonNull ProgressBar progress, @NonNull TextView title, @NonNull ActionCallbacks listener) {
		mContext = ctx;
		mListContaner = listContainer;
		mEmptyText = (TextView) listContainer.findViewById(R.id.empty);
		mTitle = title;
		mProgress = progress;
		mListener = listener;
		mHighlightedColor = ContextCompat.getColor(mContext, R.color.colorPrimary);
		mTabletMode = mContext.getResources().getBoolean(R.bool.tablet_mode);
		mRotateAnimation = AnimationUtils.loadAnimation(ctx, R.anim.rotate);
	}

	@Override
	public CommentViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		CommentsListItemBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.comments_list_item, parent, false);

		binding.commentToolbar.inflateMenu(R.menu.comment);

		return new CommentViewHolder(binding);
	}

	@Override
	public void onBindViewHolder(CommentViewHolder holder, int position) {
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
		if (SyncStatusUtils.getSyncStatus(mContext, Consts.COMMENTS_TASK_STATUS_REF) == SyncStatusUtils.SYNC_STATUS_OK) {
			mEmptyText.setText(mContext.getString(R.string.message_no_comments));
		}
		mEmptyText.setVisibility(getItemCount() == 0 ? View.VISIBLE : View.GONE);
	}

	public void setEmptyText(String text) {
		mEmptyText.setText(text);
	}

	public void setListShown(boolean shown) {
		setListShown(shown, false);
	}

	public void setListShown(boolean shown, boolean force) {
		if (mShown == shown && !force) {
			return;
		}

		mShown = shown;

		if (shown) {
			if (force) {
				mProgress.clearAnimation();
				mListContaner.clearAnimation();
				mTitle.clearAnimation();
			} else {
				mProgress.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
				mListContaner.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
				mTitle.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
			}
			mProgress.setVisibility(View.GONE);
			mListContaner.setVisibility(View.VISIBLE);
			mTitle.setVisibility(View.VISIBLE);
		} else {
			if (force) {
				mProgress.clearAnimation();
				mListContaner.clearAnimation();
				mTitle.clearAnimation();
			} else {
				mProgress.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
				mListContaner.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
				mTitle.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
			}

			mProgress.setVisibility(View.VISIBLE);
			mListContaner.setVisibility(View.GONE);
			mTitle.setVisibility(View.GONE);
		}
	}

	public class CommentViewHolder extends RecyclerView.ViewHolder {

		private final CommentsListItemBinding mBinding;

		public CommentViewHolder(CommentsListItemBinding binding) {
			super(binding.getRoot());

			mBinding = binding;
		}

		public void bindData(Cursor cursor) {
			long commentId = cursor.getLong(CommentsLoader.COL_ID);
			int userVote = cursor.getInt(CommentsLoader.COL_VOTE);
			int type = cursor.getInt(CommentsLoader.COL_TYPE);
			String commentText = cursor.getString(CommentsLoader.COL_TEXT);
			boolean readOnly = cursor.getInt(CommentsLoader.COL_READ_ONLY) == 1;

			// resize the spacerers based on comment's depth

			int depth = cursor.getInt(CommentsLoader.COL_DEPTH);
			// limit depth on phones
			if (depth > 10 && !mTabletMode) {
				depth = 10;
			}

			//add 16dp padding at each level for comment text spacer
			RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mBinding.commentTextSpacer.getLayoutParams();
			lp.width = Utils.dpToPixel(mContext, depth * 16);
			mBinding.commentTextSpacer.setLayoutParams(lp);

			// toolbar spacer is 16dp less to adjust for the expand/collapse button
			lp = (RelativeLayout.LayoutParams) mBinding.commentToolbarSpacer.getLayoutParams();
			lp.width = Utils.dpToPixel(mContext, depth * 16 - 16);
			mBinding.commentToolbarSpacer.setLayoutParams(lp);


			//comment title in the "<author> - <score>" format

			String author = cursor.getString(CommentsLoader.COL_AUTHOR);
			String score = String.valueOf(cursor.getInt(CommentsLoader.COL_SCORE));

			String authorAndScore = mContext.getString(R.string.comment_author_and_score, author, score);

			if (userVote != 0) { // if user voted, highlight the score
				int scoreStart = authorAndScore.lastIndexOf(score);
				mBinding.commentAuthorAndScore.setText(authorAndScore, TextView.BufferType.SPANNABLE);
				Spannable sp = (Spannable) mBinding.commentAuthorAndScore.getText();
				sp.setSpan(new ForegroundColorSpan(mHighlightedColor), scoreStart, scoreStart + score.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else {
				mBinding.commentAuthorAndScore.setText(authorAndScore);
			}

			// comment body
			mBinding.commentText.setText(Html.fromHtml(commentText));
			mBinding.commentText.setMovementMethod(LinkMovementMethod.getInstance());


			// adjust presentation based on comment's type

			switch (type) {
				case CommentType.WITH_REPLIES_EXPANDED:
				case CommentType.NO_REPLIES_EXPANDED:
					mBinding.commentExpandButton.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_expand));
					mBinding.commentExpandButton.setOnClickListener(new ButtonClickListener(commentId, type, null));
					mBinding.commentExpandButton.setEnabled(true);
					mBinding.commentText.setVisibility(View.VISIBLE);
					mBinding.progressbar.setVisibility(View.GONE);
					break;
				case CommentType.WITH_REPLIES_COLLAPSED:
				case CommentType.NO_REPLIES_COLLAPSED:
					mBinding.commentExpandButton.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_collapse));
					mBinding.commentExpandButton.setOnClickListener(new ButtonClickListener(commentId, type, null));
					mBinding.commentExpandButton.setEnabled(true);
					mBinding.commentText.setVisibility(View.GONE);
					mBinding.progressbar.setVisibility(View.GONE);
					break;
				default: // CommentType.MORE_COMMENTS
					mBinding.commentText.setVisibility(View.GONE);
					mBinding.commentAuthorAndScore.setText(commentText);

					boolean loading = cursor.getInt(CommentsLoader.COL_LOADING) == 1;
					if (loading) {
						mBinding.commentExpandButton.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_expand));
						mBinding.commentExpandButton.setEnabled(false);
						mBinding.progressbar.setVisibility(View.VISIBLE);
					} else {
						mBinding.commentExpandButton.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_collapse));
						mBinding.commentExpandButton.setOnClickListener(new ButtonClickListener(commentId, type, mBinding.progressbar));
						mBinding.commentExpandButton.setEnabled(true);
						mBinding.progressbar.setVisibility(View.GONE);
					}
			}

			// set appropriate content description on the  expand/collapse button
			switch (type) {
				case CommentType.WITH_REPLIES_EXPANDED:
					mBinding.commentExpandButton.setContentDescription(mContext.getString(R.string.collapse_comments_tree_contect_description));
					break;
				case CommentType.NO_REPLIES_EXPANDED:
					mBinding.commentExpandButton.setContentDescription(mContext.getString(R.string.collapse_comment_contect_description));
					break;
				case CommentType.WITH_REPLIES_COLLAPSED:
					mBinding.commentExpandButton.setContentDescription(mContext.getString(R.string.expand_comments_tree_contect_description));
					break;
				case CommentType.NO_REPLIES_COLLAPSED:
					mBinding.commentExpandButton.setContentDescription(mContext.getString(R.string.expand_comment_contect_description));
					break;
				default: // CommentType.MORE_COMMENTS
					mBinding.commentExpandButton.setContentDescription(mContext.getString(R.string.load_more_comments_contect_description));
			}

			// actions
			MenuItem replyAction = mBinding.commentToolbar.getMenu().findItem(R.id.action_reply);
			MenuItem upvoteAction = mBinding.commentToolbar.getMenu().findItem(R.id.action_upvote);
			MenuItem downvoteAction = mBinding.commentToolbar.getMenu().findItem(R.id.action_downvote);

			if (readOnly || type == CommentType.MORE_COMMENTS || type == CommentType.WITH_REPLIES_COLLAPSED || type == CommentType.NO_REPLIES_COLLAPSED) {
				// hide actions

				replyAction.setVisible(false);
				upvoteAction.setVisible(false);
				downvoteAction.setVisible(false);
				mBinding.commentToolbar.setOnMenuItemClickListener(null);
			} else {

				replyAction.setVisible(true);
				upvoteAction.setVisible(true);
				downvoteAction.setVisible(true);

				// set actions' titles/icons based on current user's vote

				Drawable thumbsUp = null;
				Drawable thumbsDown = null;
				String upvoteTitle = null;
				String downvoteTitle = null;

				if (userVote > 0) {
					thumbsUp = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_up_highlighted);
					thumbsDown = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_down);
					upvoteTitle = mContext.getString(R.string.action_undo_upvote_comment);
				} else if (userVote < 0) {
					thumbsUp = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_up);
					thumbsDown = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_down_highlighted);
					downvoteTitle = mContext.getString(R.string.action_undo_downvote_comment);
				} else {
					thumbsUp = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_up);
					thumbsDown = ContextCompat.getDrawable(mContext, R.drawable.ic_thumb_down);
					upvoteTitle = mContext.getString(R.string.action_upvote_comment);
					downvoteTitle = mContext.getString(R.string.action_downvote_comment);
				}

				upvoteAction.setIcon(thumbsUp);
				upvoteAction.setTitle(upvoteTitle);

				downvoteAction.setIcon(thumbsDown);
				downvoteAction.setTitle(downvoteTitle);

				mBinding.commentToolbar.setOnMenuItemClickListener(new MenuItemClickListener(commentId, userVote, commentText));
			}

		}
	}

	class ButtonClickListener implements View.OnClickListener {
		final long commentId;
		final int type;
		final View progress;

		ButtonClickListener(long commentId, int type, View progress) {
			this.commentId = commentId;
			this.type = type;
			this.progress = progress;
		}

		@Override
		public void onClick(View v) {

			v.startAnimation(mRotateAnimation);

			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (type == CommentType.MORE_COMMENTS) {
						mListener.onCommentLoadMore(commentId);
					} else {
						mListener.onCommentExpandCollapse(commentId);
					}
				}
			}, 200); // animation is 300 milsecs long; waiting until it is almost complete
		}
	}

	class MenuItemClickListener implements Toolbar.OnMenuItemClickListener {
		final long commentId;
		final int userVote;
		final String commentText;

		MenuItemClickListener(long commentId, int userVote, String commentText) {
			this.commentId = commentId;
			this.userVote = userVote;
			this.commentText = commentText;
		}

		@Override
		public boolean onMenuItemClick(MenuItem item) {
			switch (item.getItemId()) {
				case R.id.action_upvote:
					if (userVote > 0) {
						mListener.onCommentUnvote(commentId);
					} else {
						mListener.onCommentUpvote(commentId);
					}
					return true;
				case R.id.action_downvote:
					if (userVote < 0) {
						mListener.onCommentUnvote(commentId);
					} else {
						mListener.onCommentDownvote(commentId);
					}
					return true;
				case R.id.action_reply:
					mListener.onCommentReply(commentId, commentText);
					return true;
				default:
					return false;
			}
		}
	}
}
