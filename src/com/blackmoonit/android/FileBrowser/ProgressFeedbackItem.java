package com.blackmoonit.android.FileBrowser;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import android.os.Message;

import com.blackmoonit.filesystem.ProgressBarHandler;

/**
 * Progress item used for a basic batch operation w/o ties to a UI (descendants will handle UI concerns)
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class ProgressFeedbackItem {
	private static final AtomicInteger seq = new AtomicInteger();
	public final int seqNum;
	public final UUID mUUID;
	public long mTotalSize = 0L;
	public long mTotalSoFar = 0L;
	public long mItemSize = 0L;
	public long mItemSoFar = 0L;
	public Boolean isProcessing = true;
	public Serializable mErr = null;
	
	public ProgressFeedbackItem(Object aUUID) {
		seqNum = seq.getAndIncrement();
		mUUID = (UUID)aUUID;
	}
	
	public ProgressFeedbackItem setActionText(int aActionTextResId) {
		//since base class has no UI, nothing to do
		return this;
	}

	public void setTotalText(Message aMsg) {
		//since base class has no UI, nothing to do
	}

	public void setItemText(Message aMsg) {
		//since base class has no UI, nothing to do
	}

	public void setTotalSize(Message aMsg) {
		mTotalSize = ProgressBarHandler.getProgressAmount(aMsg);
		mTotalSoFar = 0L;
	}
	
	public void setTotalProgress(Message aMsg) {
		mTotalSoFar += ProgressBarHandler.getProgressAmount(aMsg);
	}
	
	public void setItemSize(Message aMsg) {
		mItemSize = ProgressBarHandler.getProgressAmount(aMsg);
		mItemSoFar = 0L;
	}
	
	public void setItemProgress(Message aMsg) {
		mItemSoFar += ProgressBarHandler.getProgressAmount(aMsg);
	}
	
	public void handleProgressStartMsg(Message aMsg) {
		setTotalSize(aMsg);
	}

	public void handleProgressTotalIncMaxMsg(Message aMsg) {
		mTotalSize += ProgressBarHandler.getProgressAmount(aMsg);
	}
	
	public void handleProgressTotalUpdateMsg(Message aMsg) {
		setTotalProgress(aMsg);
	}
	
	public void handleProgressItemStartMsg(Message aMsg) {
		setItemSize(aMsg);
	}
	
	public void handleProgressItemUpdateMsg(Message aMsg) {
		setItemProgress(aMsg);
	}
	
	public void handleProgressItemFinishMsg(Message aMsg) {
		//nothing to do
	}
}
