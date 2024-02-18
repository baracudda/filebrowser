package com.blackmoonit.android.FileBrowser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class WidgetGridView_v09 extends WidgetGridView_v05 {

	public WidgetGridView_v09(Context aContext) {
		super(aContext);
	}

	public WidgetGridView_v09(Context aContext, AttributeSet attrs) {
		super(aContext,attrs);
	}

	public WidgetGridView_v09(Context aContext, AttributeSet attrs, int defStyle) {
		super(aContext,attrs,defStyle);
	}
	
	@Override
	public boolean onKeyDown(int aKeyCode, KeyEvent aKeyEvent) {
		final Context theContext = getContext();
		final int theKeySource = aKeyEvent.getSource();
		if (aKeyCode==KeyEvent.KEYCODE_BACK && 
				(theContext!=null && theContext instanceof Activity) &&
				theKeySource!=InputDevice.SOURCE_TOUCHSCREEN &&
				theKeySource!=InputDevice.SOURCE_KEYBOARD
				) {
			//performLongClick();
			//return true;
		}
		return super.onKeyDown(aKeyCode,aKeyEvent);
	}
	
}
