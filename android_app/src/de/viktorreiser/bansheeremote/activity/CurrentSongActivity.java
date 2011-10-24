package de.viktorreiser.bansheeremote.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.App;
import de.viktorreiser.bansheeremote.data.BansheeConnection;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Repeat;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Shuffle;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.TrackInfo;
import de.viktorreiser.bansheeremote.data.BansheeServer;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask.OnBansheeServerCheck;
import de.viktorreiser.bansheeremote.data.CoverCache;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;

/**
 * Main activity which communicates with the banshee server (updates UI and sends control commands).<br>
 * <br>
 * This class does the main work by delegating the requests triggered by the UI, handling the
 * results and poll data periodically. It's also reacting on network changes and connection failure.
 * So there's a lot of action going on here therefore you will find the most tricky code here.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class CurrentSongActivity extends Activity implements OnBansheeServerCheck {
	
	// PACKAGE =====================================================================================
	
	/**
	 * Simple data container for server state.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	class BansheeData {
		boolean playing = false;
		int volume = -1;
		Shuffle shuffle = Shuffle.UNKNOWN;
		Repeat repeat = Repeat.UNKNOWN;
		String song = "";
		String artist = "";
		String album = "";
		String genre = "";
		int year = 0;
		String artId = "";
		long totalTime = -1;
		long currentTime = -1;
		long currentSongId = 0;
		int changeFlag = 0;
		
		public void copyFrom(BansheeData data) {
			playing = data.playing;
			volume = data.volume;
			shuffle = data.shuffle;
			repeat = data.repeat;
			song = data.song;
			artist = data.artist;
			album = data.album;
			genre = data.genre;
			year = data.year;
			artId = data.artId;
			totalTime = data.totalTime;
			currentTime = data.currentTime;
			currentSongId = data.currentSongId;
			changeFlag = data.changeFlag;
		}
	}
	
	/**
	 * This class is responsible for server status polls.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	class StatusPollHandler extends Handler {
		
		private final int MESSAGE_GET_STATUS = 1;
		private final int MESSAGE_UPDATE_POSITION = 2;
		
		private boolean mmRunning = false;
		private long mmSeekUpdateStart = 0;
		
		
		public void start() {
			mmRunning = true;
			sendEmptyMessage(MESSAGE_GET_STATUS);
		}
		
		public void stop() {
			mmRunning = false;
			removeMessages(MESSAGE_GET_STATUS);
			removeMessages(MESSAGE_UPDATE_POSITION);
		}
		
		public void updatePseudoPoll() {
			removeMessages(MESSAGE_UPDATE_POSITION);
			
			if (mData.playing) {
				long interval = App.getPollInterval(NetworkStateBroadcast.isWifiConnected());
				
				if (interval > 1000) {
					mmSeekUpdateStart = System.currentTimeMillis();
					sendEmptyMessageDelayed(MESSAGE_UPDATE_POSITION, 1000);
				}
			}
		}
		
		@Override
		public void handleMessage(Message msg) {
			if (mConnection == null) {
				return;
			}
			
			switch (msg.what) {
			case MESSAGE_GET_STATUS:
				mConnection.sendCommand(Command.PLAYER_STATUS, null);
				sendEmptyMessageDelayed(MESSAGE_GET_STATUS,
						App.getPollInterval(NetworkStateBroadcast.isWifiConnected()));
				break;
			
			case MESSAGE_UPDATE_POSITION:
				if (mmRunning) {
					mData.currentTime += (System.currentTimeMillis() - mmSeekUpdateStart);
					
					if (mData.totalTime > 0 && mData.currentTime > mData.totalTime) {
						mConnection.sendCommand(Command.PLAYER_STATUS, null);
					} else {
						mCommandHandler.updateSeekData(false);
						updatePseudoPoll();
					}
				}
				break;
			}
		}
	}
	
	// accessed by other activities
	static BansheeConnection mConnection = null;
	static BansheeData mData;
	static BansheeData mPreviousData;
	static StatusPollHandler mStatusPollHandler;
	
	// PIRVATE ====================================================================================
	
	private static final int REQUEST_SERVER_LIST = 1;
	private static final int REQUEST_SEETINGS = 2;
	private static final int REQUEST_PLAYLIST = 3;
	
	
	private boolean mActivityPaused = true;
	private boolean mDatabaseSyncRunning = false;
	private boolean mWasPlayingBeforeCall = false;
	
	private ImageView mPlay;
	private ImageView mPause;
	private TextView mRepeat;
	private TextView mRepeat2;
	private TextView mShuffle;
	private TextView mShuffle2;
	private TextView mVolume;
	private ImageView mCover1;
	private ImageView mCover2;
	private TextView mCurrentTime;
	private TextView mTotalTime;
	private TextView mSong;
	private TextView mArtist;
	private TextView mAlbum;
	private SeekBar mSeekBar;
	
	private BansheeServerCheckTask mCheckTask;
	private CoverAnimator mCoverAnimator;
	private CommandHandler mCommandHandler = new CommandHandler();
	
	// OVERRIDDEN =================================================================================
	
	/**
	 * Setup activity (fetch retained data when configuration was changed before).
	 */
	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.current_song);
		
		mStatusPollHandler = new StatusPollHandler();
		
		setupViewReferences();
		setupPhoneStateListener();
		setupViewControls();
		
		mCoverAnimator = new CoverAnimator();
		
		Object [] dataBefore = (Object []) getLastNonConfigurationInstance();
		
		if (dataBefore != null) {
			// activity is not starting for the first time so we had a valid connection, keep it
			mCheckTask = (BansheeServerCheckTask) dataBefore[0];
			mConnection = (BansheeConnection) dataBefore[1];
			mData = (BansheeData) dataBefore[2];
			mPreviousData = (BansheeData) dataBefore[3];
			mDatabaseSyncRunning = (Boolean) dataBefore[4];
			mWasPlayingBeforeCall = (Boolean) dataBefore[5];
			mCommandHandler.updateComplete(true);
			
			if (mConnection != null) {
				mConnection.updateHandleCallback(mCommandHandler);
			}
			
			if (mCheckTask != null) {
				// we are still checking a server, keep going
				mCheckTask.showDialog(this);
			} else {
				mCommandHandler.handleCoverStatus();
			}
		} else {
			BansheeServer server = BansheeServer.getDefaultServer();
			mData = new BansheeData();
			mPreviousData = new BansheeData();
			mCommandHandler.updateComplete(true);
			
			if (App.isRememberDefaultServer() && server != null) {
				// first launch, check default server
				mCheckTask = new BansheeServerCheckTask(server, this);
			} else {
				startActivityForResult(new Intent(this, ServerListActivity.class),
						REQUEST_SERVER_LIST);
			}
		}
	}
	
	/**
	 * Clear all unneeded data (especially if activity is really destroyed / finished).
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		mStatusPollHandler = null;
		
		if (mConnection != null) {
			if (isFinishing()) {
				mConnection.close();
			} else {
				// remove the old handler which is referencing the old activity so the
				// garbage collector kicks in - we'll set a new handler in the new activity
				mConnection.updateHandleCallback(null);
			}
		}
	}
	
	/**
	 * Setup activity for visible state (server poll etc).
	 */
	@Override
	public void onResume() {
		super.onResume();
		mActivityPaused = false;
		
		if (mConnection != null) {
			mStatusPollHandler.start();
		}
	}
	
	/**
	 * Setup activity for invisible state (disable server poll etc).
	 */
	@Override
	public void onPause() {
		super.onPause();
		mActivityPaused = true;
		
		mStatusPollHandler.stop();
	}
	
	/**
	 * We want to retain some data when configuration changes (data + running tasks).
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mCheckTask != null) {
			mCheckTask.dismissDialog();
		}
		
		return new Object [] {mCheckTask, mConnection, mData, mPreviousData, mDatabaseSyncRunning,
				mWasPlayingBeforeCall};
	}
	
	/**
	 * We have to handle the return from other activities here.
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_SERVER_LIST:
			if (resultCode == RESULT_OK) {
				// if the server list activity was left then because the user chose a valid server
				// get it and use it - we can trust it's valid, activity before checked that
				mCover1.setImageBitmap(null);
				mCover2.setImageBitmap(mCoverAnimator.mmDefaultCover);
				setupServerConnection(BansheeServer.getDefaultServer());
			} else if (mConnection == null) {
				// if user called server list activity from menu he had a valid connection before
				// and wouldn't land here - if he was forced to choose a server but pressed back
				// he will land here and so quit the application
				finish();
			}
			break;
		
		case REQUEST_SEETINGS:
			if (mConnection == null) {
				finish();
			} else {
				mCommandHandler.updateComplete(true);
			}
			break;
		
		default:
			super.onActivityResult(requestCode, resultCode, data);
		}
	}
	
	/**
	 * Create a options menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		menu.add(Menu.NONE, 1, Menu.NONE, R.string.choose_server)
				.setIcon(R.drawable.server);
		menu.add(Menu.NONE, 2, Menu.NONE, R.string.sync_db)
				.setIcon(R.drawable.sync);
		menu.add(Menu.NONE, 3, Menu.NONE, R.string.settings)
				.setIcon(R.drawable.settings);
		
		return true;
	}
	
	/**
	 * React on options menu selections.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 1:
			startActivityForResult(new Intent(this, ServerListActivity.class),
					REQUEST_SERVER_LIST);
			return true;
			
		case 2:
			if (!mDatabaseSyncRunning) {
				mConnection.sendCommand(Command.SYNC_DATABASE,
						Command.SyncDatabase.encodeFileTimestamp());
			}
			return true;
			
		case 3:
			startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SEETINGS);
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	/**
	 * Listen for volume keys.
	 */
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (App.isVolumeKeyControl()) {
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					mConnection.sendCommand(Command.PLAYER_STATUS,
							Command.PlayerStatus.encodeVolumeUp(null));
				}
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					mConnection.sendCommand(Command.PLAYER_STATUS,
							Command.PlayerStatus.encodeVolumeDown(null));
				}
				return true;
			}
		}
		
		return super.dispatchKeyEvent(event);
	}
	
	/**
	 * Callback of initial server check task.
	 */
	@Override
	public void onBansheeServerCheck(Integer success) {
		BansheeServer server = mCheckTask.getServer();
		mCheckTask = null;
		
		if (success == 1) {
			setupServerConnection(server);
		} else if (success == 0) {
			// check failed, force user to choose a valid server (or leave on back press)
			Toast.makeText(this, R.string.host_denied_password, Toast.LENGTH_LONG).show();
			startActivityForResult(new Intent(this, ServerListActivity.class), REQUEST_SERVER_LIST);
		} else {
			// check failed, force user to choose a valid server (or leave on back press)
			Toast.makeText(this, R.string.host_not_reachable, Toast.LENGTH_LONG).show();
			startActivityForResult(new Intent(this, ServerListActivity.class), REQUEST_SERVER_LIST);
		}
	}
	
	// PRIVATE ====================================================================================
	
	/**
	 * Get all needed references from inflated views.
	 */
	private void setupViewReferences() {
		mPlay = (ImageView) findViewById(R.id.play);
		mPause = (ImageView) findViewById(R.id.pause);
		mRepeat = (TextView) findViewById(R.id.repeat);
		mRepeat2 = (TextView) findViewById(R.id.repeat_2);
		mShuffle = (TextView) findViewById(R.id.shuffle);
		mShuffle2 = (TextView) findViewById(R.id.shuffle_2);
		mVolume = (TextView) findViewById(R.id.volume);
		mCover1 = (ImageView) findViewById(R.id.cover1);
		mCover2 = (ImageView) findViewById(R.id.cover2);
		mCurrentTime = (TextView) findViewById(R.id.seek_position);
		mTotalTime = (TextView) findViewById(R.id.seek_total);
		mSong = (TextView) findViewById(R.id.song_title);
		mArtist = (TextView) findViewById(R.id.song_artist);
		mAlbum = (TextView) findViewById(R.id.song_album);
		mSeekBar = (SeekBar) findViewById(R.id.seekbar);
	}
	
	/**
	 * Set phone state listener to send stop command to server on incoming call.
	 */
	private void setupPhoneStateListener() {
		PhoneStateListener phoneStateListener = new PhoneStateListener() {
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				if (state == TelephonyManager.CALL_STATE_RINGING
						&& App.isStopOnCall()) {
					mWasPlayingBeforeCall = mData.playing;
					
					if (mWasPlayingBeforeCall) {
						mConnection.sendCommand(Command.PLAYER_STATUS,
								Command.PlayerStatus.encodePause(null));
					}
				} else if (state == TelephonyManager.CALL_STATE_IDLE) {
					if (mWasPlayingBeforeCall) {
						mConnection.sendCommand(Command.PLAYER_STATUS,
								Command.PlayerStatus.encodePlay(null));
					}
					
					mWasPlayingBeforeCall = false;
				}
			}
		};
		
		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(
				phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	/**
	 * Setup all needed view listeners.
	 */
	private void setupViewControls() {
		findViewById(R.id.back).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodePlayPrevious(null));
			}
		});
		
		findViewById(R.id.play_pause).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodePlayToggle(null));
			}
		});
		
		findViewById(R.id.forward).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodePlayNext(null));
			}
		});
		
		View repeatClick = mRepeat2 != null ? findViewById(R.id.repeat_container) : mRepeat;
		repeatClick.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodeRepeatToggle(null));
			}
		});
		
		View shuffleClick = mShuffle2 != null ? findViewById(R.id.shuffle_container) : mShuffle;
		shuffleClick.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodeShuffleToggle(null));
			}
		});
		
		mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
				if (fromTouch && mData.totalTime > 0) {
					long value = Math.max(1, (long) (1.0 * mData.totalTime * progress
							/ seekBar.getMax()));
					
					mConnection.sendCommand(Command.PLAYER_STATUS,
							Command.PlayerStatus.encodeSeekPosition(null, value));
					mData.currentTime = value;
					mCommandHandler.updateSeekData(false);
				}
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		
		findViewById(R.id.browse_songs).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (BansheeDatabase.isOpen()) {
					// TODO react on button click
				} else {
					Toast.makeText(CurrentSongActivity.this, R.string.need_sync_db,
							Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		findViewById(R.id.browse_artists).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (BansheeDatabase.isOpen()) {
					// TODO react on button click
				} else {
					Toast.makeText(CurrentSongActivity.this, R.string.need_sync_db,
							Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		findViewById(R.id.playlist).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (BansheeDatabase.isOpen()) {
					startActivityForResult(
							new Intent(CurrentSongActivity.this, PlaylistActivity.class),
							REQUEST_PLAYLIST);
				} else {
					Toast.makeText(CurrentSongActivity.this, R.string.need_sync_db,
							Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		findViewById(R.id.browse_albums).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (BansheeDatabase.isOpen()) {
					// TODO react on button click
				} else {
					Toast.makeText(CurrentSongActivity.this, R.string.need_sync_db,
							Toast.LENGTH_SHORT).show();
				}
			}
		});
	}
	
	/**
	 * Create and setup server connection.
	 * 
	 * @param server
	 *            banshee server to connect to (the connection should've tested before)
	 */
	private void setupServerConnection(BansheeServer server) {
		mData = new BansheeData();
		mCommandHandler.updateComplete(true);
		mConnection = new BansheeConnection(server, mCommandHandler);
		BansheeDatabase.open(server);
		
		if (!mActivityPaused) {
			mStatusPollHandler.start();
		}
	}
	
	/**
	 * This class will handle all incoming server responses and so UI changes.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private class CommandHandler implements OnBansheeCommandHandle {
		
		public void updateComplete(boolean force) {
			updateVolume(force);
			updateSongData(force);
			updateSeekData(force);
			updateShuffle(force);
			updateRepeat(force);
			updatePlayStatus(force);
		}
		
		public void updateVolume(boolean force) {
			if (force || mPreviousData.volume != mData.volume) {
				mVolume.setText(mData.volume < 0 ? "" : "Vol: " + mData.volume + "%");
			}
		}
		
		public void updateShuffle(boolean force) {
			if (force || mPreviousData.shuffle != mData.shuffle) {
				if (mShuffle2 != null) {
					mShuffle2.setText(mData.shuffle.toString().toLowerCase());
				} else {
					mShuffle.setText("Shuffle " + mData.shuffle.toString().toLowerCase());
				}
			}
		}
		
		public void updateRepeat(boolean force) {
			if (force || mPreviousData.repeat != mData.repeat) {
				if (mRepeat2 != null) {
					mRepeat2.setText(mData.repeat.toString().toLowerCase());
				} else {
					mRepeat.setText("Repeat " + mData.repeat.toString().toLowerCase());
				}
			}
		}
		
		public void updateSongData(boolean force) {
			if (force || !mData.song.equals(mPreviousData.song)
					|| (App.isDisplaySongGenre() && !mData.genre.equals(mPreviousData.genre))) {
				String song = mData.song;
				
				if (song.equals("")) {
					song = getString(R.string.unknown_track);
				}
				
				if (App.isDisplaySongGenre() && !mData.genre.equals("")) {
					song += " [" + mData.genre + "]";
				}
				
				mSong.setText(song);
			}
			
			if (force || !mData.artist.equals(mPreviousData.artist)) {
				if (mData.artist.equals("")) {
					mArtist.setText(R.string.unknown_artist);
				} else {
					mArtist.setText(mData.artist);
				}
				
				mArtist.setSelected(true);
			}
			
			if (force || !mData.album.equals(mPreviousData.album)
					|| (App.isDisplayAlbumYear() && mData.year != mPreviousData.year)) {
				String album = mData.album;
				
				if (album.equals("")) {
					album = getString(R.string.unknown_album);
				}
				
				if (App.isDisplayAlbumYear() && mData.year >= 1000) {
					album += " [" + mData.year + "]";
				}
				
				mAlbum.setText(album);
			}
		}
		
		public void updateSeekData(boolean force) {
			if (force || mPreviousData.currentTime != mData.currentTime
					|| mPreviousData.totalTime != mData.totalTime) {
				mCurrentTime.setText(mData.currentTime < 0
						? "" : millisecondsToDurationString(mData.currentTime));
				mTotalTime.setText(mData.totalTime < 0
						? "" : millisecondsToDurationString(mData.totalTime));
				mSeekBar.setProgress(mData.totalTime <= 0 || mData.currentTime < 0
						? 0 : Math.round(1000f * mData.currentTime / mData.totalTime));
			}
		}
		
		public void updatePlayStatus(boolean force) {
			if (force || mPreviousData.playing != mData.playing) {
				mPlay.setVisibility(!mData.playing ? View.VISIBLE : View.INVISIBLE);
				mPause.setVisibility(mData.playing ? View.VISIBLE : View.INVISIBLE);
			}
		}
		
		@Override
		public void onBansheeCommandHandled(Command command, byte [] params, byte [] response) {
			if (command == null) {
				handleFail();
				return;
			}
			
			mPreviousData.copyFrom(mData);
			
			switch (command) {
			case PLAYER_STATUS:
				handlePlayerStatus(response);
				break;
			
			case SONG_INFO:
				handleSongInfo(response);
				break;
			
			case SYNC_DATABASE:
				if (Command.SyncDatabase.isFileTimestamp(params)) {
					handleSyncDatabaseFileSize(response);
				} else if (Command.SyncDatabase.isFileRequest(params)) {
					handleSyncDatabaseFile(response);
				}
				break;
			
			case COVER:
				handleCover(response, params);
				break;
			}
		}
		
		private void handleFail() {
			mStatusPollHandler.stop();
			mConnection = null;
			mConnection = null;
			Toast.makeText(CurrentSongActivity.this, R.string.host_offline_or_banshee_closed,
					Toast.LENGTH_LONG).show();
			
			finishActivity(REQUEST_PLAYLIST);
			Intent intent = new Intent(CurrentSongActivity.this, ServerListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivityForResult(intent, REQUEST_SERVER_LIST);
		}
		
		private void handlePlayerStatus(byte [] response) {
			mData.playing = Command.PlayerStatus.decodePlaying(response);
			mData.currentTime = Command.PlayerStatus.decodeSeekPosition(response);
			mData.repeat = Command.PlayerStatus.decodeRepeatMode(response);
			mData.shuffle = Command.PlayerStatus.decodeShuffleMode(response);
			mData.volume = Command.PlayerStatus.decodeVolume(response);
			mData.changeFlag = Command.PlayerStatus.decodeChangeFlag(response);
			mData.currentSongId = Command.PlayerStatus.decodeSongId(response);
			
			mStatusPollHandler.updatePseudoPoll();
			updateComplete(false);
			
			if (mData.changeFlag != mPreviousData.changeFlag) {
				TrackInfo info = BansheeDatabase.getTrackInfo(mData.currentSongId);
				
				if (info != null) {
					mData.totalTime = info.totalTime;
					mData.song = info.title;
					mData.artist = info.artist;
					mData.album = info.album;
					mData.genre = info.genre;
					mData.year = info.year;
					mData.artId = info.artId;
					updateComplete(false);
					handleCoverStatus();
				} else if (mData.currentSongId > 0) {
					if (BansheeDatabase.isOpen() && App.isShowDbOutOfDateHint()) {
						Toast.makeText(CurrentSongActivity.this, R.string.out_of_data_hint_db,
								Toast.LENGTH_SHORT).show();
					}
					
					mConnection.sendCommand(Command.SONG_INFO, null);
				}
			}
		}
		
		private void handleSongInfo(byte [] response) {
			Object [] d = Command.SongInfo.decode(response);
			
			if (d != null) {
				mData.totalTime = (Long) d[0];
				mData.song = (String) d[1];
				mData.artist = (String) d[2];
				mData.album = (String) d[3];
				mData.genre = (String) d[4];
				mData.year = (Integer) d[5];
				mData.artId = (String) d[6];
				
				updateComplete(false);
				handleCoverStatus();
			}
		}
		
		public void handleCoverStatus() {
			if (!mData.artId.equals("")) {
				if (CoverCache.coverExists(mData.artId)) {
					mCoverAnimator.setCover(CoverCache.getCover(mData.artId));
				} else if (NetworkStateBroadcast.isMobileConnected()
						&& !App.isMobileNetworkCoverFetch()) {
					mCoverAnimator.setDefaultCover();
				} else {
					mCoverAnimator.discardCover();
					mConnection.sendCommand(Command.COVER, Command.Cover.encode(mData.artId));
				}
			} else {
				mCoverAnimator.setDefaultCover();
			}
		}
		
		private void handleSyncDatabaseFileSize(byte [] response) {
			if (response == null) {
				return;
			}
			
			long timestamp = Command.SyncDatabase.decodeFileTimestamp(response);
			
			if (timestamp == 0) {
				Toast.makeText(CurrentSongActivity.this, R.string.no_sync_db,
						Toast.LENGTH_LONG).show();
			} else {
				if (BansheeDatabase.isDatabaseUpToDate(mConnection.getServer(), timestamp)) {
					Toast.makeText(CurrentSongActivity.this, R.string.up_to_date_sync_db,
							Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(CurrentSongActivity.this, R.string.fetching_sync_db,
							Toast.LENGTH_SHORT).show();
					mConnection.sendCommand(Command.SYNC_DATABASE,
							Command.SyncDatabase.encodeFile());
				}
			}
		}
		
		private void handleSyncDatabaseFile(byte [] response) {
			if (response == null || response.length < 2) {
				Toast.makeText(CurrentSongActivity.this, R.string.error_fetching_sync_db,
						Toast.LENGTH_LONG).show();
			} else if (!BansheeDatabase.updateDatabase(mConnection.getServer(), response)) {
				Toast.makeText(CurrentSongActivity.this, R.string.error_writing_sync_db,
						Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(CurrentSongActivity.this, R.string.updated_sync_db,
						Toast.LENGTH_LONG).show();
			}
		}
		
		private void handleCover(byte [] response, byte [] params) {
			if (response.length < 2) {
				mCoverAnimator.setDefaultCover();
			} else {
				String id = Command.Cover.getId(params);
				CoverCache.addCover(id, response);
				
				if (!mActivityPaused) {
					Bitmap cover = CoverCache.getCover(id);
					
					if (cover == null) {
						mCoverAnimator.setDefaultCover();
					} else {
						mCoverAnimator.setCover(cover);
					}
				}
			}
		}
		
		private String millisecondsToDurationString(long milliseconds) {
			long seconds = milliseconds / 1000;
			String prependedZero = (seconds % 60 < 10) ? "0" : "";
			
			if (seconds >= 60) {
				return (seconds / 60) + ":" + prependedZero + (seconds % 60);
			} else {
				return "0:" + prependedZero + seconds;
			}
		}
	}
	
	/**
	 * This class will handle the incoming cover changes.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private class CoverAnimator implements Runnable {
		
		private static final long FADE_COMPLETE = 2000;
		private static final long FADE_STEP = 50;
		
		private float mmAlpha1 = 0;
		private float mmAlpha2 = 0;
		private long mmLastStep;
		private Bitmap mmNextCover;
		private boolean mmDiscard = true;
		private boolean mmHasCover = false;
		private Bitmap mmDefaultCover =
				((BitmapDrawable) getResources().getDrawable(R.drawable.no_cover)).getBitmap();
		private Handler mmAnimationHandler = new Handler();
		
		
		public CoverAnimator() {
			mCover1.setAlpha(0);
			mCover2.setAlpha(0);
		}
		
		public void discardCover() {
			mmNextCover = null;
			mmDiscard = true;
			mmHasCover = false;
			mmAnimationHandler.removeCallbacks(this);
			mmLastStep = System.currentTimeMillis();
			mmAnimationHandler.postDelayed(this, FADE_STEP);
		}
		
		public void setDefaultCover() {
			setCover(mmDefaultCover);
		}
		
		public void setCover(Bitmap cover) {
			mmNextCover = cover;
			mmDiscard = true;
			mmAnimationHandler.removeCallbacks(this);
			mmLastStep = System.currentTimeMillis();
			mmAnimationHandler.postDelayed(this, FADE_STEP);
		}
		
		@Override
		public void run() {
			boolean animate = false;
			float step = 255f * (System.currentTimeMillis() - mmLastStep) / FADE_COMPLETE;
			
			if (Math.round(mmAlpha1) == 0 && mmNextCover != null) {
				mmAlpha1 = mmAlpha2;
				mmAlpha2 = 0;
				mCover1.setImageDrawable(mCover2.getDrawable());
				mCover2.setImageBitmap(mmNextCover);
				mmDiscard = false;
				mmHasCover = true;
				mmNextCover = null;
				animate = true;
			}
			
			mmAlpha1 = Math.max(0, mmAlpha1 - step);
			int alpha1 = Math.round(mmAlpha1);
			int alpha2 = 0;
			
			if (mmDiscard) {
				mmAlpha2 = Math.max(0, mmAlpha2 - step);
				alpha2 = Math.round(mmAlpha2);
				
				if (alpha2 != 0 || mmNextCover != null) {
					animate = true;
				}
			} else if (mmHasCover) {
				mmAlpha2 = Math.min(255, mmAlpha2 + step);
				alpha2 = Math.round(mmAlpha2);
				
				if (alpha2 != 255) {
					animate = true;
				}
			}
			
			mCover1.setAlpha(alpha1);
			mCover2.setAlpha(alpha2);
			
			if (animate) {
				mmLastStep = System.currentTimeMillis();
				mmAnimationHandler.postDelayed(this, FADE_STEP);
			}
		}
	}
}