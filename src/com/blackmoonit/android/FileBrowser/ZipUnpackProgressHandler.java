package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.util.HashMap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;

/**
 * Progress Bar handler routing messages to their appropriate ProgressBarItem and broadcasting special events.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class ZipUnpackProgressHandler extends ProgressFeedbackHandler {
	public final static String EXTRA_RECEIVER_COMPONENTNAME = "com.blackmoonit.extra.receiver.componentname";
	public final static String EXTRA_ARCHIVE_ID = Intent.EXTRA_UID;
	public final static String EXTRA_THROWABLE = "com.blackmoonit.extra.throwable";
	public final static String EXTRA_AMOUNT = "org.openintents.extra.AMOUNT"; //type of double
    public final static String ACTION_UNPACK_STARTED = "com.blackmoonit.filebrowser.ACTION_UNPACK_STARTED";
    public final static String ACTION_UNPACK_FINISHED = "com.blackmoonit.filebrowser.ACTION_UNPACK_FINISHED";
    public final static String ACTION_UNPACK_THROW_MSG = "com.blackmoonit.filebrowser.ACTION_UNPACK_THROW_MSG";
    public final static String ACTION_UNPACK_FILE = "com.blackmoonit.filebrowser.ACTION_UNPACK_FILE";
    public final static String ACTION_UNPACK_FILE_START = "com.blackmoonit.filebrowser.ACTION_UNPACK_FILE_START";
    public final static String ACTION_UNPACK_FILE_UPDATE = "com.blackmoonit.filebrowser.ACTION_UNPACK_FILE_UPDATE";
    public final static String ACTION_UNPACK_FILE_FINISH = "com.blackmoonit.filebrowser.ACTION_UNPACK_FILE_FINISH";
    protected HashMap<String, Intent> mMsgIntents = new HashMap<String, Intent>();
    protected File mArchiveFile = null;
    protected File mUnpackPath = null;
    
	public ZipUnpackProgressHandler(Activity aAct) {
		super(aAct);
	}
	
	protected void registerAction(Intent aMsgIntent, String aAction) {
		if (aAction.equals(ACTION_UNPACK_FINISHED) || aAction.equals(ACTION_UNPACK_THROW_MSG) ||
				aMsgIntent.getBooleanExtra(aAction,false)) 
			mMsgIntents.put(aAction,new Intent(aMsgIntent).setAction(aAction));
	}
	
	public void setup(Intent aIntent, File aArchiveFile, File aUnpackPath) {
		mArchiveFile = aArchiveFile;
		mUnpackPath = aUnpackPath;
		//external app registers which actions they care about and only broadcast those msgs
		if (aIntent!=null) {
			Intent theMsgIntent = new Intent(aIntent);
			theMsgIntent.setFlags(Intent.FLAG_FROM_BACKGROUND+Intent.FLAG_RECEIVER_REGISTERED_ONLY);
			theMsgIntent.setDataAndType(Uri.fromFile(mArchiveFile),mUnpackPath.getPath());

			//who will respond to the msgs
			theMsgIntent.setComponent((ComponentName)aIntent.getParcelableExtra(EXTRA_RECEIVER_COMPONENTNAME));
			
			//determine which msgs they want to receive
			registerAction(theMsgIntent,ACTION_UNPACK_FINISHED);
			registerAction(theMsgIntent,ACTION_UNPACK_THROW_MSG);
			registerAction(theMsgIntent,ACTION_UNPACK_STARTED);
			registerAction(theMsgIntent,ACTION_UNPACK_FILE);
			registerAction(theMsgIntent,ACTION_UNPACK_FILE_START);
			registerAction(theMsgIntent,ACTION_UNPACK_FILE_UPDATE);
			registerAction(theMsgIntent,ACTION_UNPACK_FILE_FINISH);
		}
	}
	
	@Override
	public void onProgressStart(Message aMsg) {
		super.onProgressStart(aMsg);
		ProgressFeedbackItem theItem = get(aMsg);
		Intent theIntent = mMsgIntents.get(ACTION_UNPACK_STARTED);
		Activity theAct = mAct.get();
		if (theIntent!=null && theItem!=null && theAct!=null) {
			theIntent.putExtra(EXTRA_ARCHIVE_ID,theItem.mUUID);
			theAct.sendBroadcast(theIntent);
		}
	}

	@Override
	public void onProgressCancel(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		super.onProgressCancel(aMsg);
		Intent theIntent = mMsgIntents.get(ACTION_UNPACK_THROW_MSG);
		Activity theAct = mAct.get();
		if (theIntent!=null && theItem!=null && theAct!=null) {
			theIntent.putExtra(EXTRA_ARCHIVE_ID,theItem.mUUID);
			theIntent.putExtra(EXTRA_THROWABLE,theItem.mErr);
			theAct.sendBroadcast(theIntent);
		}
	}

	@Override
	public void onProgressFinish(Message aMsg) {
		ProgressFeedbackItem theItem = get(aMsg);
		super.onProgressFinish(aMsg);
		Intent theIntent = mMsgIntents.get(ACTION_UNPACK_FINISHED);
		Activity theAct = mAct.get();
		if (theIntent!=null && theItem!=null && theAct!=null) {
			theIntent.putExtra(EXTRA_ARCHIVE_ID,theItem.mUUID);
			theAct.sendBroadcast(theIntent);
		}
	}

	@Override
	public void onProgressTotalUpdate(Message aMsg) {
		super.onProgressTotalUpdate(aMsg);
		Intent theIntent = mMsgIntents.get(ACTION_UNPACK_FILE);
		if (theIntent!=null) {
			ProgressFeedbackItem theItem = get(aMsg);
			Activity theAct = mAct.get();
			if (theItem!=null && theAct!=null) {
				theIntent.setDataAndType(Uri.fromFile(mArchiveFile),getProgressText(aMsg));
				theIntent.putExtra(EXTRA_ARCHIVE_ID,theItem.mUUID);
				theAct.sendBroadcast(theIntent);
			}
		}
	}

	@Override
	public void onProgressItemStart(Message aMsg) {
		super.onProgressItemStart(aMsg);
		Intent theIntent = mMsgIntents.get(ACTION_UNPACK_FILE_START);
		if (theIntent!=null) {
			ProgressFeedbackItem theItem = get(aMsg);
			Activity theAct = mAct.get();
			if (theItem!=null && theAct!=null) {
				theIntent.putExtra(EXTRA_ARCHIVE_ID,theItem.mUUID);
				theIntent.putExtra(EXTRA_AMOUNT,theItem.mItemSize);
				theAct.sendBroadcast(theIntent);
			}
		}
	}

	@Override
	public void onProgressItemFinish(Message aMsg) {
		super.onProgressItemFinish(aMsg);
		Intent theIntent = mMsgIntents.get(ACTION_UNPACK_FILE_FINISH);
		if (theIntent!=null) {
			ProgressFeedbackItem theItem = get(aMsg);
			Activity theAct = mAct.get();
			if (theItem!=null && theAct!=null) {
				theIntent.putExtra(EXTRA_ARCHIVE_ID,theItem.mUUID);
				theAct.sendBroadcast(theIntent);
			}
		}
	}

	@Override
	public void onProgressItemUpdate(Message aMsg) {
		super.onProgressItemUpdate(aMsg);
		Intent theIntent = mMsgIntents.get(ACTION_UNPACK_FILE_UPDATE);
		if (theIntent!=null) {
			ProgressFeedbackItem theItem = get(aMsg);
			Activity theAct = mAct.get();
			if (theItem!=null && theAct!=null) {
				theIntent.putExtra(EXTRA_ARCHIVE_ID,theItem.mUUID);
				theIntent.putExtra(EXTRA_AMOUNT,theItem.mItemSoFar);
				theAct.sendBroadcast(theIntent);
			}
		}
	}

	
}
