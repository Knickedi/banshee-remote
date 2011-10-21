package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils.TruncateAt;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.App;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.TrackInfo;
import de.viktorreiser.bansheeremote.data.CoverCache;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;
import de.viktorreiser.toolbox.widget.HiddenQuickActionSetup;
import de.viktorreiser.toolbox.widget.HiddenQuickActionSetup.OnQuickActionListener;
import de.viktorreiser.toolbox.widget.SwipeableHiddenView;

/**
 * Here we will load the current player play list, show it and interact with it.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class PlaylistActivity extends Activity implements OnBansheeCommandHandle, OnQuickActionListener {
	
	// PRIVATE ====================================================================================
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private boolean mLoadingDismissed;
	private List<PlaylistEntry> mPlaylist;
	private ListView mList;
	private PlaylistAdapter mAdapter;
	private int mPlaylistCount = 0;
	private boolean mPlaylistRequested;
	private Set<String> mRequestedCovers = new HashSet<String>();
	private boolean mDbOutOfDateHintShown = false;
	private HiddenQuickActionSetup mQuickActionSetup;
	
	// OVERRIDDEN =================================================================================
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.mConnection == null) {
			finish();
			return;
		}
		
		mOldCommandHandler = CurrentSongActivity.mConnection.getHandleCallback();
		CurrentSongActivity.mConnection.updateHandleCallback(new OnBansheeCommandHandle() {
			@Override
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				PlaylistActivity.this.onBansheeCommandHandled(command, params, result);
			}
		});
		
		Object [] dataBefore = (Object []) getLastNonConfigurationInstance();
		
		if (dataBefore != null) {
			mOldCommandHandler = (OnBansheeCommandHandle) dataBefore[0];
			mPlaylist = (List<PlaylistEntry>) dataBefore[1];
			mLoadingDismissed = (Boolean) dataBefore[2];
			mPlaylistCount = (Integer) dataBefore[3];
			mPlaylistRequested = (Boolean) dataBefore[4];
			mRequestedCovers = (Set<String>) dataBefore[5];
			mDbOutOfDateHintShown = (Boolean) dataBefore[6];
		} else {
			mLoadingDismissed = false;
			mPlaylist = new ArrayList<PlaylistEntry>();
			mPlaylistRequested = true;
			CurrentSongActivity.mConnection.sendCommand(Command.PLAYLIST,
					Command.Playlist.encode(0, App.getPlaylistPreloadCount()));
		}
		
		setContentView(R.layout.playlist);
		
		mList = (ListView) findViewById(R.id.list);
		mList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int p, long id) {
				if (p >= mPlaylist.size()) {
					return;
				}
				
				PlaylistEntry entry = mPlaylist.get(p);
				
				CurrentSongActivity.mConnection.sendCommand(Command.PLAYLIST_CONTROL,
						Command.PlaylistControl.encodePlay(entry.id));
			}
		});
		
		mQuickActionSetup = App.getDefaultHiddenViewSetup(PlaylistActivity.this, false);
		mQuickActionSetup.setOnQuickActionListener(PlaylistActivity.this);
		
		mAdapter = new PlaylistAdapter();
		mList.setAdapter(mAdapter);
		mList.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				
			}
			
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
					int totalItemCount) {
				if (!mPlaylistRequested && mPlaylistCount != mPlaylist.size()
						&& firstVisibleItem + visibleItemCount + 5 >= mPlaylist.size()) {
					mPlaylistRequested = true;
					CurrentSongActivity.mConnection.sendCommand(Command.PLAYLIST,
							Command.Playlist.encode(
									mPlaylist.size(),
									App.getPlaylistPreloadCount()));
				}
			}
		});
		
		if (mLoadingDismissed) {
			findViewById(R.id.loading_progress).setVisibility(View.GONE);
			refreshTitle();
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mConnection.updateHandleCallback(mOldCommandHandler);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mStatusPollHandler.start();
		}
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mOldCommandHandler, mPlaylist, mLoadingDismissed, mPlaylistCount,
				mPlaylistRequested, mRequestedCovers, mDbOutOfDateHintShown};
	}
	
	@Override
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		if (command == null) {
			return;
		}
		
		switch (command) {
		case PLAYER_STATUS:
			if (CurrentSongActivity.mData.changeFlag != CurrentSongActivity.mPreviousData.changeFlag
					|| CurrentSongActivity.mData.playing != CurrentSongActivity.mPreviousData.playing) {
				mAdapter.notifyDataSetChanged();
			}
			break;
		
		case COVER:
			String artId = Command.Cover.getId(params);
			Bitmap cover = CoverCache.getCover(artId);
			
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
		
		case PLAYLIST:
			if (result != null) {
				int count = Command.Playlist.decodeCount(result);
				
				if (mPlaylistCount <= 0) {
					mPlaylist = new ArrayList<PlaylistEntry>(mPlaylistCount);
				}
				
				mPlaylistCount = count;
				
				long [] ids = Command.Playlist.decodeTrackIds(result);
				
				for (int i = 0; i < ids.length; i++) {
					mPlaylist.add(new PlaylistEntry(ids[i]));
				}
			}
			
			mPlaylistRequested = false;
			mLoadingDismissed = true;
			findViewById(R.id.loading_progress).setVisibility(View.GONE);
			refreshTitle();
			
			if (mPlaylist.size() != 0) {
				mAdapter.notifyDataSetChanged();
			}
			break;
			
		case PLAYLIST_CONTROL:
			CurrentSongActivity.mConnection.sendCommand(Command.PLAYER_STATUS, null);
			break;
		}
	}
	
	// PRIVATE ====================================================================================
	
	private void refreshTitle() {
		((TextView) findViewById(R.id.playlist_title)).setText(
				getString(R.string.playlist) + " (" + mPlaylistCount + ")");
	}
	
	private static class PlaylistEntry {
		
		public PlaylistEntry(long id) {
			this.id = id;
		}
		
		public long id;
		public boolean requestedTrackInfo = false;
		public TrackInfo trackInfo;
	}
	
	private static class ViewHolder {
		public ImageView cover;
		public TextView track;
		public TextView artist;
		public TextView album;
		public ImageView playing;
	}
	
	@Override
	public void onQuickAction(AdapterView<?> parent, View view, int position, int quickActionId) {
		
	}
	
	private class PlaylistAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			int size = mPlaylist.size();
			return size == mPlaylistCount ? size : size + 1;
		}
		
		@Override
		public int getItemViewType(int position) {
			if (position == mPlaylist.size()) {
				return 2;
			} else if (App.isPlaylistCompact()) {
				if (position == 0) {
					return 0;
				}
				
				PlaylistEntry entry = mPlaylist.get(position);
				requestTrackInfo(entry);
				
				if (entry.trackInfo == null) {
					return 0;
				}
				
				PlaylistEntry previousEntry = mPlaylist.get(position - 1);
				requestTrackInfo(previousEntry);
				
				if (previousEntry.trackInfo == null) {
					return 0;
				}
				
				return entry.trackInfo.albumId == previousEntry.trackInfo.albumId ? 1 : 0;
			} else {
				return 0;
			}
		}
		
		@Override
		public int getViewTypeCount() {
			return 4;
		}
		
		@Override
		public Object getItem(int position) {
			return null;
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			int type = getItemViewType(position);
			
			if (convertView == null) {
				if (type == 0) {
					convertView = getLayoutInflater().inflate(R.layout.track_list_item, null);
					((SwipeableHiddenView) convertView).setHiddenViewSetup(mQuickActionSetup);
					
					ViewHolder holder = new ViewHolder();
					holder.cover = (ImageView) convertView.findViewById(R.id.cover1);
					holder.track = (TextView) convertView.findViewById(R.id.song_title);
					holder.artist = (TextView) convertView.findViewById(R.id.song_artist);
					holder.album = (TextView) convertView.findViewById(R.id.song_album);
					holder.playing = (ImageView) convertView.findViewById(R.id.playing);
					convertView.setTag(holder);
				} else if (type == 1) {
					convertView = getLayoutInflater().inflate(R.layout.track_list_item_compact,
							null);
					((SwipeableHiddenView) convertView).setHiddenViewSetup(mQuickActionSetup);
					
					ViewHolder holder = new ViewHolder();
					holder.track = (TextView) convertView.findViewById(R.id.song_title);
					holder.playing = (ImageView) convertView.findViewById(R.id.playing);
					convertView.setTag(holder);
				} else {
					convertView = getLayoutInflater()
							.inflate(R.layout.playlist_loading_item, null);
				}
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			
			if (type == 0) {
				PlaylistEntry entry = mPlaylist.get(position);
				requestTrackInfo(entry);
				
				if (entry.trackInfo != null) {
					if (CoverCache.coverExists(entry.trackInfo.artId)) {
						holder.cover.setImageBitmap(CoverCache.getCover(entry.trackInfo.artId));
						holder.cover.setTag(null);
					} else {
						holder.cover.setImageResource(R.drawable.no_cover);
						
						if ((NetworkStateBroadcast.isWifiConnected()
								|| App.isMobileNetworkCoverFetch())
								&& !mRequestedCovers.contains(entry.trackInfo.artId)) {
							mRequestedCovers.add(entry.trackInfo.artId);
							CurrentSongActivity.mConnection.sendCommand(Command.COVER,
									Command.Cover.encode(entry.trackInfo.artId));
						}
						
						holder.cover.setTag(entry.trackInfo.artId);
					}
					
					if (entry.trackInfo.artist.equals("")) {
						holder.artist.setText(R.string.unknown_artist);
					} else {
						holder.artist.setText(entry.trackInfo.artist);
					}
					
					String year = "";
					
					if (App.isDisplayAlbumYear() && entry.trackInfo.year >= 1000) {
						year = " [" + entry.trackInfo.year + "]";
						holder.album.setEllipsize(TruncateAt.MIDDLE);
					} else {
						holder.album.setEllipsize(TruncateAt.END);
					}
					
					if (entry.trackInfo.album.equals("")) {
						holder.album.setText(getString(R.string.unknown_album) + year);
					} else {
						holder.album.setText(entry.trackInfo.album + year);
					}
				} else {
					holder.cover.setImageResource(R.drawable.no_cover);
					holder.artist.setText(R.string.unknown_artist);
					holder.album.setText(R.string.unknown_album);
				}
			}
			
			if (type == 0 || type == 1) {
				PlaylistEntry entry = mPlaylist.get(position);
				requestTrackInfo(entry);
				
				if (entry.trackInfo != null) {
					if (entry.trackInfo.title.equals("")) {
						holder.track.setText(R.string.unknown_track);
					} else {
						holder.track.setText(entry.trackInfo.title);
					}
					
					if (entry.trackInfo.id == CurrentSongActivity.mData.currentSongId) {
						holder.playing.setVisibility(View.VISIBLE);
						holder.playing.setImageResource(CurrentSongActivity.mData.playing
								? R.drawable.ic_media_play : R.drawable.ic_media_pause);
					} else {
						holder.playing.setVisibility(View.GONE);
					}
				} else {
					holder.track.setText(R.string.unknown_track);
					holder.playing.setVisibility(View.GONE);
				}
			}
			
			if (type == 2) {
				int size = mPlaylist.size();
				int requested = size + App.getPlaylistPreloadCount();
				
				if (requested >= mPlaylistCount) {
					requested = mPlaylistCount;
				}
				
				((TextView) convertView.findViewById(R.id.loading_playlist))
						.setText(getString(R.string.loading_playlist,
								size + 1, requested, mPlaylistCount));
			}
			
			return convertView;
		}
		
		private void requestTrackInfo(PlaylistEntry entry) {
			if (!entry.requestedTrackInfo) {
				entry.requestedTrackInfo = true;
				entry.trackInfo = BansheeDatabase.getTrackInfo(entry.id);
				
				if (!mDbOutOfDateHintShown && entry.trackInfo == null && BansheeDatabase.isOpen()
						&& App.isShowDbOutOfDateHint()) {
					mDbOutOfDateHintShown = true;
					Toast.makeText(PlaylistActivity.this, R.string.out_of_data_hint_db,
							Toast.LENGTH_SHORT).show();
				}
			}
		}
	}
}
