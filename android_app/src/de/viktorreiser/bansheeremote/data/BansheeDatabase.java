package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * This class handles the synchronized banshee database(s).
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeDatabase {
	
	// PRIVATE ====================================================================================
	
	private static SQLiteDatabase mBansheeDatabase;
	private static BansheeServer mServer;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Track data returned by a database request.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static class TrackInfo {
		public long id;
		public long aritstId;
		public long albumId;
		public String title;
		public String artist;
		public String album;
		public String genre;
		public int year;
		public long totalTime;
		public int trackNumber;
		public String artId;
	}
	
	/**
	 * Artist data returned by a database request.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static class ArtistInfo {
		public long id;
		public String name;
		public int trackCount;
		public int albumCount;
	}
	
	/**
	 * Album data returned by a database request.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	public static class AlbumInfo {
		public long id;
		public String title;
		public String artId;
		public int trackCount;
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
		
		if (!new File(App.BANSHEE_PATH + id + App.DB_EXT).exists()) {
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
			File file = new File(App.BANSHEE_PATH + id + App.DB_EXT);
			
			file.getParentFile().mkdir();
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
		
		File file = new File(App.BANSHEE_PATH + id + App.DB_EXT);
		
		if (file.exists()) {
			try {
				mBansheeDatabase = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
						SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			} catch (Exception e) {
				mBansheeDatabase = null;
				return false;
			}
		} else {
			return false;
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
	}
	
	/**
	 * Get the banshee server which was given to open the database.
	 * 
	 * @return banshee server or {@code null} when no database is open
	 */
	public static BansheeServer getServer() {
		return mServer;
	}
	
	/**
	 * Get track info for a track.
	 * 
	 * @param id
	 *            ID of track
	 * 
	 * @return track info or {@code null} if no database is open or the given track ID is not found
	 *         in the database
	 */
	public static TrackInfo getTrackInfo(long id) {
		if (!isOpen()) {
			return null;
		}
		
		Cursor cursor = mBansheeDatabase.rawQuery(""
				+ " SELECT t." + DB.ID + ", t." + DB.TITLE + ", t." + DB.DURATION
				+ ", t." + DB.YEAR + ", t." + DB.GENRE + ", r." + DB.NAME
				+ ", l." + DB.TITLE + ", l." + DB.ART_ID + ", r." + DB.ID + ", l." + DB.ID
				+ ", t." + DB.TRACK_NUMBER
				+ " FROM " + DB.TABLE_TRACKS + " AS t"
				+ " JOIN " + DB.TABLE_ARTISTS + " AS r, " + DB.TABLE_ALBUM + " AS l"
				+ " ON r." + DB.ID + "=t." + DB.ARTIST_ID
				+ " AND l." + DB.ID + "=t." + DB.ALBUM_ID
				+ " WHERE t." + DB.ID + "=" + id, null);
		
		if (cursor.moveToFirst()) {
			TrackInfo info = new TrackInfo();
			
			info.id = cursor.getLong(0);
			info.title = cleanString(cursor, 1);
			info.totalTime = cursor.getLong(2);
			info.year = cleanInt(cursor, 3);
			info.genre = cleanString(cursor, 4);
			info.artist = cleanString(cursor, 5);
			info.album = cleanString(cursor, 6);
			info.artId = cleanString(cursor, 7);
			info.aritstId = cursor.getLong(8);
			info.albumId = cursor.getLong(9);
			info.trackNumber = cleanInt(cursor, 10);
			
			cursor.close();
			return info;
		} else {
			cursor.close();
			return null;
		}
	}
	
	public static List<ArtistInfo> getArtistInfo() {
		if (!isOpen()) {
			return null;
		}
		
		List<ArtistInfo> artistInfo = new ArrayList<ArtistInfo>();
		
		Cursor cursor = mBansheeDatabase.rawQuery(""
				+ "SELECT a." + DB.ID + ",a." + DB.NAME
				+ ", COUNT(*), COUNT(DISTINCT " + DB.ALBUM_ID + ")"
				+ " FROM " + DB.TABLE_TRACKS + " AS t"
				+ " JOIN " + DB.TABLE_ARTISTS + " AS a"
				+ " ON a." + DB.ID + "=t." + DB.ARTIST_ID
				+ " GROUP BY t." + DB.ARTIST_ID
				+ " ORDER BY a." + DB.NAME,
				null);
		
		while (cursor.moveToNext()) {
			ArtistInfo i = new ArtistInfo();
			i.id = cursor.getLong(0);
			i.name = cleanString(cursor, 1);
			i.trackCount = cursor.getInt(2);
			i.albumCount = cursor.getInt(3);
			artistInfo.add(i);
		}
		
		cursor.close();
		
		return artistInfo;
	}
	
	public static ArtistInfo getArtistInfo(long id) {
		if (!isOpen()) {
			return null;
		}
		
		Cursor cursor = mBansheeDatabase.rawQuery(""
				+ "SELECT a." + DB.ID + ",a." + DB.NAME
				+ ", COUNT(*), COUNT(DISTINCT " + DB.ALBUM_ID + ")"
				+ " FROM " + DB.TABLE_TRACKS + " AS t"
				+ " JOIN " + DB.TABLE_ARTISTS + " AS a"
				+ " ON a." + DB.ID + "=t." + DB.ARTIST_ID
				+ " WHERE t." + DB.ARTIST_ID + "=" + id
				+ " GROUP BY t." + DB.ARTIST_ID
				+ " ORDER BY a." + DB.NAME,
				null);
		
		ArtistInfo aritstInfo = new ArtistInfo();
		
		if (cursor.moveToFirst()) {
			aritstInfo.id = cursor.getLong(0);
			aritstInfo.name = cleanString(cursor, 1);
			aritstInfo.trackCount = cursor.getInt(2);
			aritstInfo.albumCount = cursor.getInt(3);
		}
		
		cursor.close();
		
		return aritstInfo;
	}
	
	public static List<AlbumInfo> getAlbumInfoOfArtist(long id) {
		if (!isOpen()) {
			return null;
		}
		
		List<AlbumInfo> info = new ArrayList<AlbumInfo>();
		
		Cursor cursor = mBansheeDatabase.rawQuery(""
				+ "SELECT a." + DB.ID + ", a." + DB.TITLE + ", a." + DB.ART_ID
				+ ", COUNT(a." + DB.ID + ")"
				+ " FROM " + DB.TABLE_TRACKS + " AS t"
				+ " JOIN " + DB.TABLE_ALBUM + " AS a"
				+ " ON a." + DB.ID + "=t." + DB.ALBUM_ID
				+ " WHERE t." + DB.ARTIST_ID + "=" + id
				+ " GROUP BY a." + DB.ID + ", a." + DB.TITLE + ", a." + DB.ART_ID
				+ " ORDER BY a." + DB.TITLE + " ASC;",
				null);
		
		while (cursor.moveToNext()) {
			AlbumInfo i = new AlbumInfo();
			i.id = cursor.getLong(0);
			i.title = cleanString(cursor, 1);
			i.artId = cleanString(cursor, 2);
			i.trackCount = cursor.getInt(3);
			info.add(i);
		}
		
		cursor.close();
		
		return info;
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
		return c.isNull(index) ? "" : c.getString(index);
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
		public static final String TABLE_ALBUM = "albums";
		
		public static final String ID = "_id";
		public static final String ARTIST_ID = "artistId";
		public static final String ALBUM_ID = "albumId";
		public static final String TITLE = "title";
		public static final String DURATION = "duration";
		public static final String YEAR = "year";
		public static final String GENRE = "genre";
		public static final String NAME = "name";
		public static final String ART_ID = "artId";
		public static final String TRACK_NUMBER = "trackNumber";
	}
}
