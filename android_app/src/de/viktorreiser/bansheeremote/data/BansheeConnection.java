package de.viktorreiser.bansheeremote.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Set;

import android.os.Handler;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;
import de.viktorreiser.toolbox.util.L;

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
 * with the {@link Command} helper methods (if command has any parameters, otherwise it's just {@code null}).
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
	
	private static final int CHECK_CONNECTION_TIMEOUT = 3000;
	
	private static byte [] mBuffer = new byte [1024];
	private static ByteArrayOutputStream mByteOutputStream = new ByteArrayOutputStream();
	
	
	private BansheeServer mServer;
	private volatile int mFailCount = 0;
	private LinkedList<CommandQueue> mCommandQueue = new LinkedList<CommandQueue>();
	private CommandThread mCommandThread = new CommandThread();
	private Handler mCommandHandler = new Handler();
	private Set<String> mPendingCoverRequests = new HashSet<String>();
	private OnBansheeCommandHandle mHandleCallback;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Number of failed requests until the connection decides on fail.<br>
	 * <br>
	 * <i>Why?</i> There might always a bad connection or something. It's annoying to lose
	 * connection just because of a single failed request so...
	 */
	public static final int MAX_FAIL_COMMANDS = 4;
	
	
	/**
	 * Command constants and helpers for request encoding and response decoding.<br>
	 * <br>
	 * Use a constant for {@link BansheeConnection#sendCommand(Command, byte[])}, encode the request
	 * parameter with the corresponding {@code encode} helpers and decode the response with the
	 * corresponding {@code decode} helpers.<br>
	 * <br>
	 * You should check the
	 * <a href="https://github.com/Knickedi/banshee-remote/wiki/Banshee-RemoteListener-Extension-API-Documentation"
	 * >API documentation</a> if you're not sure about the returned values. But the encoding and
	 * decoding is already wrapping the ugliest part of the communication.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static enum Command {
		
		PLAYER_STATUS(1, 1000, 3000),
		SONG_INFO(2, 3000, 6000),
		SYNC_DATABASE(3, 10000, 15000),
		COVER(4, 5000, 10000),
		PLAYLIST(5, 10000, 15000);
		
		private final int mCode;
		private final int mTimeoutWifi;
		private final int mTimeoutMobile;
		
		Command(int code, int timeoutWifi, int timeoutMobile) {
			mCode = code;
			mTimeoutWifi = timeoutWifi;
			mTimeoutMobile = timeoutMobile;
		}
		
		/**
		 * Helper to handle player status requests.<br>
		 * <br>
		 * Request parameters can be chained. You will notice that some {@code encode} methods has
		 * the same noun which is following. Chaining those will eliminate the previous call.
		 * 
		 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class PlayerStatus {
			
			private static byte [] getRequest(byte [] request) {
				return request == null || request.length != 7
						? new byte [] {0, 0, 0, 0, 0, 0, 0} : request;
			}
			
			/**
			 * Request a toggle of play / pause.
			 */
			public static byte [] encodePlayToggle(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x10 | request[0] & 0xf);
				return request;
			}
			
			/**
			 * Request to start playing.
			 */
			public static byte [] encodePlay(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x20 | request[0] & 0xf);
				return request;
			}
			
			/**
			 * Request to pause.
			 */
			public static byte [] encodePause(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x30 | request[0] & 0xf);
				return request;
			}
			
			/**
			 * Request to play next track.
			 */
			public static byte [] encodePlayNext(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x40 | request[0] & 0xf);
				return request;
			}
			
			/**
			 * Request to play previous track.
			 */
			public static byte [] encodePlayPrevious(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x50 | request[0] & 0xf);
				return request;
			}
			
			/**
			 * Request a certain repeat mode (unknown is ignored).
			 */
			public static byte [] encodeRepeat(byte [] request, Repeat repeat) {
				(request = getRequest(request))[0] = (byte) (repeat.mCode | request[0] & 0xf0);
				return request;
			}
			
			/**
			 * Request to toggle to next repeat mode.
			 */
			public static byte [] encodeRepeatToggle(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x4 | request[0] & 0xf0);
				return request;
			}
			
			/**
			 * Request to toggle repeat mode on / off.
			 */
			public static byte [] encodeRepeatOnOffToggle(byte [] request) {
				(request = getRequest(request))[0] = (byte) (0x5 | request[0] & 0xf0);
				return request;
			}
			
			/**
			 * Request a certain shuffle mode.
			 */
			public static byte [] encodeShuffle(byte [] request, Shuffle shuffle) {
				(request = getRequest(request))[1] = (byte) shuffle.mCode;
				return request;
			}
			
			/**
			 * Request to toggle to next shuffle mode.
			 */
			public static byte [] encodeShuffleToggle(byte [] request) {
				(request = getRequest(request))[1] = (byte) 0x7;
				return request;
			}
			
			/**
			 * Request to toggle shuffle on / off.
			 */
			public static byte [] encodeShuffleOnOffToggle(byte [] request) {
				(request = getRequest(request))[1] = (byte) 0x8;
				return request;
			}
			
			/**
			 * Request to set volume {@code 1 - 100}, {@code 0} is ignored.
			 */
			public static byte [] encodeVolume(byte [] request, int volume) {
				(request = getRequest(request))[2] = (byte) Math.max(0, Math.min(1, volume));
				return request;
			}
			
			/**
			 * Request to mute (volume {@code 0}).
			 */
			public static byte [] encodeVolumeMute(byte [] request) {
				(request = getRequest(request))[2] = (byte) 101;
				return request;
			}
			
			/**
			 * Request a volume step down.
			 */
			public static byte [] encodeVolumeDown(byte [] request) {
				(request = getRequest(request))[2] = (byte) 102;
				return request;
			}
			
			/**
			 * Request a volume step up.
			 */
			public static byte [] encodeVolumeUp(byte [] request) {
				(request = getRequest(request))[2] = (byte) 103;
				return request;
			}
			
			/**
			 * Request a to seek in track to a certain position (in milliseconds).
			 */
			public static byte [] encodeSeekPosition(byte [] request, long position) {
				byte [] p = encodeInt(position);
				System.arraycopy(p, 0, request = getRequest(request), 3, p.length);
				return request;
			}
			
			/**
			 * Is player paused?
			 */
			public static boolean decodePause(byte [] response) {
				return (response[0] & 0x80) != 0;
			}
			
			/**
			 * Is player playing?
			 */
			public static boolean decodePlaying(byte [] response) {
				return (response[0] & 0x40) != 0;
			}
			
			/**
			 * Get current repeat mode.
			 */
			public static Repeat decodeRepeatMode(byte [] response) {
				return Repeat.decode((response[0] >>> 4) & 0x3);
			}
			
			/**
			 * Get current shuffle mode.
			 */
			public static Shuffle decodeShuffleMode(byte [] response) {
				return Shuffle.decode(response[0] & 0xf);
			}
			
			/**
			 * Get current volume {@code 0 - 100}.
			 */
			public static int decodeVolume(byte [] response) {
				return response.length < 2 ? -1 : response[1] & 0xff;
			}
			
			/**
			 * Get current track seek position (in milliseconds).
			 */
			public static long decodeSeekPosition(byte [] response) {
				return response.length < 6 ? -1 : decodeInt(response, 2);
			}
			
			/**
			 * Get change flag (will be different if another track is playing now).
			 */
			public static int decodeChangeFlag(byte [] response) {
				return response.length < 8 ? -1 : decodeShort(response, 6);
			}
			
			/**
			 * Get Id of the track which is playing now (relates to the synchonized database).
			 */
			public static long decodeSongId(byte [] response) {
				return response.length < 12 ? -1 : decodeInt(response, 8);
			}
		}
		
		/**
		 * Helper to handle current track information requests.
		 * 
		 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class SongInfo {
			
			/**
			 * Get track info ({@code null} on fail).<br>
			 * <br>
			 * 0 - total length of track in seconds - {@code Integer}<br>
			 * 1 - track title - {@code String}<br>
			 * 2 - artist name - {@code String}<br>
			 * 3 - album title - {@code String}<br>
			 * 4 - genre - {@code String}<br>
			 * 5 - album year - {@code Integer}<br>
			 * 6 - cover ID - (e.g. {@code "album-823AB83..."} or empty if there's no cover) -
			 * {@code String}
			 * 7 - track rating - {@code Byte}
			 */
			public static Object [] decode(byte [] response) {
				try {
					Object [] decoded = new Object [8];
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
					
					decoded[7] = response[index];
					
					return decoded;
				} catch (ArrayIndexOutOfBoundsException e) {
					// this happens for strings if the connection is lost...
					return null;
				}
			}
		}
		
		/**
		 * Helper to handle database synchronization.
		 */
		public static class SyncDatabase {
			
			/**
			 * Request current timestamp of database (to compare it with the local one).
			 */
			public static byte [] encodeFileTimestamp() {
				return new byte [] {1};
			}
			
			/**
			 * Request the database file itself.
			 */
			public static byte [] encodeFile() {
				return new byte [] {2};
			}
			
			/**
			 * Request database re-compression.
			 */
			public static byte [] encodeCompress() {
				return new byte [] {3};
			}
			
			/**
			 * Were we requesting the timestamp with these parameters?
			 */
			public static boolean isFileTimestamp(byte [] params) {
				return params[0] == 1;
			}
			
			/**
			 * Were we requesting the database file with these parameters?
			 */
			public static boolean isFileRequest(byte [] params) {
				return params[0] == 2;
			}
			
			/**
			 * Were we requesting database re-compression?
			 */
			public static boolean isCompression(byte [] params) {
				return params[0] == 3;
			}
			
			/**
			 * Get the returned timestamp.
			 */
			public static int decodeFileTimestamp(byte [] response) {
				return response.length < 4 ? 0 : (int) decodeInt(response, 0);
			}
		}
		
		/**
		 * Helper for handling cover requests.
		 * 
		 * @author Viktor Reiser &lt;<a
		 *         href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class Cover {
			
			/**
			 * Request a certain cover.
			 */
			public static byte [] encode(String artId) {
				return encodeString(artId);
			}
			
			/**
			 * Which cover ID were we requesting?
			 */
			public static String getId(byte [] params) {
				return (String) decodeString(params, 0)[1];
			}
		}
		
		/**
		 * Helper for handling playlist requests.
		 * 
		 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
		 */
		public static class Playlist {
			
			/**
			 * Request current playlist names.
			 */
			public static byte [] encodeNames() {
				return new byte [] {1};
			}
			
			/**
			 * Were we requesting the playlist names.
			 */
			public static boolean isNames(byte [] params) {
				return params[0] == 1;
			}
			
			/**
			 * Get current active playlist ID from playlist name request.
			 */
			public static int decodeActivePlaylist(byte [] response) {
				return decodeShort(response, 0);
			}
			
			/**
			 * Decode the playlist names.<br>
			 * <br>
			 * This array will contain all available playlists. One entry contains this data:
			 * <ul>
			 * <li>0 - count of tracks in playlist</li>
			 * <li>1 - ID of playlist, the remote playlist is always {@code 1}, don't rely on other
			 * IDs, their meant to be used in real time and can change easily</li>
			 * <li>2 - name of playlist as given on the server</li>
			 * </ul>
			 */
			public static Object [][] decodeNames(byte [] response) {
				int count = decodeShort(response, 2);
				Object [][] playlists = new Object [count][];
				int index = 4;
				
				for (int i = 0; i < playlists.length; i++) {
					playlists[i] = new Object[3];
					playlists[i][0] = (int) decodeInt(response, index);
					index += 4;
					playlists[i][1] = decodeShort(response, index);
					index += 2;
					Object [] s = decodeString(response, index);
					index += (Integer) s[0];
					playlists[i][2] = s[1];
				}
				
				return playlists;
			}
			
			
			/**
			 * Request tracks from a playlist.
			 * 
			 * @param playlistId
			 *          ID of playlist to request the tracks
			 * @param startPosition
			 *          position from which the track should be returned
			 * @param maxReturns
			 *          amount of tracks to return
			 */
			public static byte [] encodeTracks(
					int playlistId, long startPosition, long maxReturns) {
				byte [] params = new byte [11];
				params[0] = 2;
				System.arraycopy(encodeShort(playlistId), 0, params, 1, 2);
				System.arraycopy(encodeInt(maxReturns), 0, params, 3, 4);
				System.arraycopy(encodeInt(startPosition), 0, params, 7, 4);
				return params;
			}
			
			/**
			 * Request tracks from a playlist (try to get it from the current played track).
			 * 
			 * @param playlistId
			 *          ID of playlist to request the tracks
			 * @param startOffset
			 *          how much tracks should be returned before the currently played track
			 * @param maxReturns
			 *          amount of tracks to return
			 */
			public static byte [] encodeTracksOnStart(
					int playlistId, long startOffset, long maxReturns) {
				return encodeTracks(playlistId, startOffset | 0x80000000, maxReturns);
			}
			
			/**
			 * Where we requesting tracks from a playlist.
			 */
			public static boolean isTracks(byte [] params) {
				return params[0] == 2;
			}
			
			/**
			 * Get the start position we were requesting for the tracks.
			 */
			public static long getTrackStartPosition(byte [] params) {
				return decodeInt(params, 7) & 0x7fffffff;
			}
			
			/**
			 * Get the returned tracks from playlist track request.
			 */
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
			
			/**
			 * Get amount of tracks in playlist from track playlist request.
			 */
			public static int decodeTrackCount(byte [] response) {
				return (int) decodeInt(response, 0);
			}
			
			/**
			 * Get the start track position from which the track playlist request is returning IDs.
			 */
			public static int decodeStartPosition(byte [] response) {
				return (int) decodeInt(response, 8);
			}
			
			
			/**
			 * Request to play a certain track.
			 */
			public static byte [] encodePlayTrack(long trackId) {
				return encodePlayTrack(0, trackId);
			}
			
			/**
			 * Request to play a certain track from a playlist
			 * (will be played anyway if playlist is not valid).
			 */
			public static byte [] encodePlayTrack(int playlist, long trackId) {
				byte [] result = new byte [7];
				result[0] = 3;
				System.arraycopy(encodeShort(playlist), 0, result, 1, 2);
				System.arraycopy(encodeInt(trackId), 0, result, 3, 4);
				return result;
			}

			/**
			 * Were we requesting to play a track?
			 */
			public static boolean isPlayTrack(byte [] params) {
				return params[0] == 3;
			}
			
			/**
			 * Get play track request status.<br>
			 * <br>
			 * 0 track not found - 1 playing - 2 playing and is in correct playlist.
			 */
			public static int decodePlayTrackStatus(byte [] response) {
				return response[0];
			}
			
			public static enum Modification {
				ADD_TRACK(4),
				ADD_ARTIST(5),
				ADD_ALBUM(6),
				REMOVE_TRACK(7),
				REMOVE_ARTIST(8),
				REMOVE_ALBUM(9);
				
				private final byte request;
				
				Modification(int request) {
					this.request = (byte) request;
				}
			}
			
			/**
			 * Add track, artist or album to playlist.
			 * 
			 * @param playlistId
			 *           ID of playlist
			 * @param mod
			 *           {@link Modification#ADD_TRACK}, {@link Modification#ADD_ARTIST}
			 *           or {@link Modification#ADD_ALBUM}
			 * @param id
			 *           ID of track, artist or album
			 * @param allowTwice
			 *           is it allowed that tracks which already are in the playlist are added
			 *           twice
			 */
			public static byte [] encodeAdd(int playlistId, Modification mod, long id,
					boolean allowTwice) {
				if (mod != Modification.ADD_TRACK && mod != Modification.ADD_ARTIST
						&& mod != Modification.ADD_ALBUM) {
					throw new IllegalArgumentException("modification is not an add constant");
				}
				
				byte [] result = new byte [8];
				result[0] = mod.request;
				result[1] = (byte) (allowTwice ? 1 : 0);
				System.arraycopy(encodeShort(playlistId), 0, result, 2, 2);
				System.arraycopy(encodeInt(id), 0, result, 4, 4);
				return result;
			}
			
			/**
			 * Remove track, artist or album from playlist.
			 * 
			 * @param playlistId
			 *           ID of playlist
			 * @param mod
			 *           {@link Modification#REMOVE_TRACK}, {@link Modification#REMOVE_ARTIST}
			 *           or {@link Modification#REMOVE_ALBUM}
			 * @param id
			 *           ID of track, artist or album
			 */
			public static byte [] encodeRemove(int playlistId, Modification mod, long id) {
				if (mod != Modification.REMOVE_TRACK && mod != Modification.REMOVE_ARTIST
						&& mod != Modification.REMOVE_ALBUM) {
					throw new IllegalArgumentException("modification is not an remove constant");
				}
				
				byte [] result = new byte [7];
				result[0] = mod.request;
				System.arraycopy(encodeShort(playlistId), 0, result, 1, 2);
				System.arraycopy(encodeInt(id), 0, result, 3, 4);
				return result;
			}
			
			/**
			 * Were we requesting add or remove from playlist?
			 */
			public static boolean isAddOrRemove(byte [] params) {
				return params[0] >= 4 && params[0] <= 9;
			}
			
			/**
			 * Get type of add or remove request.
			 * 
			 * @return {@code null} if request was not a add or remove request
			 */
			public static Modification getAddOrRemove(byte [] params) {
				for (Modification m : Modification.values()) {
					if (m.request == params[0]) {
						return m;
					}
				}
				
				return null;
			}
			
			/**
			 * Were we requesting to add a track, artist or album?
			 */
			public static boolean isAdd(byte [] params) {
				switch (getAddOrRemove(params)) {
				case REMOVE_TRACK:
				case REMOVE_ARTIST:
				case REMOVE_ALBUM:
					return false;
				
				default:
					return true;
				}
			}
			
			/**
			 * Get ID of playlist specified in the add or remove request.
			 */
			public static int getAddOrRemovePlaylist(byte [] params) {
				return decodeShort(params, isAdd(params) ? 2 : 1);
			}
			
			/**
			 * Get specified ID in add or remove request.
			 */
			public static long getAddOrRemoveId(byte [] params) {
				return decodeInt(params, isAdd(params) ? 4 : 3);
			}
			
			/**
			 * Get amount of added or removed tracks.
			 * 
			 * @return negative if tracks were removed
			 */
			public static int decodeAddOrRemoveCount(byte [] response) {
				return decodeShort(response, 0);
			}
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
		
		if (command == Command.COVER && params.length < 2) {
			L.d("dismissed empty cover request");
			return;
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
					for (CommandQueue q : mCommandQueue) {
						if (q.command != Command.COVER) {
							break;
						}
						
						i++;
					}
				} else {
					String coverId = Command.Cover.getId(params);
					
					if (mPendingCoverRequests.contains(coverId)) {
						return;
					} else {
						mPendingCoverRequests.add(coverId);
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
	 * @return {@code -1 =} not reachable - {@code 0 = } reachable but wrong password ID -
	 *         {@code 1 =} access granted
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
	 * @param timeout
	 *            timeout for request in miliseconds
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
							NetworkStateBroadcast.isMobileConnected()
								? queue.command.mTimeoutWifi : queue.command.mTimeoutMobile);
					
					if (result == null || result.length == 0) {
						handleFail(queue);
					} else {
						handleSuccess(queue, result);
					}
					
					if (queue.command == Command.COVER && result == null) {
						mPendingCoverRequests.remove(Command.Cover.getId(queue.params));
					}
				}
			}
		}
		
		private void handleFail(final CommandQueue queue) {
			logRequest(queue, false, null);
			
			mCommandHandler.post(new Runnable() {
				@Override
				public void run() {
					mHandleCallback.onBansheeCommandHandled(
							queue.command, queue.params, null);
				}
			});
			
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
			
			if (queue.command == Command.COVER && result != null && result.length > 2) {
				CoverCache.addCover(Command.Cover.getId(queue.params), result);
			}
			
			mFailCount = 0;
			
			mCommandHandler.post(new Runnable() {
				@Override
				public void run() {
					// this is happening in some cases (bug report)
					if (mHandleCallback != null) {
						mHandleCallback.onBansheeCommandHandled(
								queue.command, queue.params, result);
					}
				}
			});
		}
	}
}
