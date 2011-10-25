using System;
using System.IO;

using Banshee.Base;
using Banshee.Collection;
using Banshee.Collection.Database;
using Banshee.ServiceStack;

namespace Banshee.RemoteListener
{
	/// <summary>
	/// Static class which processes incomming requests.
	/// Exceptions will be handled as failing requests and send nothing as response.
	/// </summary>
	public static class RequestHandler
	{
		#region Request handling
		
		/// <summary>
		/// Request code definition.
		/// </summary>
		/// See https://github.com/Knickedi/banshee-remote for more.
		public enum RequestCode {
			Test = 0,
			PlayerStatus = 1,
			SongInfo = 2,
			SyncDatabase = 3,
			Cover = 4,
			Playlist = 5,
			PlaylistControl = 6,
		}
		
		public static byte [] Test(int readBytes) {
			return new byte[] {1};
		}
		
		public static byte [] PlayerStatus(int readBytes) {
			bool? playing = null;
			bool? paused = null;
			uint? newPosition = null;
			bool? resetSeek = null;
			
			if (readBytes > 0) {
				Helper.SetupPlayMode((Helper.Buffer[0] >> 4) & 0xf, out playing, out paused, out resetSeek);
				Helper.SetupRepeatMode(Helper.Buffer[0] & 0xf);
				
				if (resetSeek != null) {
					newPosition = 0;
				}
			}
			
			if (readBytes > 1) {
				Helper.SetupShuffleMode(Helper.Buffer[1] & 0xf);
			}
			
			if (readBytes > 2) {
				Helper.SetupVolume(Helper.Buffer[2]);
			}
			
			if (readBytes > 6) {
				Helper.SetupSeekPosition(Helper.IntFromBuffer(3), out newPosition);
			}
			
			byte [] result = Helper.PlayerStatusResult();
			bool forcePlaying = Helper.ForcePlay;
			
			if (paused != null && !forcePlaying) {
				if (forcePlaying) {
					paused = false;
				}
				
				result[0] = (byte) ((result[0] & 0x7f) + ((bool) paused ? 0x80 : 0x0));
			}
			
			if (playing != null && forcePlaying) {
				if (forcePlaying) {
					playing = true;
				}
				
				result[0] = (byte) ((result[0] & 0xbf) + ((bool) playing ? 0x40 : 0x0));
			}
			
			if (newPosition != null) {
				byte [] position = Helper.IntToByte((uint) newPosition);
				Array.Copy(position, 0, result, 2, position.Length);
			} else if (resetSeek != null) {
				byte [] position = Helper.IntToByte(0);
				Array.Copy(position, 0, result, 2, position.Length);
			}
			
			return result;
		}
		
		public static byte [] SongInfo(int readBytes) {
			TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
			byte [] totalTime, year, song, artist, album, genre, artId;
			
			if (track != null) {
				string home = Environment.GetEnvironmentVariable("HOME");
				string coverPath = home + "/.cache/media-art/" + track.ArtworkId +".jpg";
				
				totalTime = Helper.IntToByte((uint) track.Duration.TotalMilliseconds);
				song = Helper.StringToByte(Helper.TrimString(track.TrackTitle));
				artist = Helper.StringToByte(Helper.TrimString(track.ArtistName));
				album = Helper.StringToByte(Helper.TrimString(track.AlbumTitle));
				genre = Helper.StringToByte(Helper.TrimString(track.Genre));
				year = Helper.ShortToByte((ushort) track.Year);
				artId = Helper.StringToByte(System.IO.File.Exists(coverPath) ? track.ArtworkId : "");
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
		
		public static byte [] SyncDatabase(int readBytes) {
			if (readBytes > 0) {
				if (Helper.Buffer[0] == 1) {
					if (File.Exists(Helper.DatabasePath(true))) {
						return Helper.IntToByte((uint) Helper.DbCompressTime);
					} else {
						Helper.CompressDatabase();
						return Helper.IntToByte(File.Exists(Helper.DatabasePath(true)) ? (uint) Helper.DbCompressTime : 0);
					}
				} else if (Helper.Buffer[0] == 2) {
					if (File.Exists(Helper.DatabasePath(true))) {
						return File.ReadAllBytes(Helper.DatabasePath(true));
					}
				} else if (Helper.Buffer[0] == 3) {
					Helper.DbCompressTime = 0;
					Helper.CompressDatabase();
					return new byte [] {1};
				}
			}
			
			return new byte [] {0};
		}
		
		public static byte [] Cover(int readBytes) {
			string artId = null;
			
			if (readBytes > 1) {
				int notNeeded;
				artId = Helper.StringFromBuffer(0, out notNeeded);
			}
			
			if (artId == null) {
				TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
				
				if (track != null) {
					artId = track.ArtworkId;
				}
			}
			
			if (artId != null) {
				string coverPath = CoverArtSpec.RootPath + "/" + artId + ".jpg";
				
				if (File.Exists(coverPath)) {
					return File.ReadAllBytes(coverPath);
				}
			}
			
			return new byte [] {0};
		}
		
		public static byte [] Playlist(int readBytes) {
			TrackListModel model = ServiceManager.PlaybackController.Source.TrackModel;
			
			int maxReturn = 0;
			uint startPosition = 0;
			
			if (readBytes > 3) {
				maxReturn = (int) Helper.IntFromBuffer(0);
			}
			
			if (readBytes > 7) {
				startPosition = Helper.IntFromBuffer(4) & 0xffffffff;
			}
			
			if (model != null) {
				int count = model.Count;
				int toCount = count;
				
				if ((startPosition & 0x80000000) != 0) {
					int pos = model.IndexOf(ServiceManager.PlayerEngine.CurrentTrack);
					startPosition = startPosition & 0x7fffffff;
					
					if (pos < 0 || startPosition > 100) {
						startPosition = 0;
					} else {
						int newStart = (int) (pos - startPosition);
						startPosition = newStart < 0 ? 0 : (uint) newStart;
					}
				}
				
				int returned = count - (int) startPosition;
				
				if (returned < 0) {
					returned = 0;
				} else if (maxReturn != 0 && returned > maxReturn) {
					returned = maxReturn;
					toCount = (int) startPosition + maxReturn;
				}
				
				byte [] result = new byte [12 + 4 * returned];
				Array.Copy(Helper.IntToByte((uint) count), 0, result, 0, 4);
				Array.Copy(Helper.IntToByte((uint) returned), 0, result, 4, 4);
				Array.Copy(Helper.IntToByte((uint) startPosition), 0, result, 8, 4);
				byte [] zeroId = new byte [] {0, 0, 0, 0};
				
				for (int i = (int) startPosition; i < toCount; i++) {
					TrackInfo track = (TrackInfo) model.GetItem(i);
					
					if (track is DatabaseTrackInfo) {
						Array.Copy(Helper.IntToByte((uint) ((DatabaseTrackInfo) track).TrackId),
						                     0, result, (i - startPosition) * 4 + 12, 4);
					} else {
						Array.Copy(zeroId, 0, result, (i - startPosition) * 4 + 12, 4);
					}
					//int id = DatabaseTrackInfo.GetTrackIdForUri(((TrackInfo) ).Uri);
				}
				
				return result;
			} else {
				return new byte [] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
			}
		}
		
		public static byte [] PlaylistControl(int readBytes) {
			if (readBytes > 0) {
				switch (Helper.Buffer[0]) {
				case 1: {
					if (readBytes > 4) {
						return new byte [] {(byte) (Helper.PlayTrack(Helper.IntFromBuffer(1)) ? 1 : 0)};
					}
					break;
				}
				case 2: {
					int trackIdCount = Helper.ShortFromBuffer(2);
					int artisIdCount = Helper.ShortFromBuffer(4);
					int albumIdCount = Helper.ShortFromBuffer(6);
					int failedCount = 0;
					
					break;
				}
				}
			}
			
			return new byte [] {0};
		}
		
		#endregion
	}
}

