package com.blackmoonit.android.FileBrowser;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;

/**
 * App BackupAgent used to help migrate important data/settings to a new device.
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class AppBackupAgent extends BackupAgentHelper {

	@Override
	public void onCreate() {
		Context theContext = getApplicationContext();
		SharedPreferencesBackupHelper theHelper = new SharedPreferencesBackupHelper(theContext,
				theContext.getString(R.string.prefs_name));
		addHelper("vnd_blackmoonit_filebrowser_prefs",theHelper);
    }

}
