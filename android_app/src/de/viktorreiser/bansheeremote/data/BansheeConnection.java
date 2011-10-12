package de.viktorreiser.bansheeremote.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import android.os.Handler;

/**
 * Banshee server connector.<br>
 * <br>
 * The main task of this class is to send commands asynchronously to a banshee server and report
 * back the results on the UI thread.<br>
 * <br>
 * First you create an instance with
 * {@link #BansheeConnection(BansheeServer, OnBansheeCommandHandled)}. The command handler callback
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
	
	private static byte [] mBuffer = new byte [1024];
	private static ByteArrayOutputStream mByteOutputStream = new ByteArrayOutputStream();
	
	
	private BansheeServer mServer;
	private volatile int mFailCount = 0;
	private LinkedList<CommandQueue> mCommandQueue = new LinkedList<CommandQueue>();
	private CommandThread mCommandThread = new CommandThread();
	private Handler mCommandHandler = new Handler();
	private OnBansheeCommandHandled mHandleCallback;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Number of failed requests until the connection decides on fail.<br>
	 * <br>
	 * <i>Why?</i> There might always a bad connection or something. It's annoying to lose
	 * connection just because a single failed request so...
	 */
	public static final int MAX_FAIL_COMMANDS = 2;
	
	
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
		 * A {@code null} parameter just requests the current status
		 */
		PLAYER_STATUS(1),
		
		/**
		 * 
		 */
		SONG_INFO(2),
		SYNC_DATABASE(3);
		
		private final int mCode;
		
		Command(int code) {
			mCode = code;
		}
		
		public static class PlayerStatus {
			
			public static byte [] getRequest(byte [] request) {
				return request.length != 5 ? new byte [] {0, 0, 0, 0, 0} : request;
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
				return response[1] & 0xff;
			}
			
			public static int decodeSeekPosition(byte [] response) {
				return decodeShort(response, 2);
			}
			
			public static int decodeChangeFlag(byte [] response) {
				return decodeShort(response, 4);
			}
			
			public static long decodeSongId(byte [] response) {
				return decodeInt(response, 6);
			}
		}
		
		public static class SongInfo {
			
			public static Object [] decode(byte [] response) {
				Object [] decoded = new Object [7];
				int index = 0;
				Object [] stringData;
				
				decoded[0] = decodeShort(response, index);
				index += 2;
				
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
			}
		}
		
		public static class SyncDatabase {
			
			public static byte [] encodeFileSize() {
				return new byte [] {1};
			}
			
			public static byte [] encodeFile() {
				return new byte [] {2};
			}
			
			public static boolean isFileSizeRequest(byte [] params) {
				return params[0] == 1;
			}
			
			public static boolean isFileRequest(byte [] params) {
				return params[0] == 2;
			}
			
			public static long decodeFileSize(byte [] response) {
				return decodeInt(response, 0);
			}
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
		
		private static Object [] decodeString(byte [] response, int position) {
			int length = decodeShort(response, position);
			String string = length < 0 ? "" : new String(response, position + 2, length);
			
			return new Object [] {2 + length, string};
		}
	}
	
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
	
	public static interface OnBansheeCommandHandled {
		
		public void onBansheeCommandHandled(Command command, byte [] params, byte [] result);
	}
	
	
	public BansheeConnection(BansheeServer server, OnBansheeCommandHandled handleCallback) {
		if (server == null || handleCallback == null) {
			throw new NullPointerException();
		}
		
		mServer = server;
		mHandleCallback = handleCallback;
		mCommandThread.start();
	}
	
	public void updateHandleCallback(OnBansheeCommandHandled callback) {
		mHandleCallback = callback;
	}
	
	public void sendCommand(Command command, byte [] params) {
		sendCommand(command, params, true);
	}
	
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
						q.command = command;
						q.params = params;
						break;
					}
				}
			}
			
			if (!commandUpdated) {
				CommandQueue queue = new CommandQueue();
				queue.command = command;
				queue.params = params;
				mCommandQueue.add(0, queue);
			}
		}
		
		synchronized (mCommandThread) {
			mCommandThread.interrupt();
		}
	}
	
	public BansheeServer getServer() {
		return mServer;
	}
	
	public void close() {
		mCommandThread.run = false;
		mCommandThread.interrupt();
	}
	
	
	public static boolean checkConnection(BansheeServer server) {
		// request code 0 is a test request which does nothing
		byte [] result = sendRequest(server, 0, null);
		return result != null && result.length != 0;
	}
	
	// OVERRIDDEN =================================================================================
	
	@Override
	protected void finalize() {
		mCommandThread.run = false;
	}
	
	// PRIVATE ====================================================================================
	
	private synchronized static byte [] sendRequest(BansheeServer server, int requestCode,
			byte [] params) {
		byte [] result = null;
		byte [] request;
		Socket socket = null;
		OutputStream os = null;
		InputStream is = null;
		int read = -1;
		
		if (params == null) {
			request = new byte [1];
		} else {
			request = new byte [1 + params.length];
			System.arraycopy(params, 0, request, 1, params.length);
		}
		
		request[0] = (byte) (requestCode);
		
		try {
			socket = new Socket(server.getHost(), server.getPort());
			socket.setSoTimeout(2000);
			socket.setSoLinger(true, 2000);
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
	
	
	private static class CommandQueue {
		public Command command;
		public byte [] params;
	}
	
	private class CommandThread extends Thread {
		
		public volatile boolean run = true;
		
		
		@Override
		public void run() {
			while (run) {
				CommandQueue q = null;
				
				synchronized (mCommandQueue) {
					try {
						q = mCommandQueue.removeLast();
					} catch (NoSuchElementException e) {
					}
				}
				
				if (q == null) {
					try {
						synchronized (this) {
							wait();
						}
					} catch (InterruptedException e) {
					}
				} else {
					final CommandQueue queue = q;
					final byte [] result = sendRequest(mServer, queue.command.mCode, queue.params);
					
					if (result == null || result.length == 0) {
						mFailCount++;
						
						if (mFailCount >= MAX_FAIL_COMMANDS) {
							synchronized (mCommandQueue) {
								run = false;
								mCommandQueue.clear();
							}
							
							mCommandHandler.post(new Runnable() {
								public void run() {
									mHandleCallback.onBansheeCommandHandled(null, null, null);
								}
							});
						}
					} else {
						mFailCount = 0;
						
						mCommandHandler.post(new Runnable() {
							public void run() {
								mHandleCallback.onBansheeCommandHandled(
										queue.command, queue.params, result);
							}
						});
					}
				}
			}
		}
	}
}
