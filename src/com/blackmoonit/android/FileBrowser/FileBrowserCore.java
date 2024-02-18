package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.blackmoonit.app.BitsDialog;
import com.blackmoonit.concurrent.BitsClickTask;
import com.blackmoonit.concurrent.BitsThreadTask;
import com.blackmoonit.concurrent.ModalVar;
import com.blackmoonit.concurrent.OnClickTask;
import com.blackmoonit.content.BitsIntent;
import com.blackmoonit.dialog.DialogAbout;
import com.blackmoonit.dialog.DialogConfirm;
import com.blackmoonit.dialog.DialogFileCollision;
import com.blackmoonit.dialog.DialogQuickEdit;
import com.blackmoonit.filesystem.BitsFileUtils;
import com.blackmoonit.filesystem.BitsFileUtils.OnEachFile;
import com.blackmoonit.filesystem.FileOrchard;
import com.blackmoonit.filesystem.FilePackageZip;
import com.blackmoonit.filesystem.FilePackageZip.OnEachEntry;
import com.blackmoonit.filesystem.FilePackageZip.OnEachFileEntry;
import com.blackmoonit.filesystem.FilePackageZip.OnException;
import com.blackmoonit.filesystem.Folder;
import com.blackmoonit.filesystem.MIMEtypeMap;
import com.blackmoonit.filesystem.ProgressBarHandler;
import com.blackmoonit.graphics.BitsGraphicUtils;
import com.blackmoonit.lib.BitsLegacy;
import com.blackmoonit.lib.BitsStringUtils;
import com.blackmoonit.media.BitsThumbnailUtils;

/**
 * Core code of File Browser app.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class FileBrowserCore {
	private static final String TAG = "BITS.FileBrowser.Core";
	public static final String PERSISTANT_STATE_FILE = "stateSave_FileBrowser";
	
	// This is our state data that is stored when freezing.
	public static final String SAVE_CURRENT_FOLDER = "currentFolder";
	public static final String SAVE_CURRENT_ITEM = "itemName";
	public static final String SAVE_CLICK_MODE = "itemclickmode";
	public static final String SAVE_MARKED_FILES = "markedFiles";
	public static final String SAVE_RELOAD_MARKED = "reloadMarked";
    
	// onItemClick may perform differently depending on state
	public static final int ITEMCLICK_OPENFILE = 1;
	public static final int ITEMCLICK_MARK = 2;
	public static final int ITEMCLICK_PICKFILE = 3;
	public static int ITEMCLICK_DEFAULT = ITEMCLICK_OPENFILE;
    
	// result codes we wish to act upon
	private static final int RC_UNZIP	= Activity.RESULT_FIRST_USER + 0;
	private static final int RC_SEND	= Activity.RESULT_FIRST_USER + 1;
    
	//dialog constants
	private static final int DIALOG_ABOUT = 1;
	private static final int DIALOG_SEARCH_TIPS = 2;
	
	//fields
	private WeakReference<Activity> mAct = null;
	public FileBrowserProgressHandler msgHandler = null;
	protected FileListDataSource mDsFileList;
    protected FileListAdapter mListAdapter;
    protected FileGridAdapter mGridAdapter;
	protected String mAutoSelectName = null;
    protected String mHomeFolder = Environment.getExternalStorageDirectory().getPath();
	protected String mFolderName = mHomeFolder;
	protected int mCurrItemPos = AdapterView.INVALID_POSITION;
	private Long mLastBackPress = System.currentTimeMillis();
	private boolean mTrackSoftBackKey = false;
	public int mItemClickPerforms = ITEMCLICK_DEFAULT;
	protected FileOrchard mMarkedFiles = null;
	protected MIMEtypeMap mMimeMap = new MIMEtypeMap().createMaps();
	protected FileIcons mFileIcons;
	private int mParentContextMenuFileIndex = 0;
	
	//settings related
	protected SharedPreferences mSettings = null;
	protected boolean mBackKeyExits = false;
	private boolean mSendClearsMarked = true;
    public static final int mDefaultFontSize = 12;
    /**
     * User defined setting for text size.  0 means no change from the default size.
     */
	public int mFontSize = 0;
	public boolean mUseRecycleBin = true;
	protected boolean bIsSearching = false;
	
	//layout shortcuts
	protected ImageButton mButtonGoUp = null;
	protected ImageButton mButtonGoJump = null;
	protected TextView mCurrFolderView = null;
	protected TextView mMemSpaceView = null;
	protected ImageView mMemLowSpaceWarning = null;
	protected ImageView mFolderReadOnlyView = null;
	protected TextView mEmptyFolderView = null;
	protected Button mButtonPickFile = null;
	protected ViewGroup mLayoutProgress = null;

	//pick related fields
	protected boolean mPickFolder = false;
	protected boolean mPickFile = false;
	protected boolean mPickFiles = false;
	protected boolean mPickItem = false;
	protected boolean mPickShortcut = false;
	protected boolean mPickFileShortcut = false; //if we're creating a file shortcut instead of jumppoint
	
	//Plugin related
	//private static final String SCHEME_PLUGIN = "plugin";
	//private static final String PLUGIN_PATH_MENU_CONTEXT = "menu_context";
	//private static final String PLUGIN_PATH_MENU_OPTIONS = "menu_options";
	//private static final String PLUGIN_PATH_MENU_ACTIONS = "menu_actions";

	//refresh listing based on some system events
	private BroadcastReceiver br = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (mDsFileList!=null)
				refillList();
		}
		
	};
	
	private FileListUI mFileListUI = null;
	protected File mRecycleBin = null;
	protected File mThumbnailCache = null;
	
	
	public FileBrowserCore(Activity aUIActivity, FileListUI aFileListUI) {
		super();
		mAct = new WeakReference<Activity>(aUIActivity);
		mFileListUI = aFileListUI;
	}

	public void setup(Activity anAct) {
		//BitsFileUtils.refreshExternalStorage(anAct);  removed for taking too long
		mFileIcons = new FileIcons(anAct,mMimeMap);
		mMarkedFiles = new FileOrchard();
		mDsFileList = new FileListDataSource(anAct,mMimeMap,mMarkedFiles);
		mListAdapter = new FileListAdapter(anAct,mDsFileList,mFileIcons);
		mGridAdapter = new FileGridAdapter(anAct,mDsFileList,mFileIcons);
		
		mRecycleBin = BitsFileUtils.getRecycleBin(anAct);
		mRecycleBin.mkdirs();
		mThumbnailCache = new File(BitsFileUtils.getCacheFolder(anAct),"thumbnails");
		if (!mThumbnailCache.exists())
			mThumbnailCache.mkdirs();
		BitsThumbnailUtils.mThumbnailCacheFolder = mThumbnailCache;

		BitsFileUtils.onOutOfSpaceEvent = new BitsFileUtils.OnEachFile() {
			final FileListDataSource mBin = new FileListDataSource(new FileComparatorDate(false,false),null);

			@Override
			public void beforeProcess(File srcFob, File destFob) {
				if (mThumbnailCache!=null && BitsFileUtils.freeSpace(destFob)<srcFob.length())
					compactTrash(mThumbnailCache,srcFob,destFob);
				if (mRecycleBin!=null && BitsFileUtils.freeSpace(destFob)<srcFob.length()) 
					compactTrash(mRecycleBin,srcFob,destFob);
			}
			
			protected void compactTrash(File aTrashFolder, File srcFob, File destFob) {
				mBin.fillList(aTrashFolder);
				for (FileListDataElement theTrashFile:mBin) {
					BitsFileUtils.deleteFob(theTrashFile,null,null,null);
					Thread.yield();
					if (BitsFileUtils.freeSpace(destFob)>srcFob.length())
						break;
				}
				destFob.getParentFile().mkdirs();
			}

			@Override
			public void afterProcess(File srcFob, File destFob) {
				//do nothing
			}
		};

	}
	
	public void setupUI(final Activity anAct) {
		anAct.setProgressBarIndeterminateVisibility(true);
		//typing on a keyboard will automatically launch the Search function
		//setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
		//---is bugged and will not call onSearchRequested() like it should.
		
		//header view initialization of listeners and non-data startup requirements
		mButtonGoUp = (ImageButton)anAct.findViewById(R.id.ButtonGoUp);
		mButtonGoUp.setOnClickListener(
				new Button.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (!mFolderName.equals(File.separator)) {
							fillList("..");
						}
					}
				});
		mButtonGoJump = (ImageButton)anAct.findViewById(R.id.ButtonGoJump);
		mButtonGoJump.setOnClickListener(
				new Button.OnClickListener() {
					@Override
					public void onClick(View v) {
						showJumpPointDialog(anAct);
					}						
				});
		mButtonGoJump.setLongClickable(true);
		mCurrFolderView = (TextView)anAct.findViewById(R.id.TextViewFolderName);
		mMemSpaceView = (TextView)anAct.findViewById(R.id.TextViewMemSpace);
		mMemLowSpaceWarning = (ImageView)anAct.findViewById(R.id.ImageViewLowSpaceWarning);
		mFolderReadOnlyView = (ImageView)anAct.findViewById(R.id.ImageViewFolderReadOnly);
		mEmptyFolderView = (TextView)anAct.findViewById(android.R.id.empty);
		anAct.findViewById(R.id.LayoutPickFolder).setVisibility(View.GONE);
		anAct.findViewById(R.id.LayoutPickFile).setVisibility(View.GONE);
		mButtonPickFile = (Button)anAct.findViewById(R.id.ButtonPickFile);
		mLayoutProgress = (ViewGroup)anAct.findViewById(R.id.LayoutProgress);
		//mLayoutProgress.setVisibility(View.GONE);

		msgHandler.setup(mLayoutProgress);
	}
	
	protected void showJumpPointDialog(Activity anAct) {
		//keeping this out of the app managed dialogs because we need the icons to get recycled
		//  and not last longer than when the dialog is visible
		DialogJumpPoints d = new DialogJumpPoints(anAct,this);
		d.show();
	}

	protected void extReq_pickFile(Activity anAct, Intent aIntent) {
		//Toast.makeText(this,"PICKING FILE!",Toast.LENGTH_SHORT);
		mItemClickPerforms = ITEMCLICK_PICKFILE;
		ITEMCLICK_DEFAULT = ITEMCLICK_PICKFILE;
		anAct.findViewById(R.id.LayoutPickFile).setVisibility(View.VISIBLE);
		if (!aIntent.getBooleanExtra(BitsIntent.EXTRA_ALLOW_MULTIPLE,false)) {
			mPickFile = true;
			mButtonPickFile.setOnClickListener(
					new Button.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (mPickShortcut)
								msgHandler.sendMessage(msgHandler.obtainMessage(MSG_PICK_SHORTCUT));
							else
								msgHandler.sendMessage(msgHandler.obtainMessage(MSG_PICK_FILE));
						}						
					});
		} else {
			mPickFiles = true;
    		mButtonPickFile.setText(R.string.button_pickfiles);
			mButtonPickFile.setOnClickListener(
					new Button.OnClickListener() {
						@Override
						public void onClick(View v) {
							msgHandler.sendMessage(msgHandler.obtainMessage(MSG_PICK_FILES));
						}						
					});
		}
		mButtonPickFile.setEnabled(mMarkedFiles.size()>0);
		if (aIntent.hasExtra(BitsIntent.EXTRA_OI_BUTTON_TEXT)) {
    		mButtonPickFile.setText(aIntent.getStringExtra(BitsIntent.EXTRA_OI_BUTTON_TEXT));
		}
		
		//file filter
		if (aIntent.hasExtra(Intent.EXTRA_TEMPLATE)) {
			mDsFileList.setFileFilterRegEx(aIntent.getStringExtra(Intent.EXTRA_TEMPLATE));
		} else if (aIntent.getType()!=null && !aIntent.getType().equals("*/*")) {
			mDsFileList.setFileFilterMIMEtype(aIntent.getType().toLowerCase(Locale.getDefault()));
		}
		anAct.setTitle(R.string.title_selectfile);
	}
	
	protected void extReq_pickFolder(final Activity anAct, Intent aIntent) {
		mPickFolder = true;
		anAct.findViewById(R.id.LayoutPickFolder).setVisibility(View.VISIBLE);
		Button theButton = (Button)anAct.findViewById(R.id.ButtonNewFolder);
		theButton.setOnClickListener(
				new Button.OnClickListener() {
					@Override
					public void onClick(View v) {
						createNewFolder(anAct);
					}						
				});
		theButton = (Button)anAct.findViewById(R.id.ButtonPickFolder);
		theButton.setOnClickListener(
				new Button.OnClickListener() {
					@Override
					public void onClick(View v) {
						msgHandler.sendMessage(msgHandler.obtainMessage(MSG_PICK_FOLDER));
					}						
				});
		if (aIntent.hasExtra(BitsIntent.EXTRA_OI_BUTTON_TEXT)) {
    		theButton.setText(aIntent.getStringExtra(BitsIntent.EXTRA_OI_BUTTON_TEXT));
		}
		anAct.setTitle(R.string.title_selectfolder);
	}
	
	/**
	 * Get the item that was picked.
	 * @param aPath - full path of the picked file or null if picking marked item
	 * @return File object of aPath, File object for marked item if aPath is NULL, else NULL.
	 */
	private File getPickedFile(String aPath) {
		File theFile = null;
		if (aPath!=null) {
			theFile = new File(aPath);
		} else if (mMarkedFiles.size()>0) {
			theFile = mMarkedFiles.getFirstFile();
		}
		return theFile;
	}
	
	private void handlePickFolder(Activity anAct, String aFolderName) {
		if (mPickFolder) {
			Intent theResult = anAct.getIntent();
			theResult.setData(getFileUriForIntent(anAct,getPickedFile(aFolderName)));
			BitsIntent.setFromExtra(theResult,anAct.getComponentName().getClassName());
			anAct.setResult(Activity.RESULT_OK,theResult);
			anAct.finish();
		}
	}
	
	private void handlePickFile(Activity anAct, String aFilePath) {
		Intent theResult = anAct.getIntent();
		BitsIntent.setFromExtra(theResult,anAct.getComponentName().getClassName());
		if (mPickFile || mPickItem) {
			File thePickedFile = getPickedFile(aFilePath);
			Uri theUri = getFileUriForIntent(anAct,thePickedFile);
			if (theUri!=null) {
				//get mimeType of simple Uri
				String theType = mMimeMap.guessMIMEtype(thePickedFile);
				if (theType!=null) {
					theResult.setDataAndType(theUri,theType);
				} else {
					theResult.setData(theUri);
				}
				//special handling before we finish packing up the intent and sending it back
				if (theResult.getAction().equals(Intent.ACTION_GET_CONTENT) && theType!=null) {
					String theCategory = mMimeMap.getMIMEcategory(theType);
					if (theCategory.equals("image/*")) {
						/* may be specific to Contacts app
				        intent.putExtra("crop", "true");
				        intent.putExtra("aspectX", 1);
				        intent.putExtra("aspectY", 1);
				        intent.putExtra("outputX", ICON_SIZE);
				        intent.putExtra("outputY", ICON_SIZE);
				        intent.putExtra("return-data", true);
				        4.0+ output=file:///storage/emulated/0/Android/data/com.android.contacts/cache/tmp/ContactPhoto-IMG_20130124_175234.jpg
						 */
						int defaultOutputX = 0;
						int defaultOutputY = 0;
						int theScaling = 0;
						if (BitsStringUtils.isEqual(anAct.getCallingPackage(),"com.motorola.blur.contacts")) {
							//motorola bloatware bug
							defaultOutputX = BitsGraphicUtils.getIconSize(anAct,48);
							defaultOutputY = defaultOutputX;
						} else {
							theScaling = -1; //shrink large pics, leave smaller ones as is
							DisplayMetrics dm = anAct.getResources().getDisplayMetrics();
							defaultOutputX = dm.widthPixels;
							defaultOutputY = dm.heightPixels;							
						}
						if (theResult.getBooleanExtra(BitsIntent.EXTRA_RETURN_DATA,false)) {
							theResult.putExtra(BitsIntent.EXTRA_DATA,
									mFileIcons.getImage(thePickedFile.getPath(),theScaling,
										theResult.getIntExtra(BitsIntent.EXTRA_OUTPUTX,defaultOutputX),
										theResult.getIntExtra(BitsIntent.EXTRA_OUTPUTY,defaultOutputY)));
						} else if (theResult.getParcelableExtra(BitsIntent.EXTRA_OUTPUT)!=null) {
							final File theReturnFile = new File(((Uri)theResult.getParcelableExtra(BitsIntent.EXTRA_OUTPUT)).getPath());
							final Bitmap theBitmap = mFileIcons.getImage(thePickedFile.getPath(),theScaling,
									theResult.getIntExtra(BitsIntent.EXTRA_OUTPUTX,defaultOutputX),
									theResult.getIntExtra(BitsIntent.EXTRA_OUTPUTY,defaultOutputY));
							final ModalVar<Integer> theSaveFinished = new ModalVar<Integer>();
							Runnable theSaveTask = new Runnable(){
								@Override
								public void run() {
									try {
										FileOutputStream theOutStream = new FileOutputStream(theReturnFile);
										theBitmap.compress(Bitmap.CompressFormat.PNG,100,theOutStream);
										theOutStream.flush();
										theOutStream.close();
									} catch (Exception e) {
										
									}
									theSaveFinished.setValue(1);
								}
							};
							anAct.setProgressBarIndeterminateVisibility(true);
							new BitsThreadTask(theSaveTask).execute();
							//blocks read until task sets the value from the background thread
							theSaveFinished.getValue();
							anAct.setProgressBarIndeterminateVisibility(false);
						}
					}
				}
				
				//next time we run FB make sure we go back to normal click mode
				mItemClickPerforms = ITEMCLICK_OPENFILE;
				ITEMCLICK_DEFAULT = ITEMCLICK_OPENFILE;
				//place Uri in both Extra_Stream and Data parts of the Intent (for good measure)
				theResult.putExtra(Intent.EXTRA_STREAM,theUri);
				anAct.setResult(Activity.RESULT_OK,theResult);
				anAct.finish();
			}
		} else if (mPickFiles) {
			if (packageMarkedFiles(theResult,false)) {
				//next time we run FB make sure we go back to normal click mode
				mItemClickPerforms = ITEMCLICK_OPENFILE;
				ITEMCLICK_DEFAULT = ITEMCLICK_OPENFILE;
				anAct.setResult(Activity.RESULT_OK,theResult);
				anAct.finish();
			}
		}
	}
	
	private void handlePickShortcut(final Activity anAct, final String aPath, String aShortcutName) {
		File thePickedFile = getPickedFile(aPath);
		Uri theFileUri = getFileUriForIntent(anAct,thePickedFile);
		if (theFileUri==null)
			return;
		if (aShortcutName!=null) {
			Intent theResult = anAct.getIntent();
			
			Intent theShortcutIntent;
			if (mPickFileShortcut) {
				String theMIMEType = mMimeMap.guessMIMEtype(thePickedFile);
				theShortcutIntent = new Intent(Intent.ACTION_VIEW);
				if (theMIMEType!=null) {
					theShortcutIntent.setDataAndType(theFileUri,theMIMEType);
				} else {
					theShortcutIntent.setDataAndType(theFileUri,"*/*");
					theShortcutIntent = Intent.createChooser(theShortcutIntent,aShortcutName);
				}
				theResult.putExtra(Intent.EXTRA_SHORTCUT_ICON,((BitmapDrawable)BitsThumbnailUtils.getFileIcon(
						anAct,mMimeMap,thePickedFile,R.drawable.item_file,R.drawable.item_folder,
						R.drawable.item_zip,true,128,1)).getBitmap());
			} else {
				theShortcutIntent = new Intent(Intent.ACTION_MAIN);
				theShortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				theShortcutIntent.setClassName(anAct,FileListActivity.class.getName());
				theShortcutIntent.setData(theFileUri);
				Parcelable theIconResource = Intent.ShortcutIconResource.fromContext(anAct,
						R.drawable.button_gojump);
				theResult.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,theIconResource);
			}

			theResult.putExtra(Intent.EXTRA_SHORTCUT_INTENT,theShortcutIntent);
			theResult.putExtra(Intent.EXTRA_SHORTCUT_NAME,aShortcutName);
			handlePickFile(anAct,aPath);
		} else {
			aShortcutName = BitsFileUtils.replaceExtension(thePickedFile.getName(),"");
			final DialogQuickEdit dRename = new DialogQuickEdit(anAct);
			View.OnClickListener onRenameItem = new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					String s = dRename.getTrimmedText();
					handlePickShortcut(anAct,aPath,s);
				}
			};
			dRename.setup(R.drawable.button_gojump,R.string.dialog_rename_title,
					aShortcutName,aShortcutName.length(),aShortcutName.length(),onRenameItem,null);			
			dRename.show();
		}
	}
		
	public void processExternalRequest(Activity anAct, Intent aIntent) {
		// Android 2.3.x: Starting: Intent { act=android.intent.action.VIEW dat=file:///mnt/sdcard/download/fw4-1.pdf typ=application/pdf flg=0x10000001 cmp=com.qo.android.htcgep/com.qo.android.quickoffice.QuickofficeDispatcher } from pid 1653
		// Android 4.2.x: START u0 {act=android.intent.action.VIEW dat=content://downloads/all_downloads/2216 typ=application/pdf flg=0x10000001 cmp=com.tf.thinkdroid.sg/com.tf.thinkdroid.pdf.app.RenderScreen} from pid 12270
		if (aIntent.getData()!=null && aIntent.getData().getPath()!=null) {
			File theJumpPoint;
			if (aIntent.getScheme().equals(BitsIntent.SCHEME_CONTENT)) {
				theJumpPoint = BitsFileUtils.getFileFromContentUri(anAct,aIntent.getData());
			} else {
				theJumpPoint = new File(aIntent.getData().getPath());
			}
			if (theJumpPoint!=null) {
				if (theJumpPoint.isDirectory()) {
					mAutoSelectName = "";
					mFolderName = theJumpPoint.getPath();
				} else {
					mAutoSelectName = FileListDataElement.getDisplayName(theJumpPoint);
					mFolderName = theJumpPoint.getParent();
				}
			}
		}
		//set title
		if (aIntent.hasExtra(Intent.EXTRA_TITLE))
			anAct.setTitle(aIntent.getStringExtra(Intent.EXTRA_TITLE));
		else if (aIntent.hasExtra(BitsIntent.EXTRA_OI_TITLE))
			anAct.setTitle(aIntent.getStringExtra(BitsIntent.EXTRA_OI_TITLE));
	}

	/*
	public void onWindowFocusChanged(boolean hasFocus) {
		//note, this event does NOT fire when being installed and run for first time
		if (hasFocus) {
			if (mAutoSelectName!=null) {
				fillList(mFolderName);
			}
		}
	}
	*/

	public boolean onSearchRequested(Activity anAct) {
		Bundle appData = new Bundle();
		appData.putString(BitsIntent.EXTRA_DIRECTORY,mDsFileList.mCurrPath);
		anAct.startSearch(null,false,appData,false);
		return true;
	}
	
	public boolean isSearching() {
		return bIsSearching;
	}

	public Dialog onCreateDialog(Activity anAct, int id) {
		switch (id) {
			case DIALOG_ABOUT:
				return new DialogAbout(anAct);
			case DIALOG_SEARCH_TIPS:
				return new DialogSearchTips(anAct);
		}
		return null;
	}

	private void setShowUnreadable(boolean aShowUnreadableSetting) {
		if (mDsFileList.mHideUnreadable==aShowUnreadableSetting) { //  x != !y
			mDsFileList.mHideUnreadable = !aShowUnreadableSetting;
			refillList();
		}
	}

	private void setShowHidden(boolean aShowHiddenSetting) {
		if (mDsFileList.mHideHidden==aShowHiddenSetting) { //  x != !y
			mDsFileList.mHideHidden = !aShowHiddenSetting;
			refillList();
		}
	}
	
	private FileComparator mSorterAlpha = new FileComparatorAlpha(false,true);
	private FileComparator mSorterDate = new FileComparatorDate(false,true);
	private FileComparator mSorterSize = new FileComparatorSize(false,true);
	private FileComparator mSorterType = new FileComparatorType(false,true);

	private void setSorterToUse(String aSortOrderPref, boolean aReverseSortSetting) {
		//pref setting says to use which sorter?
		FileComparator aSorter = mSorterAlpha;
		if (aSortOrderPref.equals("date")) aSorter = mSorterDate;
		else if (aSortOrderPref.equals("size")) aSorter = mSorterSize;
		else if (aSortOrderPref.equals("type")) aSorter = mSorterType;

		//if the sorter to use is different from what we are already using, change it
		if (aSorter!=mDsFileList.mSorterInUse || 
				mDsFileList.mSorterInUse.getReverseSort()!=aReverseSortSetting) { 
			mDsFileList.mSorterInUse = aSorter;
			mDsFileList.mSorterInUse.setReverseSort(aReverseSortSetting);
			refillList();
		}
	}

	private void setFontSize(int aFontSizeSetting) {
		if (mFontSize!=aFontSizeSetting) {
			mFontSize = aFontSizeSetting;
			int theSize = (mFontSize>0)?aFontSizeSetting:mDefaultFontSize;

			mCurrFolderView.setTextSize(theSize);
			mMemSpaceView.setTextSize(theSize);
			mEmptyFolderView.setTextSize(theSize);
			
			mListAdapter.setTextSize(theSize);
			mGridAdapter.setTextSize(theSize);
			mFileListUI.setFontSize();
		}
	}

	/**
	 * called once onCreate and before onNewIntent is processed so that intents can override
	 * settings & defaults.
	 * @return Returns TRUE if mSettings != null.
	 */
	public boolean getPrefs(Activity anAct) {
		// Restore preferences
		SharedPreferences savedState = anAct.getSharedPreferences(PERSISTANT_STATE_FILE, Activity.MODE_PRIVATE);
		//mSettings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mSettings = AppPreferences.getPrefs(anAct.getApplicationContext());
		if (mSettings!=null) {
			mHomeFolder = mSettings.getString(anAct.getString(R.string.pref_key_home_folder),mHomeFolder);
			mFolderName = (savedState!=null)?savedState.getString(SAVE_CURRENT_FOLDER,mHomeFolder):mHomeFolder;
			
			// initialize the starting preferences
			mFileListUI.onSharedPreferenceChanged(mSettings,null);
        }
		if (savedState!=null && mAutoSelectName==null && savedState.contains(SAVE_CURRENT_ITEM)) {
			File theItemFile = new File(savedState.getString(SAVE_CURRENT_ITEM,""));
			if (theItemFile!=null && theItemFile.exists() && theItemFile.getPath().startsWith(mFolderName)) { 
				mAutoSelectName = FileListDataElement.getDisplayName(theItemFile);
			}

		}
		return (mSettings!=null);
	}

	public void onSharedPreferenceChanged(SharedPreferences aSettings, String aPrefKey) {
		Activity anAct = mAct.get();
		if (aSettings==null || anAct==null)
			return;
		String thePrefKey;
		thePrefKey = anAct.getString(R.string.pref_key_hide_notificationbar);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			if (aSettings.getBoolean(thePrefKey,false)) {
				anAct.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
						WindowManager.LayoutParams.FLAG_FULLSCREEN);
			} else {
				anAct.getWindow().setFlags(0,WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
		}
		thePrefKey = anAct.getString(R.string.pref_key_show_unreadable);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			setShowUnreadable(aSettings.getBoolean(thePrefKey,!mDsFileList.mHideUnreadable));
		}
		thePrefKey = anAct.getString(R.string.pref_key_show_hidden);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			setShowHidden(aSettings.getBoolean(thePrefKey,!mDsFileList.mHideHidden));
		}
		thePrefKey = anAct.getString(R.string.pref_key_show_thumbnails);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			boolean bEnableThumbnails = aSettings.getBoolean(thePrefKey,true);
			msgHandler.sendMessage(msgHandler.obtainMessage(MSG_ENABLE_THUMBNAILS,
					(bEnableThumbnails)?1:0,0));
		}
		thePrefKey = anAct.getString(R.string.pref_key_thumbnail_size);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			mFileIcons.setScaleFactor(aSettings.getString(thePrefKey,mFileIcons.getScaleFactor()));
		}
		thePrefKey = anAct.getString(R.string.pref_key_backkey_exits);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			mBackKeyExits = aSettings.getBoolean(thePrefKey,mBackKeyExits);
		}
		thePrefKey = anAct.getString(R.string.pref_key_send_clears_marked);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			mSendClearsMarked = aSettings.getBoolean(thePrefKey,mSendClearsMarked);
		}
		thePrefKey = anAct.getString(R.string.pref_key_use_recyclebin);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			mUseRecycleBin = aSettings.getBoolean(thePrefKey,mUseRecycleBin);
		}
		thePrefKey = anAct.getString(R.string.pref_key_sort_order);
		String thePrefKey2 = anAct.getString(R.string.pref_key_sort_reversed);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey) || aPrefKey.equals(thePrefKey2)) {
			setSorterToUse(aSettings.getString(thePrefKey,"name"),
					aSettings.getBoolean(thePrefKey2,false));
		}
		thePrefKey = anAct.getString(R.string.pref_key_font_size);
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			setFontSize(Integer.parseInt(aSettings.getString(thePrefKey,String.valueOf(mFontSize))));
		}
		
		thePrefKey = anAct.getString(R.string.pref_key_orientation);
		//int tempI = BitsLegacy.getDefaultRotation(this);
		//String temps = aSettings.getString(thePrefKey,String.valueOf(tempI))+"";
		if (aPrefKey==null || aPrefKey.equals(thePrefKey)) {
			BitsLegacy.setDisplayRotation(anAct,
					Integer.parseInt(aSettings.getString(thePrefKey,
					String.valueOf(BitsLegacy.getDefaultRotation(anAct)))));
		}
	}
	
	/**
	 * Saves the current item on screen to the private prefs file.
	 */
	protected void saveCurrentItem(Activity anAct) {
		Editor theEditor = anAct.getSharedPreferences(PERSISTANT_STATE_FILE,Activity.MODE_PRIVATE).edit();
		if (theEditor!=null) {
			theEditor.putString(SAVE_CURRENT_FOLDER, mFolderName);

			//save the current item
			if (mCurrItemPos==AdapterView.INVALID_POSITION || 
					mCurrItemPos<mFileListUI.getFirstVisibleItemPos() ||
					mCurrItemPos>mFileListUI.getLastVisibleItemPos()) {
				mCurrItemPos = mFileListUI.getChiefVisibleItemPos();
			}
			if (mCurrItemPos!=AdapterView.INVALID_POSITION && 
					mDsFileList!=null && mCurrItemPos>=0 && mCurrItemPos<mDsFileList.size()) 
				theEditor.putString(SAVE_CURRENT_ITEM,getListItem(mCurrItemPos).getPath());
			else
				theEditor.remove(SAVE_CURRENT_ITEM);

			AppPreferences.applyChanges(theEditor);
		}
	}
	
	public void onResume(Activity anAct) {
		mLastBackPress = System.currentTimeMillis();
		mFileIcons.setSuspend(isSearching());
	}
	
	public void onPause(Activity anAct) {
		mFileIcons.setSuspend(true);
	}
	
	public void onRestart(Activity anAct) {
	}
	
	public void onStart(Activity anAct) {
		//tell Android we want to know if certain actions take place
		IntentFilter theIF = new IntentFilter();
		theIF.addAction(Intent.ACTION_MEDIA_MOUNTED);
		theIF.addAction(Intent.ACTION_MEDIA_REMOVED);
		theIF.addAction(Intent.ACTION_MEDIA_SHARED);
		theIF.addDataScheme(ContentResolver.SCHEME_FILE);
		//theIF.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		anAct.registerReceiver(br,theIF);
		
		SharedPreferences savedState = anAct.getSharedPreferences(PERSISTANT_STATE_FILE, Activity.MODE_PRIVATE);
		if (savedState!=null) {
			if (mAutoSelectName==null && savedState.contains(SAVE_CURRENT_ITEM)) {
				File theItemFile = new File(savedState.getString(SAVE_CURRENT_ITEM,""));
				if (theItemFile!=null && theItemFile.exists() && theItemFile.getPath().startsWith(mFolderName)) { 
					mAutoSelectName = FileListDataElement.getDisplayName(theItemFile);
				}
			}
		}

		fillList(mFolderName);
	}

	public void onStop(Activity anAct) {
		anAct.unregisterReceiver(br);
	}

	public void onExitApp() {
		compactFolder(BitsFileUtils.getRecycleBin(mAct.get()),3,"compact recycle bin");
		compactFolder(BitsThumbnailUtils.mThumbnailCacheFolder,3,"compact cache");

		//not sure if these are needed, just being thorough
		mSettings = null;
		br = null;
		mDsFileList = null;
		mFileIcons = null;
		mListAdapter = null;
		mGridAdapter = null;
		mMarkedFiles = null;
		mMimeMap = null;
		msgHandler = null;
		mSorterAlpha = null;
		mSorterDate = null;
		mSorterSize = null;
		mSorterType = null;
		
		mAutoSelectName = null;
	    mHomeFolder = null;
		mFolderName = null;
		mButtonGoUp = null;
		mButtonGoJump = null;
		mCurrFolderView = null;
		mMemSpaceView = null;
		mMemLowSpaceWarning = null;
		mFolderReadOnlyView = null;
		mEmptyFolderView = null;
	    mButtonPickFile = null;
		mLayoutProgress = null;
		
	    //File theCacheFolder = BitsFileUtils.getCacheFolder(this);
		//BitsFileUtils.deleteFolderContents(theCacheFolder,null,0);
	}

	public void onRestoreInstanceState(Activity anAct, Bundle aState) {
		//app is waking from system induced cryo-sleep
		if (aState!=null) {
			SharedPreferences savedState = anAct.getSharedPreferences(PERSISTANT_STATE_FILE, Activity.MODE_PRIVATE);
			if (savedState!=null) {
				mItemClickPerforms = aState.getInt(SAVE_CLICK_MODE, mItemClickPerforms);

				if (savedState.getBoolean(SAVE_RELOAD_MARKED,false)) {
					mMarkedFiles.fromBundle(aState.getBundle(SAVE_MARKED_FILES));
				}
				
			}
		}
	}

	public void onSaveInstanceState(Activity anAct, Bundle outState) {
		//system is inducing cryo-sleep, save any temp state for later waking
		AppPreferences.applyChanges(anAct.getSharedPreferences(PERSISTANT_STATE_FILE, 
				Activity.MODE_PRIVATE).edit().putBoolean(SAVE_RELOAD_MARKED,!mMarkedFiles.isEmpty()));
		outState.putBundle(SAVE_MARKED_FILES, mMarkedFiles.toBundle());
		outState.putInt(SAVE_CLICK_MODE, mItemClickPerforms);
	}

	public void onConfigurationChanged(Context aContext, Configuration newConfig) {
		//overriding this method plus adding android:configChanges="keyboardHidden|orientation"
		//  attr to the Activity prevents destroy/recreate on rotation change
	}

	public void onLowMemory() {
		mFileIcons.emptyRecycleBin();
	}

	public void onListItemClick(int position) {
		if (position<0 || position>=mDsFileList.size())
			return;
		File theFile = getListItem(position);
		mCurrItemPos = position;
		switch (mItemClickPerforms) {
			case ITEMCLICK_OPENFILE: 
				if (theFile.canRead()) {
					if (theFile.isDirectory()) {
						fillList(theFile.getPath());
						mCurrItemPos = AdapterView.INVALID_POSITION;
					} else {
						openFile(theFile);
					}
				}
				break;	
			case ITEMCLICK_MARK:
				toggleItemMark(position);
				break;
			case ITEMCLICK_PICKFILE:
				if (theFile.isDirectory()) {
					if (theFile.canRead()) {
						fillList(theFile.getPath());
					}
				} else {
					if (!mPickFiles && mMarkedFiles.size()>0) {
						cancelMarkedFiles();
					}
					toggleItemMark(position);
				}
				break;
		}//switch
	}

	public void onCreateContextMenu(Activity anAct, ContextMenu aMenu, View aView, ContextMenuInfo aMenuInfo) {
		AdapterView.AdapterContextMenuInfo theMenuInfo = null;
		FileListDataElement theFile = null;
		try {
			theMenuInfo = (AdapterView.AdapterContextMenuInfo) aMenuInfo;
			theFile = getListItem(theMenuInfo.position);
		} catch (ClassCastException cce) {
			Log.e(TAG, "bad aMenuInfo", cce);
			return;
		} catch (NullPointerException npe) {
			//this exception can occur when using a non-touch way to open the context menu
			//  e.g. using a long-press menu key or right-clicking a mouse/trackpad.
			theFile = (FileListDataElement) mFileListUI.getFileListingView().getSelectedItem();
		}
		if (theFile==null)
			return;
		boolean isItemReadable = theFile.canRead();
		boolean isItemWritable = theFile.canWrite();
		boolean isFolder = theFile.isDirectory();
		//see if any app can accept the file if sent
		boolean hasSendRecipients = false;
		boolean isMultiApp = false;
		boolean isAppEditable = false;
		String theMIMEtype = mMimeMap.getMIMEtype(theFile);
		String theMIMEcat = BitsFileUtils.getMIMEcategory(theMIMEtype);
		if (theMIMEtype!=null) {
			Uri theUri = getFileUriForIntent(anAct,theFile);
			Intent theIntent = new Intent(Intent.ACTION_SEND);
			theIntent.setType(theMIMEtype);
			theIntent.putExtra(Intent.EXTRA_STREAM, theUri);
			theIntent.putExtra(Intent.EXTRA_SUBJECT, theFile.getName());
			hasSendRecipients = anAct.getPackageManager().queryIntentActivities(theIntent,PackageManager.MATCH_DEFAULT_ONLY).size()>0;
			//see if more than one installed app can open the file
			theIntent = new Intent(Intent.ACTION_VIEW);
			theIntent.setDataAndType(theUri, theMIMEcat);
			isMultiApp = (anAct.getPackageManager().queryIntentActivities(theIntent,PackageManager.MATCH_DEFAULT_ONLY).size()>1);
			//see if file is editable as well as viewable
			theIntent = new Intent(Intent.ACTION_EDIT);
			theIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK+Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			theIntent.setDataAndType(theUri, theMIMEtype);
			isAppEditable = (anAct.getPackageManager().queryIntentActivities(theIntent,PackageManager.MATCH_DEFAULT_ONLY).size()>0);
		}

		// Setup the menu
		anAct.getMenuInflater().inflate(R.menu.context_filelist,aMenu);
		aMenu.setHeaderTitle(theFile.getName());
		if (mPickFolder && isFolder) {
			aMenu.findItem(R.id.menu_item_pick_this_folder).setVisible(isItemReadable);
		} else if (mPickFile && !isFolder) {
			aMenu.findItem(R.id.menu_item_pick_this_file).setVisible(isItemReadable);
		} else if (mPickItem) {
			aMenu.findItem(R.id.menu_item_pick_this_item).setVisible(isItemReadable);
		}
		MenuItem m;
		m = aMenu.findItem(R.id.menu_item_mark_item);
		if (!theFile.isMarked()) {
			m.setTitle(R.string.menu_item_mark);
			m.setEnabled(isItemReadable && !theFile.containsMarked());
		} else {
			m.setTitle(R.string.menu_item_unmark);
		}
		m = aMenu.findItem(R.id.menu_item_open_folder);
		m.setVisible(isItemReadable && isFolder);
		m = aMenu.findItem(R.id.menu_item_open_menu);
		m.setTitle(m.getTitle()+"…");
		m.setVisible(isItemReadable && !isFolder);
		m = aMenu.findItem(R.id.menu_item_open_file_edit);
		m.setVisible(isAppEditable);
		m = aMenu.findItem(R.id.menu_item_open_file_with);
		m.setVisible(isMultiApp);
		m = aMenu.findItem(R.id.menu_item_send_item);
		m.setVisible(isFolder || hasSendRecipients);
		m.setEnabled(isItemReadable);
		m = aMenu.findItem(R.id.menu_item_rename_item);
		m.setEnabled(isItemWritable);
		m = aMenu.findItem(R.id.menu_item_delete_item);
		m.setEnabled(isItemWritable);
		m = aMenu.findItem(R.id.menu_item_audio_menu);
		m.setVisible(theMIMEcat.equals("audio/*"));
		m = aMenu.findItem(R.id.menu_item_open_symlink);
		m.setVisible(isFolder && theFile.isFileJumpPoint());
		
		/* commenting out since .addIntentOptions only returns the first one found
		//Allows other apps (plugins) to extend the menu with their own actions.
		try {
			Intent theIntent = new Intent(null,new Uri.Builder().scheme(SCHEME_PLUGIN).authority(getPackageName())
				.path(PLUGIN_PATH_MENU_CONTEXT).build());
			theIntent.addCategory(Intent.CATEGORY_SELECTED_ALTERNATIVE);
			// Search and populate the menu with acceptable offering applications.
		    aMenu.addIntentOptions(
		         Menu.NONE,  // Menu group to which new items will be added
		         Menu.NONE,  // Unique item ID (none)
		         0,      // Order for the items (none)
		         this.getComponentName(),   // The current Activity name
		         null,   // Specific items to place first (none)
		         theIntent, // Intent created above that describes our requirements
		         Menu.FLAG_APPEND_TO_GROUP,      // Additional flags to control items
		         null);  // Array of MenuItems that correlate to specific items (none)
		} catch (NullPointerException e) {
			//do nothing, means there were no items to add (Android 1.5 quirk)
		}
		*/
	}

	public boolean onContextItemSelected(final Activity anAct, MenuItem aMenuItem) {
		AdapterView.AdapterContextMenuInfo theMenuInfo;
		try {
			theMenuInfo = (AdapterView.AdapterContextMenuInfo) aMenuItem.getMenuInfo();
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return false;
		}
		//if theMenuInfo == null, it means we have a submenu to deal with, get saved FileIndex
		final int fileIndex = (theMenuInfo!=null)?theMenuInfo.position:mParentContextMenuFileIndex;
		final FileListDataElement theFile = getListItem(fileIndex);
		if (theFile==null)
			return false;
		final int theMenuChoice = aMenuItem.getItemId();
		mCurrItemPos = fileIndex;
		switch (theMenuChoice) {
			case R.id.menu_item_open_folder: {
				if (theFile.canRead()) {
					fillList(theFile.getPath());
					mCurrItemPos = AdapterView.INVALID_POSITION;
					mItemClickPerforms = ITEMCLICK_DEFAULT;
				}
				return true;
			}
			case R.id.menu_item_open_symlink: {
				fillList(BitsFileUtils.getCanonicalPath(theFile));
				mCurrItemPos = AdapterView.INVALID_POSITION;
				mItemClickPerforms = ITEMCLICK_DEFAULT;
				return true;
			}
			case R.id.menu_item_open_file: {
				openFile(theFile);
				return true;
			}
			case R.id.menu_item_open_file_with: {
				openFileWith(theFile);
				return true;
			}
			case R.id.menu_item_open_file_edit: {
				editFile(theFile);
				return true;
			}
			case R.id.menu_item_open_file_as: {
				openFileAs(theFile);
				return true;
			}
			case R.id.menu_item_rename_item: {
				if (!msgHandler.isStillProcessing(null)) {
					mFileListUI.setSelection(fileIndex);
					editFilename(anAct,theFile,theFile.getName());
				} else {
					BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
				}
				return true;
			}
			case R.id.menu_item_delete_item: {
				if (theFile.canWrite() && !msgHandler.isStillProcessing(null)) {
					final DialogConfirm dConfirm = new DialogConfirm(anAct);
					OnClickTask onDeleteItem = new OnClickTask(new OnClickTask.TaskDef() {
						FileListDataElement currItem = null;
						Object mProgressID = null;
						
						@Override
						public void beforeTask(View v) {
							currItem = getListItem(fileIndex);
							//check that currItem match what we clicked on (delete is a serious matter!)
							if (currItem!=null && currItem.equals(theFile) && mDsFileList!=null && 
									BitsFileUtils.isFolderEmpty(currItem)) {
								mDsFileList.removeFile(currItem);
								mFileListUI.refreshListView();
							}
						}

						@Override
						public Object doTask(View v, Handler aProgressHandler) {
							//check that currItem match what we clicked on (delete is a serious matter!)
							if (currItem!=null && currItem.equals(theFile)) {
								if (!BitsFileUtils.isFolderEmpty(currItem))
									return currItem.bIsFile;
								mProgressID = msgHandler.createNewProgressEvent(R.string.marked_deleting);
								msgHandler.getMsgProgressStart(mProgressID,currItem.getDisplayName(),
										currItem.mSize).sendToTarget();
								if (deleteFob(anAct,mProgressID,currItem))
									return currItem;
							}
							return null;
						}

						@Override
						public void onProgressUpdate(Object o) {
							//not used since we have our own custom handler
						}

						@Override
						public void afterTask(Object aTaskResult) {
							if (aTaskResult instanceof FileListDataElement) {
								//FileListDataElement theItem = (FileListDataElement)aTaskResult;
							} else if (aTaskResult instanceof Boolean) {
								int msgRes = ((Boolean)aTaskResult)?R.string.permission_denied:R.string.msg_delete_folder;
								BitsDialog.Builder(anAct,R.string.action_failed).show(msgRes);
							}
							if (msgHandler!=null && mProgressID!=null)
								msgHandler.getMsgProgressFinish(mProgressID).sendToTarget();
							//BitsFileUtils.refreshExternalStorage(anAct); removed for taking too long
							mFileListUI.refreshListView();
						}
						
					});
					dConfirm.setup(R.drawable.icon_recyclebin,R.string.dialog_confirm_delete_title,
							anAct.getString(R.string.dialog_confirm_delete_text,theFile.getName()),
							onDeleteItem,null);
					dConfirm.show();
				} else {
					BitsDialog.Builder(anAct).show(R.string.action_failed,R.string.permission_denied);
				}
				return true;
			}
			case R.id.menu_item_jumppoint_add: {
				final File theJumpPoint = theFile;
				String theInput = BitsFileUtils.replaceExtension(theFile.getName(),"");
				final DialogQuickEdit dRename = new DialogQuickEdit(anAct);
				View.OnClickListener onRenameItem = new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String s = dRename.getTrimmedText();
						if (s!=null && !s.equals("")) {
							ContentValues theValues = new ContentValues(2);
							theValues.put(JumpPointProvider.JUMP_DATA,theJumpPoint.getPath());
							theValues.put(JumpPointProvider.JUMP_NAME,s);
							anAct.getContentResolver().insert(JumpPointProvider.CONTENT_URI,theValues);
						}
					}
				};
				dRename.setup(R.drawable.button_gojump,R.string.menu_item_add_jumppoint,
						theInput,theInput.length(),theInput.length(),onRenameItem,null);
				dRename.show();
				return true;
			}
			case R.id.menu_item_send_item: {
				sendFile(theFile);
				return true;
			}
			case R.id.menu_item_mark_item: {
				if (mItemClickPerforms==ITEMCLICK_PICKFILE && !mPickFiles && 
						mMarkedFiles.size()>0 && !theFile.bMarked) {
					cancelMarkedFiles();
				}
				toggleItemMark(fileIndex);
				return true;
			}
			case R.id.menu_item_pick_this_folder: {
				handlePickFolder(anAct,theFile.getPath());
				return true;
			}//case
			case R.id.menu_item_pick_this_item:
			case R.id.menu_item_pick_this_file: {
				if (mPickShortcut)
					handlePickShortcut(anAct,theFile.getPath(),null);
				else
					handlePickFile(anAct,theFile.getPath());
				return true;
			}
			case R.id.menu_item_audio_menu: {
				(new DialogAudioSubmenu(anAct,this,theFile)).show();
				return false;
			}
			default: { //can handle submenus this way
				mParentContextMenuFileIndex = fileIndex;
				return false;
			}
		}//switch
	}
	
	private void cancelMarkedFiles() {
		mItemClickPerforms = ITEMCLICK_DEFAULT;
		clearMarkedFiles();
		//disable Pick Marked File button
		if ((mPickFile || mPickFiles) && mButtonPickFile!=null) {
			mButtonPickFile.setEnabled(mMarkedFiles.size()>0);
		}
		BitsLegacy.invalidateOptionsMenu(mAct.get());
	}
	
	protected void clearMarkedFiles() {
		mMarkedFiles.clear();
		for (FileListDataElement anItem:mDsFileList) {
			anItem.resetMarks();
		}
		mFileListUI.refreshListView();
	}

	private void createNewFolder(Activity anAct) {
		File nf = BitsFileUtils.getAutoGeneratedFile(new File(mFolderName), 
				anAct.getString(R.string.default_name_folder), "");
		if (nf!=null) {
			nf.mkdir();
			mAutoSelectName = FileListDataElement.getDisplayName(nf);
			fillList(mFolderName);
			editFilename(anAct,nf,nf.getName());
		}
	}

	//message constants
	public static final int MSG_ENABLE_THUMBNAILS = ProgressBarHandler.MSG_USER_FIRST + 1;
	public static final int MSG_PICK_FOLDER = ProgressBarHandler.MSG_USER_FIRST + 2;
	public static final int MSG_PICK_FILE = ProgressBarHandler.MSG_USER_FIRST + 3;
	public static final int MSG_PICK_FILES = ProgressBarHandler.MSG_USER_FIRST + 4;
	public static final int MSG_PICK_SHORTCUT = ProgressBarHandler.MSG_USER_FIRST + 5;
	public static final int MSG_TOAST = ProgressBarHandler.MSG_USER_FIRST + 6;
	public static final int MSG_ACTION_FAILED = ProgressBarHandler.MSG_USER_FIRST + 7;
	public static final int MSG_SHOW_EXCEPTION = ProgressBarHandler.MSG_USER_FIRST + 8;

	public void handleMessage(Activity anAct, Message msg) {
		/*
		 * NOTE: DO NOT USE mFolderName inside this message handler, use mDsFileList.mCurrPath
		 * instead.  The debugger shows mFolderName with prior instance's value instead of the
		 * current instance that is actually picking the file (both still running). I don't
		 * quite understand why this is the case since mFolderName is updated correctly.
		 */
		switch (msg.what) {
			case ProgressFeedbackHandler.MSG_PROGRESS_FINISH:
				//BitsFileUtils.refreshExternalStorage(anAct); removed for taking too long
				refillList();
				//enable/disable Pick Marked File button
				if ((mPickFile || mPickFiles) && mButtonPickFile!=null) {
					mButtonPickFile.setEnabled(mMarkedFiles.size()>0);
				}
				break;
			case MSG_PICK_FOLDER: 
				handlePickFolder(anAct,mDsFileList.mCurrPath);
				break;
			case MSG_PICK_FILE:
			case MSG_PICK_FILES: 
				handlePickFile(anAct,null);
				break;
			case MSG_PICK_SHORTCUT: 
				handlePickShortcut(anAct,null,null);
				break;
			case MSG_ENABLE_THUMBNAILS: 
				if (mFileIcons!=null) {
					mFileIcons.setEnabled(msg.arg1==1);
					if (isSearching())
						mFileIcons.setSuspend(true);
				}
				break;
			case MSG_ACTION_FAILED: 
				BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
				break;
			case MSG_SHOW_EXCEPTION:
				BitsDialog.ErrDialog(anAct,(Exception)msg.obj);
				break;
			case MSG_TOAST: 
				if (msg.obj instanceof String) {
					Toast.makeText(anAct,(String)msg.obj,(msg.arg1!=0)?msg.arg1:Toast.LENGTH_SHORT).show();					
				} else {
					Toast.makeText(anAct,anAct.getString(msg.arg1),msg.arg2).show();
				}
				break;
		}//switch
	};

	protected class ProcessMarkedItems implements BitsClickTask.TaskDef {
		protected final File[] mProcessMarkedList;
		protected final Activity mActUI;
		protected final int mMenuChoice;
		protected final File mDestFolder;
		protected Object mProcessMarkedId = null;
		     
		public ProcessMarkedItems(Activity anAct, int aMenuChoice, File aDestFolder) {
			//save off the currently marked items into a separate container and work from that
			//  provides a way to let the user keep processing a large list while they do other actions
			mProcessMarkedList = (mMarkedFiles!=null)?mMarkedFiles.listFiles():null;
			mActUI = anAct;
			mMenuChoice = aMenuChoice;
			mDestFolder = aDestFolder;
		}

		@Override
		public Object beforeTask(View v) {
			clearMarkedFiles();
			if (msgHandler!=null && mDestFolder!=null && 
					mProcessMarkedList.length>0 && mProcessMarkedList[0]!=null) {
				String theOverallProgressText = mDestFolder.getName();
				int theActionTextResId;
				switch (mMenuChoice) {
					case R.id.menu_item_move_marked:
						theActionTextResId = R.string.marked_moving; 
						break;
					case R.id.menu_item_delete_marked: 
						theActionTextResId = R.string.marked_deleting;
						theOverallProgressText = mProcessMarkedList[0].getParent();
						break;
					default:
						theActionTextResId = R.string.marked_copying; 
				}//switch
				mProcessMarkedId = msgHandler.createNewProgressEvent(theActionTextResId);
				ProgressBarHandler.sendProgressMsg(msgHandler.getMsgProgressStart(mProcessMarkedId,
						theOverallProgressText,mProcessMarkedList.length*1L));
			}
			return (mProcessMarkedId!=null);
		}

		@Override
		public Object doTask(View v, Object aBeforeTaskResult) {
			if (!(Boolean)aBeforeTaskResult)
				return null;
			final ModalVar<Integer> theWhenSameFilenameAction = new ModalVar<Integer>(mActUI);
			try {
				for (File theSrcFile:mProcessMarkedList) {
					File theDestFile = new File(mDestFolder,theSrcFile.getName());
					if (mMenuChoice!=R.id.menu_item_delete_marked && theDestFile.exists()) {
						//if same kind of file (!xor) and not the same exact file, prompt user
						if (!(theDestFile.isFile() ^ theSrcFile.isFile()) && !theSrcFile.equals(theDestFile) ) {
							theWhenSameFilenameAction.setOnObtainValueUI(new Runnable() {
								@Override
								public void run() {
									if (msgHandler!=null) {
										//turn on highlight for progress group and display dialog
										msgHandler.setProgressHighlightUI(mProcessMarkedId,true);
										AskAboutSameFilename(mActUI,mProcessMarkedId,theWhenSameFilenameAction);
									} else {
										theWhenSameFilenameAction.setValue(DialogFileCollision.FILENAME_SAME_KEEPBOTH);
									}
								}
							});
							theWhenSameFilenameAction.setOnSetValueUI(new Runnable() {
								@Override
								public void run() {
									//turn off highlight
									if (msgHandler!=null) {
										msgHandler.setProgressHighlightUI(mProcessMarkedId,false);
									}
									
								}
							});
							//blocks read until message/dialog sets the value from the UI thread
							int theResult = theWhenSameFilenameAction.getValue();
							Thread.yield(); //chance to let the onSetValueUI event run before we continue
							
							//act on the user's choice
							switch (theResult) {
								case DialogFileCollision.FILENAME_SAME_KEEPBOTH:
									theDestFile = BitsFileUtils.getSafeNewFile(mDestFolder,theSrcFile.getName());
								case DialogFileCollision.FILENAME_SAME_OVERWRITE:
									if (!processMarkedFile(mProcessMarkedId,theSrcFile,theDestFile))
										throw new CancellationException();
							}//switch
						} else if (mMenuChoice==R.id.menu_item_copy_marked) {
							//"copy onto itself" is impossible, assume "keep both"
							theDestFile = BitsFileUtils.getSafeNewFile(theDestFile);
							if (!processMarkedFile(mProcessMarkedId,theSrcFile,theDestFile))
								throw new CancellationException();
						}
					} else {
						if (!processMarkedFile(mProcessMarkedId,theSrcFile,theDestFile))
							throw new CancellationException();
					}
					if (msgHandler!=null && !msgHandler.isStillProcessing(mProcessMarkedId)) {
						throw new CancellationException();
					}
				}//end for each
			} catch (CancellationException ce) {
				//allows breaking out of deep code like the move switch statement
			}
			return mProcessMarkedId;
		}

		protected boolean processMarkedFile(Object aProgressID,	File aMarkedFile, File aNewFile) {
			switch (mMenuChoice) {
				case R.id.menu_item_copy_marked: 
					return copyFob(mActUI,aProgressID,aMarkedFile,aNewFile);
				case R.id.menu_item_move_marked:
					if (!mDestFolder.equals(aMarkedFile) && !aMarkedFile.equals(aNewFile))
						return moveFob(mActUI, aProgressID, aMarkedFile, aNewFile);
					else
						return true;
				case R.id.menu_item_delete_marked:
					//if (!mDestFolder.equals(aMarkedFile))
						return deleteFob(mActUI, aProgressID, aMarkedFile);
					//break;
				default: //safe default is "copy"
					return copyFob(mActUI,aProgressID,aMarkedFile,aNewFile);
			}
		}

		@Override
		public void afterTask(View v, Object aTaskResult) {
			if (msgHandler!=null && aTaskResult!=null)
				msgHandler.getMsgProgressFinish(aTaskResult).sendToTarget();
		}
		
	}

	protected void AskAboutSameFilename(Activity anAct, Object aProgressID, ModalVar<Integer> aUserDecision) {
		DialogFileCollision d = new DialogFileCollision(anAct,aUserDecision);
		ProgressFeedbackItemWidget pfw = msgHandler.getWidget(aProgressID);
		String s = pfw.mTextViewProgressAction.getText() + " → " + pfw.mTextViewProgressTotal.getText();
		d.setMessageText(s);
		d.show();
	}

	public boolean onKeyDown(Activity anAct, int aKeyCode, KeyEvent aKeyEvent) {
		final int theKeySource = BitsLegacy.getSource(aKeyEvent);
		if (aKeyCode==KeyEvent.KEYCODE_BACK && 
				theKeySource!=4098 && //InputDevice.SOURCE_TOUCHSCREEN &&
				theKeySource!=257 //InputDevice.SOURCE_KEYBOARD
				) {
			return false;
		} else if (aKeyCode==KeyEvent.KEYCODE_BACK && !mFileListUI.doesBackKeyExit()) {
			int theRepeatCount = aKeyEvent.getRepeatCount();
			if (theRepeatCount==0) { 
				mTrackSoftBackKey = true;
				mLastBackPress = System.currentTimeMillis();
				return true; //on Android <2.0, cannot close app, fake it onKeyUp
			} else {
				mTrackSoftBackKey = false;
				long dt = System.currentTimeMillis()-mLastBackPress;
				long timeThreshold;
				if (theRepeatCount==1)
					timeThreshold = 2000;
				else if (theRepeatCount<=6)
					timeThreshold = 1200;
				else
					timeThreshold = 500;
				if (dt<timeThreshold) {
					return true; //ignore repeats if not past our threshold
				} else { //repeat count > 0
					fillList("..");
					mLastBackPress = System.currentTimeMillis();
					return true;
				}
			}
		} else if (aKeyEvent.isPrintingKey()) {
			anAct.onSearchRequested();
			return false;
	    } else {
	        return false;
		}
	}
	
	public boolean onKeyUp(Activity anAct, int aKeyCode, KeyEvent aKeyEvent) {
		if (aKeyCode==KeyEvent.KEYCODE_BACK) {
			if (mTrackSoftBackKey) {
				//onKeyDown/Up used for pre-2.0 Android compatibility
				mTrackSoftBackKey = false;
				if (!mFileListUI.doesBackKeyExit()) {
					fillList("..");
					return true;
				} else
					//2010.02.19: seems Android>=2.0 will not finish() since I ate the onKeyDown, need to call finish() ourselves always now
					//if (android.os.Build.VERSION.SDK_INT<5) {
						//on Android <2.0, see onKeyDown app close comment
						anAct.finish();
						return true;
					//} else
					//	return super.onKeyUp(keyCode, event);
			} else {
				//right click on some keyboard docks simulate a BACK key press instead of mouse event.
				final int theKeySource = BitsLegacy.getSource(aKeyEvent);
				if (theKeySource!=4098 && //InputDevice.SOURCE_TOUCHSCREEN &&
					theKeySource!=257 //InputDevice.SOURCE_KEYBOARD
					) {
					mFileListUI.getFileListingView().performLongClick();
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean onMenuItemSelected(final Activity anAct, int aFeatureId, MenuItem aMenuItem) {
		final int theMenuChoice = aMenuItem.getItemId();
		switch (theMenuChoice) {
			case R.id.menu_item_mark_all2:
			case R.id.menu_item_mark_all: {
				mItemClickPerforms = ITEMCLICK_DEFAULT;
				if (mMarkedFiles.sizeof(mFolderName)!=mDsFileList.size()) {
					for (FileListDataElement theItem: mDsFileList) {
						theItem.setMark(true);
						MarkFile(theItem,true);
					}
					if (mButtonPickFile!=null) {
						//enable Pick Marked Files button
						if (mPickFiles)
							mButtonPickFile.setEnabled(mMarkedFiles.sizeofAll()>0);
						//enable Pick Marked File button only if 1 marked
						if (mPickFile)
							mButtonPickFile.setEnabled(mMarkedFiles.sizeofAll()==1);
					}
					mFileListUI.refreshListView();
				}
				return true;
			}
			case R.id.menu_item_newfolder: {
				createNewFolder(anAct);
				return true;
			}
			case R.id.menu_item_copy_marked_to:
			case R.id.menu_item_move_marked_to: {
				Intent theIntent = new Intent(Intent.ACTION_PICK);
				BitsIntent.setFromExtra(theIntent,anAct.getComponentName().getClassName());
				theIntent.setData(Uri.parse("folder://"+mDsFileList.mCurrPath));
				try {
					anAct.startActivityForResult(theIntent,theMenuChoice);
				} catch (Exception e) {
					BitsDialog.ErrDialog(anAct,e);
				}
				mItemClickPerforms = ITEMCLICK_DEFAULT;
				return true;
			}
			case R.id.menu_item_copy_marked:
			case R.id.menu_item_move_marked:
			case R.id.menu_item_delete_marked: {
				BitsClickTask theClickTask = new BitsClickTask(new ProcessMarkedItems(anAct,
						theMenuChoice,new File(mFolderName)));
				theClickTask.setProcessName("process "+mMarkedFiles.sizeofAll()+" marked files");
				if (theMenuChoice==R.id.menu_item_delete_marked) {
					new DialogConfirm(anAct).setup(R.drawable.icon_recyclebin,
							R.string.dialog_confirm_delete_title,
							anAct.getString(R.string.dialog_confirm_delete_text,""),
							theClickTask,null).show();
				} else
					theClickTask.execute();
				mItemClickPerforms = ITEMCLICK_DEFAULT;
				return true;
				
			}		
			case R.id.menu_item_cancel_marked: {
				cancelMarkedFiles();
				return true;
			}
			case R.id.menu_item_send_marked: {
				if (sendMarkedFiles(anAct)) {
					mFileListUI.refreshListView();
				}
				mItemClickPerforms = ITEMCLICK_DEFAULT;
				return true;
			}
			case R.id.menu_item_info: {
				/*
				//get current version num
				BitsDialog d = BitsDialog.Builder(anAct);//,R.string.menu_item_info);
				d.addHtmlMsg(anAct.getString(R.string.version_display,
						anAct.getString(R.string.version_name,
						BitsLegacy.getAppVersionName(anAct))));
				d.addHtmlMsgImgButton(R.string.version_link,android.R.drawable.ic_menu_info_details,
						Uri.parse("http://www.blackmoonit.com/android/filebrowser#versioninfo"));
				d.addHtmlMsg(R.string.version_notes);
				d.show();
				*/
				anAct.showDialog(DIALOG_ABOUT);
				return true;
			}
			case R.id.menu_item_markstart:
			case R.id.menu_item_markmode: {
				mItemClickPerforms = (mItemClickPerforms==ITEMCLICK_OPENFILE)?ITEMCLICK_MARK:ITEMCLICK_OPENFILE;
				BitsLegacy.invalidateOptionsMenu(anAct);
				return true;
			}
			case R.id.menu_item_refreshcard: {
				BitsFileUtils.refreshExternalStorage(anAct);
				refillList();
				return true;
			}
			case R.id.menu_item_exit: {
				anAct.finish();
				return true;
			}
			/*
			case R.id.menu_item_plugins: {
				Intent theIntent = new Intent(Intent.ACTION_VIEW,Uri.parse(SCHEME_PLUGIN+"://"+getPackageName()+"/"+PLUGIN_PATH_MENU_OPTIONS));
				theIntent.addCategory(Intent.CATEGORY_ALTERNATIVE);
				BitsIntent.setFromExtra(theIntent,this.getComponentName().getClassName());
				try {
					startActivity(Intent.createChooser(theIntent,aMenuItem.getTitle()));
					return true;
				} catch (Exception e) {
					BitsDialog.ErrDialog(this,e);
					return false;
				}
			}
			*/
			case R.id.menu_item_jumpto:
				showJumpPointDialog(anAct);
				return true;
			case R.id.menu_item_search:
				anAct.onSearchRequested();
				return true;
			case R.id.menu_item_search_tips:
				anAct.showDialog(DIALOG_SEARCH_TIPS);
				return true;
			default:
				if (theMenuChoice==BitsLegacy.android_R_id_home(anAct)) {
					if (!mDsFileList.mCurrPath.equals("?"))
						fillList("..");
					return true;
				}				
		}//switch
		return false;
	}
	
	public boolean onCreateOptionsMenu(Activity anAct, Menu aMenu) {
		anAct.getMenuInflater().inflate(R.menu.options_filelist,aMenu);

		/*
		Intent theIntent;
		/*
		 * PLUGIN MENU - Allows other apps (plugins) to extend the menu with their own actions.
		 *-commenting out since addIntentOptions() is bugged to only allow first one found /
		theIntent = new Intent(Intent.ACTION_VIEW,Uri.parse(SCHEME_PLUGIN+"://"+getPackageName()+"/"+PLUGIN_PATH_MENU_OPTIONS));
		theIntent.addCategory(Intent.CATEGORY_ALTERNATIVE);
		
		PackageManager pm = getPackageManager();
		List<ResolveInfo> theList = pm.queryIntentActivities(theIntent,0);
		int numApps = (theList!=null)?theList.size():0;
		if (numApps==0) {
			aMenu.setGroupVisible(R.id.menu_group_plugin_options,false);
		} else if (numApps==1) {
			MenuItem mi = aMenu.findItem(R.id.menu_item_plugins);
			Drawable theIcon = mi.getIcon();
			if (theIcon!=null) {
				theIcon = BitsGraphicUtils.scaleDrawable(
						(BitmapDrawable)(theList.get(0).activityInfo.loadIcon(pm)),
						theIcon.getIntrinsicWidth(),theIcon.getIntrinsicHeight());
				if (theIcon!=null)
					mi.setIcon(theIcon);
				mi.setTitle(theList.get(0).loadLabel(pm));
			}
		}
		/*
	    // Search and populate the menu with acceptable offering applications.
		SubMenu sm = aMenu.findItem(R.id.menu_item_plugins).getSubMenu();
		sm.addIntentOptions(
		     R.id.menu_group_plugin_options,	// Menu group to which new items will be added
		     Menu.NONE,					// Unique item ID (none)
		     Menu.NONE,					// Order for the items (none)
		     this.getComponentName(),	// The current Activity name
		     null, 						// Specific items to place first (none)
		     theIntent,					// Intent created above that describes our requirements
		     Menu.FLAG_APPEND_TO_GROUP,	// Additional flags to control items
		     null);		// Array of MenuItems that correlate to specific items (none)
		if (!sm.hasVisibleItems()) {
			aMenu.setGroupVisible(R.id.menu_group_plugin_options,false);
		}
		*/

		/*
		 * ACTIONS submenu - Plugins that expand the Folder Actions sub menu
		 *-commenting out since addIntentOptions() is bugged to only allow first one found /
		theIntent = new Intent(null,Uri.parse(SCHEME_PLUGIN+"://"+getPackageName()+"/"+PLUGIN_PATH_MENU_ACTIONS));
		theIntent.addCategory(Intent.CATEGORY_ALTERNATIVE);
		// Search and populate the menu with acceptable plugin activities.
		aMenu.findItem(R.id.menu_item_folderactions).getSubMenu().addIntentOptions(
		     R.id.menu_group_plugin_actions,	// Menu group to which new items will be added
		     Menu.NONE,					// Unique item ID (none)
		     Menu.NONE,					// Order for the items (none)
		     this.getComponentName(),	// The current Activity name
		     null, 						// Specific items to place first (none)
		     theIntent, 				// Intent created above that describes our requirements
		     Menu.FLAG_APPEND_TO_GROUP,	// Additional flags to control items
		     null);		// Array of MenuItems that correlate to specific items (none)
		*/
		
	    /*
	     * PREFERENCE activity - Add the Prefs Activity menu item
	     * even though as of Android 2.2, addIntentOptions only gets first one found, we only want 1
	     */
		Intent prefsActivity = new Intent(anAct,AppPreferences.class);
		prefsActivity.addCategory(Intent.CATEGORY_PREFERENCE);
		prefsActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	    // Get the settings activity and use most of it's definitions to auto-handle menu item
    	MenuItem theSettingsTemplate = aMenu.findItem(R.id.menu_item_settings);
    	aMenu.removeItem(R.id.menu_item_settings);
		aMenu.addIntentOptions(
			 theSettingsTemplate.getGroupId(),	// Menu group to which new items will be added
			 theSettingsTemplate.getItemId(),	// Unique item ID
			 theSettingsTemplate.getOrder(),		// Order for the item
		     anAct.getComponentName(),	// The current Activity name
		     null,						// Specific items to place first (none)
		     prefsActivity,				// Intent created above that describes our requirements
		     0,							// Additional flags to control items (none)
		     null);	// Array of MenuItems that correlate to specific items (none)
		//Android Market will not allow android drawables as part of the Manifest xml
		MenuItem theSettingsItem = aMenu.findItem(R.id.menu_item_settings);
		theSettingsItem.setIcon(android.R.drawable.ic_menu_preferences);

		return true; 
	}

	public boolean onPrepareOptionsMenu(Menu aMenu) {
		File currFolder = new File(mDsFileList.mCurrPath);
		int numItems = mDsFileList.size();
		boolean bMarkModeOff = (mItemClickPerforms==ITEMCLICK_OPENFILE);
		boolean HasMarked = (mMarkedFiles.size()>0);
		boolean folderCanRead = (currFolder.canRead() || mDsFileList.mCurrPath.equals("?"));
		boolean folderCanWrite = (currFolder.canWrite());
		boolean HasMarkedInCurrFolder = (mMarkedFiles.containsTree(currFolder.getPath()));
		boolean AllMarkedInCurrFolder = (mMarkedFiles.sizeof(currFolder.getPath())==numItems);
		boolean bMarkAll = (numItems>0 && !(HasMarked && !HasMarkedInCurrFolder) && !AllMarkedInCurrFolder);

		//folder submenu
		//aMenu.findItem(R.id.menu_item_folderactions).getSubMenu().setHeaderTitle(mFolderName);
		aMenu.findItem(R.id.menu_item_newfolder).setEnabled(folderCanRead && folderCanWrite);
		aMenu.findItem(R.id.menu_item_refreshcard).setEnabled(BitsFileUtils.isExternalStorageMounted());
		aMenu.findItem(R.id.menu_item_mark_all2).setVisible(folderCanRead && bMarkAll);

		//marking
		aMenu.findItem(R.id.menu_item_markstart).setVisible(!HasMarked && bMarkModeOff);
		aMenu.findItem(R.id.menu_item_markactions).setVisible(HasMarked || !bMarkModeOff);
		MenuItem m = aMenu.findItem(R.id.menu_item_markmode);
		if (m!=null) {
			if (bMarkModeOff) {
				m.setTitle(R.string.menu_item_markstart);
				//m.setIcon(R.drawable.emblem_check);
			} else {
				m.setTitle(R.string.menu_item_markstop);
				//m.setIcon(R.drawable.icon_uncheck);
			}
		}
		aMenu.findItem(R.id.menu_item_mark_all).setVisible(folderCanRead && bMarkAll);
		aMenu.findItem(R.id.menu_item_cancel_marked).setVisible(HasMarked);
		aMenu.findItem(R.id.menu_item_copy_marked).setEnabled(folderCanWrite && HasMarked);
		aMenu.findItem(R.id.menu_item_send_marked).setEnabled(HasMarked);
		aMenu.findItem(R.id.menu_item_move_marked).setEnabled(folderCanWrite && HasMarked  && !HasMarkedInCurrFolder);
		aMenu.findItem(R.id.menu_item_delete_marked).setEnabled(folderCanWrite && HasMarked);
		aMenu.findItem(R.id.menu_item_copy_marked_to).setEnabled(HasMarked);
		aMenu.findItem(R.id.menu_item_move_marked_to).setEnabled(HasMarked);
		
		return true;  //true means show the menu
	}

	private boolean editFilename(final Activity anAct, final File aFile, String aEditedFilename) {
		if (!aFile.canWrite())
			return false;
		int theEndSelect = aEditedFilename.length();
		if (!aEditedFilename.endsWith(".bin")) {
			theEndSelect = Math.min(aEditedFilename.lastIndexOf("."),theEndSelect);
			if (theEndSelect<2)
				theEndSelect = aEditedFilename.length();
		}
		final DialogQuickEdit dRename = new DialogQuickEdit(anAct);
		View.OnClickListener onRenameItem = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				File f = renameFile(anAct,aFile,dRename.getTrimmedText());
				if (mPickFolder && f!=null && f.isDirectory()) {
					fillList(f.getPath());
				}
			}
		};
		dRename.setup(R.string.dialog_rename_title,aEditedFilename,0,theEndSelect,onRenameItem).show();
		return true;
	}
	
	protected FileListDataElement getListItem(int aIndex) {
		try {
			return mDsFileList.get(aIndex);
		} catch (IndexOutOfBoundsException ioobe) {
			return null;
		}
	}
	
	protected void refreshFolderInfo(File aFolder) {
		if (aFolder!=null) {
			Activity anAct = mAct.get();
			//set header with foldername
			String s = BitsFileUtils.getPathRelativeToExternalStorage(aFolder.getPath());
			mCurrFolderView.setText(s);
			
			String theMemSpaceText = "";
			boolean bShowLowSpaceWarning = false;
			Long theFreeSpace = BitsFileUtils.freeSpace(aFolder);
			if (anAct!=null && theFreeSpace!=null) {
				theMemSpaceText = android.text.format.Formatter.formatFileSize(anAct,theFreeSpace);
				bShowLowSpaceWarning = (theFreeSpace<10*1024*1024L);
			}
			/* code here works, just going to use the new lib function instead
			if (aFolder.exists() && aFolder.canWrite()) {
				try {
					if (mStatFs!=null) {
						mStatFs.restat(aFolder.getPath());
					} else {
						mStatFs = new StatFs(aFolder.getPath());
					}
				} catch (IllegalArgumentException e) {
					mStatFs = null;
				}
				if (mStatFs!=null) {
					long theFreeSpace = mStatFs.getAvailableBlocks();
					//having the * on same line above gave terrible results when > max_int
					theFreeSpace = theFreeSpace*mStatFs.getBlockSize();
					if (mAct.get()!=null)
						theMemSpaceText = android.text.format.Formatter.formatFileSize(mAct.get(),theFreeSpace);
				}
			}
			*/
			mMemSpaceView.setText(theMemSpaceText);
			mMemLowSpaceWarning.setVisibility((bShowLowSpaceWarning)?View.VISIBLE:View.GONE);

			boolean bIsNotRoot = !aFolder.getPath().equals(File.separator);
			mButtonGoUp.setVisibility((bIsNotRoot)?View.VISIBLE:View.INVISIBLE);
			mFolderReadOnlyView.setVisibility((!aFolder.canWrite())?View.VISIBLE:View.INVISIBLE);
		}
	}
	
	/**
	 * Refreshes the list view to reflect file system changes.
	 * @return Returns the folder that was displayed (may have changed to ensure existance).
	 */
	protected Folder refillList() {
		Folder theResult = mDsFileList.refillList(); 
		refreshFolderInfo(theResult);
		mFileListUI.refreshListView();
		return theResult;
	}
	
	/**
	 * If the file is a folder, display it's contents, else display the file's container folder
	 * and try to move the view to show the file itself in the list view.
	 * 
	 * @param aFile - the file or folder to display
	 * @return Returns the folder that was displayed (may have changed to ensure existance).
	 */
	protected Folder fillList(File aFile) {
		if (aFile!=null) {
			if (aFile.isDirectory())
				return fillList(aFile.getPath());
			else {
				mAutoSelectName = FileListDataElement.getDisplayName(aFile);
				return fillList(aFile.getParent());
			}
		}
		return null;
	}

	/**
	 * Display the contents of aFolderName.<br>
	 * ".." means display the parent of the current path.<br>
	 * "" is equivalent to displaying the root path of File.separator.<br>
	 * "?" means display the search results.<br>
	 * 
	 * @param aFolderName - folder name to display
	 * @return Returns the folder that was displayed (may have changed to ensure existance).
	 */
	protected Folder fillList(String aFolderName) {
		if (aFolderName==null)
			aFolderName = File.pathSeparator;
		int idxCurrentTop = AdapterView.INVALID_POSITION;
		if (mDsFileList.mCurrPath.equals(aFolderName)) {
			//refreshing current folder view, try to remember what the scroll position was
			idxCurrentTop = mFileListUI.getFirstVisibleItemPos();
		} else {
			//user navigated out of the folder, reset item click behavior
			mItemClickPerforms = ITEMCLICK_DEFAULT;
			idxCurrentTop = 0; //move list to top of folder
			if (aFolderName.equals("..")) {
				//try to scroll so that subfolder we just left is visible
				mAutoSelectName = FileListDataElement.getDisplayName(new File(mDsFileList.mCurrPath));
			}
			mFileIcons.clear(); //no need to cache pics we aren't viewing anymore
		}
		Folder theFolder = mDsFileList.fillList(aFolderName); 
		refreshFolderInfo(theFolder);
		mFolderName = mDsFileList.mCurrPath;
		mFileListUI.refreshListView();
		mFileListUI.onAfterFillList(idxCurrentTop);
		return theFolder;
	}

	/**
	 * Remove item from the current list view and delete the associated file from the file system.
	 * 
	 * @param aIndex - index of file item to delete
	 */
	protected void deleteItem(Activity anAct, int aIndex) {
		FileListDataElement theItem = getListItem(aIndex);
		if (theItem==null)
			return;
		if (BitsFileUtils.isFolderEmpty(theItem) && deleteFob(anAct,null,theItem)) {
			mMarkedFiles.removeFile(theItem);
			mFileIcons.removeThumbnail(theItem);
			mDsFileList.remove(aIndex);
			//BitsFileUtils.refreshExternalStorage(anAct);  removed for taking too long
			mFileListUI.refreshListView();
		} else {
			int msgRes = (theItem.isFile())?R.string.permission_denied:R.string.msg_delete_folder;
			BitsDialog.Builder(anAct,R.string.action_failed).show(msgRes);
		}
	}

	/**
	 * Renames the file and handles all the associated fallout in doing so.
	 * 
	 * @param origFile - file to rename
	 * @param newName - new filename 
	 * @return Returns the file object of the newly renamed file because the old file object 
	 * does <i>not</i> take on the new name after the rename function succeeds.
	 */
	protected File renameFile(Activity anAct, File origFile, String newName) {
		try {
			int theItemIdx = mDsFileList.indexOf(origFile);
			File newFile = BitsFileUtils.renameFob(origFile,newName);
			if (newFile!=null) {
				FileListDataElement theNewItem = new FileListDataElement(newFile.getPath());
				theNewItem.setMark(mMarkedFiles.contains(origFile));
				if (theNewItem.isMarked()) {
					mMarkedFiles.removeFile(origFile);
					mMarkedFiles.addFile(theNewItem);
				}
				mFileIcons.removeThumbnail(origFile);
				mFileIcons.removeThumbnail(theNewItem);
				BitsFileUtils.notifyMediaScanner(anAct,theNewItem);
				mDsFileList.set(theItemIdx,theNewItem);
				mFileListUI.refreshListView();
				return newFile;
			} else {
				if (origFile.getName().equals(newName))
					return origFile;
				else {
					BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
					return null;
				}
			}
		} catch (Exception e) {
			BitsDialog.ErrDialog(anAct,e);
			return null;
		}
	}

	protected boolean packageMarkedFiles(Intent aIntent, boolean bFilesOnly) {
		ArrayList<Uri> theUris = null;
		if (mMarkedFiles!=null)
			theUris = mMarkedFiles.toUriList(bFilesOnly);
		String theOverallMIMEtype = null;
		if (theUris!=null)
			theOverallMIMEtype = mMimeMap.getOverallMIMEtype(theUris.iterator());
		if (theOverallMIMEtype!=null) {
			aIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,theUris);
			aIntent.setType(theOverallMIMEtype);
			return true;
		} else {
			return false;
		}
	}
	
	protected boolean sendMarkedFiles(Activity anAct) {
		Intent theIntent = new Intent(BitsIntent.Intent_ACTION_SEND_MULTIPLE());
		theIntent.putExtra(BitsIntent.EXTRA_DIRECTORY,BitsFileUtils.deepestCommonFolder(mMarkedFiles.toUriList(false)));
		if (packageMarkedFiles(theIntent,false) && mSendClearsMarked) {
			clearMarkedFiles();
		}
		theIntent.setType(theIntent.getType());
		theIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET+Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		BitsIntent.setFromExtra(theIntent,anAct.getComponentName().getClassName());
		//set a default folder for where the zip file should be created
		theIntent.putExtra(BitsIntent.EXTRA_DEFAULT_URI, new File(mDsFileList.mCurrPath).toURI().toString() );
		try {
			anAct.startActivityForResult(Intent.createChooser(theIntent,anAct.getString(R.string.menu_item_send_marked)),RC_SEND);
			return true;
		} catch (Exception e) {
			BitsDialog.ErrDialog(anAct,e);
			return false;
		}
	}

	protected void toggleItemMark(int aIndex) {
		FileListDataElement theItem = getListItem(aIndex);
		if (theItem!=null && !theItem.containsMarked()) {
			boolean b = !theItem.isMarked();
			theItem.setMark(b);
			MarkFile(theItem,b);
			//enable Pick Marked File button
			if ((mPickFile || mPickFiles) && mButtonPickFile!=null)
				mButtonPickFile.setEnabled(mMarkedFiles.size()>0);
			mFileListUI.invalidateViews();
		}
	}

	private void MarkFile(File aFile, Boolean bIsMarked) {
		if (bIsMarked) {
			mMarkedFiles.addFile(aFile);
		} else {
			mMarkedFiles.removeFile(aFile);
		}
	}

	public void onActivityResult(final Activity anAct, final int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case RC_UNZIP: {
				if (resultCode==Activity.RESULT_OK && data!=null && data.getData()!=null) {
					final File theZipFile = new File(Uri.decode(data.getData().getPath()));
					String theDestFolderName = data.getStringExtra(BitsIntent.EXTRA_DIRECTORY);
					final File theDestFolder;
					if (theDestFolderName!=null && !theDestFolderName.equals(""))
						theDestFolder = new File(theDestFolderName);
					else
						theDestFolder = theZipFile.getParentFile();
					new BitsThreadTask(new Runnable() {
						@Override
						public void run() {
							unpackZipFile(anAct,theZipFile,theDestFolder);
						}
					}).setProcessName("unpack-zip").execute();
				}
				break;
			}//case
			case RC_SEND: {
				if (resultCode==Activity.RESULT_OK && data!=null && data.getData()!=null && data.getExtras()!=null &&
						BitsIntent.isIntentFrom(data,"com.blackmoonit.android.FileBrowser.ZipPackActivity")) {
					final File theZipFile = BitsFileUtils.getSafeNewFile(new File(data.getData().getPath()));
					Object theExtra = data.getExtras().get(Intent.EXTRA_STREAM); 
					ArrayList<? extends Parcelable> theFileList = null;
					ArrayList<Uri> theSingleFileList = null;
					if (theExtra instanceof Uri) {
						theSingleFileList = new ArrayList<Uri>(1);
						theSingleFileList.add((Uri)theExtra);
					} else {
						theFileList = data.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
					}
					final ArrayList<? extends Parcelable> zipThese = (theFileList!=null)?theFileList:theSingleFileList;
					final String theBasePath = data.getStringExtra(BitsIntent.EXTRA_DIRECTORY);
					Runnable r = new Runnable() {	
						@Override
						public void run() {
							packZipFile(anAct,theZipFile,zipThese,theBasePath);
						}
						
					};
					new BitsThreadTask(r).setProcessName("pack-zip").execute();
				}
				break;
			}//case
			case R.id.menu_item_copy_marked_to:
			case R.id.menu_item_move_marked_to: {
				if (resultCode==Activity.RESULT_OK && data!=null && data.getData()!=null) {
					final File theDestFolder = new File(data.getData().getPath());
					if (theDestFolder.exists()) {
						int theMenuChoice = (requestCode==R.id.menu_item_copy_marked_to)?
								R.id.menu_item_copy_marked:R.id.menu_item_move_marked;
						new BitsClickTask(new ProcessMarkedItems(anAct,theMenuChoice,theDestFolder)
								).setProcessName("copy/move marked items to loc").execute();
						mItemClickPerforms = ITEMCLICK_DEFAULT;
					}
				}
				break;
			}		

		}//switch
	};
	
	private void packZipFile(final Activity anAct, File aZipFile, 
			ArrayList<? extends Parcelable> aFileList, String aBasePath) {
		final FilePackageZip theZipPackage = new FilePackageZip(aZipFile);
		if (aBasePath!=null)
			theZipPackage.mBasePath = aBasePath;

		if (msgHandler!=null) {
			theZipPackage.mMsgHandler = msgHandler;
			theZipPackage.mProgressID = msgHandler.createNewProgressEvent(R.string.act_name_zippack);
			msgHandler.getMsgProgressStart(theZipPackage.mProgressID,
					aZipFile.getName(),aFileList.size()*1L).sendToTarget();
			Thread.yield();
		}
		theZipPackage.pack(aFileList.iterator(), new OnEachFileEntry() {

			@Override
			public boolean process(ZipOutputStream zos, File aFile, ZipEntry anEntry) {
				if (aFile!=null) {
					//update overall progress
					if (msgHandler!=null)
						msgHandler.getMsgProgressTotalUpdate(theZipPackage.mProgressID,
								aFile.getName(),1L).sendToTarget();
					if (aFile.isDirectory()) {
						File[] subfiles = aFile.listFiles();
						if (msgHandler!=null && subfiles!=null)
							msgHandler.getMsgProgressIncreaseTotal(theZipPackage.mProgressID,
									subfiles.length*1L).sendToTarget();
					}
				}
				return (msgHandler==null || msgHandler.isStillProcessing(theZipPackage.mProgressID));
			}
			
		}, new OnException() {

			@Override
			public void caught(Exception e) {
				BitsDialog.ErrDialog(anAct,e);
			}
			
		});
		if (msgHandler!=null)
			msgHandler.getMsgProgressFinish(theZipPackage.mProgressID).sendToTarget();
	}
	
	private void unpackZipFile(final Activity anAct, File aZipFile, final File aDestFolder) {
		final FilePackageZip theZipPackage = new FilePackageZip(aZipFile);
		if (!aDestFolder.exists())
			aDestFolder.mkdirs();
		if (msgHandler!=null) {
			theZipPackage.mMsgHandler = msgHandler;
			theZipPackage.mProgressID = msgHandler.createNewProgressEvent(R.string.act_name_zipunpack);
			ProgressBarHandler.sendProgressMsg(msgHandler.getMsgProgressStart(theZipPackage.mProgressID,
					aDestFolder.getName(),-1L));
			Thread.yield();
		}
		final ModalVar<Integer> theWhenSameFilenameAction = new ModalVar<Integer>(anAct);
		theZipPackage.foreach(new OnEachEntry() {

			@Override
			public boolean process(ZipInputStream zis, ZipEntry anEntry) {
				try {
					File theNewFile = new File(aDestFolder,anEntry.getName());
					/* compressed size is usually -1, just use indeterminant bar
					//update overall progress
					Long approxSize = anEntry.getCompressedSize()+30+anEntry.getName().length();
					*/
					if (msgHandler!=null)
						msgHandler.getMsgProgressTotalUpdate(theZipPackage.mProgressID,
								theNewFile.getName(),null).sendToTarget();
					if (theNewFile.exists() && theNewFile.isFile()) {
						theWhenSameFilenameAction.setOnObtainValueUI(new Runnable() {
							@Override
							public void run() {
								if (msgHandler!=null) {
									//turn on the progress group highlight and show the dialog
									msgHandler.setProgressHighlightUI(theZipPackage.mProgressID,true);
									AskAboutSameFilename(anAct,theZipPackage.mProgressID,theWhenSameFilenameAction);
								} else {
									theWhenSameFilenameAction.setValue(DialogFileCollision.FILENAME_SAME_KEEPBOTH);
								}
							}
						});
						theWhenSameFilenameAction.setOnSetValueUI(new Runnable() {
							@Override
							public void run() {
								//turn off highlight
								if (msgHandler!=null) {
									msgHandler.setProgressHighlightUI(theZipPackage.mProgressID,false);
								}
								
							}
						});
						//blocks read until message/dialog sets the value from the UI thread
						int theResult = theWhenSameFilenameAction.getValue();
						Thread.yield(); //chance to let the onSetValueUI event run before we continue
						
						//act on the users choice
						switch (theResult) {
							case DialogFileCollision.FILENAME_SAME_KEEPBOTH:
								theNewFile = BitsFileUtils.getSafeNewFile(theNewFile);
							case DialogFileCollision.FILENAME_SAME_OVERWRITE:
								theZipPackage.unpackFileEntry(zis,theNewFile,anEntry);
						}//switch
					} else {
						theZipPackage.unpackFileEntry(zis,theNewFile,anEntry);
					}
				} catch (IOException e) {
					BitsDialog.ErrDialog(anAct,e);
					return false;
				} catch (Exception e) {
					BitsDialog.Builder(anAct,android.R.string.dialog_alert_title,
							android.R.drawable.ic_dialog_alert).show(R.string.action_failed);
					return false;
				}
				Thread.yield();
				return (msgHandler==null || msgHandler.isStillProcessing(theZipPackage.mProgressID));
			}
			
		}, new OnException() {

			@Override
			public void caught(Exception e) {
				BitsDialog.ErrDialog(anAct,e);
			}
			
		});
		if (msgHandler!=null)
			msgHandler.getMsgProgressFinish(theZipPackage.mProgressID).sendToTarget();
		Thread.yield();
	}

	protected boolean isJumpActivity(ResolveInfo aResolveInfo) {
		return aResolveInfo.activityInfo.name.equals("com.blackmoonit.android.FileBrowser.ViewFileAsJumpPoint");
	}

	public Uri getFileUriForIntent(Activity anAct, File aFile) {
		String theAction = (anAct!=null) ? BitsIntent.getIntentAction(anAct.getIntent()) : null;
		if (Intent.ACTION_GET_CONTENT.equals(theAction) && mMimeMap.isCategory("image",aFile)) {
			return BitsFileUtils.getContentUriFromImageFile(anAct,aFile);
		} else {
			return BitsIntent.getViewFileUri(aFile);
		}
	}
	
	public boolean openFile(File aFile) {
		String theMIMEType = mMimeMap.guessMIMEtype(aFile);
		if (theMIMEType==null)
			return openFileAs(aFile);
		final Activity anAct = mAct.get();
		Intent theIntent = new Intent(Intent.ACTION_VIEW);
		final Uri theFileUri = getFileUriForIntent(anAct,aFile);
		theIntent.setDataAndType(theFileUri, theMIMEType);
		BitsIntent.setFromExtra(theIntent,anAct.getComponentName().getClassName());
		try {
			//slightly different call if a zip file
			if (RC_UNZIP!=Activity.RESULT_CANCELED && theMIMEType.equals("application/zip")) {
				theIntent.putExtra(BitsIntent.EXTRA_RETURN_DATA,false);
				mAct.get().startActivityForResult(theIntent,RC_UNZIP);
			} else {
				//avoid JumpTo activity being the default
				List<ResolveInfo> appList = anAct.getPackageManager().queryIntentActivities(theIntent,
						PackageManager.MATCH_DEFAULT_ONLY);
				if (appList.size()>0 && isJumpActivity(appList.get(0))) {
					return openFileAs(aFile);
				} else {
					theIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK+Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
					anAct.startActivity(theIntent);
				}
			}
			return true;
		} catch (ActivityNotFoundException anfe) {
			theIntent.setDataAndType(theFileUri,"text/plain");
			try {
				anAct.startActivity(theIntent);
				return true;
			} catch (Exception e) {
				BitsDialog.ErrDialog(anAct,e);
			}
		} catch (SecurityException se) {
			BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
		} catch (Exception e) {
			BitsDialog.ErrDialog(anAct,e);
		}
		return false;
	}

	public boolean openFileWith(File aFile) {
		String theMIMEtype = mMimeMap.getMIMEtype(aFile);
		if (theMIMEtype!=null) {
			Activity anAct = mAct.get();
			Intent theIntent = new Intent(Intent.ACTION_VIEW);
			Uri theFileUri = getFileUriForIntent(anAct,aFile);
			String theMIMEcategory = mMimeMap.getMIMEcategory(aFile);
			if (theMIMEcategory!=null && !theMIMEcategory.equals("application/*"))
				theIntent.setDataAndType(theFileUri,theMIMEcategory);
			else
				theIntent.setDataAndType(theFileUri,theMIMEtype);
			BitsIntent.setFromExtra(theIntent,anAct.getComponentName().getClassName());
			try {
				if (RC_UNZIP!=Activity.RESULT_CANCELED && theMIMEtype.equals("application/zip")) {
					theIntent.putExtra(BitsIntent.EXTRA_RETURN_DATA,false);
					anAct.startActivityForResult(theIntent,RC_UNZIP);
				} else {
					theIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK+Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
					anAct.startActivity(Intent.createChooser(theIntent,aFile.getName()));
				}
				return true;
			} catch (SecurityException se) {
				BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
			} catch (Exception e) {
				BitsDialog.ErrDialog(anAct,e);
			}
		}
		return false;
	}
	
	public boolean openFileAs(File aFile) {
		Activity anAct = mAct.get();
		Intent theIntent = new Intent(Intent.ACTION_VIEW);
		Uri theFileUri = getFileUriForIntent(anAct,aFile);
		theIntent.setDataAndType(theFileUri,"*/*");
		BitsIntent.setFromExtra(theIntent,anAct.getComponentName().getClassName());
		try {
			theIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK+Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			anAct.startActivity(Intent.createChooser(theIntent,aFile.getName()));
			return true;
		} catch (SecurityException se) {
			BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
		} catch (Exception e) {
			BitsDialog.ErrDialog(anAct,e);
		}
		return false;
	}
	
    public boolean editFile(File aFile) {
    	String theIntentType = mMimeMap.getMIMEtype(aFile);
		if (theIntentType!=null) {
			Activity anAct = mAct.get();
			Uri theUri = getFileUriForIntent(anAct,aFile);
			Intent theIntent = new Intent(Intent.ACTION_EDIT);
			theIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK+Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			theIntent.setDataAndType(theUri, theIntentType);
			BitsIntent.setFromExtra(theIntent,anAct.getComponentName().getClassName());
			try {
				anAct.startActivity(theIntent);
				return true;
			} catch (SecurityException se) {
				BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
			} catch (Exception e) {
				BitsDialog.ErrDialog(anAct,e);
			}
		}
		return false;
	}

	public boolean sendFile(File aFile) {
		Activity anAct = mAct.get();
		String theMIMEtype = null;
		String theAction = Intent.ACTION_SEND;
		if (aFile.isFile()) {
			theMIMEtype = mMimeMap.getMIMEtype(aFile);
		} else {
			File[] af = aFile.listFiles();
			if (af!=null)
				theMIMEtype = mMimeMap.getOverallMIMEtype(Arrays.asList(af).iterator());
			theAction = BitsIntent.Intent_ACTION_SEND_MULTIPLE();
		}
		if (theMIMEtype!=null) {
			Intent theIntent = new Intent(theAction);
			theIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET+Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			theIntent.setType(theMIMEtype);
			if (theAction.equals(Intent.ACTION_SEND)) {
				theIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(aFile));
			} else {
				ArrayList<Uri> theUris = new ArrayList<Uri>();
				theUris.add(Uri.fromFile(aFile));
				theIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,theUris);
			}
			theIntent.putExtra(Intent.EXTRA_SUBJECT, aFile.getName());
			theIntent.putExtra(BitsIntent.EXTRA_DIRECTORY,mDsFileList.mCurrPath);
			BitsIntent.setFromExtra(theIntent,anAct.getComponentName().getClassName());
			try {
				anAct.startActivityForResult(Intent.createChooser(theIntent, aFile.getName()),RC_SEND);
				return true;
			} catch (SecurityException se) {
				BitsDialog.Builder(anAct,R.string.action_failed).show(R.string.permission_denied);
			} catch (Exception e) {
				BitsDialog.ErrDialog(anAct,e);
			}
		}
		return false;
	}

	/**
	 * Copies the scrFob to destFob.
	 * @param anAct - activity context
	 * @param aProgressID - progress group ID
	 * @param srcFob - source file/folder
	 * @param destFob - destination file/folder
	 * @return Returns true if successful, else false.
	 */
	public boolean copyFob(Activity anAct, final Object aProgressID, File srcFob, File destFob) {
		OnEachFile theCopyEvent = new OnEachFile() {
			@Override
			public void beforeProcess(File srcFob, File destFob) {
			}
			@Override
			public void afterProcess(File srcFob, File destFob) {
				if (msgHandler!=null && !msgHandler.isStillProcessing(aProgressID)) {
					throw new CancellationException();
				}
			}
		};
		Exception theCopyResult = BitsFileUtils.copyFob(srcFob,destFob,theCopyEvent,msgHandler,aProgressID);
		if (theCopyResult==null) {
			return true;
		} else if (theCopyResult instanceof CancellationException) {
			return false;
		} else if (theCopyResult instanceof SecurityException) {
			msgHandler.obtainMessage(MSG_ACTION_FAILED).sendToTarget();
		} else if (theCopyResult instanceof IOException) {
			msgHandler.obtainMessage(MSG_ACTION_FAILED).sendToTarget();
		} else if (theCopyResult instanceof Exception) {
			msgHandler.obtainMessage(MSG_SHOW_EXCEPTION,theCopyResult);
		}
		return false;
	}
	
	/**
	 * Deletes the file, or sends it to the recycle bin, depending on settings.
	 * @param anAct - activity context
	 * @param aProgressID - progress group ID
	 * @param aFile - file to delete
	 * @return Returns true if the file was successfully deleted or recycled.
	 */
	public boolean deleteFob(final Activity anAct, final Object aProgressID, File aFile) {
		Exception theDelResult = null;
		BitsFileUtils.OnEachFile theFileEvent = new BitsFileUtils.OnEachFile() {
			@Override
			public void beforeProcess(File srcFob, File destFob) {
			}
			@Override
			public void afterProcess(File srcFob, File destFob) {
				if (mFileIcons!=null)
					mFileIcons.removeThumbnail(srcFob);
				if (mMarkedFiles!=null)
					mMarkedFiles.removeFile(srcFob);
				if (msgHandler!=null && !msgHandler.isStillProcessing(aProgressID)) {
					throw new CancellationException();
				}
			}
		};
		if (mUseRecycleBin) {
			if (aFile!=null && mRecycleBin!=null &&
					!BitsFileUtils.getCanonicalPath(aFile).startsWith(BitsFileUtils.getCanonicalPath(mRecycleBin))) {
				File srcFob = aFile;
				File destFob = BitsFileUtils.getSafeNewFile(new File(mRecycleBin,srcFob.getName()));
				theDelResult = BitsFileUtils.moveFob(srcFob,destFob,theFileEvent,msgHandler,aProgressID);
				BitsFileUtils.touchFile(destFob,0);
				//if copy or touch fails, not going to consider delete a failure
				//undelete still possible until program exit
				if (theDelResult==null) {
					//move completed successfully, cancel delete
					return true;
				}
			}
		}
		if (!(theDelResult instanceof CancellationException) && !(theDelResult instanceof SecurityException))
			theDelResult = BitsFileUtils.deleteFob(aFile,theFileEvent,msgHandler,aProgressID);
		if (theDelResult==null) {
			return true;
		} else if (theDelResult instanceof CancellationException) {
			return false;
		} else if (theDelResult instanceof SecurityException) {
			msgHandler.obtainMessage(MSG_ACTION_FAILED).sendToTarget();
		} else if (theDelResult instanceof Exception) {
			msgHandler.obtainMessage(MSG_SHOW_EXCEPTION,theDelResult);
		}
		return false;
	}

	/**
	 * Moves the scrFob to destFob.
	 * @param anAct - activity context
	 * @param aProgressID - progress group ID
	 * @param srcFile - source file/folder
	 * @param destFile - destination file/folder
	 * @return Returns true if successful, else false.
	 */
	public boolean moveFob(final Activity anAct, final Object aProgressID, File srcFob, File destFob) {
		OnEachFile theMoveEvent = new OnEachFile() {
			@Override
			public void beforeProcess(final File srcFob, final File destFob) {
			}
			@Override
			public void afterProcess(final File srcFob, final File destFob) {
				if (mFileIcons!=null) {
					mFileIcons.removeThumbnail(srcFob);
					mFileIcons.removeThumbnail(destFob);
				}
				if (mMarkedFiles!=null)
					mMarkedFiles.removeFile(srcFob);
				anAct.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (mDsFileList!=null)
							mDsFileList.removeFile(srcFob);
					}
				});
				if (msgHandler!=null && !msgHandler.isStillProcessing(aProgressID)) {
					throw new CancellationException();
				}
			}
		};
		Exception theMoveResult = BitsFileUtils.moveFob(srcFob,destFob,theMoveEvent,msgHandler,aProgressID);
		if (theMoveResult==null) {
			return true;
		} else if (theMoveResult instanceof CancellationException) {
			return false;
		} else if (msgHandler!=null && theMoveResult instanceof SecurityException) {
			msgHandler.obtainMessage(MSG_ACTION_FAILED).sendToTarget();
		} else if (msgHandler!=null && theMoveResult instanceof Exception) {
			msgHandler.obtainMessage(MSG_SHOW_EXCEPTION,theMoveResult);
		}
		return false;
	}
	
	/**
	 * Remove all contents of the recycle bin.
	 */
	public void emptyRecycleBin() {
		new BitsClickTask(new BitsClickTask.TaskDef() {
			
			@Override
			public Object beforeTask(View v) {
				mAct.get().setProgressBarIndeterminateVisibility(true);
				return BitsFileUtils.getRecycleBin(mAct.get());
			}

			@Override
			public Object doTask(View v, final Object aBin) {
				if (aBin!=null)
					BitsFileUtils.deleteFolderContents((File)aBin,null,null,0);
				return aBin;
			}

			@Override
			public void afterTask(View v, Object aBin) {
				mAct.get().setProgressBarIndeterminateVisibility(false);
				if (aBin!=null && mFolderName.equals(((File)aBin).getPath())) {
					refillList();							
				}
			}
			
		}).setProcessName("empty recycle bin").execute();
	}
	
	/**
	 * Remove contents from thumbnail cache that are more than X days old.
	 * @param aFolder - folder to compact
	 * @param aNumDaysOld - maximum age of files, all files over this age are deleted
	 */
	public void compactFolder(final File aFolder, final int aNumDaysOld, String aProcessName) {
		if (aFolder==null)
			return;
		new BitsClickTask(new BitsClickTask.TaskDef() {
			private final Activity anAct = mAct.get();
			
			@Override
			public Object beforeTask(View v) {
				anAct.setProgressBarIndeterminateVisibility(true);
				return System.currentTimeMillis()-(aNumDaysOld*FileMatcher.ONE_DAY_MILLIS);
			}

			@Override
			public Object doTask(View v, final Object tossIfOlderThanThis) {
				File[] theTrash = aFolder.listFiles(new FileFilter() {
					@Override
					public boolean accept(File aFile) {
						return (aFile!=null && aFile.lastModified()<(Long)tossIfOlderThanThis);
					}
					
				});
				if (theTrash!=null) {
					for (File theFob:theTrash) {
						BitsFileUtils.deleteFob(theFob,null,null,null);
						Thread.yield();
					}
				}
				return anAct;
			}

			@Override
			public void afterTask(View v, Object aTaskResult) {
				if (aTaskResult!=null)
					anAct.setProgressBarIndeterminateVisibility(false);
			}
			
		}).setProcessName(aProcessName).execute();
	}
	
	

}
