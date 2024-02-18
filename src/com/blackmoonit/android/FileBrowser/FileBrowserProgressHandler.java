package com.blackmoonit.android.FileBrowser;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

/**
 * Progress Bar handler routing messages to their appropriate ProgressBarWidget.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class FileBrowserProgressHandler extends ProgressFeedbackHandler {
	protected WeakReference<ViewGroup> mProgressWidgetGroup = null;
	
	public FileBrowserProgressHandler(Activity aAct) {
		super(aAct);
	}
	
	public void setup(ViewGroup aProgressWidgetGroup) {
		mProgressWidgetGroup = new WeakReference<ViewGroup>(aProgressWidgetGroup);
	}

	public ProgressFeedbackItemWidget getWidget(Object aProgressID) {
		ProgressFeedbackItem theItem = get(aProgressID);
		if (theItem instanceof ProgressFeedbackItemWidget)
			return (ProgressFeedbackItemWidget)theItem;
		else
			return null;
	}
	
	@Override
	public ProgressFeedbackItem createNewProgressItem(Object aProgressID) {
		if (mProgressWidgetGroup!=null)
			return new ProgressFeedbackItemWidget(mProgressWidgetGroup.get(),aProgressID, 
				new Button.OnClickListener() {
					@Override
					public void onClick(View v) {
						sendMessage(getMsgProgressCancel(v.getTag()));
					}
				});
		else
			return super.createNewProgressItem(aProgressID);
	}

	@Override
	public void onProgressStart(Message aMsg) {
		super.onProgressStart(aMsg);
		Activity theAct = mAct.get();
		if (theAct!=null) {
			theAct.setProgressBarIndeterminateVisibility(true);
		}
	}

	@Override
	public void onProgressCancel(Message aMsg) {
		super.onProgressCancel(aMsg);
		Activity theAct = mAct.get();
		if (theAct!=null) {
			String s = theAct.getString(R.string.msg_cancelled);
			Toast.makeText(theAct,s,Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onProgressFinish(Message aMsg) {
		//remove UI widgets for this batch
		ProgressFeedbackItemWidget theWidget = getWidget(getProgressID(aMsg));
		if (theWidget!=null && theWidget.mItem!=null) {
			theWidget.mItem.setVisibility(View.GONE);
			if (mProgressWidgetGroup!=null) {
				synchronized (mProgressWidgetGroup) {
					ViewGroup theWidgetGroup = mProgressWidgetGroup.get();
					if (theWidgetGroup!=null) {
						theWidgetGroup.removeView(theWidget.mItem);
					}
				}
			}
		}
		//do inheirited stuff
		super.onProgressFinish(aMsg);
		//turn off twirly if we're done with all batches
		Activity theAct = mAct.get();
		if (mProgressItems!=null && mProgressItems.size()==0 && theAct!=null) {
			theAct.setProgressBarIndeterminateVisibility(false);
		}
	}

	@Override
	public void setProgressHighlightUI(final Object aProgressID, final boolean isHighlighted) {
		super.setProgressHighlightUI(aProgressID, isHighlighted);
		final ProgressFeedbackItemWidget theWidget = getWidget(aProgressID);
		if (theWidget!=null && theWidget.mItem!=null) {
			theWidget.mItem.setBackgroundResource((isHighlighted)?R.drawable.bkgnd_progress_highlight:0);
			theWidget.mItem.invalidate();
		}
	}
	
}
