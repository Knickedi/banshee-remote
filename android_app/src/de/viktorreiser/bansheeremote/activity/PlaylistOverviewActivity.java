package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.toolbox.util.L;

/**
 * This will load all available playlists on the server.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class PlaylistOverviewActivity extends Activity implements OnBansheeCommandHandle {
	
	// PACKAGE ====================================================================================
	
	static int mActivePlaylistIdChange;
	
	// PRIVATE ====================================================================================
	
	private static final int REQUEST_PLAYLIST = 1;
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private List<PlaylistEntry> mPlaylists = new ArrayList<PlaylistEntry>();
	private int mActivePlaylistId;
	private boolean mLoadingDismissed;
	private PlaylistsAdapter mAdapter;
	
	// OVERRIDDEN =================================================================================
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.mConnection == null) {
			finish();
			return;
		}
		
		Object [] dataBefore = (Object []) getLastNonConfigurationInstance();
		
		if (dataBefore == null) {
			CurrentSongActivity.mConnection.sendCommand(Command.PLAYLIST,
					Command.Playlist.encodePlaylistNames());
		} else {
			mPlaylists = (List<PlaylistEntry>) dataBefore[0];
			mActivePlaylistId = (Integer) dataBefore[1];
			mLoadingDismissed = (Boolean) dataBefore[2];
		}
		
		mOldCommandHandler = CurrentSongActivity.mConnection.getHandleCallback();
		CurrentSongActivity.mConnection.updateHandleCallback(new OnBansheeCommandHandle() {
			@Override
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				
				if (command != null) {
					PlaylistOverviewActivity.this.onBansheeCommandHandled(command, params, result);
				}
			}
		});
		
		setContentView(R.layout.playlist_overview);
		
		((ListView) findViewById(R.id.list)).setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int p, long id) {
				Intent intent = new Intent(
						PlaylistOverviewActivity.this, PlaylistActivity.class);
				intent.putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, mPlaylists.get(p).id);
				intent.putExtra(PlaylistActivity.EXTRA_PLAYLIST_NAME, mPlaylists.get(p).name);
				startActivityForResult(intent, REQUEST_PLAYLIST);
			}
		});
		
		refreshLoading();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mStatusPollHandler.start();
		} else {
			finish();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mStatusPollHandler.stop();
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mConnection.updateHandleCallback(mOldCommandHandler);
		}
		
		if (isFinishing()) {
			finishActivity(REQUEST_PLAYLIST);
		}
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mPlaylists, mActivePlaylistId, mLoadingDismissed};
	}
	
	@Override
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		switch (command) {
		case PLAYER_STATUS:
			if ((CurrentSongActivity.mData.playing != CurrentSongActivity.mPreviousData.playing
					|| mActivePlaylistId != mActivePlaylistIdChange) && mAdapter != null) {
				mActivePlaylistId = mActivePlaylistIdChange;
				mAdapter.notifyDataSetChanged();
			}
			break;
		
		case PLAYLIST:
			if (Command.Playlist.isPlaylistNames(params)) {
				if (result == null) {
					CurrentSongActivity.mConnection.sendCommand(Command.PLAYLIST,
							Command.Playlist.encodePlaylistNames());
				} else {
					Object [][] playlists = Command.Playlist.decodePlaylistNames(result);
					mActivePlaylistId = Command.Playlist.decodeActivePlaylist(result);
					L.d("active " + mActivePlaylistId);
					mActivePlaylistIdChange = mActivePlaylistId;
					
					for (int i = 0; i < playlists.length; i++) {
						PlaylistEntry e = new PlaylistEntry();
						e.count = (Integer) playlists[i][0];
						e.id = (Integer) playlists[i][1];
						e.name = (String) playlists[i][2];
						L.d(e.name + " " + e.id);
						mPlaylists.add(e);
					}
					
					mLoadingDismissed = true;
					refreshLoading();
				}
			}
			break;
		
		default:
			break;
		}
	}
	
	// PRIVATE ====================================================================================
	
	private static class PlaylistEntry {
		public int id;
		public String name;
		public int count;
	}
	
	private static class ViewHolder {
		public TextView title;
		public TextView count;
		public ImageView playing;
	}
	
	private class PlaylistsAdapter extends BaseAdapter {
		
		@Override
		public int getCount() {
			return mPlaylists.size();
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
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(R.layout.playlist_overview_item, null);
				
				ViewHolder holder = new ViewHolder();
				holder.count = (TextView) convertView.findViewById(R.id.count);
				holder.title = (TextView) convertView.findViewById(R.id.title);
				holder.playing = (ImageView) convertView.findViewById(R.id.playing);
				
				convertView.setTag(holder);
			}
			
			PlaylistEntry e = mPlaylists.get(position);
			ViewHolder holder = (ViewHolder) convertView.getTag();
			holder.count.setText("(" + e.count + ")");
			holder.title.setText(e.name);
			
			if (mActivePlaylistId == e.id) {
				holder.playing.setVisibility(View.VISIBLE);
				holder.playing.setImageResource(CurrentSongActivity.mData.playing
						? R.drawable.ic_media_play : R.drawable.ic_media_pause);
			} else {
				holder.playing.setVisibility(View.INVISIBLE);
			}
			
			return convertView;
		}
	}
	
	
	private void refreshLoading() {
		if (mLoadingDismissed) {
			findViewById(R.id.loading_progress).setVisibility(View.GONE);
			((TextView) findViewById(R.id.playlist_title)).setText(
					getString(R.string.playlists) + " (" + mPlaylists.size() + ")");
			mAdapter = new PlaylistsAdapter();
			((ListView) findViewById(R.id.list)).setAdapter(mAdapter);
		}
	}
}
