package com.blackmoonit.android.FileBrowser;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.blackmoonit.widget.BitsArrayAdapter;
import com.blackmoonit.widget.ViewCacher;

/**
 * Adapter for the FileListActivity.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileListAdapter extends BitsArrayAdapter<FileListDataElement> {
	protected final FileIcons mFileIcons;
	protected final Drawable mFolderIcon;
	public boolean bShowFolderInfo = false;
	
	public FileListAdapter(Context aContext, FileListDataSource aDataSource, FileIcons aFileIcons) {
		this(aContext, R.layout.fb_item, aDataSource, aFileIcons);
	}

	protected FileListAdapter(Context aContext, int aLayoutResId,
			FileListDataSource aDataSource, FileIcons aFileIcons) {
		super(aContext, aLayoutResId, aDataSource);
		mFileIcons = aFileIcons;
		mFolderIcon = aContext.getResources().getDrawable(R.drawable.item_folder);
		mResizableTextViews = new int[] {
				R.id.fb_item_filename, 
				R.id.fb_item_filefolder, 
				R.id.fb_item_fileinfo
		};
	}
	
	@Override
	protected ViewCacher getNewViewCacher() {
		return new ViewCacher(6); //6 views, since we know this in advance, optimize by telling cache
	}

	public void applyFileName(final FileListDataElement anItem, TextView v) {
		v.setText(anItem.getDisplayName());
		//since Accessibility reads the text and ignores the content desc, this is useless
		//BitsLegacy.setViewContentDesc(v,anItem.mViewDesc);
	}
	
	public void applyFileFolder(final FileListDataElement anItem, TextView v) {
		if (bShowFolderInfo) {
			v.setVisibility(View.VISIBLE);
			v.setText(anItem.mExternalStorageRelativePath);
		}
	}	
	
	public void applyFileInfo(final FileListDataElement anItem, TextView v) {
		Long displayDate = anItem.mLastModified;
		String theFileInfoText = "";
		if (anItem.isDirectory()) {
			if (anItem.isFileJumpPoint())
				theFileInfoText += "§";
			if (displayDate!=0L) {
				if (theFileInfoText.length()>0)
					theFileInfoText += ", ";
				theFileInfoText += v.getContext().getString(R.string.folder_info_fmtstr,displayDate,displayDate);
			}
		} else {
			String displaySize = android.text.format.Formatter.formatFileSize(v.getContext(),anItem.mSize); 
			theFileInfoText = v.getContext().getString(R.string.file_info_fmtstr,displaySize,displayDate,displayDate);
		}
		v.setText(theFileInfoText);
	}
	
	protected void applyFolderImg(final FileListDataElement anItem, ImageView v) {
		if (mFileIcons.mOnSetThumbnail!=null)
			mFileIcons.mOnSetThumbnail.onSetThumbnail(v,mFolderIcon);
		else
			v.setImageResource(R.drawable.item_folder);
	}
	
	protected void applyFileImg(final FileListDataElement anItem, ImageView v) {
		mFileIcons.setFileIcon(v,anItem,mFileIcons.scaleFactor);
	}
	
	//aScaleOverride = 1 for no override, 0 for no scaling at all.
	public void applyFileIcon(final FileListDataElement anItem, ImageView v) {
		mFileIcons.checkRecycleView(v,anItem);		
		//v.setVisibility(View.VISIBLE);
		if (anItem.isDirectory()) {
			applyFolderImg(anItem,v);
		} else {
			applyFileImg(anItem,v);
		}
	}
	
	public void applyFileEmblem(final FileListDataElement anItem, ImageView v) {
		if (!anItem.canRead()) {
			v.setImageResource(R.drawable.emblem_unreadable);
			v.setVisibility(View.VISIBLE);				
		} else if (!anItem.canWrite()) {
			v.setImageResource(R.drawable.emblem_readonly);
			v.setVisibility(View.VISIBLE);				
		} else {
			v.setVisibility(View.INVISIBLE);				
		}
	}
	
	public void applyFileMark(final FileListDataElement anItem, ImageView v) {
		v.setVisibility(anItem.isMarked()||anItem.containsMarked()?View.VISIBLE:View.INVISIBLE);
		if (!anItem.containsMarked())
			v.setImageResource(R.drawable.emblem_check);
		else
			v.setImageResource(R.drawable.emblem_check_partial);
		//v.setAlpha(anItem.containsMarked()?0x90:0xFF); //affects all checks if done this way
	}
	
	/**
	 * Set all the various pieces of the item view.
	 * @param anItemView - View containing all views needed to display an item
	 * @return Returns the View passed in.
	 */
	public View applyItemView(FileListDataElement anItem, View anItemView) {
		applyFileName(anItem,(TextView)getViewHandle(anItemView,R.id.fb_item_filename));
		applyFileFolder(anItem,(TextView)getViewHandle(anItemView,R.id.fb_item_filefolder));
		applyFileInfo(anItem,(TextView)getViewHandle(anItemView,R.id.fb_item_fileinfo));
		applyFileIcon(anItem,(ImageView)getViewHandle(anItemView,R.id.ImageFileThumb));
		applyFileEmblem(anItem,(ImageView)getViewHandle(anItemView,R.id.ImageReadOnlyEmblem));
		applyFileMark(anItem,(ImageView)getViewHandle(anItemView,R.id.ImageSelectedEmblem));
		return anItemView;
	}

	/**
	 * During the list's onRecycleView callback, recycle our icon/thumbnail.
	 * @param anItemView - View containing all views needed to display an item.
	 */
	public void onRecycleView(View anItemView) {
		ImageView theImageView = (ImageView)getViewHandle(anItemView,R.id.ImageFileIcon);
		mFileIcons.recycleView(theImageView);
	}

}
