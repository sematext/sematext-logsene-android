package com.sematext.logseneandroid;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent FIFO queue implementation with Sqlite.
 */
class SqliteObjectQueue {
  private final static String TABLE_NAME = "objects";
  private final static int DEFAULT_MAX_SIZE = 5000;
  private final SQLiteDatabase db;
  private final int maxSize;
  private final String dbName;
  private final static ConcurrentHashMap<String, Long> sizeCache = new ConcurrentHashMap<>();

  public class ObjectDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 2;

    public ObjectDbHelper(Context context, String dbName) {
      super(context, dbName, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE " + TABLE_NAME + " (id INTEGER PRIMARY KEY, data TEXT);");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME + ";");
      onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      onUpgrade(db, oldVersion, newVersion);
    }
  }

  /**
   * Constructor.
   *
   * @param context android context
   * @param dbName name to use for the sqlite database
   */
  public SqliteObjectQueue(Context context, String dbName) {
    Utils.requireNonNull(context);
    Utils.requireNonNull(dbName);
    ObjectDbHelper dbHelper = new ObjectDbHelper(context, dbName);
    this.db = dbHelper.getWritableDatabase();
    this.maxSize = DEFAULT_MAX_SIZE;
    this.dbName = dbName;
  }

  /**
   * Constructor.
   *
   * @param context android context
   * @param dbName name to use for the sqlite database
   * @param maxSize max size of the queue, older records will be overwritten
   */
  public SqliteObjectQueue(Context context, String dbName, int maxSize) {
    Utils.requireNonNull(context);
    Utils.requireNonNull(dbName);
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize must be greater than 0");
    }
    ObjectDbHelper dbHelper = new ObjectDbHelper(context, dbName);
    this.db = dbHelper.getWritableDatabase();
    this.maxSize = maxSize;
    this.dbName = dbName;
  }

  /**
   * Get size of the queue.
   * @return size of the queue.
   */
  public long size() {
    if (sizeCache.get(dbName) == null) {
      sizeCache.put(dbName, DatabaseUtils.queryNumEntries(db, TABLE_NAME));
    }

    return sizeCache.get(dbName);
  }

  /**
   * Pushes element to queue.
   */
  public void add(JSONObject obj) {
    Utils.requireNonNull(obj);
    db.execSQL("INSERT INTO " + TABLE_NAME + "(data) VALUES (?)", new Object[] { obj.toString() } );
    if (sizeCache.get(dbName) != null) {
      sizeCache.put(dbName, sizeCache.get(dbName) + 1);
    }
    if (size() > maxSize) {
      remove(1);
    }
  }

  /**
   * Retrieves up to specified amount of elements from queue, without removing them.
   * @param max max number of elements to return.
   * @return list of elements
   */
  public List<JSONObject> peek(int max) {
    if (max <= 0) {
      throw new IllegalArgumentException("max must be greater than 0");
    }

    List<JSONObject> results = new ArrayList<>();
    Cursor c = db.query(TABLE_NAME, new String[] { "data" }, null, null, null, null, "id asc", String.valueOf(max));
    try {
      while (c.moveToNext()) {
        String data = c.getString(c.getColumnIndex("data"));
        try {
          JSONObject o = new JSONObject(data);
          results.add(o);
        } catch (JSONException e) {
          throw new RuntimeException(e);
        }
      }
    } finally {
      c.close();
    }
    return results;
  }

  /**
   * Removes up to specified amount of elements from queue.
   * @param n amount of elements to remove.
   */
  public void remove(int n) {
    // On android sqlite deletes with limit and order keywords are disabled so we have to workaround it
    db.execSQL(String.format("DELETE FROM %s WHERE `id` IN (SELECT `id` FROM %s ORDER BY `id` ASC limit %d);",
            TABLE_NAME, TABLE_NAME, n));
    SQLiteStatement stmt = db.compileStatement("SELECT CHANGES()");
    long result = stmt.simpleQueryForLong();
    if (sizeCache.get(dbName) != null) {
      sizeCache.put(dbName, sizeCache.get(dbName) - result);
    }
  }
}
