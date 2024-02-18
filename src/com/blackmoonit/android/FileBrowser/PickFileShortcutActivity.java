package com.blackmoonit.android.FileBrowser;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Pick File Shortcut Activity of File Browser app.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class PickFileShortcutActivity extends FileListActivity {

	/**
	 * Create file-only shortcut is requested.
	 * 
	 * @param aIntent - intent describing the request
	 */
	@Override
	public void extReq_Shortcut(Intent aIntent) {
		aIntent.putExtra("shortcut.size",getShortCutSize(this));
		mCore.extReq_pickFile(this,aIntent);
		mCore.mPickShortcut = true;
		mCore.mPickFileShortcut = true;
    }
	
	static public int getShortCutSize(Activity anAct) {
    	ActivityInfo ai;
		try {
			ComponentName theName = anAct.getComponentName();
			ai = anAct.getPackageManager().getActivityInfo(theName,PackageManager.GET_META_DATA);
			return (ai!=null && ai.metaData!=null) ? ai.metaData.getInt("shortcut.size",1) : 1;
		} catch (NameNotFoundException e) {
			return 1;
		}
	}

}
