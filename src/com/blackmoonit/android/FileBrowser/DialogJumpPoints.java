package com.blackmoonit.android.FileBrowser;

import java.io.File;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageButton;

import com.blackmoonit.concurrent.BitsThread;
import com.blackmoonit.dialog.DialogConfirm;
import com.blackmoonit.dialog.DialogQuickEdit;
import com.blackmoonit.filesystem.BitsFileUtils;

/**
 * Dialog for accessing jump points. Removing them is also possible here.
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class DialogJumpPoints extends Dialog implements android.view.View.OnClickListener, 
		OnItemClickListener {
	private static final String TAG = "BITS.FileBrowser.DialogJumpPoints";

	protected final Activity mAct;
	protected final SharedPreferences mSettings;
	protected final FileBrowserCore mCore;
	protected int mFontSize = 0;

	protected Cursor mCursor = null;
	protected static final String[] JUMP_POINT_PROJECTION = new String[] {
			JumpPointProvider.JUMP_ID,
			JumpPointProvider.JUMP_NAME,
			JumpPointProvider.JUMP_DATA,
	};
	protected static final int JUMP_ID_IDX = 0;
	protected static final int JUMP_NAME_IDX = 1;
	protected static final int JUMP_DATA_IDX = 2;
	
	protected JumpPointAdapter mJumpPointAdapter = null;
	protected String[] ADAPTER_COLUMNS = new String[] {
	    	JumpPointProvider.JUMP_NAME, 
	};
	protected int[] ADAPTER_COLUMN_VIEW_MAP = new int[] {
	    	R.id.fb_cell_filename, 
	};
	
	protected static final int menu_item_rename_jumppoint = Menu.FIRST+1;
	protected static final int menu_item_delete_jumppoint = Menu.FIRST+2;
	
	protected ImageButton mRecycleBin = null;
	protected GridView mGridView = null;
	
	
	public DialogJumpPoints(Activity aContext, FileBrowserCore aCore) {
		super(aContext);
		mAct = aContext;
		mCore = aCore;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dialog_jumppoints);
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
	
    ContentObserver jumpPointsChanged = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
        	requeryJumpPoints();
        }
    };

	protected void setup() {
		//Recycle bin button
		mRecycleBin = (ImageButton)findViewById(R.id.dialog_jumppoints_ButtonGoRecycleBin);
		mRecycleBin.setOnClickListener(this);
		mRecycleBin.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View aImgButton) {
				new DialogConfirm(getContext()).setup(R.string.dialog_confirm_delete_title,
						getContext().getString(R.string.dialog_confirm_delete_recyclebin),
						new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								mRecycleBin.setImageResource(R.drawable.icon_recyclebin_empty);
								mCore.emptyRecycleBin();
							}
						}).show();
				return true;
			}
		});
		
		//SD card button
		View theSDcard = findViewById(R.id.dialog_jumppoints_ButtonGoSDcard);
		theSDcard.setOnClickListener(this);
		
		//jump point display
		mGridView = (GridView)findViewById(R.id.dialog_jumppoints_GridView);
		mGridView.setOnItemClickListener(this);
		registerForContextMenu(mGridView);
		
		mCursor = getContext().getContentResolver().query(JumpPointProvider.CONTENT_URI,
				JUMP_POINT_PROJECTION, null, null, null);
		mCursor.registerContentObserver(jumpPointsChanged);
		mJumpPointAdapter = new JumpPointAdapter(getContext(),R.layout.fb_cell,mCursor,
				ADAPTER_COLUMNS,ADAPTER_COLUMN_VIEW_MAP,mCore.mFileIcons);
		if (mFontSize>0)
			mJumpPointAdapter.setTextSize(mFontSize);
		if (mGridView!=null)
			mGridView.setAdapter(mJumpPointAdapter);
		
	}
	
	private void requeryJumpPoints() {
		mAct.runOnUiThread(new BitsThread() { 
			@Override
			public void runTask() throws InterruptedException {
				mCursor.requery();
				if (mJumpPointAdapter!=null) {
					mJumpPointAdapter.notifyDataSetChanged();
				}
				
			} 
		});
	}

	@Override
	public void show() {
		if (mRecycleBin!=null) {
			if (BitsFileUtils.isRecycleBinEmpty(getContext())) {
				mRecycleBin.setImageResource(R.drawable.icon_recyclebin_empty);
			} else {
				mRecycleBin.setImageResource(R.drawable.icon_recyclebin);
			}
		}
		super.show();
	}

	@Override
	protected void finalize() throws Throwable {
		if (mCursor!=null) {
			mCursor.deactivate();
			mCursor.close();
			mCursor = null;
		}
		super.finalize();
	}

	@Override
	public void onClick(View aView) {
		File theFile = null;
		switch (aView.getId()) {
			case R.id.dialog_jumppoints_ButtonGoRecycleBin:
				theFile = BitsFileUtils.getRecycleBin(getContext());
				break;
			case R.id.dialog_jumppoints_ButtonGoSDcard:
				theFile = Environment.getExternalStorageDirectory();
				break;
		}
		if (theFile!=null)
			mCore.fillList(theFile);
		dismiss();
	}

	@Override
	public void onItemClick(AdapterView<?> aAdapterView, View aView, int aPosition, long aId) {
		File theFile = null;
		if (mCursor.moveToPosition(aPosition)) {
			theFile = new File(mCursor.getString(JUMP_DATA_IDX));
		}
		if (theFile!=null)
			mCore.fillList(theFile);
		dismiss(); //mCursor may be null after this call, make sure we get pertinent data first!
	}

	@Override
	public void onCreateContextMenu(ContextMenu aMenu, View v, ContextMenuInfo aMenuInfo) {
		AdapterView.AdapterContextMenuInfo theMenuInfo = null;
		try {
			theMenuInfo = (AdapterView.AdapterContextMenuInfo) aMenuInfo;
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return;
		}
		if (theMenuInfo!=null && mCursor.moveToPosition(theMenuInfo.position))
			aMenu.setHeaderTitle(mCursor.getString(JUMP_NAME_IDX));
		aMenu.add(Menu.NONE,menu_item_rename_jumppoint,1,R.string.menu_item_rename);
		aMenu.add(Menu.NONE,menu_item_delete_jumppoint,2,R.string.menu_item_delete);
	}

	@Override
	public boolean onMenuItemSelected(int aFeatureId, MenuItem aMenuItem) {
		//Android bug, this is supposed to happen by default, but does not for Dialogs as of 3.0
		if (aFeatureId==Window.FEATURE_CONTEXT_MENU)
			return onContextItemSelected(aMenuItem);
		else
			return super.onMenuItemSelected(aFeatureId, aMenuItem);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem aMenuItem) {
		AdapterView.AdapterContextMenuInfo theMenuInfo;
		try {
			theMenuInfo = (AdapterView.AdapterContextMenuInfo) aMenuItem.getMenuInfo();
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return false;
		}
		if (theMenuInfo!=null && mCursor.moveToPosition(theMenuInfo.position)) {
			final Long theRowId = mCursor.getLong(JUMP_ID_IDX);
			final String theName = mCursor.getString(JUMP_NAME_IDX);
			final String theData = mCursor.getString(JUMP_DATA_IDX);
			final Uri theItemUri = ContentUris.withAppendedId(JumpPointProvider.CONTENT_URI,theRowId);
			final int theMenuChoice = aMenuItem.getItemId();
			switch (theMenuChoice) {
				case menu_item_rename_jumppoint:
					final DialogQuickEdit dRename = new DialogQuickEdit(getContext());
					View.OnClickListener onRenameJumpPoint = new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							String s = dRename.getTrimmedText();
							if (s!=null && !s.equals("")) {
								ContentValues theValues = new ContentValues(2);
								theValues.put(JumpPointProvider.JUMP_DATA,theData);
								theValues.put(JumpPointProvider.JUMP_NAME,s);
								getContext().getContentResolver().update(theItemUri,theValues,null, null);
							}								
						};
					};
					dRename.setup(R.string.dialog_rename_title,theName,theName.length(),
							theName.length(),onRenameJumpPoint).show();
					return true;
				case menu_item_delete_jumppoint:
					final DialogConfirm dConfirm = new DialogConfirm(getContext());
					View.OnClickListener onDeleteJumpPoint = new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							getContext().getContentResolver().delete(theItemUri,null, null);
						};
					};
					dConfirm.setup(R.string.dialog_confirm_delete_title,
							getContext().getString(R.string.dialog_confirm_delete_text,theName),
							onDeleteJumpPoint);
					dConfirm.show();
					return true;
			}
		}
		return super.onContextItemSelected(aMenuItem);
	}

}
