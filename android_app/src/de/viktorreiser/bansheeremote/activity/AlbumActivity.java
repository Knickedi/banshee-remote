package de.viktorreiser.bansheeremote.activity;

import java.util.List;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.FullAlbumInfo;
import de.viktorreiser.bansheeremote.data.CoverCache;

public class AlbumActivity extends Activity implements OnBansheeCommandHandle {
	
	// PRIVATE ====================================================================================
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private List<FullAlbumInfo> mAlbumEntries;
	private Object [] mAdapterSections;
	private ListView mList;
	
	// OVERRIDDEN =================================================================================
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.getConnection() == null) {
			finish();
			return;
		}
		
		Object [] data = (Object []) getLastNonConfigurationInstance();
		
		if (data != null) {
			mAlbumEntries = (List<FullAlbumInfo>) data[0];
			mAdapterSections = (Object []) data[1];
		} else {
			mAlbumEntries = BansheeDatabase.getAllAlbums();
			mAdapterSections = new Object [mAlbumEntries.size()];
			
			for (int i = 0; i < mAlbumEntries.size(); i++) {
				mAdapterSections[i] = mAlbumEntries.get(i).title.substring(0, 1).toUpperCase();
			}
		}
		
		mOldCommandHandler = CurrentSongActivity.getConnection().getHandleCallback();
		CurrentSongActivity.getConnection().updateHandleCallback(new OnBansheeCommandHandle() {
			@Override
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				
				if (command != null) {
					AlbumActivity.this.onBansheeCommandHandled(command, params, result);
				}
			}
		});
		
		setContentView(R.layout.album);
		
		mList = (ListView) findViewById(R.id.list);
		mList.setAdapter(new AlbumAdapter());
		
		((TextView) findViewById(R.id.album_title)).setText(
				getString(R.string.all_albums) + " (" + mAlbumEntries.size() + ")");
		mList.setFastScrollEnabled(true);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (CurrentSongActivity.getConnection() != null) {
			CurrentSongActivity.getConnection().updateHandleCallback(mOldCommandHandler);
		}
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mAlbumEntries, mAdapterSections};
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		return CurrentSongActivity.handleKeyEvent(e) ? true : super.dispatchKeyEvent(e);
	}

	@Override
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		switch (command) {
		case COVER:
			String artId = Command.Cover.getId(params);
			Bitmap cover = CoverCache.getThumbnailedCover(artId);
			
			if (cover != null) {
				int childCount = mList.getChildCount();
				
				for (int i = 0; i < childCount; i++) {
					ViewHolder holder = (ViewHolder) mList.getChildAt(i).getTag();
					
					if (artId.equals(holder.cover.getTag())) {
						holder.cover.setImageBitmap(cover);
					}
				}
			}
			break;
		}
	}
	
	// PRIVATE ====================================================================================
	
	private static class ViewHolder {
		public TextView album;
		public TextView artist;
		public TextView count;
		public ImageView cover;
	}
	
	private class AlbumAdapter extends BaseAdapter implements SectionIndexer {

		@Override
		public int getCount() {
			return mAlbumEntries.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(R.layout.album_item, null);
				
				ViewHolder holder = new ViewHolder();
				holder.album = (TextView) convertView.findViewById(R.id.album_title);
				holder.artist = (TextView) convertView.findViewById(R.id.artist_title);
				holder.cover = (ImageView) convertView.findViewById(R.id.cover1);
				holder.count = (TextView) convertView.findViewById(R.id.count);
				
				convertView.setTag(holder);
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			FullAlbumInfo info = mAlbumEntries.get(position);
			
			holder.album.setText(info.title);
			holder.artist.setText(info.artistName);
			holder.count.setText("(" + info.trackCount + ")");
			
			if (CoverCache.coverExists(info.artId)) {
				holder.cover.setImageBitmap(CoverCache.getThumbnailedCover(info.artId));
				holder.cover.setTag(null);
			} else {
				CurrentSongActivity.getConnection().sendCommand(
						Command.COVER, Command.Cover.encode(info.artId), false);
				holder.cover.setImageResource(R.drawable.no_cover);
				holder.cover.setTag(info.artId);
			}
			
			return convertView;
		}
		
		@Override
		public int getPositionForSection(int section) {
			return section;
		}
		
		@Override
		public int getSectionForPosition(int position) {
			return position;
		}
		
		@Override
		public Object [] getSections() {
			return mAdapterSections;
		}
	}
}
