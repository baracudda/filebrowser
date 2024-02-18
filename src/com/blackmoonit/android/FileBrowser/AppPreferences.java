package com.blackmoonit.android.FileBrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.view.Menu;
import android.view.MenuItem;

import com.blackmoonit.app.BitsPreferences;
import com.blackmoonit.lib.BitsLegacy;

/**
 * Preference activity for this application.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class AppPreferences extends BitsPreferences implements OnSharedPreferenceChangeListener {
	static public final int[] PREFERENCE_RESOURCE_IDS = {R.layout.app_prefs, R.layout.app_prefs_appearance,
			R.layout.app_prefs_behavior};
    //prefs menu
    private static final int MENU_ITEM_RESET_PREFS = Menu.FIRST + 1;
    //plugins
	//private static final String SCHEME_PLUGIN = "plugin";
	
	/**
	 * Provide our own apply() method to allow scheduling a backup if desired.
	 * @param aContext - a context
	 * @param aEditor - editor with changes to apply
	 */
	public static void applyAndBackup(Context aContext, SharedPreferences.Editor aEditor) {
		AppPreferences.applyChanges(aEditor);
		BitsLegacy.scheduleBackup(aContext);
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		BitsLegacy.scheduleBackup(getApplicationContext());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu aMenu) {
    	aMenu.add(Menu.NONE,MENU_ITEM_RESET_PREFS,0,R.string.menu_item_reset_prefs)
    		.setIcon(android.R.drawable.ic_menu_preferences);
		return super.onCreateOptionsMenu(aMenu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_ITEM_RESET_PREFS: {
				resetPrefs();
				return true;
        	}
		}
		return false;
	}
	
	@Override
	protected void onDestroy() {
		SharedPreferences theSettings = getPrefs(this);
		if (theSettings!=null) {
			theSettings.unregisterOnSharedPreferenceChangeListener(this);
		}
		super.onDestroy();
	}

	protected void setup() {
		/* //add in plugin prefs
		Intent theIntent = new Intent(null,new Uri.Builder().scheme(SCHEME_PLUGIN).authority(getPackageName())
				.build());
		theIntent.addCategory(Intent.CATEGORY_PREFERENCE);
		addPreferencesFromIntent(theIntent);
		*/
		//addPreferencesFromIntent(new Intent("com.blackmoonit.android.FileBrowser.plugin.prefs"));
		//major bugs with the PrefsFromIntent functions at this time (Android 2.2)

		SharedPreferences theSettings = getPrefs(this);
		if (theSettings!=null) {
			// register the listener to detect preference changes
			theSettings.registerOnSharedPreferenceChangeListener(this);
			
			String thePrefKey;
			thePrefKey = getString(R.string.pref_key_orientation);
			BitsLegacy.setDisplayRotation(this,Integer.parseInt(theSettings.getString(thePrefKey,"-2")));
		}
	}

	@Override
	protected int[] getPrefResources() {
		return PREFERENCE_RESOURCE_IDS;
	}
	
	/**
	 * Call this method in main activity's onCreate
	 * @param aContext - main activity
	 */
	static public void setDefaultPrefs(Context aContext) {
		setDefaultPrefs(aContext,PREFERENCE_RESOURCE_IDS,false);
	}
	
	public void resetPrefs() {
		JumpPointDatabase theJumpDb = new JumpPointDatabase(this,getPrefs(this));
		String[] theJumpPoints = theJumpDb.getJumpPoints();
		super.resetPrefs();
		theJumpDb.addJumpPoints(theJumpPoints);
	}
	
}
