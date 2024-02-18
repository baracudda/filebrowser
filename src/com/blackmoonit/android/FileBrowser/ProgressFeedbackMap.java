package com.blackmoonit.android.FileBrowser;

import java.util.LinkedHashMap;

/**
 * Container for handling multiple progress bar UI elements.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class ProgressFeedbackMap extends LinkedHashMap<Object, ProgressFeedbackItem> {
	private static final long serialVersionUID = -1003911013755870566L;

	public boolean isStillProcessing(Object aProgressID) {
		ProgressFeedbackItem theItem = get(aProgressID);
		return (theItem!=null && theItem.isProcessing);
	}

}
