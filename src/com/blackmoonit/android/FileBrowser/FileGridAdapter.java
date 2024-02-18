package com.blackmoonit.android.FileBrowser;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Adapter for the grid view of file browser.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class FileGridAdapter extends FileListAdapter {

	public FileGridAdapter(Context aContext, FileListDataSource aDataSource, FileIcons aFileIcons) {
		super(aContext, R.layout.fb_cell, aDataSource, aFileIcons);
		mResizableTextViews = new int[] {
				R.id.fb_cell_filename, 
				R.id.fb_cell_filefolder
		};
	}

	protected void applyFileImg(final FileListDataElement anItem, ImageView v) {
		//we do not want to scale the thumbnail any larger than a normal icon in grid view
		mFileIcons.setFileIcon(v,anItem,1);
	}
		
	/**
	 * Set all the various pieces of the item view.
	 * @param anItemView - View containing all views needed to display an item
	 * @return Returns the View passed in.
	 */
	@Override
	public View applyItemView(FileListDataElement anItem, View anItemView) {
		applyFileName(anItem,(TextView)getViewHandle(anItemView,R.id.fb_cell_filename));
		applyFileFolder(anItem,(TextView)getViewHandle(anItemView,R.id.fb_cell_filefolder));
		applyFileIcon(anItem,(ImageView)getViewHandle(anItemView,R.id.ImageFileIcon));
		applyFileEmblem(anItem,(ImageView)getViewHandle(anItemView,R.id.ImageReadOnlyEmblem));
		applyFileMark(anItem,(ImageView)getViewHandle(anItemView,R.id.ImageSelectedEmblem));
		return anItemView;
	}

	@Override
	public void onRecycleView(View anItemView) {
		ImageView theImageView = (ImageView)getViewHandle(anItemView,R.id.ImageFileIcon);
		mFileIcons.recycleView(theImageView);
	}

}
