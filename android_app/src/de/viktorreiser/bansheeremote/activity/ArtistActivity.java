package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.App;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command.Playlist.Modification;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.Album;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.Artist;
import de.viktorreiser.bansheeremote.data.CoverCache;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;
import de.viktorreiser.toolbox.widget.HiddenQuickActionSetup;
import de.viktorreiser.toolbox.widget.SwipeableHiddenView;
import de.viktorreiser.toolbox.widget.HiddenQuickActionSetup.OnQuickActionListener;

/**
 * Browse artists from synchronized database.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class ArtistActivity extends Activity implements OnBansheeCommandHandle,
		OnItemClickListener,
		OnQuickActionListener {
	
	// PRIVATE ====================================================================================
	
	private static final int REQUEST_ACTIVITY = 1;
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private int mArtistCount;
	private List<ArtistEntry> mArtistEntries;
	private Object [] mAdapterSections;
	private ListView mList;
	private HiddenQuickActionSetup mQuickActionSetupArtist;
	private HiddenQuickActionSetup mQuickActionSetupAlbum;
	
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
				Artist info = BansheeDatabase.getArtist(
						getIntent().getLongExtra(EXTRA_ARITST_ID, -1));
				mArtistCount = 1;
				mArtistEntries = new ArrayList<ArtistEntry>(info.getAlbumCount() + 1);
				
				ArtistEntry e = new ArtistEntry();
				e.artist = info;
				mArtistEntries.add(e);
				
				for (int i = 0; i < info.getAlbumCount(); i++) {
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
		
		setupQuickActionSetup();
		
		setContentView(R.layout.artist);
		
		mList = (ListView) findViewById(R.id.list);
		mList.setAdapter(new ArtistAdapter());
		mList.setOnItemClickListener(this);
		
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
		
		if (isFinishing()) {
			finishActivity(REQUEST_ACTIVITY);
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
	public void onItemClick(AdapterView<?> a, View v, int p, long id) {
		ArtistEntry e = mArtistEntries.get(p);
		Intent intent = new Intent(this, TrackActivity.class);
		
		if (e.isAlbum) {
			intent.putExtra(TrackActivity.EXTRA_ALBUM_ID, e.album.getId());
		}
		
		intent.putExtra(TrackActivity.EXTRA_ARTIST_ID, e.artist.getId());
		startActivityForResult(intent, REQUEST_ACTIVITY);
	}
	
	@Override
	public void onQuickAction(AdapterView<?> parent, View view, int position, int quickActionId) {
		ArtistEntry entry = mArtistEntries.get(position);
		boolean isAlbum = entry.isAlbum;
		long id = isAlbum ? entry.album.getId() : entry.artist.getId();
		int playlistId = quickActionId == App.QUICK_ACTION_REMOVE
				|| quickActionId == App.QUICK_ACTION_ADD
				? App.PLAYLIST_REMOTE : App.PLAYLIST_QUEUE;
		
		
		switch (quickActionId) {
		case App.QUICK_ACTION_ADD:
		case App.QUICK_ACTION_ENQUEUE: {
			Modification mod = isAlbum ? Modification.ADD_ALBUM : Modification.ADD_ARTIST;
			CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
					Command.Playlist.encodeAdd(playlistId, mod, id, App.isPlaylistAddTwice()));
			break;
		}
		case App.QUICK_ACTION_REMOVE:
		case App.QUICK_ACTION_REMOVE_QUEUE: {
			Modification mod = isAlbum ? Modification.REMOVE_ALBUM : Modification.REMOVE_ARTIST;
			CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
					Command.Playlist.encodeRemove(playlistId, mod, id));
			break;
		}
		}
		
		App.shortToast(R.string.request_sent);
	}
	
	@Override
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		switch (command) {
		case COVER:
			String artId = Command.Cover.getId(params);
			Bitmap cover = CoverCache.getThumbCover(artId);
			
			int childCount = mList.getChildCount();
			
			for (int i = 0; i < childCount; i++) {
				ViewHolder holder = (ViewHolder) mList.getChildAt(i).getTag();
				
				if (holder != null && holder.cover != null
						&& artId.equals(holder.cover.getTag())) {
					holder.cover.setImageBitmap(cover);
				}
			}
			break;
		}
	}
	
	// PRIVATE ====================================================================================
	
	private void setupQuickActionSetup() {
		mQuickActionSetupAlbum = App.getDefaultHiddenViewSetup(this);
		mQuickActionSetupAlbum.setOnQuickActionListener(this);
		mQuickActionSetupArtist = App.getDefaultHiddenViewSetup(this);
		mQuickActionSetupArtist.setOnQuickActionListener(this);
		
		mQuickActionSetupAlbum.addAction(App.QUICK_ACTION_ENQUEUE,
				R.string.quick_enqueue_album, R.drawable.enqueue);
		mQuickActionSetupAlbum.addAction(App.QUICK_ACTION_REMOVE_QUEUE,
				R.string.quick_remove_queue_album, R.drawable.queue_remove);
		mQuickActionSetupAlbum.addAction(App.QUICK_ACTION_ADD,
				R.string.quick_add_album, R.drawable.add);
		mQuickActionSetupAlbum.addAction(App.QUICK_ACTION_REMOVE,
				R.string.quick_remove_album, R.drawable.remove);
		
		mQuickActionSetupArtist.addAction(App.QUICK_ACTION_ENQUEUE,
				R.string.quick_enqueue_artist, R.drawable.enqueue);
		mQuickActionSetupArtist.addAction(App.QUICK_ACTION_REMOVE_QUEUE,
				R.string.quick_remove_queue_artist, R.drawable.queue_remove);
		mQuickActionSetupArtist.addAction(App.QUICK_ACTION_ADD,
				R.string.quick_add_artist, R.drawable.add);
		mQuickActionSetupArtist.addAction(App.QUICK_ACTION_REMOVE,
				R.string.quick_remove_artist, R.drawable.remove);
	}
	
	private void setupAllArtistsInfo() {
		Artist [] artistInfo = BansheeDatabase.getOrderedArtists();
		List<SectionEntry> sections = new LinkedList<SectionEntry>();
		Set<String> characters = new TreeSet<String>();
		mArtistEntries = new ArrayList<ArtistEntry>();
		mArtistCount = artistInfo.length;
		
		for (int i = 0; i < mArtistCount; i++) {
			String c = artistInfo[i].getName().substring(0, 1).toUpperCase();
			
			if (!characters.contains(c)) {
				SectionEntry e = new SectionEntry();
				e.character = c;
				e.position = mArtistEntries.size();
				sections.add(e);
				characters.add(c);
			}
			
			ArtistEntry artist = new ArtistEntry();
			artist.artist = artistInfo[i];
			artist.isAlbum = false;
			mArtistEntries.add(artist);
			
			for (int j = 0; j < artistInfo[i].getAlbumCount(); j++) {
				ArtistEntry album = new ArtistEntry();
				album.artist = artistInfo[i];
				album.isAlbum = true;
				mArtistEntries.add(album);
			}
			
			mAdapterSections = sections.toArray();
		}
	}
	
	
	private static class ArtistEntry {
		public Artist artist;
		public boolean isAlbum = false;
		public Album album;
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
					((SwipeableHiddenView) convertView).setHiddenViewSetup(mQuickActionSetupAlbum);
				} else {
					convertView = getLayoutInflater().inflate(R.layout.artist_item, null);
					((SwipeableHiddenView) convertView).setHiddenViewSetup(mQuickActionSetupArtist);
				}
				
				holder.title = (TextView) convertView.findViewById(R.id.title);
				holder.count = (TextView) convertView.findViewById(R.id.count);
				
				convertView.setTag(holder);
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			
			if (entry.isAlbum) {
				if (entry.album == null) {
					Album [] info = BansheeDatabase.getOrderedAlbumsOfArtist(entry.artist.getId());
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
					
					for (int j = 0; j < info.length; j++) {
						mArtistEntries.get(i + j).album = info[j];
					}
				}
				
				holder.title.setText(entry.album.getTitle());
				
				holder.count.setText("(" + entry.album.getTrackCount() + ")");
				
				if (CoverCache.coverExists(entry.album.getArtId())) {
					holder.cover.setImageBitmap(CoverCache.getThumbCover(entry.album.getArtId()));
					holder.cover.setTag(null);
				} else {
					if (NetworkStateBroadcast.isWifiConnected()
							|| App.isMobileNetworkCoverFetch()) {
						CurrentSongActivity.getConnection().sendCommand(Command.COVER,
								Command.Cover.encode(entry.album.getArtId()), false);
					}
					
					holder.cover.setImageBitmap(CoverCache.getThumbCover(""));
					holder.cover.setTag(entry.album.getArtId());
				}
			} else {
				holder.title.setText(entry.artist.getName());
				holder.count.setText("(" + entry.artist.getTrackCount() + ")");
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
