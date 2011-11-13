using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

using Hyena;

using Banshee.Base;
using Banshee.Collection;
using Banshee.Collection.Database;
using Banshee.ServiceStack;
using Banshee.SmartPlaylist;
using Banshee.Sources;

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
		/// You'll find the full and detailed documentation in the project wiki;
		/// https://github.com/Knickedi/banshee-remote/wiki/Banshee-RemoteListener-Extension-API-Documentation
		public enum RequestCode {
			Test = 0,
			PlayerStatus = 1,
			SongInfo = 2,
			SyncDatabase = 3,
			Cover = 4,
			Playlist = 5,
		}
		
		#endregion
		
		
		#region Test
		
		public static byte [] Test(int readBytes) {
			return new byte[] {1};
		}
		
		#endregion
		
		
		#region Player status
		
		public static byte [] PlayerStatus(int readBytes) {
			bool? playing = null;
			bool? paused = null;
			uint? newPosition = null;
			
			if (readBytes > 0) {
				bool? resetSeek = null;
				
				// handle play mode request
				Helper.SetupPlayMode((Helper.Buffer[0] >> 4) & 0xf,
					out playing, out paused, out resetSeek);
				
				// handle repeat mode request
				Helper.SetupRepeatMode(Helper.Buffer[0] & 0xf);
				
				if (resetSeek != null) {
					newPosition = 0;
				}
			}
			
			if (readBytes > 1) {
				// handle shuffle mode request
				Helper.SetupShuffleMode(Helper.Buffer[1] & 0xf);
			}
			
			if (readBytes > 2) {
				// handle volume request
				Helper.SetupVolume(Helper.Buffer[2]);
			}
			
			if (readBytes > 6) {
				// handle seek position request
				Helper.SetupSeekPosition(Helper.IntFromBuffer(3), out newPosition);
			}
			
			// get current status
			byte [] result = Helper.PlayerStatusResult();
			bool forcePlaying = Helper.ForcePlay;
			
			// fix the paused flag (if needed)
			// banshee is not reporting a valid state after change!
			if (paused != null && !forcePlaying) {
				if (forcePlaying) {
					paused = false;
				}
				
				result[0] = (byte) ((result[0] & 0x7f) + ((bool) paused ? 0x80 : 0x0));
			}
			
			// fix the playing flag (if needed)
			// banshee is not reporting a valid state after change!
			if (playing != null || forcePlaying) {
				if (forcePlaying) {
					playing = true;
				}
				
				result[0] = (byte) ((result[0] & 0xbf) + ((bool) playing ? 0x40 : 0x0));
			}
			
			// fix current seek position (if needed)
			// banshee is not reporting a valid state after change!
			if (newPosition != null) {
				byte [] position = Helper.IntToByte((uint) newPosition);
				Array.Copy(position, 0, result, 2, position.Length);
			}
			
			return result;
		}
		
		#endregion
		
		
		#region Song info
		
		public static byte [] SongInfo(int readBytes) {
			TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
			byte [] totalTime, year, song, artist, album, genre, artId;
			
			if (track != null) {
				// track available - get the info
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
				// no track set everything to zero (empty strings)
				totalTime = year = song = artist = album = genre = artId = new byte [] {0, 0};
			}
			
			// copy collected information to a byte array
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
		
		#endregion
		
		
		#region Sync database
		
		public static byte [] SyncDatabase(int readBytes) {
			byte request = 0;
			
			if (readBytes > 0) {
				request = Helper.Buffer[0];
			}
			
			switch (request) {
			case 1:
				// requested database timestamp
				return Helper.IntToByte(File.Exists(Helper.DatabasePath(true))
					? (uint) Helper.DbCompressTime : 0);
				
			case 2:
				// requested database file
				if (File.Exists(Helper.DatabasePath(true))) {
					return File.ReadAllBytes(Helper.DatabasePath(true));
				}
				break;
				
			case 3:
				// requested forced recompression
				Helper.DbCompressTime = 0;
				Helper.CompressDatabase();
				return new byte [] {1};
			}
			
			return new byte [] {0};
		}
		
		#endregion
		
		
		#region Cover
		
		public static byte [] Cover(int readBytes) {
			string artId = null;
			
			if (readBytes > 1) {
				int notNeeded;
				artId = Helper.StringFromBuffer(0, out notNeeded);
			}
			
			// no art ID parameter, try to get that from the current track
			if (artId == null) {
				TrackInfo track = ServiceManager.PlayerEngine.CurrentTrack;
				
				if (track != null) {
					artId = track.ArtworkId;
				}
			}
			
			// if cover exists return it
			if (artId != null) {
				string coverPath = Path.Combine(CoverArtSpec.RootPath, artId + ".jpg");
				
				if (File.Exists(coverPath)) {
					return File.ReadAllBytes(coverPath);
				}
			}
			
			return new byte [] {0};
		}
		
		#endregion
		
		
		#region Playlist
		
		public static byte [] Playlist(int readBytes) {
			byte request = 0;
			
			if (readBytes > 0) {
				request = Helper.Buffer[0];
			}
			
			switch (request) {
			case 1: {
				// requested playlist names
				Source remotePlaylist = Helper.RemotePlaylist;
				((DatabaseSource) remotePlaylist).Reload();
				
				ushort count = 0;
				bool remotePlaylistAdded = false;
				int index = 4;
				
				Helper.Buffer[0] = Helper.Buffer[1] = 0;
				
				// - we're going to fill the buffer with all playlists which are not empty (expect remote playlist)
				// - we'll use the request buffer because it's big enough and we don't know the final size
				// - the remote playlist won't be listed if it's fresh created, so we do that manually
				//   we just fit it into position where the source enumaration would contain it 
				foreach (Source s in ServiceManager.SourceManager.Sources) {
					if (s is DatabaseSource && s != remotePlaylist && s.Parent == ServiceManager.SourceManager.MusicLibrary) {
						DatabaseSource so = s as DatabaseSource;
						Helper.ClearSourceFilters(so);
						
						if (so.TrackModel.Count > 0 || s == Helper.PlayQueuePlaylist) {
							if (!remotePlaylistAdded && String.Compare(s.Name, remotePlaylist.Name) >= 0) {
								count++;
								index = Helper.SourceAsPlaylistToBuffer(index, remotePlaylist);
								remotePlaylistAdded = true;
							}
							
							count++;
							index = Helper.SourceAsPlaylistToBuffer(index, s);
						}
					}
				}
				
				// remote playlist would be at the end so it's still not added to return - do it
				if (!remotePlaylistAdded) {
					count++;
					index = Helper.SourceAsPlaylistToBuffer(index, remotePlaylist);
				}
				
				// write playlist count to buffer
				Array.Copy(Helper.ShortToByte(count), 0, Helper.Buffer, 2, 2);
				
				// get needed bytes from buffer so we can return it
				byte [] result = new byte [index];
				Array.Copy(Helper.Buffer, 0, result, 0, index);
				
				return result;
			}
				
			case 2: {
				// requested tracks from playlist
				ushort playlistId = 0;
				int maxReturn = 0;
				uint startPosition = 0;
				
				// get parameters if given
				if (readBytes > 2) {
					playlistId = Helper.ShortFromBuffer(1);
				}
				if (readBytes > 6) {
					maxReturn = (int) Helper.IntFromBuffer(3);
				}
				if (readBytes > 10) {
					startPosition = Helper.IntFromBuffer(7) & 0xffffffff;
				}
				
				// search for the playlist which was requested
				Source source = Helper.GetPlaylistSource(playlistId);
				TrackListModel model = null;
				
				if (source != null) {
					model = ((ITrackModelSource) source).TrackModel;
				}
				
				if (model != null) {
					// playlist exists, return the requested amount of track IDs
					
					int count = model.Count;
					int toCount = count;
					
					// request wants a return from the current track position
					// let's see if this model contains the currently played track
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
					
					// correct te amount of returned tracks
					if (returned < 0) {
						returned = 0;
					} else if (maxReturn != 0 && returned > maxReturn) {
						returned = maxReturn;
						toCount = (int) startPosition + maxReturn;
					}
					
					// get requested tracks and write it in the result buffer
					byte [] result = new byte [12 + 4 * returned];
					Array.Copy(Helper.IntToByte((uint) count), 0, result, 0, 4);
					Array.Copy(Helper.IntToByte((uint) returned), 0, result, 4, 4);
					Array.Copy(Helper.IntToByte((uint) startPosition), 0, result, 8, 4);
					byte [] zeroId = new byte [] {0, 0, 0, 0};
					
					for (int i = (int) startPosition; i < toCount; i++) {
						TrackInfo track = (TrackInfo) model.GetItem(i);
						Array.Copy(track is DatabaseTrackInfo
							? Helper.IntToByte((uint) ((DatabaseTrackInfo) track).TrackId) : zeroId,
							0, result, (i - startPosition) * 4 + 12, 4);
					}
					
					return result;
				} else {
					// count = returned = startPosition = 0
					return new byte [] {
						0, 0, 0, 0,
						0, 0, 0, 0,
						0, 0, 0, 0};
				}
			}
				
			case 3: {
				// requested to play tracks
				int playlistId = 0;
				long trackId = 0;
				
				// get parameters if given
				if (readBytes > 6) {
					playlistId = Helper.ShortFromBuffer(1);
					trackId = Helper.IntFromBuffer(3);
				}
				
				return new byte [] {Helper.PlayTrack(playlistId, trackId)};
			}
				
			case 4:
				if (readBytes > 7 && Helper.AddTrackToPlayList(
						Helper.ShortFromBuffer(2),
						(int) Helper.IntFromBuffer(4),
				    	(Helper.Buffer[1] & 0x1) != 0)) {
					return Helper.ShortToByte(1);
				}
				
				return Helper.ShortToByte(0);
				
			case 5:
				if (readBytes > 6 && Helper.RemoveTrackFromPlaylist(
						Helper.ShortFromBuffer(1), (int) Helper.IntFromBuffer(3))) {
					return Helper.ShortToByte(1);
				}
				
				return Helper.ShortToByte(0);
				
			case 6:
				return Helper.ShortToByte(0);
				
			case 7:
				return Helper.ShortToByte(0);
				
			case 8:
				return Helper.ShortToByte(0);
				
			case 9:
				return Helper.ShortToByte(0);
			}
			
			return new byte [] {0};
		}
		
		#endregion
	}
}

