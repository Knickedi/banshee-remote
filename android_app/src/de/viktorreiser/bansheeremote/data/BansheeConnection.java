package de.viktorreiser.bansheeremote.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import de.viktorreiser.toolbox.content.NetworkStateBroadcast;
import de.viktorreiser.toolbox.util.L;

import android.os.Handler;

/**
 * Banshee server connector.<br>
 * <br>
 * The main task of this class is to send commands asynchronously to a banshee server and report
 * back the results on the UI thread.<br>
 * <br>
 * First you create an instance with
 * {@link #BansheeConnection(BansheeServer, OnBansheeCommandHandle)}. The command handler callback
 * is called on the UI thread after {@value #MAX_FAIL_COMMANDS} failed requests with {@code null}
 * parameters. After that every new command request will be just ignored so the connection is
 * useless (no need for {@link #close()}). If you decide to stop sending commands and close the
 * connection don't forget to call {@link #close()}!<br>
 * <br>
 * You can send commands with {@link #sendCommand(Command, byte[])}. The parameters can be encoded
 * {@link Command} helper methods (if command has any parameters, otherwise it's just {@code null}).
 * The command callback given in the constructor will be called to report results. {@link Command}
 * methods can be used to decode the results. Default behavior is that pending commands of same type
 * will be just updated instead creating a new request. If you really want to create a new command
 * instead call {@link #sendCommand(Command, byte[], boolean)} with {@code false}. But the default
 * behavior should be fine because this prevents command flooding (multiple clicks of same button or
 * especially when you trigger a seek command based on a seek bar change event).
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeConnection {
	
	// PRIVATE ====================================================================================
	
	private static final int CHECK_CONNECTION_TIMEOUT = 4000;
	
	private static byte [] mBuffer = new byte [1024];
	private static ByteArrayOutputStream mByteOutputStream = new ByteArrayOutputStream();
	
	
	private BansheeServer mServer;
	private volatile int mFailCount = 0;
	private LinkedList<CommandQueue> mCommandQueue = new LinkedList<CommandQueue>();
	private CommandThread mCommandThread = new CommandThread();
	private Handler mCommandHandler = new Handler();
	private OnBansheeCommandHandle mHandleCallback;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Number of failed requests until the connection decides on fail.<br>
	 * <br>
	 * <i>Why?</i> There might always a bad connection or something. It's annoying to lose
	 * connection just because a single failed request so...
	 */
	public static final int MAX_FAIL_COMMANDS = 4;
	
	
	/**
	 * Command constants and helpers for request encoding and response decoding.<br>
	 * <br>
	 * Use a constant for {@link BansheeConnection#sendCommand(Command, byte[])}, encode the request
	 * parameter with the corresponding {@code encode} helper and decode the response with the
	 * corresponding {@code decode} helper.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static enum Command {
		/**
		 * (Set and) get player status.<br>
		 * <br>
		 * A {@code null} parameter just requests the current status (status will be returned after
		 * any type of request).
		 * 
		 * You can bundle status request type in a single request. Just pass the previous encoded
		 * request as parameter to the next one. Some request types are bundled and will override
		 * the previous request parameter which was encoded. See {@link PlayerStatus} for more.
		 */
		PLAYER_STATUS(1, 1000),
		
		/**
		 * Get track data (see {@link SongInfo} how to interpret the results).
		 */
		SONG_INFO(2, 1000),
		
		/**
		 * Get sync database (information).<br>
		 * <br>
		 * This request will return also {@code null} so your handler knows about failed requests
		 * (see {@link SyncDatabase} for more).
		 */
		SYNC_DATABASE(3, 10000),
		
		/**
		 * Get cover.<br>
		 * <br>
		 * {@code null} request will return (if available). You can request a specific cover (see
		 * {@link Cover}). If no cover is available you get a {@code 0} byte.
		 */
		COVER(4, 5000),
		
		/**
		 * Get current playlist.<br>
		 * <br>
		 * The list of track IDs relate to the local database.<br>
		 * You can control how much tracks you want to have returned and from which position the
		 * return should start (see {@link Playlist} for more).
		 */
		PLAYLIST(5, 2000),
		
		PLAYLIST_CONTROL(6, 3000);
		
		private final int mCode;
		private final int mTimeout;
		
		Command(int code, int timeout) {
			mCode = code;
			mTimeout = timeout;
		}
		
		/*@formatter:off*/
		/**
		 * Encode request and decode response.<br>
		 * <br>
		 * <b>Request</b> groups can be combined to one request parameter (see
		 * {@link Command#PLAYER_STATUS}).<br>
		 * <br>
		 * <table border="0" cellspacing="0" cellpadidng="0">
		 * <tr><td>pause</td><td>&nbsp;{@link #encodePause(byte[])}</td></tr>
		 * <tr><td>play</td><td>&nbsp;{@link #encodePlay(byte[])}</td></tr>
		 * <tr><td>next track</td><td>&nbsp;{@link #encodePlayNext(byte[])}</td></tr>
		 * <tr><td>previous track</td><td>&nbsp;{@link #encodePlayPrevious(byte[])}</td></tr>
		 * <tr><td>toggle play / pause</td><td>&nbsp;{@link #encodePlayToggle(byte[])}</td></tr>
		 * </table>
		 * <br>
		 * <table border="0" cellspacing="0" cellpadidng="0">
		 * <tr><td>set repeat mode</td><td>&nbsp;{@link #encodeRepeat(byte[], Repeat)}</td></tr>
		 * <tr><td>toggle repeat on / off</td><td>&nbsp;{@link #encodeRepeatOnOffToggle(byte[])}</td></tr>
		 * <tr><td>toggle between all modes</td><td>&nbsp;{@link #encodeRepeatToggle(byte[])}</td></tr>
		 * </table>
		 * <br>
		 * <table border="0" cellspacing="0" cellpadidng="0">
		 * <tr><td>set shuffle mode</td><td>&nbsp;{@link #encodeShuffle(byte[], Shuffle)}</td></tr>
		 * <tr><td>toggle shuffle on / off</td><td>&nbsp;{@link #encodeRepeatOnOffToggle(byte[])}</td></tr>
		 * <tr><td>toggle shuffle all modes</td><td>&nbsp;{@link #encodeShuffleToggle(byte[])}</td></tr>
		 * </table>
		 * <br>
		 * <table border="0" cellspacing="0" cellpadidng="0">
		 * <tr><td>set volume {@code 1-100}</td><td>&nbsp;{@link #encodeVolume(byte[], int)}</td></tr>
		 * <tr><td>set volume to {@code 0}</td><td>&nbsp;{@link #encodeVolumeMute(byte[])}</td></tr>
		 * <tr><td>volume one step down</td><td>&nbsp;{@link #encodeVolumeDown(byte[])}</td></tr>
		 * <tr><td>volume one step up</td><td>&nbsp;{@link #encodeVolumeUp(byte[])}</td></tr>
		 * </table>
		 * <br>
		 * <table border="0" cellspacing="0" cellpadidng="0">
		 * <tr><td>set seek position in milliseconds<br>{@code 0} is ignored</td>
		 * <td>&nbsp;{@link #encodeSeekPosition(byte[], long)}</td></tr>
		 * </table>
		 * <br>
		 * <b>Response</b> provides some player status information.<br>
		 * <br>
		 * <table border="0" cellspacing="0" cellpadidng="0">
		 * <tr><td>is player playing</td><td>&nbsp;{@link #decodePause(byte[])}</td></tr>
		 * <tr><td>is player paused</td><td>&nbsp;{@link #decodePause(byte[])}</td></tr>
		 * <tr><td>(if both are {@code false} then the player is idle)</td><td></td></tr>
		 * <tr><td>get current repeat mode</td>
		 * <td>&nbsp;{@link #decodeShuffleMode(byte[])}</td></tr>
		 * <tr><td>get current shuffle mode</td>
		 * <td>&nbsp;{@link #decodeRepeatMode(byte[])}</td></tr>
		 * <tr><td>get current volume {@code 0-100}</td>
		 * <td>&nbsp;{@link #decodeVolume(byte[])}</td></tr>
		 * <tr><td>get current track position<br>(in milliseconds)</td>
		 * <td>&nbsp;{@link #decodeSeekPosition(byte[])}</td></tr>
		 * <tr><td>get change flag - {@code 0} means no track is playing<br>
		 * ({@code oldFlag != newFlag == newSong})</td>
		 * <td>&nbsp;{@link #decodeChangeFlag(byte[])}</td></tr>
		 * <tr><td>get track ID (relates to database)</td>
		 * <td>&nbsp;{@link #decodeSongId(byte[])}</td></tr>
		 * </table>
		 * 
		 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		/*@formatter:on*/
		public static class PlayerStatus {
			
			private static byte [] getRequest(byte [] request) {
				return request == null || request.length != 7
						? new byte [] {0, 0, 0, 0, 0, 0, 0} : request;
			}
			
			public static byte [] encodePlayToggle(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x10 | request[0] & 0xf);
				return request;
			}
			
			public static byte [] encodePlay(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x20 | request[0] & 0xf);
				return request;
			}
			
			public static byte [] encodePause(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x30 | request[0] & 0xf);
				return request;
			}
			
			public static byte [] encodePlayNext(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x40 | request[0] & 0xf);
				return request;
			}
			
			public static byte [] encodePlayPrevious(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x50 | request[0] & 0xf);
				return request;
			}
			
			public static byte [] encodeRepeat(byte [] request, Repeat repeat) {
				(request = getRequest(request))[0] = (byte) (repeat.mCode | request[0] & 0xf0);
				return request;
			}
			
			public static byte [] encodeRepeatToggle(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x4 | request[0] & 0xf0);
				return request;
			}
			
			public static byte [] encodeRepeatOnOffToggle(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x5 | request[0] & 0xf0);
				return request;
			}
			
			public static byte [] encodeShuffle(byte [] request, Shuffle shuffle) {
				(request = getRequest(request))[1] = (byte) shuffle.mCode;
				return request;
			}
			
			public static byte [] encodeShuffleToggle(byte [] request) {
				(request = getRequest(request))[1] = (byte) 0x7;
				return request;
			}
			
			public static byte [] encodeShuffleOnOffToggle(byte [] request) {
				(request = getRequest(request))[1] = (byte) 0x8;
				return request;
			}
			
			public static byte [] encodeVolume(byte [] request, int volume) {
				(request = getRequest(request))[2] = (byte) Math.max(0, Math.min(1, volume));
				return request;
			}
			
			public static byte [] encodeVolumeMute(byte [] request) {
				(request = getRequest(request))[2] = (byte) 101;
				return request;
			}
			
			public static byte [] encodeVolumeDown(byte [] request) {
				(request = getRequest(request))[2] = (byte) 102;
				return request;
			}
			
			public static byte [] encodeVolumeUp(byte [] request) {
				(request = getRequest(request))[2] = (byte) 103;
				return request;
			}
			
			public static byte [] encodeSeekPosition(byte [] request, long position) {
				byte [] p = encodeInt(position);
				System.arraycopy(p, 0, request = getRequest(request), 3, p.length);
				return request;
			}
			
			public static boolean decodePause(byte [] response) {
				return (response[0] & 0x80) != 0;
			}
			
			public static boolean decodePlaying(byte [] response) {
				return (response[0] & 0x40) != 0;
			}
			
			public static Repeat decodeRepeatMode(byte [] response) {
				return Repeat.decode((response[0] >>> 4) & 0x3);
			}
			
			public static Shuffle decodeShuffleMode(byte [] response) {
				return Shuffle.decode(response[0] & 0xf);
			}
			
			public static int decodeVolume(byte [] response) {
				return response.length < 2 ? -1 : response[1] & 0xff;
			}
			
			public static long decodeSeekPosition(byte [] response) {
				return response.length < 6 ? -1 : decodeInt(response, 2);
			}
			
			public static int decodeChangeFlag(byte [] response) {
				return response.length < 8 ? -1 : decodeShort(response, 6);
			}
			
			public static long decodeSongId(byte [] response) {
				return response.length < 12 ? -1 : decodeInt(response, 8);
			}
		}
		
		/**
		 * Get track info.<br>
		 * <br>
		 * This command has no parameter. It will return the current track data. Usually you call
		 * this if the status reported a change in it's change flag. {@link SongInfo#decode(byte[])}
		 * will return an {@code Object} array with following data:<br>
		 * <br>
		 * 0 - total length of track in seconds - {@code Integer}<br>
		 * 1 - track title - {@code String}<br>
		 * 2 - artist name - {@code String}<br>
		 * 3 - album title - {@code String}<br>
		 * 4 - genre - {@code String}<br>
		 * 5 - album year - {@code Integer}<br>
		 * 6 - cover ID (e.g. {@code "album-023AB83..."} or empty if there's no cover) -
		 * {@code String}
		 * 
		 * @author Viktor Reiser &lt;<a
		 *         href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class SongInfo {
			
			public static Object [] decode(byte [] response) {
				try {
					Object [] decoded = new Object [7];
					int index = 0;
					Object [] stringData;
					
					decoded[0] = decodeInt(response, index);
					index += 4;
					
					stringData = decodeString(response, index);
					index += (Integer) stringData[0];
					decoded[1] = stringData[1];
					
					stringData = decodeString(response, index);
					index += (Integer) stringData[0];
					decoded[2] = stringData[1];
					
					stringData = decodeString(response, index);
					index += (Integer) stringData[0];
					decoded[3] = stringData[1];
					
					stringData = decodeString(response, index);
					index += (Integer) stringData[0];
					decoded[4] = stringData[1];
					
					decoded[5] = decodeShort(response, index);
					index += 2;
					
					stringData = decodeString(response, index);
					index += (Integer) stringData[0];
					decoded[6] = stringData[1];
					
					return decoded;
				} catch (ArrayIndexOutOfBoundsException e) {
					// this happens for strings if the connection is lost...
					return null;
				}
			}
		}
		
		/**
		 * Synchronize banshee database.<br>
		 * <br>
		 * {@code null} request will give you a {@code 0} byte response!<br>
		 * <br>
		 * Use {@link #encodeFileTimestamp()} and {@link #decodeFileTimestamp(byte[])} to request
		 * the database size in bytes. You will get {@code 0} when there's no database available<br>
		 * <br>
		 * Use {@link #encodeFile()} to get the database as response encoded as byte array. You will
		 * get a single byte wit the value {@code 0} when the database is not available.
		 * 
		 * @author Viktor Reiser &lt;<a
		 *         href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class SyncDatabase {
			
			public static byte [] encodeFileTimestamp() {
				return new byte [] {1};
			}
			
			public static byte [] encodeFile() {
				return new byte [] {2};
			}
			
			public static boolean isFileTimestamp(byte [] params) {
				return params[0] == 1;
			}
			
			public static boolean isFileRequest(byte [] params) {
				return params[0] == 2;
			}
			
			public static long decodeFileTimestamp(byte [] response) {
				return response.length < 4 ? 0 : decodeInt(response, 0);
			}
		}
		
		/**
		 * Get cover.<br>
		 * <br>
		 * Use {@link #encode(String)} to request a specific cover.
		 * 
		 * @author Viktor Reiser &lt;<a
		 *         href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class Cover {
			
			public static byte [] encode(String artId) {
				return encodeString(artId);
			}
			
			public static String getId(byte [] params) {
				return (String) decodeString(params, 0)[1];
			}
		}
		
		/**
		 * Get current playlist track IDs.<br>
		 * <br>
		 * A {@code null} request will return all tracks but this could be slow so you can specify
		 * how much and from which start position you want to have track IDs ({#encode(int, int)}).<br>
		 * {@link #decodeCount(byte[])} will give you the size of the playlist.
		 * {@link #decodeTrackIds(byte[])} will give you the returned IDs.
		 * 
		 * @author Viktor Reiser &lt;<a
		 *         href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class Playlist {
			
			public static byte [] encode(long startPosition, long maxReturns) {
				byte [] params = new byte [8];
				System.arraycopy(encodeInt(maxReturns), 0, params, 0, 4);
				System.arraycopy(encodeInt(startPosition), 0, params, 4, 4);
				return params;
			}
			
			public static byte [] encodeOnCurrent(long startOffset, long maxReturns) {
				byte [] params = new byte [8];
				System.arraycopy(encodeInt(maxReturns), 0, params, 0, 4);
				System.arraycopy(encodeInt(startOffset | 0x80000000), 0, params, 4, 4);
				return params;
			}
			
			public static long getStartPosition(byte [] params) {
				return decodeInt(params, 0) & 0x8fffffff;
			}
			
			public static long [] decodeTrackIds(byte [] response) {
				try {
					int returned = (int) decodeInt(response, 4);
					long [] result = new long [returned];
					
					for (int i = 0; i < returned; i++) {
						result[i] = decodeInt(response, i * 4 + 12);
					}
					
					return result;
				} catch (ArrayIndexOutOfBoundsException e) {
					return new long [0];
				}
			}
			
			public static int decodeCount(byte [] response) {
				return (int) decodeInt(response, 0);
			}
			
			public static int decodeStartPosition(byte [] response) {
				return (int) decodeInt(response, 8);
			}
		}
		
		public static class PlaylistControl {
			
			public static byte [] encodePlay(long trackId) {
				byte [] result = new byte [5];
				result[0] = 1;
				System.arraycopy(encodeInt(trackId), 0, result, 1, 4);
				return result;
			}
			
			// TODO enqueue tracks
//			public static byte [] endcode(long [] tracksIds, long [] artistIds, long [] albumIds) {
//				
//			}
		}
		
		private static byte [] encodeShort(int value) {
			return new byte [] {(byte) value, (byte) (value >> 8)};
		}
		
		private static byte [] encodeInt(long value) {
			return new byte [] {(byte) value, (byte) (value >> 8), (byte) (value >> 16),
					(byte) (value >> 24)};
		}
		
		private static int decodeShort(byte [] response, int position) {
			return (response[position] & 0xff) + ((response[position + 1] & 0xff) << 8);
		}
		
		private static long decodeInt(byte [] response, int position) {
			return (response[position] & 0xff)
					+ ((response[position + 1] & 0xff) << 8)
					+ ((response[position + 2] & 0xff) << 16)
					+ ((long) (response[position + 3] & 0xff) << 24);
		}
		
		private static byte [] encodeString(String s) {
			byte [] sBytes = s.getBytes();
			byte [] length = encodeShort(sBytes.length);
			
			byte [] result = new byte [length.length + sBytes.length];
			System.arraycopy(length, 0, result, 0, length.length);
			System.arraycopy(sBytes, 0, result, length.length, sBytes.length);
			return result;
		}
		
		private static Object [] decodeString(byte [] response, int position) {
			int length = decodeShort(response, position);
			String string = length < 0 ? "" : new String(response, position + 2, length);
			
			return new Object [] {2 + length, string};
		}
	}
	
	/**
	 * Banshee shuffle modes.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static enum Shuffle {
		UNKNOWN(0),
		OFF(1),
		SONG(2),
		ARTIST(3),
		ALBUM(4),
		RATING(5),
		SCORE(6);
		
		private final int mCode;
		
		Shuffle(int code) {
			mCode = code;
		}
		
		static Shuffle decode(int code) {
			for (Shuffle s : values()) {
				if (s.mCode == code) {
					return s;
				}
			}
			
			return UNKNOWN;
		}
	}
	
	/**
	 * Banshee repeat modes.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static enum Repeat {
		UNKNOWN(0),
		OFF(1),
		SINGLE(2),
		ALL(3);
		
		private final int mCode;
		
		Repeat(int code) {
			mCode = code;
		}
		
		static Repeat decode(int code) {
			for (Repeat r : values()) {
				if (r.mCode == code) {
					return r;
				}
			}
			
			return UNKNOWN;
		}
	}
	
	/**
	 * Callback interface for handled commands.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static interface OnBansheeCommandHandle {
		
		/**
		 * Callback interface for handled commands.
		 * 
		 * @param command
		 *            the command requested
		 * @param params
		 *            encoded request parameters
		 * @param result
		 *            response which should be decoded
		 */
		public void onBansheeCommandHandled(Command command, byte [] params, byte [] result);
	}
	
	
	/**
	 * Create a connection (object) which will communicate with the banshee server.
	 * 
	 * @param server
	 *            data of banshee server
	 * @param handleCallback
	 *            callback which will be issued when a command is handled
	 */
	public BansheeConnection(BansheeServer server, OnBansheeCommandHandle handleCallback) {
		if (server == null || handleCallback == null) {
			throw new NullPointerException();
		}
		
		mServer = server;
		mHandleCallback = handleCallback;
		mCommandThread.start();
	}
	
	/**
	 * Update the callback which was given in the constructor.
	 * 
	 * @param callback
	 *            command handle callback
	 */
	public void updateHandleCallback(OnBansheeCommandHandle callback) {
		mHandleCallback = callback;
	}
	
	/**
	 * Get current command handler.
	 * 
	 * @return current command handler.
	 */
	public OnBansheeCommandHandle getHandleCallback() {
		return mHandleCallback;
	}
	
	/**
	 * Put command to the request queue.
	 * 
	 * @param command
	 *            request command
	 * @param params
	 *            encoded request parameters
	 */
	public void sendCommand(Command command, byte [] params) {
		sendCommand(command, params, true);
	}
	
	/**
	 * Put command to the request queue.
	 * 
	 * @param command
	 *            request command
	 * @param params
	 *            encoded request parameters
	 * @param updatePendingRequest
	 *            {@code true} will update the last request of the same kind instead putting a new
	 *            request into the queue, {@code false} will just add a new request into the queue
	 */
	public void sendCommand(Command command, byte [] params, boolean updatePendingRequest) {
		if (command == null) {
			throw new NullPointerException();
		}
		
		synchronized (mCommandQueue) {
			if (!mCommandThread.run) {
				return;
			}
			
			boolean commandUpdated = false;
			
			if (updatePendingRequest) {
				for (CommandQueue q : mCommandQueue) {
					if (q.command == command) {
						commandUpdated = true;
						q.params = params;
						break;
					}
				}
			}
			
			if (!commandUpdated) {
				int i = 0;
				
				// cover commands has less priority
				if (command != Command.COVER) {
					for (Iterator<CommandQueue> it = mCommandQueue.iterator(); it.hasNext();) {
						if (it.next().command != Command.COVER) {
							break;
						}
						
						i++;
					}
				}
				
				CommandQueue queue = new CommandQueue();
				queue.command = command;
				queue.params = params;
				mCommandQueue.add(i, queue);
			}
		}
		
		synchronized (mCommandThread) {
			mCommandThread.interrupt();
		}
	}
	
	/**
	 * Get banshee server of connection.
	 * 
	 * @return banshee server of this connection
	 */
	public BansheeServer getServer() {
		return mServer;
	}
	
	/**
	 * Stop background request thread.
	 */
	public void close() {
		mCommandThread.run = false;
		mCommandThread.interrupt();
	}
	
	
	/**
	 * This will send a synchronous (!) request to the given banshee server to test its
	 * availability.
	 * 
	 * @param server
	 *            banshee server to test
	 * 
	 * @return {@code true} if the given banshee server was available
	 */
	public static int checkConnection(BansheeServer server) {
		// request code 0 is a test request which does nothing
		byte [] result = sendRequest(server, 0, null, CHECK_CONNECTION_TIMEOUT);
		return result != null && result.length != 0 ? result[0] : -1;
	}
	
	// OVERRIDDEN =================================================================================
	
	/**
	 * Kill request thread if we forgot to call {@link #close()}.
	 */
	@Override
	protected void finalize() {
		mCommandThread.run = false;
	}
	
	// PRIVATE ====================================================================================
	
	/**
	 * Send request to server and get the response.
	 * 
	 * @param server
	 *            banshee server to which the request will be sent
	 * @param requestCode
	 *            request code is taken from {@link Command} constant
	 * @param params
	 *            encoded parameters
	 * 
	 * @return response as byte array
	 */
	private synchronized static byte [] sendRequest(BansheeServer server, int requestCode,
			byte [] params, int timeout) {
		byte [] result = null;
		byte [] request;
		Socket socket = null;
		OutputStream os = null;
		InputStream is = null;
		int read = -1;
		
		if (params == null) {
			request = new byte [3];
		} else {
			request = new byte [3 + params.length];
			System.arraycopy(params, 0, request, 3, params.length);
		}
		
		request[0] = (byte) (requestCode);
		System.arraycopy(Command.encodeShort(server.getPasswordId()), 0, request, 1, 2);
		
		try {
			socket = new Socket(server.getHost(), server.getPort());
			socket.setSoTimeout(timeout);
			os = socket.getOutputStream();
			is = socket.getInputStream();
			os.write(request, 0, request.length);
			
			mByteOutputStream.reset();
			
			while ((read = is.read(mBuffer, 0, mBuffer.length)) != -1) {
				mByteOutputStream.write(mBuffer, 0, read);
			}
			
			result = mByteOutputStream.toByteArray();
		} catch (UnknownHostException e) {
		} catch (IOException e) {
		} finally {
			try {
				is.close();
			} catch (Exception e) {
			}
			
			try {
				os.close();
			} catch (Exception e) {
			}
			
			try {
				socket.close();
			} catch (Exception e) {
			}
		}
		
		return result;
	}
	
	
	/**
	 * Object which represents a pending request.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private static class CommandQueue {
		public Command command;
		public byte [] params;
	}
	
	private void logRequest(CommandQueue queue, boolean success, byte [] response) {
		if (success && L.isV() || !success && L.isW()) {
			StringBuilder s = new StringBuilder();
			s.append(success ? "Success " : "Fail ");
			s.append(queue.command.toString());
			s.append(" (pass ");
			s.append(mServer.getPasswordId());
			s.append(")");
			
			if (queue.params == null) {
				s.append(" no parameters");
			} else {
				s.append(" [ ");
				
				for (int i = 0; i < Math.min(queue.params.length, 20); i++) {
					s.append(Integer.toHexString(queue.params[i] & 0xff));
					s.append(" ");
				}
				
				if (queue.params.length > 20) {
					s.append("... ");
				}
				
				s.append("]");
			}
			
			if (response != null) {
				s.append(" [ ");
				
				for (int i = 0; i < Math.min(response.length, 20); i++) {
					s.append(Integer.toHexString(response[i] & 0xff));
					s.append(" ");
				}
				
				if (response.length > 20) {
					s.append("... ");
				}
				
				s.append("]");
			}
			
			if (success) {
				L.v(s.toString());
			} else {
				L.w(s.toString());
			}
		}
	}
	
	
	/**
	 * Thread which takes request from queue and delegates the response to the connection callback.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private class CommandThread extends Thread {
		
		public volatile boolean run = true;
		
		
		@Override
		public void run() {
			while (run) {
				CommandQueue queue = null;
				
				synchronized (mCommandQueue) {
					try {
						queue = mCommandQueue.removeLast();
					} catch (NoSuchElementException e) {
					}
				}
				
				if (queue == null) {
					try {
						synchronized (this) {
							wait();
						}
					} catch (InterruptedException e) {
					}
				} else {
					byte [] result = sendRequest(mServer, queue.command.mCode, queue.params,
							queue.command.mTimeout
								* (NetworkStateBroadcast.isMobileConnected() ? 2 : 1));
					
					if (result == null || result.length == 0) {
						handleFail(queue);
					} else {
						handleSuccess(queue, result);
					}
				}
			}
		}
		
		private void handleFail(final CommandQueue queue) {
			logRequest(queue, false, null);
			
			if (queue.command == Command.SYNC_DATABASE || queue.command == Command.PLAYLIST) {
				mCommandHandler.post(new Runnable() {
					@Override
					public void run() {
						mHandleCallback.onBansheeCommandHandled(
								queue.command, queue.params, null);
					}
				});
			}
			
			mFailCount++;
			
			if (mFailCount >= MAX_FAIL_COMMANDS) {
				synchronized (mCommandQueue) {
					run = false;
					mCommandQueue.clear();
				}
				
				mCommandHandler.post(new Runnable() {
					@Override
					public void run() {
						mHandleCallback.onBansheeCommandHandled(null, null, null);
					}
				});
			}
		}
		
		private void handleSuccess(final CommandQueue queue, final byte [] result) {
			logRequest(queue, true, result);
			
			mFailCount = 0;
			
			mCommandHandler.post(new Runnable() {
				@Override
				public void run() {
					mHandleCallback.onBansheeCommandHandled(
							queue.command, queue.params, result);
				}
			});
		}
	}
}
