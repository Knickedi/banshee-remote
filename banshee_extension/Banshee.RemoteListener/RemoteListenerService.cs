using System;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Text;
using System.IO;

using Mono.Unix;
using Banshee.Base;
using Banshee.Collection;
using Banshee.Collection.Database;
using Banshee.Sources;
using Banshee.Metadata;
using Banshee.MediaEngine;
using Banshee.Library;
using Banshee.PlaybackController;
using Banshee.ServiceStack;
using Banshee.Preferences;
using Banshee.Configuration;
using Banshee.Query;
using Banshee.Streaming;

using Hyena;
using Hyena.Data;
using Hyena.Data.Sqlite;

namespace Banshee.RemoteListener
{
	public class RemoteListenerService : IExtensionService, IDisposable
	{
		#region Attributes
		
		private int [] _VolumeSteps = new int [] {
			0, 1, 2, 3, 5, 7, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100
		};
		private string [] _ShuffleModes = new string [] {
				"off", "song", "artist", "album", "rating", "score"
		};
		private PlaybackRepeatMode [] _RepeatModes = new PlaybackRepeatMode [] {
				PlaybackRepeatMode.None,
				PlaybackRepeatMode.RepeatSingle,
				PlaybackRepeatMode.RepeatAll
		};
		
		private Socket _listener;
		private byte[] _buffer = new byte[1024];
		private PreferenceBase _portPref;
		private PreferenceService _prefs;
		private int _dbCompressTime = 0;
		
		#endregion
		
		#region Banshee extension

		string IService.ServiceName { get { return "RemoteServer"; } }

		void IExtensionService.Initialize()
		{
			_prefs = ServiceManager.Get<PreferenceService>();
			
			if (_prefs == null) {
				return;
			}
			
			Page remoteControlPage = new Page("RemoteControl", "Remote Control", 3);
			_prefs.FindOrAdd(remoteControlPage);
			
			Section BansheeRemotePrefs = remoteControlPage.FindOrAdd(
				new Section("BansheeRemote", "Banshee Remote", 0));
			
			_portPref = BansheeRemotePrefs.Add(new SchemaPreference<int>(
				RemotePortSchema, Catalog.GetString("Banshee Remote port"),
			    Catalog.GetString("Banshee will listen for the Android Banshee "
						+ "Remote app on this port")
			));
			
			_prefs["RemoteControl"]["BansheeRemote"]["remote_control_port"]
					.ValueChanged += delegate {
				StartRemoteListener();
			};
			
			if (File.Exists(DatabasePath(true))) {
				_dbCompressTime = Timestamp(new FileInfo(DatabasePath(true)).CreationTimeUtc);
			}
			
			CompressDatabase();
			StartRemoteListener();
		}

		void IDisposable.Dispose()
		{
			_prefs["RemoteControl"]["BansheeRemote"].Remove(_portPref);
			_prefs["RemoteControl"].Remove (_prefs["RemoteControl"].FindById("BansheeRemote"));
			_prefs.Remove(_prefs.FindById("RemoteControl"));
			
			if (_listener != null) {
				try {
					_listener.Close();
				} catch (Exception e) {
					Log.Error("error while closing socket of remote listener: " + e.Message);
				}
			}
		}

		public static readonly SchemaEntry<int> RemotePortSchema = new SchemaEntry<int>(
			"remote_control", "remote_control_port",
			8484, 1024, 49151, "BansheeRemote Port",
			"BansheeRemoteListener will listen for the BansheeRemote Android app on this port"
		);

		#endregion

		#region RemoteListener connector

		public void StartRemoteListener ()
		{
			int port = (int) _prefs["RemoteControl"]["BansheeRemote"]["remote_control_port"]
					.BoxedValue;
			
			if (_listener != null) {
				_listener.Disconnect(false);
			}
			
			try {
				Log.Information("remote listener start listening on port " + port.ToString());
				IPEndPoint endpoint = new IPEndPoint(IPAddress.Any, port);
				
				_listener = new Socket(AddressFamily.InterNetwork,
						SocketType.Stream, ProtocolType.Tcp);
				_listener.Bind(endpoint);
				_listener.Listen(10);
				_listener.BeginAccept(OnIncomingConnection, _listener);
			} catch (Exception e) {
				Log.Error("error while starting remote listener", e.Message);
			}
		}

		void OnIncomingConnection(IAsyncResult ar)
		{
			Socket client = null;
			
			try {
				client = ((Socket)ar.AsyncState).EndAccept(ar);
				client.BeginReceive(_buffer, 0, _buffer.Length, SocketFlags.None,
					OnReceiveRequest, client);
			} catch (Exception e) {
				Log.Error("error whil handling client request by remote listener: " + e.Message);
				_listener.BeginAccept(new AsyncCallback(OnIncomingConnection), _listener);
			}
		}
		
		void OnReceiveRequest(IAsyncResult ar) {
			Socket client = null;
			bool isListenerAccepting = false;
			
			try {
				client = (Socket)ar.AsyncState;
				int readBytes = client.EndReceive(ar);
				
				string requestName = ((RequestCode) _buffer[0]).ToString();
				byte [] result = (byte []) typeof(RemoteListenerService).InvokeMember(
					requestName,
					BindingFlags.Default | BindingFlags.InvokeMethod,
                    null, this, new object [] {readBytes});
				
				// we handled the request and have the data, handle other requests now
				isListenerAccepting = true;
				_listener.BeginAccept(new AsyncCallback(OnIncomingConnection), _listener);
				
				if (result != null && result.Length != 0) {
					client.BeginSend(result, 0, result.Length, SocketFlags.None, 
					                 OnSentResponse, client);
				}
			} catch (Exception e) {
				Log.Error("remote listener request error", e.Message);
				
				if (!isListenerAccepting) {
					try {
						// error occurred on request handle listening was not started
						_listener.BeginAccept(new AsyncCallback(OnIncomingConnection), _listener);
					} catch (Exception e2) {
						Log.Error("error while starting accepting of remote listener", e2.Message);
					}
				}
			}
		}
		
		void OnSentResponse(IAsyncResult ar) {
			try {
				((Socket)ar.AsyncState).Close();
			} catch {
			}
		}
		
		#endregion
		
		#region Helper functions
		
		private int ShortFromBuffer(int p) {
			return ((_buffer[p + 1] << 8) & 0xff00) + _buffer[p];
		}
		
		private byte [] ShortToByte(int i) {
			return new byte [] {(byte) i, (byte) ((i >> 8) & 0xff)};
		}
		
		private byte [] IntToByte(int i) {
			return new byte [] {(byte) i, (byte) ((i >> 8) & 0xff),
					(byte) ((i >> 16) & 0xff), (byte) ((i >> 24) & 0xff)};
		}
		
		private byte [] StringToByte(string s) {
			if (s == null || s.Length == 0) {
				return new byte [] {0, 0};
			}
			
			byte [] stringBytes = Encoding.UTF8.GetBytes(s);
			byte [] result = new byte [2 + stringBytes.Length];
			byte [] length = ShortToByte(stringBytes.Length);
			
			Array.Copy(length, 0, result, 0, length.Length);
			Array.Copy(stringBytes, 0, result, 2, stringBytes.Length);
			
			return result;
		}
		
		private string DatabasePath(bool compressed) {
			string home = Environment.GetEnvironmentVariable("HOME");
			string dbPath = home + "/.config/banshee-1/banshee";
			
			return dbPath + (compressed ? "compressed.db" : ".db");
		}
		
		private int ShuffleMode() {
			switch (ServiceManager.PlaybackController.ShuffleMode) {
			case "off":     return 1;
			case "song":    return 2;
			case "artist":  return 3;
			case "album":   return 4;
			case "rating":  return 5;
			case "score":   return 6;
			default:        return 0;
			}
		}
		
		private int RepeatMode() {
			switch (ServiceManager.PlaybackController.RepeatMode) {
			case PlaybackRepeatMode.None:          return 1;
			case PlaybackRepeatMode.RepeatSingle:  return 2;
			case PlaybackRepeatMode.RepeatAll:     return 3;
			default:                               return 0;
			}
		}
		
		private void SetupVolume(int volume) {
			if (volume == 0 || volume > 103) {
				return;
			}
			
			int currentVolume = ServiceManager.PlayerEngine.Volume;
			
			if (volume == 101) {
				volume = 0;
			} else if (volume == 102) {
				int position = 0; // will be set in for loop
				
				for (int i = 1; i < _VolumeSteps.Length; i++) {
					if (currentVolume <= _VolumeSteps[i]) {
						position = i - 1;
						break;
					}
				}
					
				volume = _VolumeSteps[position];
			} else if (volume == 103) {
				int position = 0; // will be set in for loop
				
				for (int i = _VolumeSteps.Length - 2; i >= 0; i--) {
					if (currentVolume >= _VolumeSteps[i]) {
						position = i + 1;
						break;
					}
				}
					
				volume = _VolumeSteps[position];
			}
				
			ServiceManager.PlayerEngine.Volume = (ushort) volume;
		}
		
		private void SetupRepeatMode(int mode) {
			if (mode >= 1 && mode <= 3) {
				if (ShuffleMode() == 0) {
					ServiceManager.PlaybackController.RepeatMode = _RepeatModes[0];
				} else {
					ServiceManager.PlaybackController.RepeatMode = _RepeatModes[mode - 1];
				}
			} else if (mode == 4) {
				ServiceManager.PlaybackController.RepeatMode
							= _RepeatModes[RepeatMode() % _RepeatModes.Length];
			} else if (mode == 5) {
				ServiceManager.PlaybackController.ToggleRepeat();
			}
		}
		
		private void SetupShuffleMode(int mode) {
			if (mode >= 1 && mode <= 6) {
				if (ShuffleMode() == 0) {
					ServiceManager.PlaybackController.ShuffleMode = _ShuffleModes[0];
				} else {
					ServiceManager.PlaybackController.ShuffleMode = _ShuffleModes[mode - 1];
				}
			} else if (mode == 7) {
				ServiceManager.PlaybackController.ShuffleMode
							= _ShuffleModes[ShuffleMode() % _ShuffleModes.Length];
			} else if (mode == 8) {
				ServiceManager.PlaybackController.ToggleShuffle();
			}
		}
		
		private void SetupPlayMode(int mode, out bool? playing, out bool? paused) {
			playing = paused = null;
			
			switch (mode) {
			case 1:
				if (ServiceManager.PlayerEngine.CurrentState == PlayerState.Playing) {
					ServiceManager.PlayerEngine.Pause();
					playing = false;
					paused = true;
				} else {
					ServiceManager.PlayerEngine.Play();
					playing = true;
					paused = false;
				}
				break;
			case 2:
				if (ServiceManager.PlayerEngine.CurrentState != PlayerState.Playing) {
					ServiceManager.PlayerEngine.Play();
					playing = true;
					paused = false;
				}
				break;
			case 3:
				if (ServiceManager.PlayerEngine.CurrentState == PlayerState.Playing
				    	&& ServiceManager.PlayerEngine.CanPause) {
					ServiceManager.PlayerEngine.Pause();
					playing = false;
					paused = true;
				}
				break;
			case 4:
				ServiceManager.PlaybackController.Next();
				playing = true;
				paused = false;
				break;
			case 5:
				ServiceManager.PlaybackController.Previous();
				playing = true;
				paused = false;
				break;
			}
		}
		
		private void SetupSeekPosition(int position) {
			
		}
		
		private byte [] PlayerStatusResult() {
			byte [] result = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
			
			if (ServiceManager.PlayerEngine.CurrentState == PlayerState.Paused) {
				result[0] = 0x80;
			} else if (ServiceManager.PlayerEngine.CurrentState == PlayerState.Playing) {
				result[0] = 0x40;
			}
			
			result[0] |= (byte) ((RepeatMode() << 4) & 0x30);
			result[0] |= (byte) ShuffleMode();
			result[1] = (byte) ServiceManager.PlayerEngine.Volume;
			
			Array.Copy(ShortToByte((int) ServiceManager.PlayerEngine.Position / 100),
			           0, result, 2, 2);
			
			TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
			
			if (track != null) {
				Array.Copy(ShortToByte((int) track.FileSize), 0, result, 4, 2);
			}
			
			return result;
		}
		
		private int Timestamp(DateTime date) {
			return Convert.ToInt32(new TimeSpan(
				date.Ticks - new DateTime(1970, 1, 1).Ticks).TotalSeconds);
		}
		
		private int Timestamp() {
			return Convert.ToInt32(new TimeSpan(
				DateTime.Now.Ticks - new DateTime(1970, 1, 1).Ticks).TotalSeconds);
		}
		
		private void CompressDatabase() {
			if (Timestamp() - _dbCompressTime < 24 * 60 * 60) {
				return;
			}
			
			try {
				File.Delete(DatabasePath(true));
				File.Delete(DatabasePath(true) + "-journal");
				File.Copy(DatabasePath(false), DatabasePath(true));
				
				// database was in use, unlock it or queries will just fail
				HyenaSqliteConnection db = new HyenaSqliteConnection(DatabasePath(true));
				db.BeginTransaction();
				db.CommitTransaction();
				db.Dispose();
				
				db = new HyenaSqliteConnection(DatabasePath(true));
				db.BeginTransaction();
				
				IDataReader cursor = db.Query(
						  "SELECT tbl_name FROM sqlite_master WHERE "
						+ "type='table' AND tbl_name NOT IN "
				        + "('CoreTracks', 'CoreArtists', 'CoreAlbums', 'sqlite_stat1', 'sqlite_stat2');");
				
				// drop unnecessary tables
				while (cursor.Read()) {
					db.Execute("DROP TABLE " + cursor.Get(0, typeof(string)) + ";");
				}
				
				// clear analytic data (if available)
				if (db.TableExists("sqlite_stat1")) {
					db.Execute("DELETE FROM sqlite_stat1;");
				}
				if (db.TableExists("sqlite_stat2")) {
					db.Execute("DELETE FROM sqlite_stat2;");
				}
				
				cursor.Dispose();
				
				// remove unecessary columns from tracks table
				db.Execute("CREATE TABLE tracks (\n"
						+ "	_id  PRIMARY KEY,\n"
						+ "	artistId  INTEGER,\n"
						+ "	albumId  INTEGER,\n"
				        + "	fileSize INTEGER,\n"
				        + "	bitrate INTEGER,\n"
				        + "	title TEXT,\n"
				        + " trackNumber INTEGER,\n"
				        + "	duration INTEGER,\n"
				        + "	year INTEGER,\n"
				        + "	genre TEXT\n"
						+ ");");
				db.Execute("INSERT INTO tracks(_id, artistId, albumId, fileSize, "
				        + "bitrate, title, trackNumber, duration, year, genre) "
				        + "SELECT TrackID, ArtistID, AlbumId, FileSize, "
				        + "BitRate, Title, TrackNumber, Duration, Year, Genre "
				        + "FROM CoreTracks;");
				db.Execute("DROP TABLE CoreTracks;");
				
				// remove unecessary columns from artist table
				db.Execute("CREATE TABLE artists (\n"
						+ "	_id  PRIMARY KEY,\n"
						+ "	name TEXT\n"
						+ ");");
				db.Execute("INSERT INTO artists(_id, name) "
				        + "SELECT ArtistID, Name FROM CoreArtists;");
				db.Execute("DROP TABLE CoreArtists;");
				
				// remove unecessary columns from album table
				db.Execute("CREATE TABLE albums (\n"
						+ "	_id  PRIMARY KEY,\n"
						+ "	artistId INTEGER,\n"
				        + " title TEXT,\n"
				        + " artId TEXT\n"
						+ ");");
				db.Execute("INSERT INTO albums(_id, artistId, title, artId) "
				        + "SELECT AlbumID, ArtistID, Title, ArtworkID FROM CoreAlbums;");
				db.Execute("DROP TABLE CoreAlbums;");
				
				db.CommitTransaction();
				db.Execute("VACUUM;");
				db.Dispose();
				
				_dbCompressTime = Timestamp();
			} catch (Exception e) {
				Log.Error("remote listener failed to compress database: " + e.Message);
				File.Delete(DatabasePath(true));
			}
		}
		
		#endregion
		
		#region RemoteListener request type definition
		
		/* Every request is mapped onto one of thes enum constants.
		 * 
		 * A request should have following byte format:
		 * Request code + parameter bytes (optional)
		 * [byte request code][param byte 1]...[param byte n]
		 * 
		 * If request code can't be mapped onto one of these constants the
		 * request just fails. If it can be mapped the corresponding method
		 * will be called. If it isn't defined then the request fails too.
		 * 
		 * The corresponding method should have the followind singnature:
		 * public byte [] RequestCodeConstantName(int readBytes)
		 * Return of null and byte array with length 0 will be interpreted as error!
		 * 
		 * If the request expacts parameters or returns data then always these
		 * convertions will be followed:
		 * - short    big endian - 2 byte
		 * - integer  big endian - 4 byte
		 * - string   first is short and gives the length (byte count) of the
		 *            following string
		 */
		public enum RequestCode {
			
			/* Like ping. Just for testing the connection.
			 * 
			 * Output: 0 value byte.
			 */
			Test = 0,
			
			/* (Set and) get player status.
			 * 
			 * 
			 * Input: (all optional - length too, missing parameters will be just
			 * ignored - you can use this too just get the player status)
			 * 
			 * SHUFFLE CHANGES DISABLED! It crashes banshee, bug is reported.
			 * This feature will be enabled when bug was fixed...
			 * 
			 * Why everything in one request?
			 * This way you can set changes in a single request. Although there's
			 * hardly a chance you would do that but you can simply ignore other
			 * parameters and just commit a single change so...
			 * It's more efficient that way, a request costs a couple of bytes (or
			 * even none when no parameters suplied) but a socket connection for
			 * every status change might be expensive.
			 * 
			 * Bit 7-8 of byte 1       : 0 - no action
			 *                           1 - toggle play/pause
			 *                           2 - play
			 *                           3 - pause
			 *                           4 - next
			 *                           5 - previous
			 * Bit 4-1 of byte 1       : 0 - no action
			 *                           1-3 - set repeat mode (see output)
			 *                           4 - toggle between repeat modes
			 *                           5 - toggle repeat on / off
			 * Byte 2                  : 0 - no action
			 *                           1-6 - set shuffle mode (see output)
			 *                           7 - toggle between shuffle modes
			 *                           8 - toggle shuffle on / off
			 * Byte 3                  : 0 - no action
			 *                           1-100 - set volume
			 *                           101 - mute volume
			 *                           102 - volume step down
			 *                           103 - volume step up
			 * Byte 4-5                : Set song seek position (in tenth second)
			 *                           Starts with 1, 0 won't do anything
			 * 
			 * 
			 * Output:
			 * 
			 * Bit 8 (last) of byte 1  : 1 when the player is paused otherwise 0
			 * Bit 7 of byte 1         : 1 when the player is playing otherwise 0
			 *                           If both bits are 0 then the player is idle
			 * Bit 6-5 of byte 1       : repeat mode
			 *                           0 - unknown
			 *                           1 - off
			 *                           2 - single
			 *                           3 - all
			 * Bit 3-1 of byte 1       : shuffle mode
			 *                           0 - unknown
			 *                           1 - off
			 *                           2 - song
			 *                           3 - artist
			 *                           4 - album
			 *                           5 - rating
			 *                           6 - score
			 * Byte 2                  : volume (0 - 100 see volume request)
			 * byte 3-4                : song seek position (tenth second)
			 * Byte 5-6                : change flag (song ID is not enough because it
			 *                           could be non existing when the song is not
			 *                           stored in the database)
			 * Byte 7-10               : song ID in database
			 * 
			 * If the change flag differs from the change flag from previous request
			 * then the song has changed and you should request song data or use
			 * song ID locally.
			 */
			PlayerStatus = 1,
			
			/* Request current track data.
			 * 
			 * Input: None
			 * 
			 * Output: (the data will be empty or 0 when there's no track shich is
			 * currently playing)
			 * 
			 * Track length seconds as short.
			 * Track title as string.
			 * Artist title as string.
			 * Album title as string.
			 * Genre as string.
			 * Year as short.
			 * Cover ID as string or empty if there's no cover.
			 */
			SongInfo = 2,
			
			/* Synchronize banshee database.
			 * 
			 * Input: byte
			 * 1 - get banshee data base size
			 * 2 - get database
			 * all other request will return a byte with 0
			 *
			 * Output:
			 * 1 - integer with database isze in bytes or 0 if there's no database
			 * 2 - sqlite database packed in binary bytes or byte with value 0
			 *     if there's no database
			 */
			SyncDatabase = 3,
		}
		
		#endregion
		
		#region RemoteListener request type callbacks
		
		public byte [] Test(int readBytes) {
			return new byte[] {0};
		}
		
		public byte [] PlayerStatus(int readBytes) {
			bool? playing = null;
			bool? paused = null;
			
			if (readBytes > 1) {
				SetupPlayMode((_buffer[1] >> 4) & 0xf, out playing, out paused);
				SetupRepeatMode(_buffer[1] & 0xf);
			}
			
			if (readBytes > 2) {
				// FIXME remove comment if shuffle bug is fixed
				// SetupShuffleMode(_buffer[2] & 0xf);
			}
			
			if (readBytes > 3) {
				SetupVolume(_buffer[3]);
			}
			
			if (readBytes > 5) {
				SetupSeekPosition(ShortFromBuffer(4));
			}
			
			byte [] result = PlayerStatusResult();
			
			if (paused != null) {
				result[0] = (byte) ((result[0] & 0x7f) + ((bool) paused ? 0x80 : 0x0));
			}
			
			if (playing != null) {
				result[0] = (byte) ((result[0] & 0xbf) + ((bool) playing ? 0x40 : 0x0));
			}
			
			return result;
		}
		
		public byte [] SongInfo(int readBytes) {
			TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
			byte [] totalTime, year, song, artist, album, genre, artId;
			
			if (track != null) {
				string home = Environment.GetEnvironmentVariable("HOME");
				string coverPath = home + "/.cache/media-art/" + track.ArtworkId +".jpg";
				
				totalTime = ShortToByte((int) track.Duration.TotalSeconds);
				song = StringToByte(track.DisplayTrackTitle);
				artist = StringToByte(track.DisplayArtistName);
				album = StringToByte(track.DisplayAlbumTitle);
				genre = StringToByte(StringUtil.MaybeFallback(track.DisplayGenre, ""));
				year = ShortToByte(track.Year);
				artId = StringToByte(System.IO.File.Exists(coverPath) ? track.ArtworkId : "");
			} else {
				totalTime = year = song = artist = album = genre = artId = new byte [] {0, 0};
			}
			
			byte [] result = new byte [totalTime.Length + year.Length + genre.Length
				+ song.Length + artist.Length + album.Length + artId.Length];
			
			int index = 0;
			Array.Copy(totalTime, 0, result, index, totalTime.Length);
			index += totalTime.Length;
			Array.Copy(song, 0, result, index, song.Length);
			index += song.Length;
			Array.Copy(artist, 0, result, index, artist.Length);
			index += artist.Length;
			Array.Copy(album, 0, result, index, album.Length);
			index += album.Length;
			Array.Copy(genre, 0, result, index, genre.Length);
			index += genre.Length;
			Array.Copy(year, 0, result, index, year.Length);
			index += year.Length;
			Array.Copy(artId, 0, result, index, artId.Length);
			index += artId.Length;
			
			return result;
		}
		
		public byte [] SyncDatabase(int readBytes) {
			if (readBytes > 1) {
				if (_buffer[1] == 1) {
					if (File.Exists(DatabasePath(true))) {
						return IntToByte((int) new FileInfo(DatabasePath(true)).Length);
					} else {
						CompressDatabase();
						
						if (File.Exists(DatabasePath(true))) {
							return IntToByte((int) new FileInfo(DatabasePath(true)).Length);
						} else {
							return IntToByte(0);
						}
					}
				} else if (_buffer[1] == 2) {
					if (File.Exists(DatabasePath(true))) {
						return File.ReadAllBytes(DatabasePath(true));
					}
				}
			}
			
			return new byte [] {0};
		}
		
		#endregion
	}
}
