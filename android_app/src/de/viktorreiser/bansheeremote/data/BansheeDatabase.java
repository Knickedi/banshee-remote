package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.viktorreiser.bansheeremote.R;

/**
 * This class handles the synchronized banshee database(s).
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeDatabase {
	
	// PRIVATE ====================================================================================
	
	private static SQLiteDatabase mBansheeDatabase;
	private static BansheeServer mServer;
	
	private static Set<Track> mOrderedTrackInfo = null;
	private static Map<Long, Track> mTrackInfo = null;
	private static Set<Album> mOrderedAlbumInfo = null;
	private static Map<Long, Album> mAlbumInfo = null;
	private static Set<Artist> mOrderedArtistInfo = null;
	private static Map<Long, Artist> mArtistInfo = null;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Track information returned by database requests.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static class Track {
		
		private static Track createUnknown() {
			Track i = new Track();
			i.title = App.getContext().getString(R.string.unknown_track);
			i.genre = "";
			return i;
		}
		
		private long id;
		private long artistId;
		private long albumId;
		private String title;
		private int trackNumber;
		private int duration;
		private short year;
		private String genre;
		private byte rating;
		private Album album;
		private Artist artist;
		
		public long getId() {
			return id;
		}
		
		public long getArtistId() {
			return artistId;
		}
		
		public long getAlbumId() {
			return albumId;
		}
		
		public String getTitle() {
			return title;
		}
		
		public int getTrackNumber() {
			return trackNumber;
		}
		
		public int getDuration() {
			return duration;
		}
		
		public short getYear() {
			return year;
		}
		
		public String getGenre() {
			return genre;
		}
		
		public byte getRating() {
			return rating;
		}
		
		public Album getAlbum() {
			if (album == null) {
				album = BansheeDatabase.getAlbum(albumId);
			}
			
			return album;
		}
		
		public Artist getArtist() {
			if (artist == null) {
				artist = BansheeDatabase.getArtist(artistId);
			}
			
			return artist;
		}
	}
	
	/**
	 * Album information returned by database requests.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static class Album {
		
		private static Album createUnknown() {
			Album i = new Album();
			i.title = App.getContext().getString(R.string.unknown_album);
			i.artId = "";
			return i;
		}
		
		private long id;
		private long artistId;
		private String title;
		private String artId;
		private int trackCount;
		private Artist artist;
		
		public long getId() {
			return id;
		}
		
		public long getArtistId() {
			return artistId;
		}
		
		public String getTitle() {
			return title;
		}
		
		public String getArtId() {
			return artId;
		}
		
		public int getTrackCount() {
			return trackCount;
		}
		
		public Artist getArtist() {
			if (artist == null) {
				artist = BansheeDatabase.getArtist(artistId);
			}
			
			return artist;
		}
	}
	
	/**
	 * Artist information returned by database requests.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static class Artist {
		
		private static Artist createUnknown() {
			Artist i = new Artist();
			i.name = App.getContext().getString(R.string.unknown_artist);
			return i;
		}
		
		private long id;
		private String name;
		private int trackCount;
		private int albumCount;
		
		public long getId() {
			return id;
		}
		
		public String getName() {
			return name;
		}
		
		public int getTrackCount() {
			return trackCount;
		}
		
		public int getAlbumCount() {
			return albumCount;
		}
	}
	
	
	/**
	 * Get all tracks ordered ascending by track title.
	 * 
	 * @return all tracks
	 */
	public static Track [] getOrderedTracks() {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		return mOrderedTrackInfo.toArray(new Track [0]);
	}
	
	/**
	 * Get tracks of an album ordered by their track numbers.
	 * 
	 * @param id
	 *            ID of album
	 * 
	 * @return tracks of the album or empty array for an invalid ID
	 */
	public static Track [] getOrderedTracksOfAlbum(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		List<Track> info = new LinkedList<Track>();
		
		for (Track i : mOrderedTrackInfo) {
			if (i.getAlbumId() == id) {
				info.add(i);
			}
		}
		
		Collections.sort(info, new Comparator<Track>() {
			@Override
			public int compare(Track lhs, Track rhs) {
				return lhs.trackNumber - rhs.trackNumber;
			}
		});
		
		return info.toArray(new Track [0]);
	}
	
	/**
	 * Get all tracks of an artist ordered ascending by track title.
	 * 
	 * @param id
	 *            artist ID
	 * 
	 * @return all tracks of an artist or empty array for an invalid ID
	 */
	public static Track [] getOrderedTracksOfArtist(long id) {
		if (!isOpen()) {
			return null;
		}
		
		List<Track> info = new LinkedList<Track>();
		
		for (Track i : mOrderedTrackInfo) {
			if (i.getArtistId() == id) {
				info.add(i);
			}
		}
		
		return info.toArray(new Track [0]);
	}
	
	/**
	 * Get track information.
	 * 
	 * @param id
	 *            track ID
	 * 
	 * @return track information (which will be filled with default data if ID is invalid)
	 */
	public static Track getTrack(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		Track i = mTrackInfo.get(id);
		return i == null ? Track.createUnknown() : i;
	}
	
	/**
	 * Get track information.<br>
	 * <br>
	 * The difference is that this request won't trigger {@link #setupDbCache()}. This has to be
	 * done once but takes some time to finish. So this should be done for heavy database use but we
	 * don't need to perform that for a single track lookup.
	 * 
	 * @param id
	 *            track ID
	 * 
	 * @return track information (which will be filled with default data if ID is invalid)
	 */
	public static Track getUncachedTrack(long id) {
		if (!isOpen()) {
			return Track.createUnknown();
		}
		
		if (mOrderedTrackInfo != null) {
			Track i = mTrackInfo.get(id);
			return i == null ? Track.createUnknown() : i;
		}
		
		Track i = null;
		
		Cursor c = mBansheeDatabase.rawQuery(""
				+ "SELECT t." + DB.ID + ", t." + DB.ARTIST_ID + ", t." + DB.ALBUM_ID
				+ ", t." + DB.TITLE + ", t." + DB.TRACK_NUMBER + ", t." + DB.DURATION
				+ ", t." + DB.YEAR + ", t." + DB.GENRE + ", a." + DB.NAME
				+ ", l." + DB.TITLE + ", l." + DB.ART_ID + ", t." + DB.RATING
				+ " FROM " + DB.TABLE_TRACKS + " AS t"
				+ " JOIN " + DB.TABLE_ARTISTS + " AS a, " + DB.TABLE_ALBUMS + " AS l"
				+ " ON a." + DB.ID + "=t." + DB.ARTIST_ID
				+ " AND l." + DB.ID + "=t." + DB.ALBUM_ID
				+ " WHERE t." + DB.ID + "=" + id,
				null);
		
		if (c.moveToFirst()) {
			i = new Track();
			
			String title = cleanString(c, 3);
			String artist = cleanString(c, 8);
			String album = cleanString(c, 9);
			
			i.id = c.getLong(0);
			i.artistId = c.getLong(1);
			i.albumId = c.getLong(2);
			i.title = "".equals(title) ? App.getContext().getString(R.string.unknown_track) : title;
			i.trackNumber = cleanInt(c, 4);
			i.duration = cleanInt(c, 5);
			i.year = (short) cleanInt(c, 6);
			i.genre = cleanString(c, 7);
			i.rating = (byte) cleanInt(c, 11);
			
			i.artist = new Artist();
			i.artist.id = i.artistId;
			i.artist.name = "".equals(artist)
					? App.getContext().getString(R.string.unknown_artist) : artist;
			
			i.album = new Album();
			i.album.id = i.albumId;
			i.album.artistId = i.artistId;
			i.album.artist = i.artist;
			i.album.title = "".equals(album)
					? App.getContext().getString(R.string.unknown_album) : album;
			i.album.artId = cleanString(c, 10);
		} else {
			i = Track.createUnknown();
			i.album = Album.createUnknown();
			i.artist = Artist.createUnknown();
		}
		
		c.close();
		
		return i;
	}
	
	/**
	 * Get all albums ordered ascending by album title.
	 * 
	 * @return all albums
	 */
	public static Album [] getOrderedAlbums() {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		return mOrderedAlbumInfo.toArray(new Album [0]);
	}
	
	/**
	 * Get all albums of an artist ordered ascending by artist ID.
	 * 
	 * @param id
	 *            artist ID
	 * 
	 * @return all albums of an artist or emtpy array for an invalid ID
	 */
	public static Album [] getOrderedAlbumsOfArtist(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		List<Album> info = new LinkedList<Album>();
		
		for (Album i : mOrderedAlbumInfo) {
			if (i.getArtistId() == id) {
				info.add(i);
			}
		}
		
		return info.toArray(new Album [0]);
	}
	
	/**
	 * Get album information.
	 * 
	 * @param id
	 *            album ID
	 * 
	 * @return album information (which will be filled with default data if ID is invalid)
	 */
	public static Album getAlbum(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		Album i = mAlbumInfo.get(id);
		return i == null ? Album.createUnknown() : i;
	}
	
	/**
	 * Get all artists ordered ascending by name.
	 * 
	 * @return all artists
	 */
	public static Artist [] getOrderedArtists() {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		return mOrderedArtistInfo.toArray(new Artist [0]);
	}
	
	/**
	 * Get artist information.
	 * 
	 * @param id
	 *            artist ID
	 * 
	 * @return artist information (which will be filled with default data if ID is invalid)
	 */
	public static Artist getArtist(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		Artist i = mArtistInfo.get(id);
		return i == null ? Artist.createUnknown() : i;
	}
	
	
	/**
	 * Read whole database into memory for a quick lookup.
	 */
	public static void setupDbCache() {
		if (!isOpen() || mOrderedTrackInfo != null) {
			return;
		}
		
		mTrackInfo = new TreeMap<Long, Track>();
		mAlbumInfo = new TreeMap<Long, Album>();
		mArtistInfo = new TreeMap<Long, Artist>();
		
		mOrderedTrackInfo = new TreeSet<Track>(new Comparator<Track>() {
			@Override
			public int compare(Track lhs, Track rhs) {
				char lc = lhs.title.charAt(0);
				char rc = rhs.title.charAt(0);
				boolean ra = Character.isLetter(rc);
				int result;
				
				if (Character.isLetter(lc)) {
					result = ra ? lhs.title.compareToIgnoreCase(rhs.title) : 1;
				} else {
					result = ra ? -1 : lhs.title.compareToIgnoreCase(rhs.title);
				}
				
				return result == 0 ? -1 : result;
			}
		});
		
		Cursor c = mBansheeDatabase.query(
				DB.TABLE_TRACKS,
				new String [] {
						DB.ID, DB.ARTIST_ID, DB.ALBUM_ID, DB.TITLE,
						DB.TRACK_NUMBER, DB.DURATION, DB.YEAR, DB.GENRE, DB.RATING},
				null, null, null, null, null);
		
		while (c.moveToNext()) {
			String title = cleanString(c, 3);
			Track i = new Track();
			
			i.id = c.getLong(0);
			i.artistId = c.getLong(1);
			i.albumId = c.getLong(2);
			i.title = "".equals(title) ? App.getContext().getString(R.string.unknown_track) : title;
			i.trackNumber = cleanInt(c, 4);
			i.duration = cleanInt(c, 5);
			i.year = (short) cleanInt(c, 6);
			i.genre = cleanString(c, 7);
			i.rating = (byte) cleanInt(c, 8);
			
			mOrderedTrackInfo.add(i);
			mTrackInfo.put(i.id, i);
		}
		
		mOrderedAlbumInfo = new TreeSet<Album>(new Comparator<Album>() {
			@Override
			public int compare(Album lhs, Album rhs) {
				char lc = lhs.title.charAt(0);
				char rc = rhs.title.charAt(0);
				boolean ra = Character.isLetter(rc);
				int result;
				
				if (Character.isLetter(lc)) {
					result = ra ? lhs.title.compareToIgnoreCase(rhs.title) : 1;
				} else {
					result = ra ? -1 : lhs.title.compareToIgnoreCase(rhs.title);
				}
				
				return result == 0 ? -1 : result;
			}
		});
		
		c.close();
		c = mBansheeDatabase.query(
				DB.TABLE_ALBUMS,
				new String [] {DB.ID, DB.ARTIST_ID, DB.TITLE, DB.ART_ID},
				null, null, null, null, null);
		
		while (c.moveToNext()) {
			String title = cleanString(c, 2);
			Album i = new Album();
			
			i.id = c.getLong(0);
			i.artistId = c.getLong(1);
			i.title = "".equals(title) ? App.getContext().getString(R.string.unknown_album) : title;
			i.artId = cleanString(c, 3);
			
			mOrderedAlbumInfo.add(i);
			mAlbumInfo.put(i.id, i);
		}
		
		mOrderedArtistInfo = new TreeSet<Artist>(new Comparator<Artist>() {
			@Override
			public int compare(Artist lhs, Artist rhs) {
				char lc = lhs.name.charAt(0);
				char rc = rhs.name.charAt(0);
				boolean ra = Character.isLetter(rc);
				int result;
				
				if (Character.isLetter(lc)) {
					result = ra ? lhs.name.compareToIgnoreCase(rhs.name) : 1;
				} else {
					result = ra ? -1 : lhs.name.compareToIgnoreCase(rhs.name);
				}
				
				return result == 0 ? -1 : result;
			}
		});
		
		c.close();
		c = mBansheeDatabase.query(
				DB.TABLE_ARTISTS,
				new String [] {DB.ID, DB.NAME},
				null, null, null, null, null);
		
		while (c.moveToNext()) {
			String title = cleanString(c, 1);
			Artist i = new Artist();
			
			i.id = c.getLong(0);
			i.name = "".equals(title) ? App.getContext().getString(R.string.unknown_artist) : title;
			
			mOrderedArtistInfo.add(i);
			mArtistInfo.put(i.id, i);
		}
		
		c.close();
		
		c = mBansheeDatabase.query(
				DB.TABLE_TRACKS,
				new String [] {DB.ARTIST_ID, "COUNT(*)"},
				null, null, DB.ARTIST_ID, null, null);
		
		while (c.moveToNext()) {
			Artist i = mArtistInfo.get(c.getLong(0));
			i.trackCount = c.getInt(1);
		}
		
		c.close();
		c = mBansheeDatabase.query(
				DB.TABLE_TRACKS,
				new String [] {DB.ALBUM_ID, "COUNT(" + DB.ID + ")"},
				null, null, DB.ALBUM_ID, null, null);
		
		while (c.moveToNext()) {
			mAlbumInfo.get(c.getLong(0)).trackCount = c.getInt(1);
		}
		
		for (Album album : mOrderedAlbumInfo) {
			Artist artist = mArtistInfo.get(album.getArtistId());
			
			if (artist != null) {
				artist.albumCount++;
			}
		}
		
		c.close();
	}
	
	/**
	 * Is database up to date?
	 * 
	 * @param server
	 *            banshee server to check
	 * @param timestamp
	 *            timestamp returned by server
	 * 
	 * @return {@code true} if database exists and has the same timestamp
	 */
	public static boolean isDatabaseUpToDate(BansheeServer server, long timestamp) {
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (id > 0 && same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		} else if (id > 0) {
			server = same;
		} else {
			id = server.getId();
		}
		
		if (!new File(App.CACHE_PATH + id + App.DB_EXT).exists()) {
			return false;
		} else {
			return timestamp <= server.mDbTimestamp;
		}
	}
	
	/**
	 * Persist a new database for a banshee server.
	 * 
	 * @param server
	 *            banshee server for which will use the database
	 * @param dbData
	 *            database file as raw byte array
	 * @param timestamp
	 *            timestamp of database received from previous request
	 * 
	 * @return {@code true} if database was updated successfully ({@link #open(BansheeServer)} is
	 *         called automatically) otherwise {@code false} and no database is bound anymore (e.g.
	 *         when given data doesn't represent a valid database)
	 */
	public static boolean updateDatabase(BansheeServer server, byte [] dbData, int timestamp) {
		if (server.getId() < 1) {
			throw new IllegalArgumentException("server is not a valid added server");
		}
		
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (id > 0 && same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		} else if (id < 1) {
			id = server.getId();
		} else {
			server = same;
		}
		
		try {
			close();
			
			File file = new File(App.CACHE_PATH + id + App.DB_EXT);
			
			file.getParentFile().mkdirs();
			file.delete();
			
			OutputStream os = new FileOutputStream(file);
			os.write(dbData);
			os.close();
			
			if (!open(server)) {
				file.delete();
				return false;
			}
			
			if (same == null) {
				server.mDbTimestamp = timestamp;
				BansheeServer.updateServer(server);
			} else {
				same.mDbTimestamp = timestamp;
				BansheeServer.updateServer(same);
			}
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Open database for access.
	 * 
	 * @param server
	 *            banshee server for which a database should be opened
	 * 
	 * @return {@code true} if database was opened and is ready for access, otherwise {@code false}
	 *         (because there is no synchronized database)
	 */
	public static boolean open(BansheeServer server) {
		mServer = null;
		
		if (server.getId() < 1) {
			throw new IllegalArgumentException("server is not a valid added server");
		}
		
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (id > 0 && same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
		}
		
		if (id < 1) {
			id = server.getId();
		}
		
		close();
		
		File file = new File(App.CACHE_PATH + id + App.DB_EXT);
		
		if (file.exists()) {
			try {
				mBansheeDatabase = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
						SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			} catch (Exception e) {
				mBansheeDatabase = null;
				return false;
			}
		} else {
			return false;
		}
		
		try {
			mBansheeDatabase.query(DB.TABLE_TRACKS, new String [] {DB.RATING},
					null, null, null, null, null, "1");
		} catch (Exception e) {
			// upgrade - new column "rating" - add it if it is missing
			mBansheeDatabase.execSQL("ALTER TABLE " + DB.TABLE_TRACKS
					+ " ADD COLUMN " + DB.RATING + " INTEGER NOT NULL DEFAULT 0;");
		}
		
		mServer = server;
		
		return true;
	}
	
	/**
	 * Is there an open database?
	 * 
	 * @return {@code true} if a database is open and ready for access
	 */
	public static boolean isOpen() {
		return mBansheeDatabase != null;
	}
	
	/**
	 * Force database close (if open).
	 */
	public static void close() {
		if (isOpen()) {
			mBansheeDatabase.close();
			mBansheeDatabase = null;
			mServer = null;
		}
		
		mOrderedAlbumInfo = null;
		mOrderedArtistInfo = null;
		mOrderedTrackInfo = null;
		mAlbumInfo = null;
		mArtistInfo = null;
		mTrackInfo = null;
	}
	
	/**
	 * Get the banshee server which was given to open the database.
	 * 
	 * @return banshee server or {@code null} when no database is open
	 */
	public static BansheeServer getServer() {
		return mServer;
	}
	
	// PRIVATE ====================================================================================
	
	/**
	 * Get string from cursor.
	 * 
	 * @param c
	 *            cursor
	 * @param index
	 *            index from which the string should be get
	 * 
	 * @return if string is {@code null} you'll get an empty string otherwise the string itself
	 */
	private static String cleanString(Cursor c, int index) {
		return c.isNull(index) ? "" : c.getString(index).trim();
	}
	
	/**
	 * Get integer from cursor.
	 * 
	 * @param c
	 *            cursor
	 * @param index
	 *            index from which the integer should be get
	 * 
	 * @return if integer is {@code null} you'll {@code -1} otherwise the value itself
	 */
	private static int cleanInt(Cursor c, int index) {
		return c.isNull(index) ? -1 : c.getInt(index);
	}
	
	/**
	 * Database (column) constants for the synchronized database.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private static class DB {
		public static final String TABLE_TRACKS = "tracks";
		public static final String TABLE_ARTISTS = "artists";
		public static final String TABLE_ALBUMS = "albums";
		
		public static final String ID = "_id";
		public static final String ARTIST_ID = "artistId";
		public static final String ALBUM_ID = "albumId";
		public static final String TITLE = "title";
		public static final String DURATION = "duration";
		public static final String YEAR = "year";
		public static final String GENRE = "genre";
		public static final String RATING = "rating";
		public static final String NAME = "name";
		public static final String ART_ID = "artId";
		public static final String TRACK_NUMBER = "trackNumber";
	}
}
