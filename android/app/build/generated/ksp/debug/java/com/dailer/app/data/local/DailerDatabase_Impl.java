package com.dailer.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.dailer.app.data.local.dao.CallRecordDao;
import com.dailer.app.data.local.dao.CallRecordDao_Impl;
import com.dailer.app.data.local.dao.RelativeDao;
import com.dailer.app.data.local.dao.RelativeDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DailerDatabase_Impl extends DailerDatabase {
  private volatile RelativeDao _relativeDao;

  private volatile CallRecordDao _callRecordDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `relatives` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `enrollmentId` TEXT NOT NULL, `enrolledAt` INTEGER NOT NULL, `isVerified` INTEGER NOT NULL, `relationship` TEXT NOT NULL, `profileImageUri` TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relatives_phoneNumber` ON `relatives` (`phoneNumber`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relatives_enrollmentId` ON `relatives` (`enrollmentId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `call_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `callId` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `relativeId` TEXT, `verdict` TEXT NOT NULL, `confidence` REAL NOT NULL, `syntheticScore` REAL NOT NULL, `speakerScore` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `audioPath` TEXT, `callDirection` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `isUploaded` INTEGER NOT NULL, `reason` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f8f249359e2242f4535b0315daa58b16')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `relatives`");
        db.execSQL("DROP TABLE IF EXISTS `call_records`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsRelatives = new HashMap<String, TableInfo.Column>(8);
        _columnsRelatives.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRelatives.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRelatives.put("phoneNumber", new TableInfo.Column("phoneNumber", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRelatives.put("enrollmentId", new TableInfo.Column("enrollmentId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRelatives.put("enrolledAt", new TableInfo.Column("enrolledAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRelatives.put("isVerified", new TableInfo.Column("isVerified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRelatives.put("relationship", new TableInfo.Column("relationship", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRelatives.put("profileImageUri", new TableInfo.Column("profileImageUri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRelatives = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRelatives = new HashSet<TableInfo.Index>(2);
        _indicesRelatives.add(new TableInfo.Index("index_relatives_phoneNumber", false, Arrays.asList("phoneNumber"), Arrays.asList("ASC")));
        _indicesRelatives.add(new TableInfo.Index("index_relatives_enrollmentId", false, Arrays.asList("enrollmentId"), Arrays.asList("ASC")));
        final TableInfo _infoRelatives = new TableInfo("relatives", _columnsRelatives, _foreignKeysRelatives, _indicesRelatives);
        final TableInfo _existingRelatives = TableInfo.read(db, "relatives");
        if (!_infoRelatives.equals(_existingRelatives)) {
          return new RoomOpenHelper.ValidationResult(false, "relatives(com.dailer.app.data.local.entity.RelativeEntity).\n"
                  + " Expected:\n" + _infoRelatives + "\n"
                  + " Found:\n" + _existingRelatives);
        }
        final HashMap<String, TableInfo.Column> _columnsCallRecords = new HashMap<String, TableInfo.Column>(14);
        _columnsCallRecords.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("callId", new TableInfo.Column("callId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("phoneNumber", new TableInfo.Column("phoneNumber", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("relativeId", new TableInfo.Column("relativeId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("verdict", new TableInfo.Column("verdict", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("confidence", new TableInfo.Column("confidence", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("syntheticScore", new TableInfo.Column("syntheticScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("speakerScore", new TableInfo.Column("speakerScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("audioPath", new TableInfo.Column("audioPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("callDirection", new TableInfo.Column("callDirection", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("durationMs", new TableInfo.Column("durationMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("isUploaded", new TableInfo.Column("isUploaded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCallRecords.put("reason", new TableInfo.Column("reason", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCallRecords = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCallRecords = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCallRecords = new TableInfo("call_records", _columnsCallRecords, _foreignKeysCallRecords, _indicesCallRecords);
        final TableInfo _existingCallRecords = TableInfo.read(db, "call_records");
        if (!_infoCallRecords.equals(_existingCallRecords)) {
          return new RoomOpenHelper.ValidationResult(false, "call_records(com.dailer.app.data.local.entity.CallRecordEntity).\n"
                  + " Expected:\n" + _infoCallRecords + "\n"
                  + " Found:\n" + _existingCallRecords);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "f8f249359e2242f4535b0315daa58b16", "2240f387d6edda09bd03b22745330005");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "relatives","call_records");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `relatives`");
      _db.execSQL("DELETE FROM `call_records`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(RelativeDao.class, RelativeDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CallRecordDao.class, CallRecordDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public RelativeDao relativeDao() {
    if (_relativeDao != null) {
      return _relativeDao;
    } else {
      synchronized(this) {
        if(_relativeDao == null) {
          _relativeDao = new RelativeDao_Impl(this);
        }
        return _relativeDao;
      }
    }
  }

  @Override
  public CallRecordDao callRecordDao() {
    if (_callRecordDao != null) {
      return _callRecordDao;
    } else {
      synchronized(this) {
        if(_callRecordDao == null) {
          _callRecordDao = new CallRecordDao_Impl(this);
        }
        return _callRecordDao;
      }
    }
  }
}
