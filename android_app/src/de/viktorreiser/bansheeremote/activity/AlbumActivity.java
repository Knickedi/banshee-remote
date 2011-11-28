package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
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
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command.Playlist.Modification;
import de.viktorreiser.bansheeremote.data.App;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.Album;
import de.viktorreiser.bansheeremote.data.CoverCache;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;
import de.viktorreiser.toolbox.widget.HiddenQuickActionSetup;
import de.viktorreiser.toolbox.widget.SwipeableHiddenView;
import de.viktorreiser.toolbox.widget.HiddenQuickActionSetup.OnQuickActionListener;

/**
 * Browse albums from synchronized database.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class AlbumActivity extends Activity implements OnBansheeCommandHandle, OnItemClickListener,
		OnQuickActionListener {
	
	// PRIVATE ====================================================================================
	
	private static final int REQUEST_ACTIVITY = 1;
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private Album [] mAlbumEntries;
	private Object [] mAdapterSections;
	private ListView mList;
	private HiddenQuickActionSetup mQuickActionSetup;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.getConnection() == null) {
			finish();
			return;
		}
		
		Object [] data = (Object []) getLastNonConfigurationInstance();
		
		if (data != null) {
			mAlbumEntries = (Album []) data[0];
			mAdapterSections = (Object []) data[1];
		} else {
			mAlbumEntries = BansheeDatabase.getOrderedAlbums();
			List<SectionEntry> sections = new ArrayList<SectionEntry>();
			Set<String> characters = new TreeSet<String>();
			
			for (int i = 0; i < mAlbumEntries.length; i++) {
				String c = mAlbumEntries[i].getTitle().substring(0, 1).toUpperCase();
				
				if (!characters.contains(c)) {
					SectionEntry s = new SectionEntry();
					s.character = c;
					s.position = i;
					sections.add(s);
					characters.add(c);
				}
			}
			
			mAdapterSections = sections.toArray();
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
		
		mQuickActionSetup = App.getDefaultHiddenViewSetup(this);
		mQuickActionSetup.setOnQuickActionListener(this);
		
		mQuickActionSetup.addAction(App.QUICK_ACTION_ENQUEUE,
				R.string.quick_enqueue_album, R.drawable.enqueue);
		mQuickActionSetup.addAction(App.QUICK_ACTION_REMOVE_QUEUE,
				R.string.quick_remove_queue_album, R.drawable.queue_remove);
		mQuickActionSetup.addAction(App.QUICK_ACTION_ADD,
				R.string.quick_add_album, R.drawable.add);
		mQuickActionSetup.addAction(App.QUICK_ACTION_REMOVE,
				R.string.quick_remove_album, R.drawable.remove);
		
		setContentView(R.layout.album);
		
		mList = (ListView) findViewById(R.id.list);
		mList.setAdapter(new AlbumAdapter());
		mList.setOnItemClickListener(this);
		
		((TextView) findViewById(R.id.album_title)).setText(
				getString(R.string.all_albums) + " (" + mAlbumEntries.length + ")");
		mList.setFastScrollEnabled(true);
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
		return new Object [] {mAlbumEntries, mAdapterSections};
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		return CurrentSongActivity.handleKeyEvent(e) ? true : super.dispatchKeyEvent(e);
	}
	
	@Override
	public void onItemClick(AdapterView<?> a, View v, int p, long id) {
		Intent intent = new Intent(this, TrackActivity.class);
		intent.putExtra(TrackActivity.EXTRA_ALBUM_ID, mAlbumEntries[p].getId());
		intent.putExtra(TrackActivity.EXTRA_ARTIST_ID, mAlbumEntries[p].getArtistId());
		startActivityForResult(intent, REQUEST_ACTIVITY);
	}
	
	@Override
	public void onQuickAction(AdapterView<?> parent, View view, int position, int quickActionId) {
		int playlistId = quickActionId == App.QUICK_ACTION_REMOVE
				|| quickActionId == App.QUICK_ACTION_ADD
				? App.PLAYLIST_REMOTE : App.PLAYLIST_QUEUE;
		
		switch (quickActionId) {
		case App.QUICK_ACTION_ADD:
		case App.QUICK_ACTION_ENQUEUE:
			CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
					Command.Playlist.encodeAdd(playlistId, Modification.ADD_ALBUM,
							mAlbumEntries[position].getId(), App.isPlaylistAddTwice()));
			break;
		
		case App.QUICK_ACTION_REMOVE:
		case App.QUICK_ACTION_REMOVE_QUEUE:
			CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
					Command.Playlist.encodeRemove(playlistId, Modification.REMOVE_ALBUM,
							mAlbumEntries[position].getId()));
			break;
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
				
				if (artId.equals(holder.cover.getTag())) {
					holder.cover.setImageBitmap(cover);
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
	
	private static class SectionEntry {
		public String character;
		public int position;
		
		@Override
		public String toString() {
			return character;
		}
	}
	
	private class AlbumAdapter extends BaseAdapter implements SectionIndexer {
		
		@Override
		public int getCount() {
			return mAlbumEntries.length;
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
				holder.artist = (TextView) convertView.findViewById(R.id.artist_name);
				holder.cover = (ImageView) convertView.findViewById(R.id.cover1);
				holder.count = (TextView) convertView.findViewById(R.id.count);
				
				convertView.setTag(holder);
				
				((SwipeableHiddenView) convertView).setHiddenViewSetup(mQuickActionSetup);
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			Album info = mAlbumEntries[position];
			
			holder.album.setText(info.getTitle());
			holder.artist.setText(info.getArtist().getName());
			holder.count.setText("(" + info.getTrackCount() + ")");
			
			if (CoverCache.coverExists(info.getArtId())) {
				holder.cover.setImageBitmap(CoverCache.getThumbCover(info.getArtId()));
				holder.cover.setTag(null);
			} else {
				if (NetworkStateBroadcast.isWifiConnected()
						|| App.isMobileNetworkCoverFetch()) {
					CurrentSongActivity.getConnection().sendCommand(Command.COVER,
							Command.Cover.encode(info.getArtId()), false);
				}
				
				holder.cover.setImageBitmap(CoverCache.getThumbCover(""));
				holder.cover.setTag(info.getArtId());
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
