package com.blackmoonit.android.FileBrowser;

import java.io.File;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.Toast;

import com.blackmoonit.app.BitsDialog;
import com.blackmoonit.concurrent.BitsThreadTask;
import com.blackmoonit.filesystem.BitsFileUtils;

/**
 * Dialog for setting audio files as various kinds of ringtones.
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class DialogAudioSubmenu extends Dialog {
	protected final Activity mAct;
	protected final FileBrowserCore mCore;
	protected final File mAudioFile;
	protected final SharedPreferences mSettings;
	protected int mFontSize = 0;

	public DialogAudioSubmenu(Activity aContext, FileBrowserCore aCore, File aAudioFile) {
		super(aContext);
		mAct = aContext;
		mCore = aCore;
		mAudioFile = aAudioFile;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dialog_set_audio);
		mSettings = AppPreferences.getPrefs(getContext());
		getPrefs(mSettings);
		setup();
	}

	protected void getPrefs(SharedPreferences aSettings) {
		if (aSettings!=null) {
			mFontSize = AppPreferences.getFontSize(getContext(),aSettings);
			AppPreferences.applyOrientationSetting(getContext(),aSettings);
        }
	}
	
	private void linktoSetAudioSubmenuOption(final Activity anAct, int aOptionResId, 
			final File aCustomAudioFolder) {
		if (aOptionResId==0)
			return;
		CheckedTextView ctv = (CheckedTextView)findViewById(aOptionResId);
		if (mFontSize>0)
			ctv.setTextSize(mFontSize);
		/*
		Uri u = RingtoneManager. getActualDefaultRingtoneUri(this,RingtoneManager.TYPE_RINGTONE);
		if (u.equals(Uri.fromFile(aFile))) //content://media/external/audio/media/41
			return;
		*/
		ctv.setChecked(BitsFileUtils.customAudioExists(aCustomAudioFolder,mAudioFile));
		ctv.setEnabled( !mAudioFile.getParentFile().equals(aCustomAudioFolder) );
			//&& !RingtoneManager.isDefault(Uri.fromFile(aFile)) );
		ctv.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//toggle checkbox
				final boolean bIsChecked = !((CheckedTextView)v).isChecked();
				//set new check value
				((CheckedTextView)v).setChecked(bIsChecked);
				//define action
				Thread t = new BitsThreadTask(new Runnable() {
					@Override
					public void run() {
						//force destination folders to exist
						aCustomAudioFolder.mkdirs();
						//new filename
						File theAudioFile = new File(aCustomAudioFolder,mAudioFile.getName());
						//add/remove file in special folder
						if (bIsChecked) {
							mCore.copyFob(anAct,null,mAudioFile,theAudioFile);
						} else {
							BitsFileUtils.deleteFile(theAudioFile);
						}
						//let system know about the new file
						BitsFileUtils.notifyMediaScanner(mAct,theAudioFile);
						
						//RingtoneManager.setActualDefaultRingtoneUri(this,RingtoneManager.TYPE_RINGTONE,
						//		MediaStore.Audio.Media.getContentUriForPath(theFile.getPath()));
					}
				});
				//UI busy toast
				Toast.makeText(anAct,R.string.msg_busy,Toast.LENGTH_SHORT).show();
				//run action
				t.start();
			}
		});
	}
	
	protected void setup() {
		//set the current value of the checkboxes
		linktoSetAudioSubmenuOption(mAct,R.id.CheckedTextViewRingtones,BitsFileUtils.customRingtoneFolder);
		linktoSetAudioSubmenuOption(mAct,R.id.CheckedTextViewNotifications,BitsFileUtils.customNotificationFolder);
		linktoSetAudioSubmenuOption(mAct,R.id.CheckedTextViewAlarms,BitsFileUtils.customAlarmFolder);
		Button b = (Button)findViewById(R.id.ButtonJumpToAudioSettings);
		b.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					mAct.startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
				} catch (ActivityNotFoundException anfe) {
					try {
						mAct.startActivity(new Intent(Settings.ACTION_SETTINGS));
					} catch (Exception e) {
						BitsDialog.ErrDialog(mAct,e);
					}
				} catch (Exception e) {
					BitsDialog.ErrDialog(mAct,e);
				}
			}
		});
	}
	

}
