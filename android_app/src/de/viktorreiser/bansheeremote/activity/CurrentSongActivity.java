package de.viktorreiser.bansheeremote.activity;

import java.io.OutputStream;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
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
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandled;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Repeat;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Shuffle;
import de.viktorreiser.bansheeremote.data.BansheeServer;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask.OnBansheeServerCheck;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;

/**
 * Main activity which communicates with the banshee server (updates and control commands).<br>
 * <br>
 * 
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class CurrentSongActivity extends Activity implements OnBansheeServerCheck {
	
	// PIRVATE ====================================================================================
	
	private static final int REQUEST_SERVER_LIST = 1;
	private static final int REQUEST_SEETINGS = 2;
	
	
	private boolean mActivityPaused = true;
	private boolean mWaitingForServerList = false;
	
	private ImageView mPlay;
	private ImageView mPause;
	private TextView mRepeat;
	private TextView mRepeat2;
	private TextView mShuffle;
	private TextView mShuffle2;
	private TextView mVolume;
	private ImageView mCover;
	private TextView mCurrentTime;
	private TextView mTotalTime;
	private TextView mSong;
	private TextView mArtist;
	private TextView mAlbum;
	private SeekBar mSeekBar;
	
	private volatile BitmapDrawable mCoverDrawable = new BitmapDrawable();
	private BitmapDrawable mDefaultColorDrawable;
	private BansheeServerCheckTask mCheckTask;
	private CommandHandler mCommandHandler = new CommandHandler();
	private StatusPollHandler mStatusPollHandler = new StatusPollHandler();
	
	private BansheeConnection mConnection;
	private BansheeData mData;
	private BansheeData mPreviousData;
	private BroadcastReceiver mNetworkChangeListener;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.current_song);
		
		mDefaultColorDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.no_cover);
		
		setupViewReferences();
		setupPhoneStateListener();
		setupViewControls();
		setupNetworkChangeListener();
		
		Object [] dataBefore = (Object []) getLastNonConfigurationInstance();
		
		if (dataBefore != null) {
			// activity is not starting for the first time so we had a valid connection, keep it
			mCheckTask = (BansheeServerCheckTask) dataBefore[0];
			mConnection = (BansheeConnection) dataBefore[1];
			mData = (BansheeData) dataBefore[2];
			mPreviousData = (BansheeData) dataBefore[3];
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
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		unregisterReceiver(mNetworkChangeListener);
		
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
	
	@Override
	public void onResume() {
		super.onResume();
		mActivityPaused = false;
		
		if (mConnection != null) {
			mStatusPollHandler.start();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		mActivityPaused = true;
		
		mStatusPollHandler.stop();
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mCheckTask != null) {
			mCheckTask.dismissDialog();
		}
		
		return new Object [] {mCheckTask, mConnection, mData, mPreviousData};
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_SERVER_LIST:
			mWaitingForServerList = false;
			
			if (resultCode == RESULT_OK) {
				// if the server list activity was left then because the user chose a valid server
				// get it and use it - we can trust it's valid, activity before checked that
				setupServerConnection(BansheeServer.getDefaultServer());
			} else if (mConnection == null) {
				// if user called server list activity from menu he had a valid connection before
				// and wouldn't land here - if he was forced to choose a server but pressed back
				// he will land here and so quit the application
				finish();
			}
			break;
		
		case REQUEST_SEETINGS:
			mCommandHandler.updateComplete(true);
			break;
		
		default:
			super.onActivityResult(requestCode, resultCode, data);
		}
	}
	
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
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 1:
			mWaitingForServerList = true;
			startActivityForResult(new Intent(this, ServerListActivity.class),
					REQUEST_SERVER_LIST);
			return true;
		
		case 2:
			mConnection.sendCommand(Command.SYNC_DATABASE, Command.SyncDatabase.encodeFileSize());
			return true;
			
		case 3:
			startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SEETINGS);
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
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
	
	public void onBansheeServerCheck(boolean success) {
		BansheeServer server = mCheckTask.getServer();
		mCheckTask = null;
		
		if (success) {
			setupServerConnection(server);
		} else {
			// check failed, force user to choose a valid server (or leave on back press)
			Toast.makeText(this, R.string.host_not_reachable, Toast.LENGTH_LONG).show();
			startActivityForResult(new Intent(this, ServerListActivity.class), REQUEST_SERVER_LIST);
		}
	}
	
	// PRIVATE ====================================================================================
	
	private void setupViewReferences() {
		mPlay = (ImageView) findViewById(R.id.play);
		mPause = (ImageView) findViewById(R.id.pause);
		mRepeat = (TextView) findViewById(R.id.repeat);
		mRepeat2 = (TextView) findViewById(R.id.repeat_2);
		mShuffle = (TextView) findViewById(R.id.shuffle);
		mShuffle2 = (TextView) findViewById(R.id.shuffle_2);
		mVolume = (TextView) findViewById(R.id.volume);
		mCover = (ImageView) findViewById(R.id.cover);
		mCurrentTime = (TextView) findViewById(R.id.seek_position);
		mTotalTime = (TextView) findViewById(R.id.seek_total);
		mSong = (TextView) findViewById(R.id.song_title);
		mArtist = (TextView) findViewById(R.id.song_artist);
		mAlbum = (TextView) findViewById(R.id.song_album);
		mSeekBar = (SeekBar) findViewById(R.id.seekbar);
	}
	
	private void setupPhoneStateListener() {
		PhoneStateListener phoneStateListener = new PhoneStateListener() {
			public void onCallStateChanged(int state, String incomingNumber) {
				if (state == TelephonyManager.CALL_STATE_RINGING
						&& App.isStopOnCall()) {
					// TODO trigger pause command when playing
				}
			}
		};
		
		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(
				phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	private void setupViewControls() {
		findViewById(R.id.back).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodePlayPrevious(null));
			}
		});
		
		findViewById(R.id.play_pause).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodePlayToggle(null));
			}
		});
		
		findViewById(R.id.forward).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodePlayNext(null));
			}
		});
		
		View repeatClick = mRepeat2 != null ? findViewById(R.id.repeat_container) : mRepeat;
		repeatClick.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mConnection.sendCommand(Command.PLAYER_STATUS,
						Command.PlayerStatus.encodeRepeatToggle(null));
			}
		});
		
		// FIXME uncomment if bug fixed: https://bugzilla.gnome.org/show_bug.cgi?id=661322
		// View shuffleClick = mShuffle2 != null ? findViewById(R.id.shuffle_container) : mShuffle;
		// shuffleClick.setOnClickListener(new OnClickListener() {
		// public void onClick(View v) {
		// mConnection.sendCommand(Command.PLAYER_STATUS,
		// Command.PlayerStatus.encodeShuffleToggle(null));
		// }
		// });
		
		mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
				if (fromTouch) {
					// TODO trigger seek command
				}
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		
		findViewById(R.id.browse_songs).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// TODO react on button click
			}
		});
		
		findViewById(R.id.browse_artists).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// TODO react on button click
			}
		});
		
		findViewById(R.id.browse_albums).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// TODO react on button click
			}
		});
	}
	
	private void setupNetworkChangeListener() {
		NetworkStateBroadcast.initialCheck(this);
		
		mNetworkChangeListener = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				
			}
		};
		
		registerReceiver(mNetworkChangeListener,
				new IntentFilter(NetworkStateBroadcast.NETWORK_STATE_ACTION));
	}
	
	private void setupServerConnection(BansheeServer server) {
		mData = new BansheeData();
		mCommandHandler.updateComplete(true);
		mConnection = new BansheeConnection(server, mCommandHandler);
		
		if (!mActivityPaused) {
			mStatusPollHandler.start();
		}
	}
	
	
	private class BansheeData {
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
		int totalTime = -1;
		int currentTime = -1;
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
	
	private class CommandHandler implements OnBansheeCommandHandled {
		
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
				if (mData.shuffle == Shuffle.UNKNOWN) {
					mShuffle.setVisibility(View.INVISIBLE);
					
					if (mShuffle2 != null) {
						mShuffle2.setVisibility(View.INVISIBLE);
					}
				} else {
					mShuffle.setVisibility(View.VISIBLE);
					
					if (mShuffle2 != null) {
						mShuffle2.setVisibility(View.VISIBLE);
						mShuffle2.setText(mData.shuffle.toString().toLowerCase());
					} else {
						mShuffle.setText("Shuffle " + mData.shuffle.toString().toLowerCase());
					}
				}
			}
		}
		
		public void updateRepeat(boolean force) {
			if (force || mPreviousData.repeat != mData.repeat) {
				if (mData.repeat == Repeat.UNKNOWN) {
					mRepeat.setVisibility(View.INVISIBLE);
					
					if (mRepeat2 != null) {
						mRepeat2.setVisibility(View.INVISIBLE);
					}
				} else {
					mRepeat.setVisibility(View.VISIBLE);
					
					if (mRepeat2 != null) {
						mRepeat2.setVisibility(View.VISIBLE);
						mRepeat2.setText(mData.repeat.toString().toLowerCase());
					} else {
						mRepeat.setText("Repeat " + mData.repeat.toString().toLowerCase());
					}
				}
			}
		}
		
		public void updateSongData(boolean force) {
			if (force || !mData.song.equals(mPreviousData.song)
					|| (App.isDisplaySongGenre() && !mData.genre.equals(mPreviousData.genre))) {
				String song = mData.song;
				
				if (App.isDisplaySongGenre() && !mData.genre.equals("")) {
					song += " [" + mData.genre + "]";
				}
				
				mSong.setText(song);
			}
			
			if (force || !mData.artist.equals(mPreviousData.artist)) {
				mArtist.setText(mData.artist);
			}
			
			if (force || !mData.album.equals(mPreviousData.album)
					|| (App.isDisplayAlbumYear() && mData.year != mPreviousData.year)) {
				String album = mData.album;
				
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
						? "" : secondsToDurationString(mData.currentTime / 10));
				mTotalTime.setText(mData.totalTime < 0
						? "" : secondsToDurationString(mData.totalTime));
				mSeekBar.setProgress(mData.totalTime <= 0 || mData.currentTime < 0
						? 0 : Math.round(100f * mData.currentTime / mData.totalTime));
			}
		}
		
		public void updatePlayStatus(boolean force) {
			if (force || mPreviousData.playing != mData.playing) {
				mPlay.setVisibility(!mData.playing ? View.VISIBLE : View.INVISIBLE);
				mPause.setVisibility(mData.playing ? View.VISIBLE : View.INVISIBLE);
			}
		}
		
		public void onBansheeCommandHandled(Command command, byte [] params, byte [] response) {
			if (command == null) {
				handleFail();
				return;
			}
			
			mPreviousData.copyFrom(mData);
			
			switch (command) {
			case PLAYER_STATUS:
				mData.playing = Command.PlayerStatus.decodePlaying(response);
				mData.currentTime = Command.PlayerStatus.decodeSeekPosition(response);
				mData.repeat = Command.PlayerStatus.decodeRepeatMode(response);
				mData.shuffle = Command.PlayerStatus.decodeShuffleMode(response);
				mData.volume = Command.PlayerStatus.decodeVolume(response);
				mData.changeFlag = Command.PlayerStatus.decodeChangeFlag(response);
				mData.currentSongId = Command.PlayerStatus.decodeSongId(response);
				updateComplete(false);
				
				if (mData.changeFlag != mPreviousData.changeFlag) {
					mConnection.sendCommand(Command.SONG_INFO, null);
				}
				break;
			
			case SONG_INFO:
				Object [] d = Command.SongInfo.decode(response);
				mData.totalTime = (Integer) d[0];
				mData.song = (String) d[1];
				mData.artist = (String) d[2];
				mData.album = (String) d[3];
				mData.genre = (String) d[4];
				mData.year = (Integer) d[5];
				mData.artId = (String) d[6];
				updateComplete(false);
				break;
				
			case SYNC_DATABASE:
				if (Command.SyncDatabase.isFileSizeRequest(params)) {
					mConnection.sendCommand(Command.SYNC_DATABASE, Command.SyncDatabase.encodeFile());
				} else if (Command.SyncDatabase.isFileRequest(params)) {
					try {
						if (response == null || response.length < 2) {
							throw new Exception();
						}
						
						long id = mConnection.getServer().getSameHostId();
						
						if (id < 0) {
							id = mConnection.getServer().getId();
						}
						
						OutputStream os = CurrentSongActivity.this.openFileOutput(id + ".db", 0);
						os.write(response);
						os.close();
					} catch (Exception e) {
						// TODO add database write error message
						Toast.makeText(CurrentSongActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
					}
				}
				break;
			}
		}
		
		private void handleFail() {
			mStatusPollHandler.stop();
			mConnection = null;
			Toast.makeText(CurrentSongActivity.this, R.string.host_offline_or_banshee_closed,
					Toast.LENGTH_LONG).show();
			
			if (!mWaitingForServerList) {
				// only start the activity if it's not already started manually
				startActivityForResult(
						new Intent(CurrentSongActivity.this, ServerListActivity.class),
						REQUEST_SERVER_LIST);
			}
		}
		
		private String secondsToDurationString(int seconds) {
			String prependedZero = (seconds % 60 < 10) ? "0" : "";
			
			if (seconds >= 60) {
				return (seconds / 60) + ":" + prependedZero + (seconds % 60);
			} else {
				return "0:" + prependedZero + seconds;
			}
		}
	}
	
	private class StatusPollHandler extends Handler implements Runnable {
		
		public void start() {
			post(this);
		}
		
		public void stop() {
			removeCallbacks(this);
		}
		
		public void run() {
			mConnection.sendCommand(Command.PLAYER_STATUS, null);
			postDelayed(this, App.getPollInterval(NetworkStateBroadcast.isWifiConnected()));
		}
	}
}