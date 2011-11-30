package de.viktorreiser.bansheeremote.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils.TruncateAt;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.bugsense.trace.BugSenseHandler;

import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.App;
import de.viktorreiser.bansheeremote.data.BansheeConnection;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Repeat;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Shuffle;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.Track;
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
 * So there's a lot of action going on here therefore you will find the most tricky code here.<br>
 * <br>
 * This activity is also performing the central communication. So it provides some static methods
 * which are used by other activities. The can't even exist without an instance of this activity.
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
	static class BansheeData {
		boolean playing = false;
		int volume = -1;
		Shuffle shuffle = Shuffle.UNKNOWN;
		Repeat repeat = Repeat.UNKNOWN;
		String song = App.getContext().getString(R.string.unknown_track);
		String artist = App.getContext().getString(R.string.unknown_artist);
		String album = App.getContext().getString(R.string.unknown_album);
		String genre = "";
		int year = 0;
		String artId = "";
		long totalTime = -1;
		long currentTime = -1;
		long currentSongId = 0;
		int changeFlag = 0;
		byte rating = 0;
		
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
			rating = data.rating;
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
		
		
		/**
		 * Start polling.
		 */
		public void start() {
			mmRunning = true;
			sendEmptyMessage(MESSAGE_GET_STATUS);
		}
		
		/**
		 * Stop polling.
		 */
		public void stop() {
			mmRunning = false;
			removeMessages(MESSAGE_GET_STATUS);
			removeMessages(MESSAGE_UPDATE_POSITION);
		}
		
		/**
		 * Trigger a pseudo poll in on second.<br>
		 * <br>
		 * Because the actual poll request might be send e.g. every 10 seconds we need to simulate a
		 * poll an keep progressing the track seek bar position.
		 */
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
						// pseudo poll has detected track end, we need to request more now
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
	
	/**
	 * Get current track data.
	 * 
	 * @return track data ({@code null} when Activity is not running)
	 */
	static BansheeData getData() {
		return mInstance == null ? null : mInstance.mData;
	}
	
	/**
	 * Get track data of previous request.
	 * 
	 * @return track data ({@code null} when Activity is not running)
	 */
	static BansheeData getPreviousData() {
		return mInstance == null ? null : mInstance.mPreviousData;
	}
	
	/**
	 * Get (global) banshee connection.
	 * 
	 * @return banshee connection ({@code null} when Activity is not running)
	 */
	static BansheeConnection getConnection() {
		return mInstance == null ? null : mInstance.mConnection;
	}
	
	/**
	 * Tell activity to close an open connection.
	 */
	static void resetConnection() {
		if (mInstance != null && mInstance.mConnection != null) {
			mInstance.mConnection.close();
			mInstance.mConnection = null;
		}
	}
	
	/**
	 * Get poll handler of activity.
	 * 
	 * @return poll handler ({@code null} when Activity is not running)
	 */
	static StatusPollHandler getPollHandler() {
		return mInstance == null ? null : mInstance.mStatusPollHandler;
	}
	
	/**
	 * Handle a key event (global action).
	 * 
	 * @param event
	 *            key event
	 * 
	 * @return {@code true} if key event handled.
	 */
	static boolean handleKeyEvent(KeyEvent event) {
		return mInstance == null ? false : mInstance.handleVolumeKey(event);
	}
	
	// PIRVATE ====================================================================================
	
	private static CurrentSongActivity mInstance = null;
	
	private static final int REQUEST_SERVER_LIST = 1;
	private static final int REQUEST_SEETINGS = 2;
	private static final int REQUEST_OTHER_ACTIVITY = 3;
	
	
	private boolean mActivityPaused = true;
	private boolean mDatabaseSyncRunning = false;
	private boolean mWasPlayingBeforeCall = false;
	private int mDbTimestamp = 0;
	
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
	private ViewGroup mRating;
	
	private BansheeServerCheckTask mCheckTask;
	private CoverAnimator mCoverAnimator;
	private CommandHandler mCommandHandler = new CommandHandler();
	
	private BansheeConnection mConnection = null;
	private BansheeData mData;
	private BansheeData mPreviousData;
	private StatusPollHandler mStatusPollHandler;
	
	// OVERRIDDEN =================================================================================
	
	/**
	 * Setup activity (fetch retained data when configuration was changed before).
	 */
	@Override
	protected void onCreate(Bundle bundle) {
		mInstance = this;
		super.onCreate(bundle);
		
		// release debug key
		// Don't use this while working on code. It's there for release so we know that someone
		// produced a crash on a released version. When modifying the code here use the testing key,
		// we don't want bugs you produced your self.
		// BugSenseHandler.setup(this, "8edaa907");
		
		// testing debug key
		BugSenseHandler.setup(this, "590eb975");
		
		
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
		mInstance = null;
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
		
		if (mCheckTask == null) {
			mCoverAnimator.hideImmediately();
			mCommandHandler.handleCoverStatus();
		}
		
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
				mCover2.setImageResource(R.drawable.no_cover);
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
		
		menu.add(Menu.NONE, 2, 1, R.string.sync_db)
				.setIcon(R.drawable.sync);
		menu.add(Menu.NONE, 1, 1, R.string.choose_server)
				.setIcon(R.drawable.server);
		menu.add(Menu.NONE, 3, 1, R.string.settings)
				.setIcon(R.drawable.settings);
		
		return true;
	}
	
	/**
	 * Create a options menu.
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.removeItem(4);
		
		if (!"".equals(mData.artId)) {
			menu.add(Menu.NONE, 4, 0, R.string.renew_cover)
					.setIcon(R.drawable.cover);
		}
		
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
				mDatabaseSyncRunning = true;
				mConnection.sendCommand(Command.SYNC_DATABASE,
						Command.SyncDatabase.encodeFileTimestamp());
			}
			return true;
			
		case 3:
			startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SEETINGS);
			return true;
			
		case 4:
			mConnection.sendCommand(Command.COVER, Command.Cover.encode(mData.artId));
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
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mDatabaseSyncRunning) {
			return true;
		}
		
		return handleVolumeKey(event) ? true : super.dispatchKeyEvent(event);
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
			return;
		} else if (success == 0) {
			App.longToast(R.string.host_denied_password);
		} else {
			App.longToast(R.string.host_not_reachable);
		}
		
		// check failed, force user to choose a valid server (or leave on back press)
		startActivityForResult(new Intent(this, ServerListActivity.class), REQUEST_SERVER_LIST);
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
		mRating = (ViewGroup) findViewById(R.id.rating);
		
		for (int i = 0; i < mRating.getChildCount(); i++) {
			((ImageView) mRating.getChildAt(i)).setAlpha(0xa0);
		}
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
				if (BansheeDatabase.isOpen() && !mDatabaseSyncRunning) {
					startActivityForResult(new Intent(CurrentSongActivity.this,
							TrackActivity.class), REQUEST_OTHER_ACTIVITY);
				} else {
					App.shortToast(R.string.need_sync_db);
				}
			}
		});
		
		findViewById(R.id.browse_artists).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (BansheeDatabase.isOpen() && !mDatabaseSyncRunning) {
					startActivityForResult(new Intent(CurrentSongActivity.this,
							ArtistActivity.class), REQUEST_OTHER_ACTIVITY);
				} else {
					App.shortToast(R.string.need_sync_db);
				}
			}
		});
		
		findViewById(R.id.playlist).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (BansheeDatabase.isOpen() && !mDatabaseSyncRunning) {
					startActivityForResult(
							new Intent(CurrentSongActivity.this, PlaylistOverviewActivity.class),
							REQUEST_OTHER_ACTIVITY);
				} else {
					App.shortToast(R.string.need_sync_db);
				}
			}
		});
		
		findViewById(R.id.browse_albums).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (BansheeDatabase.isOpen() && !mDatabaseSyncRunning) {
					startActivityForResult(
							new Intent(CurrentSongActivity.this, AlbumActivity.class),
							REQUEST_OTHER_ACTIVITY);
				} else {
					App.shortToast(R.string.need_sync_db);
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
		mCoverAnimator.setDefaultCover();
		mDatabaseSyncRunning = false;
		
		if (!mActivityPaused) {
			mStatusPollHandler.start();
		}
	}
	
	/**
	 * Handle key event if it's a volume button
	 * 
	 * @param e
	 *            key event
	 * 
	 * @return {@code true} if handled
	 */
	private boolean handleVolumeKey(KeyEvent e) {
		if (App.isVolumeKeyControl()) {
			switch (e.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (e.getAction() == KeyEvent.ACTION_DOWN) {
					mConnection.sendCommand(Command.PLAYER_STATUS,
							Command.PlayerStatus.encodeVolumeUp(null));
				}
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (e.getAction() == KeyEvent.ACTION_DOWN) {
					mConnection.sendCommand(Command.PLAYER_STATUS,
							Command.PlayerStatus.encodeVolumeDown(null));
				}
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * This class will handle all incoming server responses and so UI changes.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private class CommandHandler implements OnBansheeCommandHandle {
		
		/**
		 * Update complete UI.
		 * 
		 * @param force
		 *            {@code true} if updated should be forced (regardless whether values haven't
		 *            changed)
		 */
		public void updateComplete(boolean force) {
			updateVolume(force);
			updateSongData(force);
			updateSeekData(force);
			updateShuffle(force);
			updateRepeat(force);
			updatePlayStatus(force);
		}
		
		/**
		 * Update volume UI.
		 * 
		 * @param force
		 *            {@code true} if updated should be forced (regardless whether values haven't
		 *            changed)
		 */
		public void updateVolume(boolean force) {
			if (force || mPreviousData.volume != mData.volume) {
				mVolume.setText(mData.volume < 0 ? "" : "Vol: " + mData.volume + "%");
			}
		}
		
		/**
		 * Update shuffle UI.
		 * 
		 * @param force
		 *            {@code true} if updated should be forced (regardless whether values haven't
		 *            changed)
		 */
		public void updateShuffle(boolean force) {
			if (force || mPreviousData.shuffle != mData.shuffle) {
				if (mShuffle2 != null) {
					mShuffle2.setText(mData.shuffle.toString().toLowerCase());
				} else {
					mShuffle.setText("Shuffle " + mData.shuffle.toString().toLowerCase());
				}
			}
		}
		
		/**
		 * Update repeat UI.
		 * 
		 * @param force
		 *            {@code true} if updated should be forced (regardless whether values haven't
		 *            changed)
		 */
		public void updateRepeat(boolean force) {
			if (force || mPreviousData.repeat != mData.repeat) {
				if (mRepeat2 != null) {
					mRepeat2.setText(mData.repeat.toString().toLowerCase());
				} else {
					mRepeat.setText("Repeat " + mData.repeat.toString().toLowerCase());
				}
			}
		}
		
		/**
		 * Update track title, artist and album UI.
		 * 
		 * @param force
		 *            {@code true} if updated should be forced (regardless whether values haven't
		 *            changed)
		 */
		public void updateSongData(boolean force) {
			if (force || !mData.song.equals(mPreviousData.song)
					|| (App.isDisplaySongGenre() && !mData.genre.equals(mPreviousData.genre))) {
				if (App.isDisplaySongGenre() && !mData.genre.equals("")) {
					mSong.setEllipsize(TruncateAt.MIDDLE);
					mSong.setText(mData.song + " [" + mData.genre + "]");
				} else {
					mSong.setEllipsize(TruncateAt.END);
					mSong.setText(mData.song);
				}
			}
			
			if (force || !mData.artist.equals(mPreviousData.artist)) {
				mArtist.setText(mData.artist);
			}
			
			if (force || !mData.album.equals(mPreviousData.album)
					|| (App.isDisplayAlbumYear() && mData.year != mPreviousData.year)) {
				if (App.isDisplayAlbumYear() && mData.year >= 1000) {
					mAlbum.setEllipsize(TruncateAt.MIDDLE);
					mAlbum.setText(mData.album + " [" + mData.year + "]");
				} else {
					mAlbum.setEllipsize(TruncateAt.END);
					mAlbum.setText(mData.album);
				}
			}
			
			if (force || mData.rating != mPreviousData.rating) {
				if (mData.rating < 1 || !App.isDisplayRating()) {
					mRating.setVisibility(View.GONE);
				} else {
					mRating.setVisibility(View.VISIBLE);
					
					for (int i = 0; i < mRating.getChildCount(); i++) {
						mRating.getChildAt(i).setVisibility(i < mData.rating
								? View.VISIBLE : View.GONE);
					}
				}
			}
		}
		
		/**
		 * Update track seek position and total time UI.
		 * 
		 * @param force
		 *            {@code true} if updated should be forced (regardless whether values haven't
		 *            changed)
		 */
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
		
		/**
		 * Update play / pause icon.
		 * 
		 * @param force
		 *            {@code true} if updated should be forced (regardless whether values haven't
		 *            changed)
		 */
		public void updatePlayStatus(boolean force) {
			if (force || mPreviousData.playing != mData.playing) {
				mPlay.setVisibility(!mData.playing ? View.VISIBLE : View.INVISIBLE);
				mPause.setVisibility(mData.playing ? View.VISIBLE : View.INVISIBLE);
			}
		}
		
		@Override
		public void onBansheeCommandHandled(Command command, byte [] params, byte [] response) {
			if (mStatusPollHandler == null) {
				return;
			}
			
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
				} else if (Command.SyncDatabase.isCompression(params)) {
					if (response == null) {
						App.longToast(R.string.request_failed);
					} else {
						mConnection.sendCommand(Command.SYNC_DATABASE,
								Command.SyncDatabase.encodeFileTimestamp());
					}
				}
				break;
			
			case COVER:
				handleCover(response, params);
				break;
			
			case PLAYLIST:
				handlePlaylist(response, params);
				break;
			}
		}
		
		/**
		 * Finish all activities and start server choose activity.
		 */
		private void handleFail() {
			mStatusPollHandler.stop();
			mConnection = null;
			mConnection = null;
			App.longToast(R.string.host_offline_or_banshee_closed);
			
			finishActivity(REQUEST_OTHER_ACTIVITY);
			Intent intent = new Intent(CurrentSongActivity.this, ServerListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivityForResult(intent, REQUEST_SERVER_LIST);
		}
		
		private void handlePlayerStatus(byte [] response) {
			if (response == null) {
				// request failed, try again
				mConnection.sendCommand(Command.PLAYER_STATUS, null);
				return;
			}
			
			mData.playing = Command.PlayerStatus.decodePlaying(response);
			mData.currentTime = Command.PlayerStatus.decodeSeekPosition(response);
			mData.repeat = Command.PlayerStatus.decodeRepeatMode(response);
			mData.shuffle = Command.PlayerStatus.decodeShuffleMode(response);
			mData.volume = Command.PlayerStatus.decodeVolume(response);
			mData.changeFlag = Command.PlayerStatus.decodeChangeFlag(response);
			mData.currentSongId = Command.PlayerStatus.decodeSongId(response);
			
			updateComplete(false);
			mStatusPollHandler.updatePseudoPoll();
			
			if (mData.changeFlag != mPreviousData.changeFlag) {
				Track info = BansheeDatabase.getUncachedTrack(mData.currentSongId);
				
				if (info.getId() > 0) {
					mData.totalTime = info.getDuration();
					mData.song = info.getTitle();
					mData.artist = info.getArtist().getName();
					mData.album = info.getAlbum().getTitle();
					mData.genre = info.getGenre();
					mData.year = info.getYear();
					mData.artId = info.getAlbum().getArtId();
					mData.rating = info.getRating();
					updateComplete(false);
					handleCoverStatus();
				} else {
					if (mData.currentSongId > 0 && BansheeDatabase.isOpen()
							&& App.isShowDbOutOfDateHint()) {
						App.shortToast(R.string.out_of_data_hint_db);
					}
					
					mConnection.sendCommand(Command.SONG_INFO, null);
				}
			}
		}
		
		private void handleSongInfo(byte [] response) {
			if (response == null) {
				// request failed, try again
				mConnection.sendCommand(Command.SONG_INFO, null);
				return;
			}
			
			Object [] d = Command.SongInfo.decode(response);
			
			if (d != null) {
				mData.totalTime = (Long) d[0];
				mData.song = (String) d[1];
				mData.artist = "".equals((String) d[2])
						? App.getContext().getString(R.string.unknown_artist) : (String) d[2];
				mData.album = "".equals((String) d[3])
						? App.getContext().getString(R.string.unknown_album) : (String) d[3];
				mData.genre = (String) d[4];
				mData.year = (Integer) d[5];
				mData.artId = (String) d[6];
				mData.rating = (Byte) d[7];
				
				updateComplete(false);
				handleCoverStatus();
			}
		}
		
		public void handleCoverStatus() {
			if (!mData.artId.equals("")) {
				if (CoverCache.coverExists(mData.artId)) {
					mCoverAnimator.setCover(CoverCache.getUnscaledCover(mData.artId));
				} else if (NetworkStateBroadcast.isMobileConnected()
						&& !App.isMobileNetworkCoverFetch()) {
					mCoverAnimator.setDefaultCover();
				} else {
					mCoverAnimator.hide();
					mConnection.sendCommand(Command.COVER, Command.Cover.encode(mData.artId));
				}
			} else {
				mCoverAnimator.setDefaultCover();
			}
		}
		
		private void handleSyncDatabaseFileSize(byte [] response) {
			mDatabaseSyncRunning = false;
			
			if (response == null) {
				App.longToast(R.string.error_fetching_sync_db);
				return;
			}
			
			mDbTimestamp = Command.SyncDatabase.decodeFileTimestamp(response);
			String message = null;
			
			if (mDbTimestamp == 0) {
				message = App.getContext().getString(R.string.no_sync_db);
			} else if (BansheeDatabase.isDatabaseUpToDate(mConnection.getServer(), mDbTimestamp)) {
				message = App.getContext().getString(R.string.up_to_date_sync_db);
			} else {
				App.longToast(R.string.fetching_sync_db);
				mDatabaseSyncRunning = true;
				mConnection.sendCommand(Command.SYNC_DATABASE,
						Command.SyncDatabase.encodeFile());
			}
			
			if (message != null) {
				DialogInterface.OnClickListener c = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (mConnection != null) {
							mConnection.sendCommand(Command.SYNC_DATABASE,
									Command.SyncDatabase.encodeCompress());
							App.longToast(R.string.request_sent);
						}
					}
				};
				
				new AlertDialog.Builder(CurrentSongActivity.this)
						.setMessage(message)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(android.R.string.ok, c)
						.show();
			}
		}
		
		private void handleSyncDatabaseFile(byte [] response) {
			mDatabaseSyncRunning = false;
			
			if (response == null || response.length < 2) {
				App.longToast(R.string.error_fetching_sync_db);
			} else if (!BansheeDatabase.updateDatabase(mConnection.getServer(), response,
					mDbTimestamp)) {
				App.longToast(R.string.error_writing_sync_db);
			} else {
				App.longToast(R.string.updated_sync_db);
			}
			
			mDbTimestamp = 0;
		}
		
		private void handleCover(byte [] response, byte [] params) {
			String artId = Command.Cover.getId(params);
			
			if (artId.equals(mData.artId)) {
				if (response == null || response.length < 2) {
					mCoverAnimator.setDefaultCover();
				} else {
					if (!mActivityPaused) {
						Bitmap cover = CoverCache.getUnscaledCover(artId);
						
						if (cover == null) {
							mCoverAnimator.setDefaultCover();
						} else {
							mCoverAnimator.setCover(cover);
						}
					}
				}
			}
		}
		
		private void handlePlaylist(byte [] response, byte [] params) {
			if (Command.Playlist.isAddOrRemove(params)) {
				if (response == null) {
					App.shortToast(R.string.request_failed);
				} else {
					int count = Command.Playlist.decodeAddOrRemoveCount(response);
					
					if (count != 0) {
						int resId = Command.Playlist.isAdd(params)
								? R.plurals.added_to_playlist : R.plurals.removed_from_playlist;
						App.shortToast(App.getContext().getResources().getQuantityString(
								resId, count, count));
					} else {
						App.shortToast(Command.Playlist.isAdd(params)
								? R.string.added_to_playlist_zero
								: R.string.removed_from_playlist_zero);
					}
				}
			} else if (Command.Playlist.isPlayTrack(params)) {
				if (response != null && App.isResetOnPlay()
						&& Command.Playlist.decodePlayTrackStatus(response) != 0) {
					finishActivity(REQUEST_OTHER_ACTIVITY);
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
		private Handler mmAnimationHandler = new Handler();
		
		
		public CoverAnimator() {
			hideImmediately();
		}
		
		/**
		 * Hide the cover immediately.
		 */
		public void hideImmediately() {
			mmDiscard = true;
			mmHasCover = false;
			mmAlpha1 = mmAlpha2 = 0;
			mCover1.setAlpha(0);
			mCover2.setAlpha(0);
		}
		
		/**
		 * Start fade out of currently displayed cover.
		 */
		public void hide() {
			mmNextCover = null;
			mmDiscard = true;
			mmHasCover = false;
			mmAnimationHandler.removeCallbacks(this);
			mmLastStep = System.currentTimeMillis();
			mmAnimationHandler.postDelayed(this, FADE_STEP);
		}
		
		/**
		 * Load (animate) default cover.
		 */
		public void setDefaultCover() {
			setCover(((BitmapDrawable) App.getContext().getResources()
					.getDrawable(R.drawable.no_cover)).getBitmap());
		}
		
		/**
		 * Load (animate) given cover.
		 * 
		 * @param cover
		 *            cover to display
		 */
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