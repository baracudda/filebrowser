package com.blackmoonit.android.FileBrowser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;

@TargetApi(Build.VERSION_CODES.ECLAIR)
public class WidgetListView_v05 extends WidgetListView_v03 {

	public WidgetListView_v05(Context aContext) {
		super(aContext);
	}

	public WidgetListView_v05(Context aContext, AttributeSet attrs) {
		super(aContext,attrs);
	}

	public WidgetListView_v05(Context aContext, AttributeSet attrs, int defStyle) {
		super(aContext,attrs,defStyle);
	}
	
	@Override
	public boolean onKeyDown(int aKeyCode, KeyEvent aKeyEvent) {
		if (aKeyCode==KeyEvent.KEYCODE_MENU && !isInTouchMode()) {
			aKeyEvent.startTracking();
			//return true;
		}
		return super.onKeyDown(aKeyCode,aKeyEvent);
	}
	
	@Override
	public boolean onKeyLongPress(int aKeyCode, KeyEvent aKeyEvent) {
		Context theContext = getContext();
		if (aKeyCode==KeyEvent.KEYCODE_MENU && theContext!=null && theContext instanceof Activity) {
			((Activity)getContext()).openContextMenu(this);
			return true;
		}
		return super.onKeyLongPress(aKeyCode,aKeyEvent);
	}
	
	@Override
	public boolean onKeyUp(int aKeyCode, KeyEvent aKeyEvent) {
		Context theContext = getContext();
		if (aKeyCode==KeyEvent.KEYCODE_MENU && !isInTouchMode() && !aKeyEvent.isCanceled()
				&& theContext!=null && theContext instanceof Activity) {
			((Activity)getContext()).openOptionsMenu();
			return true; 
		}
		return super.onKeyUp(aKeyCode,aKeyEvent);
	}
	
}
