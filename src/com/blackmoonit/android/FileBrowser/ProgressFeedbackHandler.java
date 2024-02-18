package com.blackmoonit.android.FileBrowser;

import java.lang.ref.WeakReference;
import java.util.concurrent.CancellationException;

import android.app.Activity;
import android.os.Message;

import com.blackmoonit.filesystem.ProgressBarHandler;

/**
 * Progress Bar handler routing messages to their appropriate ProgressBarItem.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class ProgressFeedbackHandler extends ProgressBarHandler {
	protected final WeakReference<Activity> mAct;
	protected ProgressFeedbackMap mProgressItems = new ProgressFeedbackMap();
	
	public ProgressFeedbackHandler(Activity aAct) {
		mAct = new WeakReference<Activity>(aAct);
	}
	
	public ProgressFeedbackItem get(Message aMsg) {
		return get((aMsg!=null)?aMsg.obj:null);
	}
	
	public ProgressFeedbackItem get(Object aProgressID) {
		return (mProgressItems!=null && aProgressID!=null)?mProgressItems.get(aProgressID):null;
	}
	
	/**
	 * Creates a new progress item to be stored in the mapping.
	 * @param aProgressID - progress bar group ID
	 * @return Returns a new progress item.
	 */
	public ProgressFeedbackItem createNewProgressItem(Object aProgressID) {
		return new ProgressFeedbackItem(aProgressID);
	}
	
	@Override
	public Object createNewProgressEvent(int aActionTextResID) {
		Object theProgressID = super.createNewProgressEvent(aActionTextResID);
		//place the progress ID into the list of progress items so that isStillProcessing() will be 100% accurate
		synchronized (mProgressItems) {
			mProgressItems.put(theProgressID,createNewProgressItem(theProgressID).setActionText(aActionTextResID));
		}
		return theProgressID;
	}
	
	/**
	 * Check to see if processing files in background. Pass in NULL for any group, else an ID for 
	 * a specific group as returned by sendProgressStartMsg().
	 * @param aProgressID - NULL for any, else the ID of the group desired to check
	 * @return Return TRUE if the group is not finished, or if size of group list > 0 for NULL group.
	 * @see com.blackmoonit.filesystem.ProgressBarHandler#sendProgressStartMsg()
	 */
	public boolean isStillProcessing(Object aProgressID) {
		ProgressFeedbackItem theItem = get(aProgressID);
		if (theItem!=null)
			return theItem.isProcessing;
		else if (mProgressItems!=null)
			return (mProgressItems.size()>0);
		else
			return false;
	}
	
	@Override
	public void onProgressStart(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null)
			theItem.handleProgressStartMsg(aMsg);
	}

	@Override
	public void onProgressTotalUpdate(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null)
			theItem.handleProgressTotalUpdateMsg(aMsg);
	}

	@Override
	public void onProgressTotalIncMax(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null)
			theItem.handleProgressTotalIncMaxMsg(aMsg);
	}

	@Override
	public void onProgressCancel(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null) {
			theItem.isProcessing = false;
			theItem.mErr = getProgressCancelMsg(aMsg);
			if (theItem.mErr==null)
				theItem.mErr = new CancellationException();
		}
	}

	@Override
	public void onProgressFinish(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null && mProgressItems!=null && aMsg!=null) {
			theItem.isProcessing = false;
			synchronized (mProgressItems) {
				mProgressItems.remove(aMsg.obj);
			}
			theItem = null;
		}
	}

	@Override
	public void onProgressItemStart(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null)
			theItem.handleProgressItemStartMsg(aMsg);
	}

	@Override
	public void onProgressItemFinish(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null)
			theItem.handleProgressItemFinishMsg(aMsg);
	}

	@Override
	public void onProgressItemUpdate(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		if (theItem!=null)
			theItem.handleProgressItemUpdateMsg(aMsg);
	}

	/**
	 * Must call this function from within the UI thread. If I make this function use runOnUIThread,
	 * out of order execution will cause the dialog to be displayed before the highlight takes effect.
	 * Always. No matter what method is used to block the thread and force the highlight to go first.
	 * 
	 * @param aProgressID - ID of the progress bar group that is going to display the dialog
	 * @param isHighlighted - TRUE to highlight the group, FALSE to set it back to normal
	 */
	public void setProgressHighlightUI(final Object aProgressID, final boolean isHighlighted) {
		//base method does nothing
	}
	
}
