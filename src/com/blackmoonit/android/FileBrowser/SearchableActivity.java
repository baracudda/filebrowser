package com.blackmoonit.android.FileBrowser;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.blackmoonit.app.BitsDialog;
import com.blackmoonit.content.BitsIntent;
import com.blackmoonit.filesystem.BitsFileUtils;
import com.blackmoonit.lib.BitsLegacy;

/**
 * Search Activity of File Browser app.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class SearchableActivity extends FileListActivity {
	@SuppressWarnings("unused")
	private static final String TAG = "BITS.FileBrowser.Search";
	private String mSearchQuery = null;
	private View mLayoutHeader = null;

	@Override
	protected void setup() {
		mCore.bIsSearching = true;
		System.gc();
		super.setup();
		mCore.mMarkedFiles.setSingleFolderLimit(false);
		mCore.mListAdapter.bShowFolderInfo = true;
		mCore.mGridAdapter.bShowFolderInfo = true;
		mCore.mButtonGoJump.setVisibility(View.INVISIBLE);
		mLayoutHeader = findViewById(R.id.LayoutHeader);
		mLayoutHeader.setVisibility(View.GONE);
		mCore.mAutoSelectName = "?";
		//API 11+ =/
		//getListView().setFastScrollAlwaysVisible(true);
		//mGridView.setFastScrollAlwaysVisible(true);
	}
	
	@Override
	protected int onSetContentView() {
		setTitle(R.string.act_name_search);
		return super.onSetContentView();
	}

	@Override
	protected void onNewIntent(Intent aIntent) {
		setIntent(aIntent);
		processExternalRequest(aIntent);
	}

	@Override
	public void processExternalRequest(Intent aIntent) {
		if (Intent.ACTION_VIEW.equals(aIntent.getAction())) {
			handleSuggestion(aIntent.getData());
			finish();
		} else if (Intent.ACTION_SEARCH.equals(aIntent.getAction())) {
			final String theSearchPath;
			Bundle appData = getIntent().getBundleExtra(SearchManager.APP_DATA);
			if (appData!=null) {
				theSearchPath = appData.getString(BitsIntent.EXTRA_DIRECTORY);
			} else {
				theSearchPath = null;
			}
			mCore.mFolderName = "?";
			mSearchQuery = aIntent.getStringExtra(SearchManager.QUERY);
			handleSearch(mSearchQuery,theSearchPath);
	    }
	}

	@Override
	public boolean onSearchRequested() {
		Bundle appData = getIntent().getBundleExtra(SearchManager.APP_DATA);
		if (appData!=null) {
			startSearch(null,false,appData,false);
			return true;
		} else {
			return super.onSearchRequested();
		}
	}

	protected void handleSuggestion(Uri aData) {
		if (aData!=null) {
			Intent theIntent = new Intent(Intent.ACTION_VIEW);
			Uri theData = aData;
			File theFile = new File(aData.getPath());
			if (mCore.mMimeMap.isCategory("image",theFile))
				theData = BitsFileUtils.getContentUriFromImageFile(this,theFile);
			else if (mCore.mMimeMap.isCategory("video",theFile))
				theData = BitsFileUtils.getContentUriFromVideoFile(this,theFile);
			theIntent.setDataAndType(theData,mCore.mMimeMap.getMIMEtype(theFile));
			try {
				startActivity(theIntent);
			} catch (Exception e) {
				BitsDialog.ErrDialog(this, e);
			}
		}
	}
	
	@SuppressLint("SdCardPath")
	protected void handleSearch(String aQuery, String aSearchPath) {
		Thread.yield();
		File theSearchFolder = new File(aSearchPath);
		/* no need to check this anymore, file structure horribly messed up since simple 1.x;
		 * now we just check the current folder and all public ones defined.
		if (theSearchFolder!=null) {
			String theSearchPath = BitsFileUtils.getCanonicalPath(theSearchFolder);
			if (theSearchPath.startsWith("/sdcard") ||
					theSearchPath.startsWith(Environment.getExternalStorageDirectory().getPath()) ||
					!theSearchFolder.exists() ) { 
				theSearchFolder = null;
			}
		}
		*/
		//stop prior search if it's still ongoing
		stopSearchTask();
		Thread.yield();
		//reset searching conditions
		setTitle(getString(R.string.act_name_search)+": "+aQuery);
		mCore.bIsSearching = true;
		if (mCore.mFileIcons!=null)
			mCore.mFileIcons.setSuspend(true); //speed optimization, turn off thumbnail generation
		//setup search tasks
		mCore.mDsFileList.setSearchQuery(aQuery,theSearchFolder,this);
		//start search and pass in end condition task
		mCore.mDsFileList.startSearchTask(new Runnable() {
			@Override
			public void run() {
				if (mCore!=null) {
					mCore.bIsSearching = false;
					if (mCore.mFileIcons!=null)
						mCore.mFileIcons.setSuspend(false); //resume thumbnail generation
				}
			}
		});
		//start displaying search results
		mCore.fillList("?");
	}
	
	@Override
	public void finish() {
		stopSearchTask();
		super.finish();
	}
	
	protected void stopSearchTask() {
		mCore.mDsFileList.stopSearchTask();
	}

	@Override
	public boolean doesBackKeyExit() {
		return super.doesBackKeyExit() || mCore.mDsFileList.mCurrPath.equals("?");
	}
	
	@Override
	protected void saveCurrentItem() {
		//do nothing, we don't wish to save current item on search since contents are very volitile.
	}

	@Override 
	public boolean onCreateOptionsMenu(Menu aMenu) {
		super.onCreateOptionsMenu(aMenu);
		aMenu.findItem(R.id.menu_item_jumpto).setVisible(false);
		aMenu.findItem(R.id.menu_item_refreshcard).setVisible(false);
		return true;
	}

	@Override
	public void onAfterFillList(int aItemPos) {
		super.onAfterFillList(aItemPos);
		if (mLayoutHeader!=null) {
			if (mCore.mDsFileList.mCurrPath.equals("?"))
				mLayoutHeader.setVisibility(View.GONE);
			else
				mLayoutHeader.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public boolean onMenuItemSelected(int aFeatureId, MenuItem aMenuItem) {
		final int theMenuChoice = aMenuItem.getItemId();
		switch (theMenuChoice) {
			default:
				if (theMenuChoice==BitsLegacy.android_R_id_home(this)) {
					finish();
					return true;
				}				
		}
		return super.onMenuItemSelected(aFeatureId,aMenuItem);
	}

}
