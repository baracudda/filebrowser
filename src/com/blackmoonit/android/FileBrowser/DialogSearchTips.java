package com.blackmoonit.android.FileBrowser;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.util.Linkify;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabWidget;
import android.widget.TextView;

import com.blackmoonit.app.BitsDialog;
import com.blackmoonit.widget.BitsWidgetUtils;
import com.blackmoonit.widget.SimpleGestureHandler;
import com.blackmoonit.widget.SimpleGestureHandler.OnSimpleGesture;

/**
 * Dialog for file browser search tips.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class DialogSearchTips extends BitsDialog implements OnSimpleGesture, TabContentFactory {
	private TabHost mTabHost = null;
	private int mTabCount = 0;
	private SimpleGestureHandler mSwipeHandler = null;
	
	private static final String TAB_SEARCH_TIP_FILENAME = "search_tip_filename";
	private static final String TAB_SEARCH_TIP_FILETYPE = "search_tip_filetype";
	private static final String TAB_SEARCH_TIP_FILEDATE = "search_tip_filedate";
	private static final String TAB_SEARCH_TIP_FILESIZE = "search_tip_filesize";
	private static final String TAB_SEARCH_TIP_DUPLICATES = "search_tip_duplicates";
	private static final String TAB_SEARCH_TIP_REGEX = "search_tip_regex";

	public DialogSearchTips(Context aContext) {
		super(aContext);

		setTitle(R.string.menu_item_search_tips);
		
		mSwipeHandler = new SimpleGestureHandler(this.getContext(),this);
		mSwipeHandler.setSwipeLimits(40,100,10);

		//intro text
		addMsg(R.string.search_tip_intro);
		int introID = vb.LastUsedID; //save off ID of intro text for later
		
		//create an empty tab widget
		TabHost theTabHost = BitsWidgetUtils.createTabHost(this.getContext());
		
		Resources r = getContext().getResources();
		
		//add the various tabs
		theTabHost.addTab(createNewTab(theTabHost,TAB_SEARCH_TIP_FILENAME,"",
				r.getDrawable(R.drawable.searchtip_name)));
		theTabHost.addTab(createNewTab(theTabHost,TAB_SEARCH_TIP_FILETYPE,"",
				r.getDrawable(R.drawable.searchtip_mime)));
		theTabHost.addTab(createNewTab(theTabHost,TAB_SEARCH_TIP_FILEDATE,"",
				r.getDrawable(R.drawable.searchtip_date)));
		theTabHost.addTab(createNewTab(theTabHost,TAB_SEARCH_TIP_FILESIZE,"",
				r.getDrawable(R.drawable.searchtip_size)));
		theTabHost.addTab(createNewTab(theTabHost,TAB_SEARCH_TIP_DUPLICATES,"",
				r.getDrawable(R.drawable.searchtip_dup)));
		theTabHost.addTab(createNewTab(theTabHost,TAB_SEARCH_TIP_REGEX,"",
				r.getDrawable(R.drawable.searchtip_regex)));
		//set each tab current from back to front, leaves tab0 active and measures all in process
		FrameLayout tabContents = (FrameLayout)theTabHost.findViewById(android.R.id.tabcontent);
		tabContents.setVerticalScrollBarEnabled(true);
		tabContents.setMeasureAllChildren(true);
		mTabCount = ((TabWidget)theTabHost.findViewById(android.R.id.tabs)).getChildCount();
		for (int i=mTabCount-1; i>=0; i--) {
			theTabHost.setCurrentTab(i);
		}

		//place tab widget into a container (tab requires a special ID, we need our own ID for
		//  layout purposes)
		ViewGroup vg = new RelativeLayout(mContext.get());
		vg.addView(theTabHost);

		
		//add tab widget to our view below the intro text
		vb.addCustomView(vg,introID);
		RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams)vb.vLayout.getLayoutParams();
		if (rlp==null)
			rlp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
		rlp.setMargins(6, 6, 6, 6);
		vb.vLayout.setLayoutParams(rlp);
		
		mTabHost = theTabHost;
	}

	protected TabHost.TabSpec createNewTab(TabHost aTabHost, String aTabKey, 
			String aTabText, final Drawable aTabIcon) {
		if (aTabHost==null || aTabKey==null || aTabText==null)
			return null;
		TabHost.TabSpec theTab = aTabHost.newTabSpec(aTabKey);
		theTab.setIndicator(aTabText,aTabIcon);
		theTab.setContent(this);
		return theTab;
	}

	@Override
	public boolean onMouseButton(MotionEvent aMotionEvent, int aButtonState) {
		//let the default mouse click handlers do their job
		return false;
	}

	@Override
	public boolean onSwipe(MotionEvent aMotionEvent, float aDirection) {
		int idxTab = mTabHost.getCurrentTab();
		if (aDirection>0 && idxTab<mTabCount-1) {
			idxTab += 1;
		} else if (aDirection<0 && idxTab>0) {
			idxTab -= 1;
		}
		if (idxTab!=mTabHost.getCurrentTab())
			mTabHost.setCurrentTab(idxTab);
		return false;
	}

	@Override
	public boolean onScroll(MotionEvent aMotionEvent, float aDirection) {
		//we don't scroll up or down
		return false;
	}

	@Override
	public View createTabContent(String tag) {
		final TextView tv = new TextView(getContext());
		if (tag.equals(TAB_SEARCH_TIP_FILENAME)) {
			tv.setText(Html.fromHtml("&nbsp;<font color=yellow>!</font><font color=aqua>a</font>"+
				"<font color=yellow> - </font>"+
				mContext.get().getString(R.string.search_tip_filename,
						"\"<font color=yellow>!</font><font color=aqua>blue</font>\"",
						"\"blue\"")
			));
		} else if (tag.equals(TAB_SEARCH_TIP_FILETYPE)) {
			tv.setText(Html.fromHtml("&nbsp;<font color=yellow>.</font><font color=aqua>MIME</font>"+
					"<font color=yellow> </font>"+
					mContext.get().getString(R.string.search_tip_filetype,
							"\"<font color=yellow>.</font><font color=aqua>audio</font>\"",
							"\".audio\"")
			));
		} else if (tag.equals(TAB_SEARCH_TIP_FILEDATE)) {
			tv.setText(Html.fromHtml("&nbsp;<font color=yellow>&lt;</font><font color=aqua>#</font>"+
					"<font color=yellow> </font>"+
					mContext.get().getString(R.string.search_tip_filedate,
							"\"&gt;#\"",
							"\"<font color=yellow>&lt;</font><font color=aqua>7</font>\"")
			));
		} else if (tag.equals(TAB_SEARCH_TIP_FILESIZE)) {
			tv.setText(Html.fromHtml("&nbsp;<font color=yellow>&gt;</font><font color=aqua>#</font>"+
					"<font color=yellow>K </font>"+
					mContext.get().getString(R.string.search_tip_filesize,
							"\"&lt;#Kb\"",
							"\"<font color=yellow>&lt;</font><font color=aqua>1mb</font>\"")
			));
		} else if (tag.equals(TAB_SEARCH_TIP_DUPLICATES)) {
			tv.setText(Html.fromHtml("&nbsp;<font color=yellow>/2/ </font>"+
					mContext.get().getString(R.string.search_tip_duplicates,
							"<font color=yellow>/22/</font>")
			));
		} else { //if (tag.equals(TAB_SEARCH_TIP_REGEX)) {
			tv.setText(Html.fromHtml("&nbsp;<font color=yellow>/.*/ - </font>"+
					mContext.get().getString(R.string.search_tip_regex)
			));
		}
		if (vb.mFontSize>0)
			tv.setTextSize(vb.mFontSize);
		Linkify.addLinks(tv,Linkify.ALL);
		//tv.setOnTouchListener(mSwipeHandler);
		//tv.setMovementMethod(LinkMovementMethod.getInstance());
		return tv;
	}
	
}
