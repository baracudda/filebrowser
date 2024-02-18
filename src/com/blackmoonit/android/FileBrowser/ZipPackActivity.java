package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.view.View;
import android.view.Window;

import com.blackmoonit.app.BitsDialog;
import com.blackmoonit.concurrent.BitsClickTask;
import com.blackmoonit.content.BitsIntent;
import com.blackmoonit.dialog.DialogFileLocation;
import com.blackmoonit.filesystem.BitsFileUtils;
import com.blackmoonit.filesystem.FilePackageZip;
import com.blackmoonit.lib.BitsStringUtils;
import com.blackmoonit.widget.BitsWidgetUtils;

/**
 * Dialog used to pack up file(s) using Zip and save the Zip file somewhere. 
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class ZipPackActivity extends DialogFileLocation {
	//private static final String TAG = "BITS.FileBrowser.ZipPackActivity";
	private ArrayList<? extends Parcelable> mUriList = null;
	private Uri mUriToZip = null;
	private String mCurrFolder = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		super.onCreate(savedInstanceState);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,R.drawable.act_zippack);
	}
	
	@Override
	protected void setup(Bundle savedInstanceState) {
		Intent theIntent = getIntent();
		if (theIntent==null) {
			finish();
			return;
		}
		//this is the deepest common folder path between the items to zip
		mCurrFolder = theIntent.getStringExtra(BitsIntent.EXTRA_DIRECTORY);
		//single item to zip or a list of items?
		if (BitsStringUtils.isEqual(theIntent.getAction(),Intent.ACTION_SEND)) {
			mUriToZip = theIntent.getParcelableExtra(Intent.EXTRA_STREAM);
		} else {
			mUriList = theIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
		}
		//do we have a default filename or at least a default folder?
		if (theIntent.getData()!=null) {
			mDefaultFile = new File(theIntent.getData().getPath());
		} else if (theIntent.getStringExtra(BitsIntent.EXTRA_DEFAULT_URI)!=null) {
			String a = theIntent.getStringExtra(BitsIntent.EXTRA_DEFAULT_URI);
			URI b = URI.create(a);
			mDefaultFile = new File(b);
		}
		File theBaseFolder = null;
		if (mDefaultFile!=null && mDefaultFile.exists() && mDefaultFile.isDirectory()) {
			theBaseFolder = mDefaultFile;
			mDefaultFile = null;
		}
		//check for default filename, else generate one
		if (mDefaultFile!=null) {
			//all set, no further action
		} else if (mUriList!=null) {
			/*
			Object theNextItem = mUriList.get(0);
			File theFile;
			if (theNextItem instanceof Uri) {
				theFile = new File(((Uri)theNextItem).getPath());
			} else if (theNextItem instanceof File) {
				theFile = (File)theNextItem;
			} else if (theNextItem instanceof String) {
				theFile = new File((String)theNextItem);
			} else {
				theFile = null; //list of unknown type, cannot process it
			}
			if (theFile!=null) {
				File theParentFile = theFile.getParentFile();
				String theFilename;
				if (mUriList.size()>1) {
					theFilename = theParentFile.getName();
				} else {
					theFilename = theFile.getName();
				}
				mDefaultFile = new File(theParentFile,
						BitsFileUtils.replaceExtension(theFilename,".zip"));
			}
			*/
			if (theBaseFolder==null) {
				theBaseFolder = new File(BitsFileUtils.deepestCommonFolder(mUriList));
				if (!theBaseFolder.exists())
					theBaseFolder = Environment.getExternalStorageDirectory();
			}
			String theFilename = null;
			if (mUriList.size()<1) {
				finish();
				return;
			} else if (mUriList.size()>1) {
				theFilename = theBaseFolder.getName();
			} else {
				Object theNextItem = mUriList.get(0);
				File theFile;
				if (theNextItem instanceof Uri) {
					theFile = new File(((Uri)theNextItem).getPath());
				} else if (theNextItem instanceof File) {
					theFile = (File)theNextItem;
				} else if (theNextItem instanceof String) {
					theFile = new File((String)theNextItem);
				} else {
					theFile = null; //list of unknown type, cannot process it
				}
				if (theFile!=null)
					theFilename = theFile.getName();
			}
			if (theFilename!=null) {
				mDefaultFile = new File(theBaseFolder,
						BitsFileUtils.replaceExtension(theFilename,".zip"));
			}
			
		} else if (mUriToZip!=null && (mUriToZip instanceof Uri)) {
			File theFile = new File(mUriToZip.getPath());
			if (theBaseFolder==null) {
				theBaseFolder = theFile.getParentFile();
				if (theBaseFolder==null || !theBaseFolder.exists())
					theBaseFolder = Environment.getExternalStorageDirectory();
			}
			mDefaultFile = new File(theBaseFolder,
					BitsFileUtils.replaceExtension(theFile.getName(),".zip"));
		} else {
			finish();
		}
		if (mCurrFolder==null && mDefaultFile!=null)
			mCurrFolder = mDefaultFile.getParent();
		super.setup(savedInstanceState);
	}	
	
	@Override
	protected void onPositiveButtonClick(View v) {
		File theZipFile = new File(BitsWidgetUtils.getTrimmedText(mEditLocationView),
				BitsWidgetUtils.getTrimmedText(mEditFilenameView));
		if (getCallingActivity()!=null || 
				BitsIntent.isIntentFrom(getIntent(),"com.blackmoonit.android.FileBrowser")) {
			Intent theResult = getIntent();
			theResult.setData(Uri.fromFile(theZipFile));
			BitsIntent.setFromExtra(theResult,this.getComponentName().getClassName());
			setResult(RESULT_OK,theResult);
		} else if (mUriList!=null) {
			final FilePackageZip fpz = new FilePackageZip(theZipFile);
			if (mCurrFolder!=null)
				fpz.mBasePath = mCurrFolder;
			new BitsClickTask(new BitsClickTask.TaskDef() {
				@Override
				public Object beforeTask(View v) {
					return null;
				}

				@Override
				public Object doTask(View v, Object aBeforeTaskResult) {
					try {
						fpz.pack(mUriList.iterator());
					} catch (Exception e) {
						return e;
					}
					return null;
				}

				@Override
				public void afterTask(View v, Object aTaskResult) {
					if (aTaskResult!=null)
						BitsDialog.ErrDialog(ZipPackActivity.this,(Exception)aTaskResult);
				}
				
			}).execute();
		} else if (mUriToZip!=null) {
			final ArrayList<Uri> theListOfOne = new ArrayList<Uri>();
			theListOfOne.add(mUriToZip);
			final FilePackageZip fpz = new FilePackageZip(theZipFile);
			if (mCurrFolder!=null)
				fpz.mBasePath = mCurrFolder;
			new BitsClickTask(new BitsClickTask.TaskDef() {
				@Override
				public Object beforeTask(View v) {
					return null;
				}

				@Override
				public Object doTask(View v, Object aBeforeTaskResult) {
					try {
						fpz.pack(theListOfOne.iterator());
					} catch (Exception e) {
						return e;
					}
					return null;
				}

				@Override
				public void afterTask(View v, Object aTaskResult) {
					if (aTaskResult!=null)
						BitsDialog.ErrDialog(ZipPackActivity.this,(Exception)aTaskResult);
				}
				
			}).execute();
		}
		finish();
	}

}
