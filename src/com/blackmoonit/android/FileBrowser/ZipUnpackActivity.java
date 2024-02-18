package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import com.blackmoonit.concurrent.BitsThreadTask;
import com.blackmoonit.content.BitsIntent;
import com.blackmoonit.dialog.DialogFileLocation;
import com.blackmoonit.filesystem.FilePackageZip;
import com.blackmoonit.filesystem.FilePackageZip.OnEachEntry;
import com.blackmoonit.filesystem.FilePackageZip.OnException;
import com.blackmoonit.lib.BitsStringUtils;

/**
 * Unpack a Zip file dialog.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class ZipUnpackActivity extends DialogFileLocation {
	//private static final String TAG = "BITS.FileBrowser.ZipUnpackActivity";
	ZipUnpackProgressHandler msgHandler = new ZipUnpackProgressHandler(ZipUnpackActivity.this);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		super.onCreate(savedInstanceState);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,R.drawable.act_zipunpack);
	}
	
	@Override
	protected void setup(Bundle savedInstanceState) {
		bRemoveFilename = true;		
		super.setup(savedInstanceState);
	}
	
	@Override
	protected String getDefaultLocation() {
		if (getIntent()!=null && getIntent().hasExtra(BitsIntent.EXTRA_DIRECTORY)) {
			String theFolder = getIntent().getStringExtra(BitsIntent.EXTRA_DIRECTORY);
			if (!BitsStringUtils.isEmpty(theFolder))
				return theFolder;
			else
				return super.getDefaultLocation();
			
		} else
			return super.getDefaultLocation();
	}

	@Override
	protected void onPositiveButtonClick(View v) {
		final File theFolder = new File(mEditLocationView.getEditableText().toString());
		Runnable r = new Runnable() {
			@Override
			public void run() {
				/*
				FilePackageZip fpz = new FilePackageZip(mDefaultFile);
				try {
					fpz.unpack(theFolder.getPath());
				} catch (Exception e) {
					BitsDialog.ErrDialog(ZipUnpackActivity.this,e);
				}
				*/
				unpackZipFile(ZipUnpackActivity.this,mDefaultFile,theFolder);
			}
		};
		final BitsThreadTask t = new BitsThreadTask(r);
		t.setProcessName("unpack-"+mDefaultFile.getName());
		if (getCallingActivity()!=null) {
			Intent theResult = getIntent();
			theResult.setData(Uri.fromFile(mDefaultFile));
			theResult.removeExtra(BitsIntent.EXTRA_DIRECTORY); //remove the optional extra so we can replace it
			theResult.putExtra(BitsIntent.EXTRA_DIRECTORY,theFolder.getPath());
			BitsIntent.setFromExtra(theResult,this.getComponentName().getClassName());
			if (theResult.hasExtra("thread.priority")) {
				t.setProcessPriority(theResult.getIntExtra("thread.priority",Thread.NORM_PRIORITY-1));
			}
			if (theResult.getBooleanExtra(BitsIntent.EXTRA_RETURN_DATA,true)) {
				t.execute();
			}
			setResult(RESULT_OK,theResult);
		} else {
			t.execute();
		}
		finish();
	}

	private void unpackZipFile(final Activity anAct, File aZipFile, final File aDestFolder) {
		final FilePackageZip theZipPackage = new FilePackageZip(aZipFile);
		if (!aDestFolder.exists())
			aDestFolder.mkdirs();
		msgHandler.setup(getIntent(),aZipFile,aDestFolder);
		theZipPackage.mMsgHandler = msgHandler;
		theZipPackage.mProgressID = msgHandler.createNewProgressEvent(R.string.act_name_zipunpack);
		msgHandler.getMsgProgressStart(theZipPackage.mProgressID,
				aDestFolder.getPath(),-1L).sendToTarget();
		try {
			theZipPackage.foreach(new OnEachEntry() {

				@Override
				public boolean process(ZipInputStream zis, ZipEntry anEntry) {
					try {
						File theNewFile = new File(aDestFolder,anEntry.getName());
						/* compressed size is usually -1, just use indeterminant bar
						//update overall progress
						Long approxSize = anEntry.getCompressedSize()+30+anEntry.getName().length();
						*/
						msgHandler.getMsgProgressTotalUpdate(theZipPackage.mProgressID,theNewFile.getPath(),
								null).sendToTarget();
						theZipPackage.unpackFileEntry(zis,theNewFile,anEntry);
					} catch (Exception e) {
						msgHandler.getMsgProgressCancel(theZipPackage.mProgressID,e).sendToTarget();
						return false;
					}
					Thread.yield();
					return (msgHandler==null || msgHandler.isStillProcessing(theZipPackage.mProgressID));
				}
				
			}, new OnException() {

				@Override
				public void caught(Exception e) {
					msgHandler.getMsgProgressCancel(theZipPackage.mProgressID,e).sendToTarget();
				}
				
			});
			msgHandler.getMsgProgressFinish(theZipPackage.mProgressID).sendToTarget();
		} catch (Exception e) {
			msgHandler.getMsgProgressCancel(theZipPackage.mProgressID,e).sendToTarget();
		}
	}

		
}
