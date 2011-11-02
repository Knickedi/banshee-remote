package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
import de.viktorreiser.bansheeremote.data.App;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.AlbumInfo;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.ArtistInfo;
import de.viktorreiser.bansheeremote.data.CoverCache;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;

/**
 * Browse artists from synchronized database.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class ArtistActivity extends Activity implements OnBansheeCommandHandle {
	
	// PRIVATE ====================================================================================
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private int mArtistCount;
	private List<ArtistEntry> mArtistEntries;
	private Object [] mAdapterSections;
	private ListView mList;
	
	// PUBLIC =====================================================================================
	
	public static final String EXTRA_ARITST_ID = "aid";
	
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
			mArtistCount = (Integer) data[0];
			mArtistEntries = (List<ArtistEntry>) data[1];
			mAdapterSections = (Object []) data[2];
		} else {
			if (getIntent().hasExtra(EXTRA_ARITST_ID)) {
				ArtistInfo info = BansheeDatabase.getArtistInfo(
						getIntent().getLongExtra(EXTRA_ARITST_ID, -1));
				mArtistCount = 1;
				mArtistEntries = new ArrayList<ArtistEntry>(info.albumCount + 1);
				
				ArtistEntry e = new ArtistEntry();
				e.artist = info;
				mArtistEntries.add(e);
				
				for (int i = 0; i < info.albumCount; i++) {
					e = new ArtistEntry();
					e.artist = info;
					e.isAlbum = true;
					mArtistEntries.add(e);
				}
			} else {
				setupAllArtistsInfo();
			}
		}
		
		mOldCommandHandler = CurrentSongActivity.getConnection().getHandleCallback();
		CurrentSongActivity.getConnection().updateHandleCallback(new OnBansheeCommandHandle() {
			@Override
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				
				if (command != null) {
					ArtistActivity.this.onBansheeCommandHandled(command, params, result);
				}
			}
		});
		
		setContentView(R.layout.artist);
		
		mList = (ListView) findViewById(R.id.list);
		mList.setAdapter(new ArtistAdapter());
		
		if (mArtistCount == 1) {
			((TextView) findViewById(R.id.artist_title)).setText(R.string.artist);
		} else {
			((TextView) findViewById(R.id.artist_title)).setText(
					getString(R.string.all_artists) + " (" + mArtistCount + ")");
			mList.setFastScrollEnabled(true);
		}
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
		return new Object [] {mArtistCount, mArtistEntries, mAdapterSections};
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
					
					if (holder != null && holder.cover != null
							&& artId.equals(holder.cover.getTag())) {
						holder.cover.setImageBitmap(cover);
					}
				}
			}
			break;
		}
	}
	
	// PRIVATE ====================================================================================
	
	private void setupAllArtistsInfo() {
		List<ArtistInfo> artistInfo = BansheeDatabase.getArtistInfo();
		List<SectionEntry> sections = new LinkedList<SectionEntry>();
		Set<String> characters = new TreeSet<String>();
		mArtistEntries = new ArrayList<ArtistEntry>();
		mArtistCount = artistInfo.size();
		
		for (int i = 0; i < mArtistCount; i++) {
			ArtistInfo info = artistInfo.get(i);
			String c = info.name.substring(0, 1).toUpperCase();
			
			if (!characters.contains(c)) {
				SectionEntry e = new SectionEntry();
				e.character = c;
				e.position = mArtistEntries.size();
				sections.add(e);
				characters.add(c);
			}
			
			ArtistEntry artist = new ArtistEntry();
			artist.artist = info;
			artist.isAlbum = false;
			mArtistEntries.add(artist);
			
			for (int j = 0; j < info.albumCount; j++) {
				ArtistEntry album = new ArtistEntry();
				album.artist = info;
				album.isAlbum = true;
				mArtistEntries.add(album);
			}
			
			mAdapterSections = sections.toArray();
		}
	}
	
	
	private static class ArtistEntry {
		public ArtistInfo artist;
		public boolean isAlbum = false;
		public AlbumInfo album;
	}
	
	private static class SectionEntry {
		public String character;
		public int position;
		
		@Override
		public String toString() {
			return character;
		}
	}
	
	private static class ViewHolder {
		public TextView title;
		public TextView count;
		public ImageView cover;
	}
	
	private class ArtistAdapter extends BaseAdapter implements SectionIndexer {
		
		@Override
		public int getItemViewType(int position) {
			return mArtistEntries.get(position).isAlbum ? 1 : 0;
		}
		
		@Override
		public int getViewTypeCount() {
			return 2;
		}
		
		@Override
		public int getCount() {
			return mArtistEntries.size();
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
			ArtistEntry entry = mArtistEntries.get(position);
			
			if (convertView == null) {
				ViewHolder holder = new ViewHolder();
				
				if (entry.isAlbum) {
					convertView = getLayoutInflater().inflate(R.layout.artist_item_2, null);
					holder.cover = (ImageView) convertView.findViewById(R.id.cover1);
				} else {
					convertView = getLayoutInflater().inflate(R.layout.artist_item, null);
				}
				
				holder.title = (TextView) convertView.findViewById(R.id.title);
				holder.count = (TextView) convertView.findViewById(R.id.count);
				
				convertView.setTag(holder);
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			
			if (entry.isAlbum) {
				if (entry.album == null) {
					List<AlbumInfo> info = BansheeDatabase.getAlbumInfoOfArtist(entry.artist.id);
					ArtistEntry tmpEntry = entry;
					int i = position;
					
					while (tmpEntry.isAlbum) {
						i--;
						
						if (i < 0) {
							break;
						}
						
						tmpEntry = mArtistEntries.get(i);
					}
					
					i++;
					
					for (int j = 0; j < info.size(); j++) {
						mArtistEntries.get(i + j).album = info.get(j);
					}
				}
				
				if (entry.album.title.equals("")) {
					holder.title.setText(R.string.no_album);
				} else {
					holder.title.setText(entry.album.title);
				}
				
				holder.count.setText("(" + entry.album.trackCount + ")");
				
				if (CoverCache.coverExists(entry.album.artId)) {
					holder.cover.setImageBitmap(CoverCache.getThumbnailedCover(entry.album.artId));
					holder.cover.setTag(null);
				} else {
					holder.cover.setImageResource(R.drawable.no_cover);
					
					if (NetworkStateBroadcast.isWifiConnected()
							|| App.isMobileNetworkCoverFetch()) {
						CurrentSongActivity.getConnection().sendCommand(Command.COVER,
								Command.Cover.encode(entry.album.artId), false);
					}
					
					holder.cover.setTag(entry.album.artId);
				}
			} else {
				if (entry.artist.name.equals("")) {
					holder.title.setText(R.string.unknown_artist);
				} else {
					holder.title.setText(entry.artist.name);
				}
				
				holder.count.setText("(" + entry.artist.trackCount + ")");
			}
			
			return convertView;
		}
		
		@Override
		public int getPositionForSection(int section) {
			return ((SectionEntry) mAdapterSections[section]).position;
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
