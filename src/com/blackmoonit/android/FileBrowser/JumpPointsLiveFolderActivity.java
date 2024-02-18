package com.blackmoonit.android.FileBrowser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.LiveFolders;

/**
 * Create the Live Folder intent that will display the user's defined jump points.
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class JumpPointsLiveFolderActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getIntent()!=null && LiveFolders.ACTION_CREATE_LIVE_FOLDER.equals(getIntent().getAction())) {
			Intent theResult = new Intent();
			theResult.setData(JumpPointProvider.LIVE_FOLDER_URI);
			theResult.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME,getString(R.string.menu_item_jumpto));
			theResult.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON,
					Intent.ShortcutIconResource.fromContext(this,R.drawable.act_jumplivefolder));
			theResult.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,LiveFolders.DISPLAY_MODE_GRID);
			setResult(RESULT_OK,theResult);
		} else {
			setResult(RESULT_CANCELED);
		}
		finish();
	}

}
