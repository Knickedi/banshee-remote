package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils.TruncateAt;
import android.view.KeyEvent;
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
import de.viktorreiser.bansheeremote.data.BansheeDatabase.FullTrackInfo;
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
public class PlaylistActivity extends Activity implements OnBansheeCommandHandle,
		OnQuickActionListener {
	
	// PRIVATE ====================================================================================
	
	private static final int REQUEST_ACTIVITY = 1;
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private boolean mLoadingDismissed;
	private List<PlaylistEntry> mPlaylist;
	private TextView mPlaylistPositionText;
	private PositionPopup mPositionPopup;
	private ListView mList;
	private PlaylistAdapter mAdapter;
	private int mPlaylistCount = -1;
	private int mPlaylistStart = -1;
	private int mPlaylistEnd = -1;
	private boolean mPlaylistRequested;
	private boolean mDbOutOfDateHintShown = false;
	private HiddenQuickActionSetup mQuickActionSetup;
	private int mPlaylistId;
	private String mPlaylistName;
	
	// PUBLIC =====================================================================================
	
	public static final String EXTRA_PLAYLIST_ID = "plid";
	public static final String EXTRA_PLAYLIST_NAME = "plname";
	
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
			setupDataAfterConfigurationChange(data);
		} else {
			mPlaylistId = getIntent().getIntExtra(EXTRA_PLAYLIST_ID, 0);
			mPlaylistName = getIntent().getStringExtra(EXTRA_PLAYLIST_NAME);
			
			if (mPlaylistId < 1 || mPlaylistName == null) {
				finish();
				return;
			}
			
			mLoadingDismissed = false;
			mPlaylist = new ArrayList<PlaylistEntry>();
			mPlaylistRequested = true;
		}
		
		setContentView(R.layout.playlist);
		mList = (ListView) findViewById(R.id.list);
		mPlaylistPositionText = (TextView) findViewById(R.id.playlist_position);
		mPositionPopup = new PositionPopup();
		
		mQuickActionSetup = App.getDefaultHiddenViewSetup(this);
		mQuickActionSetup.setOnQuickActionListener(PlaylistActivity.this);
		
		mQuickActionSetup.addAction(
				App.QUICK_ACTION_ENQUEUE, R.string.quick_enqueue, R.drawable.enqueue);
		
		if (mPlaylistId == 1) {
			mQuickActionSetup.addAction(
					App.QUICK_ACTION_REMOVE, R.string.quick_remove, R.drawable.remove);
		}
		
		mQuickActionSetup.addAction(
				App.QUICK_ACTION_ARTIST, R.string.quick_artist, R.drawable.quick_artist);
		
		mAdapter = new PlaylistAdapter();
		mList.setAdapter(mAdapter);
		
		((TextView) findViewById(R.id.playlist_title)).setText(mPlaylistName);
		
		setupCommandHandler(data == null);
		setupListClickListener();
		setupListScrollListener();
		refreshLoading();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (isFinishing()) {
			finishActivity(REQUEST_ACTIVITY);
		}
		
		if (CurrentSongActivity.getConnection() != null) {
			CurrentSongActivity.getConnection().updateHandleCallback(mOldCommandHandler);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (CurrentSongActivity.getConnection() != null) {
			CurrentSongActivity.getPollHandler().start();
		} else {
			finish();
			return;
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (CurrentSongActivity.getConnection() != null) {
			CurrentSongActivity.getPollHandler().stop();
		}
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mOldCommandHandler, mPlaylist, mLoadingDismissed, mPlaylistCount,
				mPlaylistRequested, mDbOutOfDateHintShown, mPlaylistStart,
				mPlaylistEnd, mPlaylistId, mPlaylistName};
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		return CurrentSongActivity.handleKeyEvent(e) ? true : super.dispatchKeyEvent(e);
	}
	
	@Override
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		switch (command) {
		case PLAYER_STATUS:
			if (CurrentSongActivity.getData().changeFlag
						!= CurrentSongActivity.getPreviousData().changeFlag
					|| CurrentSongActivity.getData().playing
						!= CurrentSongActivity.getPreviousData().playing) {
				mAdapter.notifyDataSetChanged();
			}
			break;
		
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
		
		case PLAYLIST:
			if (Command.Playlist.isPlayTrack(params)) {
				if (result != null) {
					int status = Command.Playlist.decodePlayTrackStatus(result);
					if (status > 0) {
						if (status == 2) {
							PlaylistOverviewActivity.mActivePlaylistIdChange = mPlaylistId;
						}
						
						CurrentSongActivity.getConnection()
								.sendCommand(Command.PLAYER_STATUS, null);
					}
				}
				
				break;
			}
			
			if (result != null) {
				int count = Command.Playlist.decodeTrackCount(result);
				int startPosition = Command.Playlist.decodeStartPosition(result);
				long [] ids = Command.Playlist.decodeTrackIds(result);
				int previousListPosition = mList.getFirstVisiblePosition();
				int previousListOffset = 0;
				
				if (mPlaylistCount <= 0) {
					mPlaylist = new ArrayList<PlaylistEntry>(count);
				} else {
					previousListOffset = mList.getChildAt(0).getTop();
				}
				
				mPlaylistCount = count;
				
				if (mPlaylistStart < 0) {
					mPlaylistStart = startPosition;
					mPlaylistEnd = mPlaylistStart + ids.length;
					
					for (int i = 0; i < ids.length; i++) {
						mPlaylist.add(new PlaylistEntry(ids[i]));
					}
				} else if (startPosition <= mPlaylistStart) {
					mPlaylistStart = startPosition;
					List<PlaylistEntry> entries = new ArrayList<PlaylistEntry>();
					
					previousListPosition += ids.length;
					
					if (mPlaylistStart == 0) {
						previousListPosition--;
					}
					
					for (int i = 0; i < ids.length; i++) {
						entries.add(new PlaylistEntry(ids[i]));
					}
					
					mPlaylist.addAll(0, entries);
				} else {
					mPlaylistEnd = startPosition + ids.length;
					
					for (int i = 0; i < ids.length; i++) {
						mPlaylist.add(new PlaylistEntry(ids[i]));
					}
				}
				
				mPlaylistRequested = false;
				mLoadingDismissed = true;
				
				refreshLoading();
				
				mAdapter.notifyDataSetChanged();
				mList.setSelectionFromTop(previousListPosition, previousListOffset);
			} else {
				int start = Math.max(0, mPlaylistStart - App.getPlaylistPreloadCount());
				
				if (mPlaylistCount == 0) {
					CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
							Command.Playlist.encodePlaylistTracksOnStart(
									mPlaylistId, 0, App.getPlaylistPreloadCount()));
				} else if (Command.Playlist.getPlaylistTrackStartPosition(params) == mPlaylistEnd) {
					CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
							Command.Playlist.encodePlaylistTracks(
									mPlaylistId, mPlaylistEnd, App.getPlaylistPreloadCount()));
				} else if (Command.Playlist.getPlaylistTrackStartPosition(params) == start) {
					CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
							Command.Playlist.encodePlaylistTracks(
									mPlaylistId, start, mPlaylistStart - start));
				} else {
					mPlaylistRequested = false;
				}
			}
			break;
		}
	}
	
	@Override
	public void onQuickAction(AdapterView<?> parent, View view, int position, int quickActionId) {
		// TODO implement quick action on playlist
		switch (quickActionId) {
		case App.QUICK_ACTION_ARTIST: {
			Intent intent = new Intent(this, ArtistActivity.class);
			intent.putExtra(ArtistActivity.EXTRA_ARITST_ID,
					mPlaylist.get(position).trackInfo.aritstId);
			startActivityForResult(intent, REQUEST_ACTIVITY);
			break;
		}
		}
	}
	
	// PRIVATE ====================================================================================
	
	@SuppressWarnings("unchecked")
	private void setupDataAfterConfigurationChange(Object [] data) {
		mOldCommandHandler = (OnBansheeCommandHandle) data[0];
		mPlaylist = (List<PlaylistEntry>) data[1];
		mLoadingDismissed = (Boolean) data[2];
		mPlaylistCount = (Integer) data[3];
		mPlaylistRequested = (Boolean) data[4];
		mDbOutOfDateHintShown = (Boolean) data[5];
		mPlaylistStart = (Integer) data[6];
		mPlaylistEnd = (Integer) data[7];
		mPlaylistId = (Integer) data[8];
		mPlaylistName = (String) data[9];
	}
	
	private void setupCommandHandler(boolean intialRequest) {
		mOldCommandHandler = CurrentSongActivity.getConnection().getHandleCallback();
		CurrentSongActivity.getConnection().updateHandleCallback(new OnBansheeCommandHandle() {
			@Override
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				
				if (command != null) {
					PlaylistActivity.this.onBansheeCommandHandled(command, params, result);
				}
			}
		});
		
		if (intialRequest) {
			mPlaylistRequested = true;
			CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
					Command.Playlist.encodePlaylistTracksOnStart(
							mPlaylistId, 0, App.getPlaylistPreloadCount()));
		}
	}
	
	private void setupListClickListener() {
		mList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int p, long id) {
				if (p >= mPlaylist.size()) {
					return;
				}
				
				if (mPlaylistStart != 0) {
					p--;
				}
				
				PlaylistEntry entry = mPlaylist.get(p);
				
				CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
						Command.Playlist.encodePlayTrack(mPlaylistId, entry.id));
			}
		});
	}
	
	private void setupListScrollListener() {
		mList.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				
			}
			
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
					int totalItemCount) {
				mPositionPopup.showPosition(firstVisibleItem + mPlaylistStart + 1);
				
				if (!mPlaylistRequested && mPlaylistCount != mPlaylist.size()) {
					if (mPlaylistEnd < mPlaylistCount
							&& firstVisibleItem + visibleItemCount + 5 >= mPlaylist.size()) {
						mPlaylistRequested = true;
						CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
								Command.Playlist.encodePlaylistTracks(
										mPlaylistId, mPlaylistEnd, App.getPlaylistPreloadCount()));
					} else if (mPlaylistStart > 0 && firstVisibleItem <= 4) {
						mPlaylistRequested = true;
						int start = Math.max(0, mPlaylistStart - App.getPlaylistPreloadCount());
						CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
								Command.Playlist.encodePlaylistTracks(
										mPlaylistId, start, mPlaylistStart - start));
					}
				}
			}
		});
	}
	
	private void refreshLoading() {
		if (mLoadingDismissed) {
			findViewById(R.id.loading_progress).setVisibility(View.GONE);
			((TextView) findViewById(R.id.playlist_title)).setText(
					mPlaylistName + " (" + mPlaylistCount + ")");
		}
	}
	
	
	private static class PlaylistEntry {
		
		public PlaylistEntry(long id) {
			this.id = id;
		}
		
		public long id;
		public boolean requestedTrackInfo = false;
		public FullTrackInfo trackInfo;
	}
	
	private static class ViewHolder {
		public ImageView cover;
		public TextView track;
		public TextView artist;
		public TextView album;
		public ImageView playing;
		public TextView loading;
	}
	
	
	private class PositionPopup extends Handler {
		
		private static final long DISMISS_TIMEOUT = 500;
		private static final long FADE_DURATION = 500;
		
		private static final int MSG_ANIMATE = 1;
		private static final int MSG_DISMISS = 2;
		
		private int mmTextColor;
		private boolean mmAnimateForward;
		private long mmLastAnimationStep;
		private float mmAlpha = 0f;
		private boolean mmAnimating = false;
		private int mmLastPosition = 0;
		
		
		public PositionPopup() {
			mmTextColor = mPlaylistPositionText.getTextColors().getDefaultColor() & 0xffffff;
			mPlaylistPositionText.setBackgroundDrawable(
					Toast.makeText(PlaylistActivity.this, "", 0).getView().getBackground());
			mPlaylistPositionText.getBackground().setAlpha(0);
			mPlaylistPositionText.setTextColor(0);
		}
		
		public void showPosition(int position) {
			if (position == mmLastPosition) {
				return;
			}
			
			mmLastPosition = position;
			mPlaylistPositionText.setText(String.valueOf(position));
			mmAnimateForward = true;
			removeMessages(MSG_DISMISS);
			
			if (!mmAnimating) {
				mmLastAnimationStep = System.currentTimeMillis();
				mmAnimating = true;
				sendEmptyMessageDelayed(MSG_ANIMATE, 40);
			}
		}
		
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_ANIMATE) {
				float step = 1f * (System.currentTimeMillis() - mmLastAnimationStep)
						/ FADE_DURATION;
				mmLastAnimationStep = System.currentTimeMillis();
				
				if (mmAnimateForward) {
					mmAlpha += step;
					
					if (mmAlpha >= 1) {
						mmAlpha = 1;
						mmAnimating = false;
						sendEmptyMessageDelayed(MSG_DISMISS, DISMISS_TIMEOUT);
					}
				} else {
					mmAlpha -= step;
					
					if (mmAlpha <= 0) {
						mmAlpha = 0;
						mmAnimating = false;
					}
				}
				
				int alpha = Math.round(mmAlpha * 255);
				mPlaylistPositionText.getBackground().setAlpha(alpha);
				mPlaylistPositionText.setTextColor(mmTextColor | (alpha << 24));
			} else {
				mmAnimateForward = false;
				mmAnimating = true;
				mmLastAnimationStep = System.currentTimeMillis();
			}
			
			if (mmAnimating) {
				sendEmptyMessageDelayed(MSG_ANIMATE, 40);
			}
		}
	}
	
	private class PlaylistAdapter extends BaseAdapter {
		
		@Override
		public int getCount() {
			int size = mPlaylist.size();
			
			if (size == 0) {
				return 0;
			} else if (size == mPlaylistCount) {
				return size;
			} else {
				return size + (mPlaylistStart == 0 ? 0 : 1)
						+ (mPlaylistEnd == mPlaylistCount ? 0 : 1);
			}
		}
		
		@Override
		public int getItemViewType(int position) {
			// 0 normal - 1 compact track - 2 loading
			
			if (mPlaylistStart == 0 && position == mPlaylist.size()
					|| mPlaylistStart != 0 && position == mPlaylist.size() + 1
					|| position == 0 && mPlaylistStart != 0) {
				return 2;
			} else if (App.isPlaylistCompact()) {
				int offset = mPlaylistStart != 0 ? -1 : 0;
				
				if (position + offset == 0) {
					return 0;
				}
				
				PlaylistEntry entry = mPlaylist.get(position + offset);
				requestTrackInfo(entry);
				
				if (entry.trackInfo == null) {
					return 0;
				}
				
				PlaylistEntry previousEntry = mPlaylist.get(position + offset - 1);
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
			return 3;
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
					
					ViewHolder holder = new ViewHolder();
					holder.loading = (TextView) convertView.findViewById(R.id.loading_playlist);
					convertView.setTag(holder);
				}
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			int offset = mPlaylistStart != 0 ? -1 : 0;
			
			if (type == 0) {
				PlaylistEntry entry = mPlaylist.get(position + offset);
				requestTrackInfo(entry);
				
				if (entry.trackInfo != null) {
					if (CoverCache.coverExists(entry.trackInfo.artId)) {
						holder.cover.setImageBitmap(CoverCache.getThumbnailedCover(
								entry.trackInfo.artId));
						holder.cover.setTag(null);
					} else {
						holder.cover.setImageResource(R.drawable.no_cover);
						
						if (NetworkStateBroadcast.isWifiConnected()
								|| App.isMobileNetworkCoverFetch()) {
							CurrentSongActivity.getConnection().sendCommand(Command.COVER,
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
				PlaylistEntry entry = mPlaylist.get(position + offset);
				requestTrackInfo(entry);
				
				if (entry.trackInfo != null) {
					if (entry.trackInfo.title.equals("")) {
						holder.track.setText(R.string.unknown_track);
					} else {
						holder.track.setText(entry.trackInfo.title);
					}
					
					if (entry.trackInfo.id == CurrentSongActivity.getData().currentSongId
							&& PlaylistOverviewActivity.mActivePlaylistIdChange == mPlaylistId) {
						holder.playing.setVisibility(View.VISIBLE);
						holder.playing.setImageResource(CurrentSongActivity.getData().playing
								? R.drawable.ic_media_play : R.drawable.ic_media_pause);
					} else {
						holder.playing.setVisibility(View.GONE);
					}
				} else {
					holder.track.setText(R.string.unknown_track);
					holder.playing.setVisibility(View.GONE);
				}
			}
			
			if (type == 2 && position == 0) {
				// load indicator at list start
				int start = mPlaylistStart + 1 - App.getPlaylistPreloadCount();
				
				if (start < 1) {
					start = 1;
				}
				
				holder.loading.setText(getString(R.string.loading_playlist,
						start, mPlaylistStart, mPlaylistCount));
			} else if (type == 2) {
				// load indicator at list end
				int requested = mPlaylistEnd + App.getPlaylistPreloadCount();
				
				if (requested > mPlaylistCount) {
					requested = mPlaylistCount;
				}
				
				holder.loading.setText(getString(R.string.loading_playlist,
						mPlaylistEnd + 1, requested, mPlaylistCount));
			}
			
			return convertView;
		}
		
		private void requestTrackInfo(PlaylistEntry entry) {
			if (!entry.requestedTrackInfo) {
				entry.requestedTrackInfo = true;
				entry.trackInfo = BansheeDatabase.getTrackInfo(entry.id);
				
				if (entry.id > 0 && !mDbOutOfDateHintShown && entry.trackInfo == null
						&& BansheeDatabase.isOpen() && App.isShowDbOutOfDateHint()) {
					mDbOutOfDateHintShown = true;
					Toast.makeText(PlaylistActivity.this, R.string.out_of_data_hint_db,
							Toast.LENGTH_SHORT).show();
				}
			}
		}
	}
}
