package com.blackmoonit.android.FileBrowser;

import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.blackmoonit.filesystem.ProgressBarHandler;

/**
 * Progress item used per batch operation with ties to FileBrowser's UI
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class ProgressFeedbackItemWidget extends ProgressFeedbackItem {
	public final View mItem;
	public final TextView mTextViewProgressAction;
	public final TextView mTextViewProgressTotal;
	public final TextView mTextViewProgressItem;
	public final ProgressBar mProgressBarItem;
	public final ProgressBar mProgressBarTotal;
	
	public ProgressFeedbackItemWidget(final ViewGroup aProgressItemGroup, Object aUUID, 
			Button.OnClickListener aOnCancelClick) {
		super(aUUID);
		mItem = View.inflate(aProgressItemGroup.getContext(),R.layout.progress_item,null);
		mItem.setTag(aUUID);
		mTextViewProgressAction = (TextView)mItem.findViewById(R.id.TextViewProgressAction);
		mTextViewProgressTotal = (TextView)mItem.findViewById(R.id.TextViewProgressOverall);
		mTextViewProgressItem = (TextView)mItem.findViewById(R.id.TextViewProgressMsg);
		mProgressBarTotal = (ProgressBar)mItem.findViewById(R.id.ProgressBarOverall);
		mProgressBarItem = (ProgressBar)mItem.findViewById(R.id.ProgressBarFile);
		Button b = ((Button)mItem.findViewById(R.id.ButtonCancelProgress));
		b.setTag(aUUID);
		b.setOnClickListener(aOnCancelClick);
		aProgressItemGroup.post(new Runnable() {
			@Override
			public void run() {
				aProgressItemGroup.addView(mItem);
			}
		});
	}
	
	@Override
	public ProgressFeedbackItem setActionText(final int aActionTextResId) {
		if (aActionTextResId!=0 && mTextViewProgressAction!=null) {
			mTextViewProgressAction.postDelayed(new Runnable() {
				@Override
				public void run() {
					try {
						mTextViewProgressAction.setText(aActionTextResId);
					} catch (Exception e) {
						setActionText(aActionTextResId);
					}
				}
				
			},100L);
		}
		return this;
	}
	
	@Override
	public void setTotalText(Message aMsg) {
		String s = ProgressBarHandler.getProgressText(aMsg);
		if (s!=null && mTextViewProgressTotal!=null) {
			mTextViewProgressTotal.setText(s);
		}
	}

	@Override
	public void setTotalSize(Message aMsg) {
		super.setTotalSize(aMsg);
		if (mProgressBarTotal!=null) {
			if (mTotalSize==-1L)
				mProgressBarTotal.setIndeterminate(true);
			else
				mProgressBarTotal.setProgress(0);
		}
	}
	
	@Override
	public void setTotalProgress(Message aMsg) {
		super.setTotalProgress(aMsg);
		if (mProgressBarTotal!=null && mTotalSize>0L) {
			int prog = Math.round(mTotalSoFar*mProgressBarTotal.getMax()/mTotalSize);
			mProgressBarTotal.setProgress(prog);
			//mTextViewProgressTotal.setText(""+prog+", "+mTotalSoFar+"/"+mTotalSize);
		}
	}
	
	@Override
	public void setItemSize(Message aMsg) {
		super.setItemSize(aMsg);
		if (mProgressBarItem!=null) {
			if (mItemSize==-1L)
				mProgressBarItem.setIndeterminate(true);
			else
				mProgressBarItem.setProgress(0);
		}
	}

	@Override
	public void setItemProgress(Message aMsg) {
		super.setItemProgress(aMsg);
		if (mProgressBarItem!=null && mItemSize>0L) {
			int prog = Math.round(mItemSoFar*mProgressBarItem.getMax()/mItemSize);
			mProgressBarItem.setProgress(prog);
		}
	}
	
	@Override
	public void setItemText(Message aMsg) {
		String s = ProgressBarHandler.getProgressText(aMsg);
		if (s!=null && mTextViewProgressItem!=null) {
			mTextViewProgressItem.setText(s);
		}
	}

	@Override
	public void handleProgressStartMsg(Message aMsg) {
		super.handleProgressStartMsg(aMsg);
		setTotalText(aMsg);
		setActionText(aMsg.arg1);
	}

	@Override
	public void handleProgressTotalUpdateMsg(Message aMsg) {
		super.handleProgressTotalUpdateMsg(aMsg);
		setItemText(aMsg);
	}
	
	@Override
	public void handleProgressItemFinishMsg(Message aMsg) {
		super.handleProgressItemFinishMsg(aMsg);
		if (mProgressBarItem!=null && !mProgressBarItem.isIndeterminate())
			mProgressBarItem.setProgress(mProgressBarItem.getMax());
	}

}
