package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.os.Environment;

public class BansheeDatabase {
	
	public static boolean isDatabaseUpToDate(BansheeServer server, long dbSize) {
		long id = server.getSameHostId();
		
		if (id < 0) {
			id = server.getId();
		}
		
		if (!new File(Environment.getExternalStorageDirectory(), "BansheeRemote/" + id + ".db")
				.exists()) {
			return false;
		}
		
		long currentDbSize = server.getDbSize();
		BansheeServer same = BansheeServer.getServer(server.getSameHostId());
		
		if (same != null) {
			currentDbSize = server.getDbSize();
		}
		
		return currentDbSize == dbSize;
	}
	
	public static boolean updateDatabase(BansheeServer server, byte [] dbData) {
		if (server.getId() < 1) {
			throw new IllegalArgumentException("server is not a valid added server");
		}
		
		long id = server.getSameHostId();
		
		if (id < 0) {
			id = server.getId();
		}
		
		try {
			File file = new File(Environment.getExternalStorageDirectory(),
					"BansheeRemote/" + id + ".db");
			file.delete();
			OutputStream os = new FileOutputStream(file);
			os.write(dbData);
			os.close();
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
