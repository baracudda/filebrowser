package com.blackmoonit.android.FileBrowser;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import com.blackmoonit.content.BitsIntent;

public class QSBpreference extends Preference {
	protected boolean bQSBprefsExist = false;
	
	public QSBpreference(Context aContext, AttributeSet attrs) {
		super(aContext, attrs);
		bQSBprefsExist = BitsIntent.existsQSBsettings(aContext);
		setEnabled(bQSBprefsExist);
	}

	@Override
	public boolean getShouldDisableView() {
		return bQSBprefsExist;
	}

	@Override
	protected void onClick() {
		if (isEnabled()) {
			BitsIntent.launchQSBsettings(getContext());
		} else {
			super.onClick();
		}
	}
	
}
