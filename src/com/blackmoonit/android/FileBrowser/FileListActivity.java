package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.lang.ref.WeakReference;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewStub;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.blackmoonit.android.FileBrowser.FileIcons.OnSetThumbnail;
import com.blackmoonit.app.BitsReportAnExceptionHandler;
import com.blackmoonit.content.BitsIntent;
import com.blackmoonit.lib.BitsLegacy;
import com.blackmoonit.widget.SimpleGestureHandler;
import com.blackmoonit.widget.SimpleGestureHandler.OnSimpleGesture;

/**
 * ListView Activity of File Browser app.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileListActivity extends ListActivity 
		implements 
			FileListUI, 
			OnSimpleGesture, 
			OnItemClickListener, 
			OnItemSelectedListener, 
			OnScrollListener, 
			SharedPreferences.OnSharedPreferenceChangeListener, 
			OnLongClickListener, 
			OnSetThumbnail {
	@SuppressWarnings("unused")
	private static final String TAG = "BITS.FileBrowser.ListActivity";
	protected BitsReportAnExceptionHandler mDamageReport = new BitsReportAnExceptionHandler(this,R.string.portmortem_report_emailmsg);
	protected FileBrowserCore mCore = null;
	private int mSoftTop = 24;
	protected static final int VIEW_TYPE_LIST = 0;
	protected static final int VIEW_TYPE_GRID = 1;
	protected int mCurrViewType = VIEW_TYPE_LIST;
	
	//view shortcuts
	protected ViewGroup mLayoutGrid = null;
	protected GridView mGridView = null;
	protected ViewGroup mLayoutGridDetail = null;
	protected int mGridDetailItemPos = AbsListView.INVALID_POSITION;
	protected FileListDataElement mGridDetailItem = null;
	protected ViewSwitcher mSwitchViewType = null;
	
	@Override
	public boolean doesBackKeyExit() {
		return mCore.mBackKeyExits || mCore.mDsFileList.mCurrPath.equals(File.separator);
	}
	
	/**
	 * Create JumpPoint shortcut is requested.
	 * 
	 * @param aIntent - intent describing the request
	 */
	public void extReq_Shortcut(Intent aIntent) {
		mCore.extReq_pickFile(this,aIntent);
		mCore.mPickItem = true;
		mCore.mPickShortcut = true;
	}
	
	@Override
	public int getChiefVisibleItemPos() {
		//make note of "first most visible item" before checking "selected item"
		//  else pointToPosition will return wrong result if done after getSelected
		int theFirstItemPos = getFirstVisibleItemPos();
		int theLastItemPos = getLastVisibleItemPos();
		int theSelectedItemPos;
		if (mCurrViewType==VIEW_TYPE_LIST) 
			theSelectedItemPos = getListView().getSelectedItemPosition();
		else
			theSelectedItemPos = mGridView.getSelectedItemPosition();
		// if nothing is selected, return avg between first and last
		if (theSelectedItemPos!=AdapterView.INVALID_POSITION)
			return theSelectedItemPos;
		else
			if (theFirstItemPos!=AdapterView.INVALID_POSITION)
				if (theLastItemPos!=AdapterView.INVALID_POSITION)
					return theFirstItemPos+((theLastItemPos-theFirstItemPos)/2); //midpoint between first and last	
				else
					return theFirstItemPos;
			else
				if (theLastItemPos!=AdapterView.INVALID_POSITION)
					return theLastItemPos;
				else
					return 	AdapterView.INVALID_POSITION;
	}
	
	public AbsListView getFileListingView() {
		if (mCurrViewType==VIEW_TYPE_LIST) 
			return getListView();
		else
			return mGridView;
	}
	
	@Override
	public int getFirstVisibleItemPos() {
		//getFirstVisiblePosition() is totally unreliable!
		AbsListView theView;
		int theTopPadding;
		if (mCurrViewType==VIEW_TYPE_LIST) {
			theView = getListView();
			theTopPadding = (mSoftTop*3/2);
		} else {
			theView = mGridView;
			theTopPadding = 1;
		}
		Point p = new Point(theView.getListPaddingLeft()+1,theView.getListPaddingTop()+theTopPadding);
		return theView.pointToPosition(p.x,p.y);
	}

	@Override
	public int getLastVisibleItemPos() {
		if (mCurrViewType==VIEW_TYPE_LIST) 
			return getListView().getLastVisiblePosition();
		else
			return mGridView.getLastVisiblePosition();
	}
	
	public View getViewFromItemPosition(int aPosition) {
		AbsListView theView = getFileListingView();
		return theView.getChildAt(aPosition - getFirstVisibleItemPos());
	}

	@Override
	public void invalidateViews() {
		if (mCurrViewType==VIEW_TYPE_LIST)
			getListView().invalidateViews();
		else {
			mGridView.invalidateViews();
			if (mGridDetailItemPos!=AbsListView.INVALID_POSITION) 
				updateGridDetail(mGridDetailItemPos);
		}
		BitsLegacy.invalidateOptionsMenu(this);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		/* Maybe the following IF statement will protect against:
		 * java.lang.RuntimeException: Unable to resume activity 
		 * {com.blackmoonit.android.FileBrowser/com.blackmoonit.android.FileBrowser.FileListActivity}: 
		 * java.lang.RuntimeException: Failure delivering result 
		 * ResultInfo{who=null, request=2131296384, result=-1, data=Intent 
		 * { act=android.intent.action.PICK dat=directory:///ext_card/kk 
		 *   cmp=com.blackmoonit.android.FileBrowser/.FileListActivity (has extras) }} 
		 * to activity 
		 * {com.blackmoonit.android.FileBrowser/com.blackmoonit.android.FileBrowser.FileListActivity}: 
		 * java.lang.NullPointerException
		 * --------- Activity Stack Trace ---------
		 * 1. FileListActivity (Blackmoon File Browser)
		 * 2. FileListActivity (Please select a folder)
		 * 3. null (this activity has been destroyed already)
		 */
		if (this!=null && mCore!=null) {
			mCore.onActivityResult(this,requestCode,resultCode,data);
		}
	}

	@Override
	public void onAfterFillList(int aItemPos) {
		//Toast.makeText(this,(mCore.mAutoSelectName!=null)?mCore.mAutoSelectName:"#"+aFirstVisibleItemPos,Toast.LENGTH_SHORT).show();
		if (mCore.mAutoSelectName!=null) {
			int thePos = mCore.mDsFileList.indexOfName(mCore.mAutoSelectName);
			if (thePos>0)
				aItemPos = thePos;
			mCore.mAutoSelectName = null; //reset the autoselect
		}
		if (aItemPos>AbsListView.INVALID_POSITION) {
			setSelectionFromTop(aItemPos);
		}
		if (mCurrViewType==VIEW_TYPE_GRID)
			updateGridDetail(aItemPos);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		//overriding this method plus adding android:configChanges="keyboardHidden|orientation"
		//  attr to the Activity prevents destroy/recreate on rotation change
		mCore.onConfigurationChanged(this,newConfig);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem aMenuItem) {
		return mCore.onContextItemSelected(this,aMenuItem) || super.onContextItemSelected(aMenuItem);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mCore = new FileBrowserCore(this,this);
		setup();
		processExternalRequest(getIntent());

	}

	@Override
	public void onCreateContextMenu(ContextMenu aMenu, View aView, ContextMenuInfo aMenuInfo) {
		super.onCreateContextMenu(aMenu,aView,aMenuInfo);
		mCore.onCreateContextMenu(this,aMenu,aView,aMenuInfo);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog d = mCore.onCreateDialog(this,id);
		if (d!=null)
			return d;
		else
			return super.onCreateDialog(id);
	}

	@Override 
	public boolean onCreateOptionsMenu(Menu aMenu) {
		try {
			return super.onCreateOptionsMenu(aMenu) && mCore.onCreateOptionsMenu(this,aMenu);
		} catch (OutOfMemoryError oom) {
			onLowMemory();
			return super.onCreateOptionsMenu(aMenu) && mCore.onCreateOptionsMenu(this,aMenu);
		}
	}

	@Override
	protected void onDestroy() {
		if (mCore.mSettings!=null) {
			mCore.mSettings.unregisterOnSharedPreferenceChangeListener(this);
		}
		mCore.onExitApp();
		mDamageReport.cleanup();
		mDamageReport = null;
		super.onDestroy();
	}

	@Override
	public void onItemClick(AdapterView<?> g, View v, int position, long id) {
		//get the item clicked on
		if (position<0 || position>=mCore.mGridAdapter.getCount())
			return;
		FileListDataElement theItem = mCore.mGridAdapter.getItem(position);
		if (mCore.mItemClickPerforms!=FileBrowserCore.ITEMCLICK_OPENFILE) {
			//if not normal operation, perform click and update detail
			mCore.onListItemClick(position);
			updateGridDetail((mCore.mItemClickPerforms!=FileBrowserCore.ITEMCLICK_MARK)?position:-1);
		} else if (mGridDetailItemPos!=position && theItem!=null && theItem.isFile()) {
			//if item is a file and we do not have is selected, select it
			mCore.mCurrItemPos = position;
			updateGridDetail(position);
		} else {
			//the item is either a file we already have selected or a folder, do a normal click
			mCore.onListItemClick(position);
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> aGridView, View v, int position, long id) {
		if (mCurrViewType==VIEW_TYPE_GRID) {
			updateGridDetail(position);
		}
	}

	@Override
	public boolean onKeyDown(int aKeyCode, KeyEvent aKeyEvent) {
		return mCore.onKeyDown(this,aKeyCode,aKeyEvent) || super.onKeyDown(aKeyCode,aKeyEvent);
	}
	
	@Override
	public boolean onKeyUp(int aKeyCode, KeyEvent aKeyEvent) {
		return mCore.onKeyUp(this,aKeyCode,aKeyEvent) || super.onKeyUp(aKeyCode,aKeyEvent);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		mCore.onListItemClick(position);
	}
	
	@Override
	public boolean onLongClick(View v) {
		if (mGridDetailItemPos!=AbsListView.INVALID_POSITION) {
			View theCellView = getViewFromItemPosition(mGridDetailItemPos);
			if (theCellView!=null) {
				openContextMenu(theCellView);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mCore.onLowMemory();
	}
	
	@Override
	public boolean onMenuItemSelected(int aFeatureId, MenuItem aMenuItem) {
		final int theMenuChoice = aMenuItem.getItemId();
		switch (theMenuChoice) {
			case R.id.menu_item_viewtype_list: {
				setViewType(VIEW_TYPE_LIST);
				return true;
			}
			case R.id.menu_item_viewtype_grid: {
				setViewType(VIEW_TYPE_GRID);				
				return true;
			}
		}
		return mCore.onMenuItemSelected(this,aFeatureId,aMenuItem) ||
				super.onMenuItemSelected(aFeatureId,aMenuItem);
	}

	@Override
	public boolean onMouseButton(MotionEvent aEvent, int aButtonState) {
		if (aButtonState==BitsLegacy.BUTTON_SECONDARY || aButtonState==BitsLegacy.BUTTON_TERTIARY) {
			AbsListView theView = getFileListingView();
			int i = theView.pointToPosition(Math.round(aEvent.getX()),Math.round(aEvent.getY()));
			if (i>=0 && i<mCore.mDsFileList.size()) {
				this.onLongClick(theView.findViewById(i));
				return true; //no further processing of swipe event
			} else
				return false;
		}
		return false;
	}

	@Override
	public void onNothingSelected(AdapterView<?> aGridView) {
		// no need to do anything
		//if (mLayoutGrid.getVisibility()==View.VISIBLE) {
		//	updateGridDetail(AbsListView.INVALID_POSITION);
		//}
	};
	
	@Override
	protected void onPause() {
		saveCurrentItem();
		mCore.onPause(this);
		mDamageReport.suspend();
		super.onPause();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu aMenu) {
		//view type
		aMenu.findItem(R.id.menu_item_viewtype_list).setVisible(mCurrViewType==VIEW_TYPE_GRID);
		aMenu.findItem(R.id.menu_item_viewtype_grid).setVisible(mCurrViewType==VIEW_TYPE_LIST);
		
		return mCore.onPrepareOptionsMenu(aMenu) && super.onPrepareOptionsMenu(aMenu);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle aState) {
		super.onRestoreInstanceState(aState);
		mCore.onRestoreInstanceState(this,aState);
	}
	
	@Override
	protected void onResume() {
		mDamageReport.resume();
		super.onResume();
		mCore.onResume(this);
		mGridView.postDelayed(new Runnable() {
			@Override
			public void run() {
				updateGridDetail(mGridDetailItemPos);
			}					
		},10);

	}
	
	@Override
	protected void onRestart() {
		mDamageReport.resume();
		mCore.onRestart(this);
		super.onRestart();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		mCore.onSaveInstanceState(this,outState);
	}

	@Override
	public boolean onScroll(MotionEvent aMotionEvent, float aDirection) {
		//let the ListView handle the scroll events
		return false;
	}
	
	@Override
	public void onScroll(AbsListView v, int arg1, int arg2, int arg3) {
		//nothing to do, needed for interface though
	}

	@Override
	public void onScrollStateChanged(AbsListView v, int arg1) {
		updateGridDetail(AbsListView.INVALID_POSITION);
	}

	@Override
	public boolean onSearchRequested() {
		return mCore.onSearchRequested(this);
	}

	/**
	 * Initialize window features and return the layout resource for the list content view.
	 * 
	 * @return The layout ID to be used in {@link setContentView}
	 */
	protected int onSetContentView() {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        return R.layout.fb_list;
	}
	
	@Override
	public void onSetThumbnail(ImageView v, Drawable d) {
		if (v==null)
			return;
		v.setImageDrawable(d);
		if (v.getId()==R.id.ImageFileThumb && d!=null) {
			try {
				ViewParent theLayout = v.getParent().getParent();
				if (theLayout!=null && theLayout instanceof LinearLayout) {
					if ( (getResources().getDisplayMetrics().widthPixels-d.getIntrinsicWidth()) < 
							(mCore.mFileIcons.origIconSize*2)) {
						((LinearLayout)theLayout).setOrientation(LinearLayout.VERTICAL);
					} else {
						((LinearLayout)theLayout).setOrientation(LinearLayout.HORIZONTAL);
					}
				}
			} catch (Exception e) {
				//skip
			}
		}
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences aSettings, String aPrefKey) {
		mCore.onSharedPreferenceChanged(aSettings, aPrefKey);
	}

	@Override
	protected void onStart() {
		super.onStart();
		mCore.onStart(this);
	}

	@Override
	protected void onStop() {
		mCore.onStop(this);
		super.onStop();
	}

	public boolean onSwipe(MotionEvent aEvent, float aDirection) {
		AbsListView theView = getFileListingView();
		//horizontal swipe, left/right doesn't matter, lefty friendly
		//convert event to item index
		int i = theView.pointToPosition(Math.round(aEvent.getX()),Math.round(aEvent.getY()));
		if (i>=0 && i<mCore.mDsFileList.size()) {
			this.closeContextMenu();
			mCore.sendFile(mCore.mDsFileList.get(i));
			return true; //no further processing of swipe event
		} else
			return false;
	}
	
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		if (mCurrViewType==VIEW_TYPE_LIST) {
			return super.onTrackballEvent(event);
		} else {
			mGridView.requestFocusFromTouch();
			Thread.yield();
			return mGridView.onTrackballEvent(event);
		}
	}
	
	/*
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		//note, this event does NOT fire when being installed and run for first time
		mCore.onWindowFocusChanged(hasFocus);
		super.onWindowFocusChanged(hasFocus);
	}
	*/

	@Override
	public void processExternalRequest(Intent aIntent) {
		String theAction = null;
		String theScheme = null;
		if (aIntent!=null) {
			theAction = aIntent.getAction();
			theScheme = aIntent.getScheme();
		}
		if (theAction!=null) {
			if (theAction.equals(Intent.ACTION_PICK) || theAction.equals(BitsIntent.ACTION_OI_PICK_FILE) ||
					theAction.equals(Intent.ACTION_GET_CONTENT)) {
				//Toast.makeText(this,"PICKING!",Toast.LENGTH_SHORT);
				if (theAction.equals(Intent.ACTION_GET_CONTENT) || BitsIntent.isSchemeFile(theScheme)) {
					mCore.extReq_pickFile(this,aIntent);
				} else if (BitsIntent.isSchemeFolder(theScheme)) {
					mCore.extReq_pickFolder(this,aIntent);
				}

			} else if (theAction.equals(Intent.ACTION_CREATE_SHORTCUT)) {
				extReq_Shortcut(aIntent);
	        } else if (theAction.equals(BitsIntent.ACTION_OI_PICK_FOLDER)) {
				mCore.extReq_pickFolder(this,aIntent);	        	
	        }
			mCore.processExternalRequest(this,aIntent);
		}
	}

	/**
	 * Refreshes the list view to reflect changes in it's internal list.
	 * Any file system changes are ignored.
	 */
	public void refreshListView() {
		//onContentChanged(); -- causes Exception if refreshing a non-empty view to become empty
		if (mCore.mListAdapter!=null)
			mCore.mListAdapter.notifyDataSetChanged();
		if (mCore.mGridAdapter!=null) {
			mCore.mGridAdapter.notifyDataSetChanged();
			/*
			if (mCurrViewType==VIEW_TYPE_GRID && mGridDetailItemPos!=AdapterView.INVALID_POSITION) {
				try {
					if (mGridDetailItem.equals(mCore.mGridAdapter.getItem(mGridDetailItemPos)))
						updateGridDetail(mGridDetailItemPos);
					else
						updateGridDetail(AdapterView.INVALID_POSITION);
				} catch (Exception e) {
					updateGridDetail(AdapterView.INVALID_POSITION);
				}
			}
			*/
		}
		invalidateViews();
	}

	protected void saveCurrentItem() {
		if (!mCore.mPickFolder) {
			mCore.saveCurrentItem(this);
		}
	}

	@Override
	public void scrollToPosition(final int anItemPos) {
		if (anItemPos!=AdapterView.INVALID_POSITION) {
			int theFirstItemPos = getFirstVisibleItemPos();
			int theLastItemPos = getLastVisibleItemPos();
			if (anItemPos<theFirstItemPos || theLastItemPos<anItemPos) {
				if (mCurrViewType==VIEW_TYPE_LIST) {
					getListView().postDelayed(new Runnable() {
						@Override
						public void run() {
							getListView().setSelection(anItemPos);
							getListView().clearFocus();
						}					
					},10);
				} else {
					mGridView.postDelayed(new Runnable() {
						@Override
						public void run() {
							mGridView.setSelection(anItemPos);
							mGridView.clearFocus();
						}					
					},10);
				}
			}
		}
	}

	@Override
	public void setFontSize() {
		ListView l = getListView();
		if (l!=null) {
			for (int i=0; i<l.getChildCount(); i++) {
				mCore.mListAdapter.applyFontSize(l.getChildAt(i));
			}
		}
		
		GridView g = mGridView;
		if (g!=null) {
			for (int i=0; i<g.getChildCount(); i++) {
				mCore.mGridAdapter.applyFontSize(g.getChildAt(i));
			}
		}
		
		if (mGridDetailItem!=null)
			mCore.mListAdapter.applyFontSize(mLayoutGridDetail);

		TextView tv = (TextView)findViewById(R.id.EmptyGridView);
		if (tv!=null && mCore.mFontSize>0)
			tv.setTextSize(mCore.mFontSize);
	}

	@Override
	public void setSelectionFromTop(final int anItemPos) {
		if (anItemPos!=AdapterView.INVALID_POSITION) {
			if (mCurrViewType==VIEW_TYPE_LIST) {
				getListView().postDelayed(new Runnable() {
					@Override
					public void run() {
						getListView().setSelectionFromTop(anItemPos,mSoftTop);
					}
				},10);
			} else {
				mGridView.postDelayed(new Runnable() {
					@Override
					public void run() {
						mGridView.setSelection(anItemPos);
					}
				},10);
			}
			
		}
	}
	
	static protected class MyHandler extends FileBrowserProgressHandler {
		WeakReference<FileListActivity> mAct;
		
		public MyHandler(FileListActivity aAct) {
			super(aAct);
			mAct = new WeakReference<FileListActivity>(aAct);
		}
		
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			FileListActivity theAct = mAct.get();
			theAct.mCore.handleMessage(theAct,msg);
			switch (msg.what) {
				case FileBrowserCore.MSG_ENABLE_THUMBNAILS: {
					theAct.getListView().invalidateViews();
					break;
				}
			}//switch
			Thread.yield();
		}

	}

	protected void setup() {
		mDamageReport.setup();
		AppPreferences.setDefaultPrefs(this);

		mCore.msgHandler = new MyHandler(this);

		mCore.setup(this);
		mCore.mFileIcons.mOnSetThumbnail = this;
		
		setContentView(onSetContentView());

		mCore.setupUI(this);

		//more UI setup
		mLayoutGrid = (ViewGroup)findViewById(R.id.LayoutGrid);
		
		mGridView = (GridView)findViewById(R.id.FileGridView);
		mGridView.setEmptyView(findViewById(R.id.EmptyGridView));
		
		//grid listeners
		mGridView.setOnCreateContextMenuListener(this);
		mGridView.setOnItemClickListener(this);
		mGridView.setOnScrollListener(this);
		mGridView.setOnItemSelectedListener(this);
		
		mLayoutGridDetail = (ViewGroup)((ViewStub)findViewById(R.id.LayoutGridDetail_stub)).inflate();
		mLayoutGridDetail.setBackgroundResource(R.drawable.border_simple_rounded);
		mLayoutGridDetail.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				updateGridDetail(AbsListView.INVALID_POSITION);
			}
		});
		mLayoutGridDetail.setOnLongClickListener(this);
		updateGridDetail(AbsListView.INVALID_POSITION);
		View v = mCore.mListAdapter.getViewHandle(mLayoutGridDetail, R.id.ImageFileThumb);
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mCore.onListItemClick(mGridDetailItemPos);
			}
		});
		((View)v.getParent()).setTag("GridDetail");
		
		mSwitchViewType = (ViewSwitcher)findViewById(R.id.SwitcherViewType);
		mSwitchViewType.setDisplayedChild(0);
		
		mSoftTop = Math.max(mCore.mButtonGoUp.getHeight()/2,mSoftTop);
		
		//tie the UI with the data adapters
		setListAdapter(mCore.mListAdapter);
		mGridView.setAdapter(mCore.mGridAdapter);

		//prefs
		if (mCore.getPrefs(this)) {
			setViewType(mCore.mSettings.getInt(getString(R.string.pref_key_viewtype),mCurrViewType));

			// register the listener to detect preference changes
			mCore.mSettings.registerOnSharedPreferenceChangeListener(this);
		}
		
		//gesture detectors
		SimpleGestureHandler sgHandler = new SimpleGestureHandler(this,this);
		getListView().setOnTouchListener(sgHandler);
		mGridView.setOnTouchListener(sgHandler);
		SimpleGestureHandler sgHandlerForGridDetail = new SimpleGestureHandler(this,
				new OnSimpleGesture() {
			@Override
			public boolean onSwipe(MotionEvent aMotionEvent, float aDirection) {
				if (mGridDetailItem!=null) {
					mCore.sendFile(mGridDetailItem);
					return true;
				} else
					return false;
			}
			
			@Override
			public boolean onScroll(MotionEvent aMotionEvent, float aDirection) {
				if (aDirection<0) {
					updateGridDetail(AbsListView.INVALID_POSITION);
					return true;
				} else
					return false;
			}

			@Override
			public boolean onMouseButton(MotionEvent aMotionEvent, int aButtonState) {
				return false;
			}
		});
		mLayoutGridDetail.setOnTouchListener(sgHandlerForGridDetail);
		mCore.mListAdapter.getViewHandle(mLayoutGridDetail, R.id.ImageFileThumb).setOnTouchListener(sgHandlerForGridDetail);
		
		//recycled list/grid views should also recycle their icons
		getListView().setRecyclerListener(new AbsListView.RecyclerListener() {
			@Override
			public void onMovedToScrapHeap(View aItemView) {
				if (mCore!=null && mCore.mListAdapter!=null)
					mCore.mListAdapter.onRecycleView(aItemView);
			}
		});
		mGridView.setRecyclerListener(new AbsListView.RecyclerListener() {
			@Override
			public void onMovedToScrapHeap(View aItemView) {
				if (mCore!=null && mCore.mGridAdapter!=null)
					mCore.mGridAdapter.onRecycleView(aItemView);
			}
		});
		
        //tell the list we will make our own context menu for items
		registerForContextMenu(getListView());
		registerForContextMenu(mGridView);
		setProgressBarIndeterminateVisibility(false);
	}

	public void setViewType(int aViewType) {
		if (mCurrViewType!=aViewType) {
			final int thePos = getChiefVisibleItemPos();
			if (aViewType==VIEW_TYPE_LIST) {
				mSwitchViewType.setDisplayedChild(VIEW_TYPE_LIST);
				Thread.yield();
				mLayoutGridDetail.setVisibility(View.GONE);
			} else {
				mSwitchViewType.setDisplayedChild(VIEW_TYPE_GRID);
				Thread.yield();
				updateGridDetail(thePos);
			}
			mCurrViewType = aViewType;

			//save view type in settings
			String thePrefKey = getString(R.string.pref_key_viewtype);
			AppPreferences.applyAndBackup(this,mCore.mSettings.edit().putInt(thePrefKey,mCurrViewType));

			scrollToPosition(thePos);
			
			BitsLegacy.invalidateOptionsMenu(this);
		}
	}

	public void updateGridDetail(int position) {
		if (mCurrViewType!=VIEW_TYPE_GRID || mCore==null || position<0 || mCore.mGridAdapter==null || 
				position>=mCore.mGridAdapter.getCount()) {
			mLayoutGridDetail.setVisibility(View.GONE);
			mGridDetailItemPos = AbsListView.INVALID_POSITION;
			mGridDetailItem = null;
			return;
		}
		mGridDetailItemPos = position;
		mGridDetailItem = mCore.mGridAdapter.getItem(position);
		mCore.mListAdapter.applyItemView(mGridDetailItem,mLayoutGridDetail);
		mLayoutGridDetail.getLayoutParams().height = 
				+mCore.mListAdapter.getViewHandle(mLayoutGridDetail,R.id.fb_item_filename).getLayoutParams().height
				+mCore.mListAdapter.getViewHandle(mLayoutGridDetail,R.id.fb_item_filefolder).getLayoutParams().height
				+mCore.mListAdapter.getViewHandle(mLayoutGridDetail,R.id.fb_item_fileinfo).getLayoutParams().height
				;
		mLayoutGridDetail.invalidate();
		mLayoutGridDetail.setVisibility(View.VISIBLE);
		mGridView.postDelayed(new Runnable() {
			@Override
			public void run() {
				scrollToPosition(mGridDetailItemPos);
			}
		},10);
	}

}
