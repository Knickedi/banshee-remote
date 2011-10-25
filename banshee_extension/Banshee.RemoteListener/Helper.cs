using System;
using System.IO;
using System.Text;

using Banshee.Collection;
using Banshee.Collection.Database;
using Banshee.Library;
using Banshee.MediaEngine;
using Banshee.PlaybackController;
using Banshee.Playlist;
using Banshee.ServiceStack;
using Banshee.Sources;

using Hyena;
using Hyena.Data;
using Hyena.Data.Sqlite;

namespace Banshee.RemoteListener
{
	/// <summary>
	/// Static class which help with request handling.
	/// </summary>
	public static class Helper
	{
		
		#region Attributes
		
		/// <summary>
		/// Timespan the compressed database will be cached (in seconds)
		/// </summary>
		private static int _DB_CACHED_COMPRESSION = 24 * 60 * 60;
		
		/// <summary>
		/// Timespan to pass until "previous" request triggers differently.
		/// </summary>
		/// Amount of seconds which should be passed so the current song is played again on
		/// "previous" track request if given amount of seconds hasn't passed the track prior to
		/// the urrent will be played (allows replay of current track).
		private static int _PREVIOUS_TRACK_OFFSET = 15;
		
		/// <summary>
		/// Volume step down / up steps.
		/// </summary>
		private static int [] _VolumeSteps = new int [] {
			0, 1, 2, 3, 5, 7, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100
		};
		
		/// <summary>
		/// Default shuffle mode states.
		/// </summary>
		private static string [] _ShuffleModes = new string [] {
				"off", "song", "artist", "album", "rating", "score"
		};
		
		/// <summary>
		/// Default repeat mode states.
		/// </summary>
		private static PlaybackRepeatMode [] _RepeatModes = new PlaybackRepeatMode [] {
				PlaybackRepeatMode.None,
				PlaybackRepeatMode.RepeatSingle,
				PlaybackRepeatMode.RepeatAll
		};
		
		/// <summary>
		/// Timestamp of last database compression
		/// </summary>
		private static int _dbCompressTime = 0;
		
		/// <summary>
		/// Buffer to which incoming requests will be written.
		/// </summary>
		private static byte[] _buffer = new byte[1024 * 100];
		
		/// <summary>
		/// Banshee is not understanding that another track is chosen to be played and reports that
		/// player engine has stopped playing for a while. So we will force the playing status for
		/// two seconds.
		/// </summary>
		private static int _playTimeout = 0;
		
		#endregion
		
		
		#region Getter / Setter
		
		public static int DbCompressTime {
			get { return _dbCompressTime; }
			set { _dbCompressTime = value; }
		}
		
		public static byte [] Buffer {
			get { return _buffer; }
		}
		
		public static bool ForcePlay {
			get { return Timestamp() - _playTimeout <= 1; }
		}
		
		#endregion
		
		
		#region Service helpers
		
		public static void StripGlobalInfoFromBuffer(int length) {
			Array.Copy(_buffer, 3, _buffer, 0, length);
		}
		
		public static void SetDbCompressTimeFromFile() {
			if (File.Exists(DatabasePath(true))) {
				DbCompressTime = Timestamp(new FileInfo(DatabasePath(true)).CreationTimeUtc);
			}
		}
		
		#endregion
		
		
		#region Request helpers
		
		/// <summary>
		/// Read a big endian short value from buffer.
		/// </summary>
		/// <param name="p">
		/// Position in buffer where the short value is located.
		/// </param>
		/// <returns>
		/// Read short value. 
		/// </returns>
		public static ushort ShortFromBuffer(int p) {
			return (ushort) (_buffer[p] + ((_buffer[p + 1] << 8) & 0xff00));
		}
		
		/// <summary>
		/// Get big endian representation of a short value.
		/// </summary>
		/// <param name="s">
		/// Short value to convert.
		/// </param>
		/// <returns>
		/// Big endian representation of the given short value.
		/// </returns>
		public static byte [] ShortToByte(ushort s) {
			return new byte [] {(byte) s, (byte) ((s >> 8) & 0xff)};
		}
		
		/// <summary>
		/// Read a big endian integer value from buffer.
		/// </summary>
		/// <param name="p">
		/// Position in buffer where the integer value is located.
		/// </param>
		/// <returns>
		/// Read integer value. 
		/// </returns>
		public static uint IntFromBuffer(int p) {
			return (uint) (_buffer[p] + ((_buffer[p + 1] << 8) & 0xff00)
				+ ((_buffer[p + 2] << 16) & 0xff0000)
				+ ((_buffer[p + 3] << 24) & 0xff000000));
		}
		
		/// <summary>
		/// Get big endian representation of a integer value.
		/// </summary>
		/// <param name="s">
		/// Integer value to convert.
		/// </param>
		/// <returns>
		/// Big endian representation of the given integer value.
		/// </returns>
		public static byte [] IntToByte(uint i) {
			return new byte [] {(byte) i, (byte) ((i >> 8) & 0xff),
					(byte) ((i >> 16) & 0xff), (byte) ((i >> 24) & 0xff)};
		}
		
		/// <summary>
		/// Get (UTF-8) string from buffer (see StringToByte for more).
		/// </summary>
		/// <param name="p">
		/// Position in buffer where the string definition is located.
		/// </param>
		/// <returns>
		/// Decoded (UTF-8) string.
		/// </returns>
		public static string StringFromBuffer(int p, out int byteLength) {
			byteLength = ShortFromBuffer(p);
			string s = Encoding.UTF8.GetString(_buffer, p + 2, byteLength);
			byteLength += 2;
			
			return s;
		}
		
		/// <summary>
		/// Get byte representation of an UTF-8 string.
		/// </summary>
		/// <param name="s">
		/// String to convert.
		/// </param>
		/// <returns>
		/// First two bytes is a big endian short which tells how many following bytes represent
		/// the converted string.
		/// </returns>
		public static byte [] StringToByte(string s) {
			if (s == null || s.Length == 0) {
				return new byte [] {0, 0};
			}
			
			byte [] stringBytes = Encoding.UTF8.GetBytes(s);
			byte [] result = new byte [2 + stringBytes.Length];
			byte [] length = ShortToByte((ushort) stringBytes.Length);
			
			Array.Copy(length, 0, result, 0, length.Length);
			Array.Copy(stringBytes, 0, result, 2, stringBytes.Length);
			
			return result;
		}
		
		/// <summary>
		/// Get trimmed string.
		/// </summary>
		/// <param name="s">
		/// String to timm.
		/// </param>
		/// <returns>
		/// Trimmed string, null will be returned as empty string.
		/// </returns>
		public static string TrimString(string s) {
			return s == null ? "" : s.Trim();
		}
		
		/// <summary>
		/// Get unix timestamp.
		/// </summary>
		/// <returns>
		/// Current unix timestamp.
		/// </returns>
		public static int Timestamp() {
			return Convert.ToInt32(new TimeSpan(
				DateTime.Now.Ticks - new DateTime(1970, 1, 1).Ticks).TotalSeconds);
		}
		
		/// <summary>
		/// Get unix timestamp for given date.
		/// </summary>
		/// <param name="date">
		/// Date for which the timestamp should be calculated.
		/// </param>
		/// <returns>
		/// Unix timestamp of given date.
		/// </returns>
		public static int Timestamp(DateTime date) {
			return Convert.ToInt32(new TimeSpan(
				date.Ticks - new DateTime(1970, 1, 1).Ticks).TotalSeconds);
		}
		
		/// <summary>
		/// Get path to (compressed) banshee database.
		/// </summary>
		/// <param name="compressed">
		/// True will return the path to compress database.
		/// </param>
		/// <returns>
		/// Path to database.
		/// </returns>
		public static string DatabasePath(bool compressed) {
			string home = Environment.GetEnvironmentVariable("HOME");
			string dbPath = home + "/.config/banshee-1/banshee";
			
			return dbPath + (compressed ? "compressed.db" : ".db");
		}
		
		public static Source GetOrCreateRemotePlaylist() {
			PlaylistSource source = new PlaylistSource("Banshee Remote", new MusicLibrarySource());
			source.Save();
			source.PrimarySource.AddChildSource(source);
			source.NotifyUser();
			
			return source;
		}
		
		/// <summary>
		/// Get current player shuffle mode.
		/// </summary>
		/// <returns>
		/// 0 - unknown
		/// 1 - off
		/// 2 - song
		/// 3 - artist
		/// 4 - album
		/// 5 - rating
		/// 6 - score
		/// </returns>
		public static int ShuffleMode() {
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
		
		/// <summary>
		/// Get current player repeat mode.
		/// </summary>
		/// <returns>
		/// 0 - unknown
		/// 1 - off
		/// 2 - single
		/// 3 - all
		/// </returns>
		public static int RepeatMode() {
			switch (ServiceManager.PlaybackController.RepeatMode) {
			case PlaybackRepeatMode.None:          return 1;
			case PlaybackRepeatMode.RepeatSingle:  return 2;
			case PlaybackRepeatMode.RepeatAll:     return 3;
			default:                               return 0;
			}
		}
		
		/// <summary>
		/// Set volume of player.
		/// </summary>
		/// <param name="volume">
		/// 1 - 100 - set volume
		///     101 - set 0
		///     102 - step down
		///     103 - step up
		/// </param>
		public static void SetupVolume(int volume) {
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
		
		/// <summary>
		/// Set repeat mode of player.
		/// </summary>
		/// <param name="mode">
		/// 1 - off
		/// 2 - single
		/// 3 - all
		/// 4 - toggle to next mode
		/// 5 - toggle on / off
		/// </param>
		public static void SetupRepeatMode(int mode) {
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
		
		/// <summary>
		/// Set player shuffle mode
		/// </summary>
		/// <param name="mode">
		/// 1 - off
		/// 2 - song
		/// 3 - artist
		/// 4 - album
		/// 5 - rating
		/// 6 - score
		/// 7 - toggle to next mode
		/// 8 - toggle on / off
		/// </param>
		public static void SetupShuffleMode(int mode) {
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
		
		/// <summary>
		/// Set player play mode / status.
		/// </summary>
		/// <param name="mode">
		/// 1 - toggle play / pause
		/// 2 - play
		/// 3 - pause
		/// 4 - next
		/// 5 - previous
		/// </param>
		/// <param name="playing">
		/// Banshee is not getting the fact what that play status was changed imediatelly.
		/// So this variable will contain true or false when the player is playing now or
		/// null if you can read the current status because request didn't change anything.
		/// </param>
		/// <param name="paused">
		/// Banshee is not getting the fact what that play status was changed imediatelly.
		/// So this variable will contain true or false when the player is paused now or
		/// null if you can read the current status because request didn't change anything.
		/// </param>
		/// <param name="reserSeekPosition">
		/// Will be set to true if next or previous was requested and the seek position should
		/// be set to 0.
		/// </param>
		public static void SetupPlayMode(int mode, out bool? playing, out bool? paused, out bool? resetSeekPosition) {
			playing = paused = resetSeekPosition = null;
			
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
				resetSeekPosition = true;
				break;
				
			case 5:
				if (ServiceManager.PlayerEngine.Position / 1000 > _PREVIOUS_TRACK_OFFSET) {
					ServiceManager.PlaybackController.RestartOrPrevious();
				} else {
					ServiceManager.PlaybackController.Previous();
				}
				
				playing = true;
				paused = false;
				resetSeekPosition = true;
				break;
			}
		}
		
		/// <summary>
		/// Set track seek position of player.
		/// </summary>
		/// <param name="position">
		/// Position in milliseconds. 0 will be ignored, use 1 instead.
		/// </param>
		/// <param name="newPosition">
		/// This will contain the new position. The player returns a wrong position on set.
		/// </param>
		public static void SetupSeekPosition(uint position, out uint? newPosition) {
			if (position == 0) {
				newPosition = null;
				return;
			}
			
			TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
			
			if (track == null) {
				int duration = (int) track.Duration.TotalMilliseconds;
				
				if (duration <= position) {
					ServiceManager.PlayerEngine.Position = (uint) position;
					position = 0;
				} else {
					ServiceManager.PlayerEngine.Position = (uint) position;
				}
			} else {
				ServiceManager.PlayerEngine.Position = (uint) position;
			}
				
			newPosition = position;
		}
		
		/// <summary>
		/// Generate result for player status request
		/// </summary>
		/// <returns>
		/// Bit 8 (last) of byte 1  : 1 when the player is paused otherwise 0
		/// Bit 7 of byte 1         : 1 when the player is playing otherwise 0
		///                           If both bits are 0 then the player is idle
		/// Bit 6-5 of byte 1       : See RepeatMode()
		/// Bit 3-1 of byte 1       : See SuffleMode()
		/// Byte 2                  : volume (0 - 100 see volume request)
		/// byte 3-6                : song seek position (in millisecond)
		/// Byte 7-8                : change flag (song ID is not enough because it
		///                           could be non existing when the song is not
		///                           stored in the database)
		/// Byte 9-12               : song ID in database
		/// </returns>
		public static byte [] PlayerStatusResult() {
			byte [] result = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
			
			if (ServiceManager.PlayerEngine.CurrentState == PlayerState.Paused) {
				result[0] = 0x80;
			} else if (ServiceManager.PlayerEngine.CurrentState == PlayerState.Playing) {
				result[0] = 0x40;
			}
			
			result[0] |= (byte) ((RepeatMode() << 4) & 0x30);
			result[0] |= (byte) ShuffleMode();
			result[1] = (byte) ServiceManager.PlayerEngine.Volume;
			
			Array.Copy(IntToByte((uint) (ServiceManager.PlayerEngine.Position)),
			           0, result, 2, 4);
			
			TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
			
			if (track != null) {
				Array.Copy(ShortToByte((ushort) track.FileSize), 0, result, 6, 2);
				Array.Copy(IntToByte((uint) (track is DatabaseTrackInfo ? ((DatabaseTrackInfo) track).TrackId : 0)),
				           0, result, 8, 4);
			}
			
			return result;
		}
		
		/// <summary>
		/// Compress banshee database if it is not already compressed.
		/// </summary>
		/// This won't do anything if last compression is not more than  _DB_CACHED_COMPRESSION
		/// senconds ago. To avoid that you can reset _dbCompressTime to 0.
		/// 
		/// See https://github.com/Knickedi/banshee-remote for more.
		public static void CompressDatabase() {
			if (Timestamp() - _dbCompressTime < _DB_CACHED_COMPRESSION) {
				return;
			}
			
			try {
				_dbCompressTime = Timestamp();
				
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
						+ "	_id INTEGER PRIMARY KEY,\n"
						+ "	artistId  INTEGER,\n"
						+ "	albumId  INTEGER,\n"
				        + "	title TEXT,\n"
				        + " trackNumber INTEGER,\n"
				        + "	duration INTEGER,\n"
				        + "	year INTEGER,\n"
				        + "	genre TEXT\n"
						+ ");");
				db.Execute("INSERT INTO tracks(_id, artistId, albumId, "
				        + "title, trackNumber, duration, year, genre) "
				        + "SELECT TrackID, ArtistID, AlbumId, "
				        + "Title, TrackNumber, Duration, Year, Genre "
				        + "FROM CoreTracks;");
				db.Execute("DROP TABLE CoreTracks;");
				
				// remove unecessary columns from artist table
				db.Execute("CREATE TABLE artists (\n"
						+ "	_id INTEGER PRIMARY KEY,\n"
						+ "	name TEXT\n"
						+ ");");
				db.Execute("INSERT INTO artists(_id, name) "
				        + "SELECT ArtistID, Name FROM CoreArtists;");
				db.Execute("DROP TABLE CoreArtists;");
				
				// remove unecessary columns from album table
				db.Execute("CREATE TABLE albums (\n"
						+ "	_id INTEGER PRIMARY KEY,\n"
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
		
		/// <summary>
		/// Set track to play (immediately).
		/// </summary>
		/// <param name="id">
		/// ID of track in database.
		/// </param>
		/// <returns>
		/// Returns true if track is available and is plaing now.
		/// </returns>
		public static bool PlayTrack(long id) {
			DatabaseTrackInfo track = new DatabaseTrackModelProvider<DatabaseTrackInfo>(
					ServiceManager.DbConnection).FetchSingle(id);
					
			if (track != null) {
				TrackListModel model = ServiceManager.PlaybackController.Source.TrackModel;
				int i;
				
				for (i = 0; i < model.Count; i++) {
					TrackInfo t = (TrackInfo) model.GetItem(i);
					
					if (t is DatabaseTrackInfo && ((DatabaseTrackInfo) t).TrackId == track.TrackId) {
						break;
					}
				}
				
				if (i != model.Count) {
					ServiceManager.PlayerEngine.OpenPlay((TrackInfo) model.GetItem(i));
					_playTimeout = Timestamp();
				} else {
					ServiceManager.PlayerEngine.OpenPlay(track);
					_playTimeout = Timestamp();
				}
				
				return true;
			}
			
			return false;
		}
		
		#endregion
	}
}

