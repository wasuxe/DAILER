package com.dailer.app.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.dailer.app.data.local.entity.CallRecordEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CallRecordDao_Impl implements CallRecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CallRecordEntity> __insertionAdapterOfCallRecordEntity;

  private final EntityDeletionOrUpdateAdapter<CallRecordEntity> __updateAdapterOfCallRecordEntity;

  private final SharedSQLiteStatement __preparedStmtOfMarkUploaded;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public CallRecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCallRecordEntity = new EntityInsertionAdapter<CallRecordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `call_records` (`id`,`callId`,`phoneNumber`,`relativeId`,`verdict`,`confidence`,`syntheticScore`,`speakerScore`,`timestamp`,`audioPath`,`callDirection`,`durationMs`,`isUploaded`,`reason`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CallRecordEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getCallId());
        statement.bindString(3, entity.getPhoneNumber());
        if (entity.getRelativeId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getRelativeId());
        }
        statement.bindString(5, entity.getVerdict());
        statement.bindDouble(6, entity.getConfidence());
        statement.bindDouble(7, entity.getSyntheticScore());
        statement.bindDouble(8, entity.getSpeakerScore());
        statement.bindLong(9, entity.getTimestamp());
        if (entity.getAudioPath() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getAudioPath());
        }
        statement.bindString(11, entity.getCallDirection());
        statement.bindLong(12, entity.getDurationMs());
        final int _tmp = entity.isUploaded() ? 1 : 0;
        statement.bindLong(13, _tmp);
        if (entity.getReason() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getReason());
        }
      }
    };
    this.__updateAdapterOfCallRecordEntity = new EntityDeletionOrUpdateAdapter<CallRecordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `call_records` SET `id` = ?,`callId` = ?,`phoneNumber` = ?,`relativeId` = ?,`verdict` = ?,`confidence` = ?,`syntheticScore` = ?,`speakerScore` = ?,`timestamp` = ?,`audioPath` = ?,`callDirection` = ?,`durationMs` = ?,`isUploaded` = ?,`reason` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CallRecordEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getCallId());
        statement.bindString(3, entity.getPhoneNumber());
        if (entity.getRelativeId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getRelativeId());
        }
        statement.bindString(5, entity.getVerdict());
        statement.bindDouble(6, entity.getConfidence());
        statement.bindDouble(7, entity.getSyntheticScore());
        statement.bindDouble(8, entity.getSpeakerScore());
        statement.bindLong(9, entity.getTimestamp());
        if (entity.getAudioPath() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getAudioPath());
        }
        statement.bindString(11, entity.getCallDirection());
        statement.bindLong(12, entity.getDurationMs());
        final int _tmp = entity.isUploaded() ? 1 : 0;
        statement.bindLong(13, _tmp);
        if (entity.getReason() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getReason());
        }
        statement.bindLong(15, entity.getId());
      }
    };
    this.__preparedStmtOfMarkUploaded = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE call_records SET isUploaded = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM call_records WHERE timestamp < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final CallRecordEntity record,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCallRecordEntity.insertAndReturnId(record);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final CallRecordEntity record,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfCallRecordEntity.handle(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markUploaded(final long id, final boolean isUploaded,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkUploaded.acquire();
        int _argIndex = 1;
        final int _tmp = isUploaded ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkUploaded.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOlderThan(final long timestamp,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestamp);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CallRecordEntity>> getAll() {
    final String _sql = "SELECT * FROM call_records ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"call_records"}, new Callable<List<CallRecordEntity>>() {
      @Override
      @NonNull
      public List<CallRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCallId = CursorUtil.getColumnIndexOrThrow(_cursor, "callId");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfRelativeId = CursorUtil.getColumnIndexOrThrow(_cursor, "relativeId");
          final int _cursorIndexOfVerdict = CursorUtil.getColumnIndexOrThrow(_cursor, "verdict");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfSyntheticScore = CursorUtil.getColumnIndexOrThrow(_cursor, "syntheticScore");
          final int _cursorIndexOfSpeakerScore = CursorUtil.getColumnIndexOrThrow(_cursor, "speakerScore");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfCallDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "callDirection");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfIsUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "isUploaded");
          final int _cursorIndexOfReason = CursorUtil.getColumnIndexOrThrow(_cursor, "reason");
          final List<CallRecordEntity> _result = new ArrayList<CallRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CallRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCallId;
            _tmpCallId = _cursor.getString(_cursorIndexOfCallId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpRelativeId;
            if (_cursor.isNull(_cursorIndexOfRelativeId)) {
              _tmpRelativeId = null;
            } else {
              _tmpRelativeId = _cursor.getString(_cursorIndexOfRelativeId);
            }
            final String _tmpVerdict;
            _tmpVerdict = _cursor.getString(_cursorIndexOfVerdict);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            final float _tmpSyntheticScore;
            _tmpSyntheticScore = _cursor.getFloat(_cursorIndexOfSyntheticScore);
            final float _tmpSpeakerScore;
            _tmpSpeakerScore = _cursor.getFloat(_cursorIndexOfSpeakerScore);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpCallDirection;
            _tmpCallDirection = _cursor.getString(_cursorIndexOfCallDirection);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final boolean _tmpIsUploaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUploaded);
            _tmpIsUploaded = _tmp != 0;
            final String _tmpReason;
            if (_cursor.isNull(_cursorIndexOfReason)) {
              _tmpReason = null;
            } else {
              _tmpReason = _cursor.getString(_cursorIndexOfReason);
            }
            _item = new CallRecordEntity(_tmpId,_tmpCallId,_tmpPhoneNumber,_tmpRelativeId,_tmpVerdict,_tmpConfidence,_tmpSyntheticScore,_tmpSpeakerScore,_tmpTimestamp,_tmpAudioPath,_tmpCallDirection,_tmpDurationMs,_tmpIsUploaded,_tmpReason);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getRecent(final int limit,
      final Continuation<? super List<CallRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM call_records ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CallRecordEntity>>() {
      @Override
      @NonNull
      public List<CallRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCallId = CursorUtil.getColumnIndexOrThrow(_cursor, "callId");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfRelativeId = CursorUtil.getColumnIndexOrThrow(_cursor, "relativeId");
          final int _cursorIndexOfVerdict = CursorUtil.getColumnIndexOrThrow(_cursor, "verdict");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfSyntheticScore = CursorUtil.getColumnIndexOrThrow(_cursor, "syntheticScore");
          final int _cursorIndexOfSpeakerScore = CursorUtil.getColumnIndexOrThrow(_cursor, "speakerScore");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfCallDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "callDirection");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfIsUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "isUploaded");
          final int _cursorIndexOfReason = CursorUtil.getColumnIndexOrThrow(_cursor, "reason");
          final List<CallRecordEntity> _result = new ArrayList<CallRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CallRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCallId;
            _tmpCallId = _cursor.getString(_cursorIndexOfCallId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpRelativeId;
            if (_cursor.isNull(_cursorIndexOfRelativeId)) {
              _tmpRelativeId = null;
            } else {
              _tmpRelativeId = _cursor.getString(_cursorIndexOfRelativeId);
            }
            final String _tmpVerdict;
            _tmpVerdict = _cursor.getString(_cursorIndexOfVerdict);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            final float _tmpSyntheticScore;
            _tmpSyntheticScore = _cursor.getFloat(_cursorIndexOfSyntheticScore);
            final float _tmpSpeakerScore;
            _tmpSpeakerScore = _cursor.getFloat(_cursorIndexOfSpeakerScore);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpCallDirection;
            _tmpCallDirection = _cursor.getString(_cursorIndexOfCallDirection);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final boolean _tmpIsUploaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUploaded);
            _tmpIsUploaded = _tmp != 0;
            final String _tmpReason;
            if (_cursor.isNull(_cursorIndexOfReason)) {
              _tmpReason = null;
            } else {
              _tmpReason = _cursor.getString(_cursorIndexOfReason);
            }
            _item = new CallRecordEntity(_tmpId,_tmpCallId,_tmpPhoneNumber,_tmpRelativeId,_tmpVerdict,_tmpConfidence,_tmpSyntheticScore,_tmpSpeakerScore,_tmpTimestamp,_tmpAudioPath,_tmpCallDirection,_tmpDurationMs,_tmpIsUploaded,_tmpReason);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByPhoneNumber(final String phoneNumber,
      final Continuation<? super List<CallRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM call_records WHERE phoneNumber = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, phoneNumber);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CallRecordEntity>>() {
      @Override
      @NonNull
      public List<CallRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCallId = CursorUtil.getColumnIndexOrThrow(_cursor, "callId");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfRelativeId = CursorUtil.getColumnIndexOrThrow(_cursor, "relativeId");
          final int _cursorIndexOfVerdict = CursorUtil.getColumnIndexOrThrow(_cursor, "verdict");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfSyntheticScore = CursorUtil.getColumnIndexOrThrow(_cursor, "syntheticScore");
          final int _cursorIndexOfSpeakerScore = CursorUtil.getColumnIndexOrThrow(_cursor, "speakerScore");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfCallDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "callDirection");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfIsUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "isUploaded");
          final int _cursorIndexOfReason = CursorUtil.getColumnIndexOrThrow(_cursor, "reason");
          final List<CallRecordEntity> _result = new ArrayList<CallRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CallRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCallId;
            _tmpCallId = _cursor.getString(_cursorIndexOfCallId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpRelativeId;
            if (_cursor.isNull(_cursorIndexOfRelativeId)) {
              _tmpRelativeId = null;
            } else {
              _tmpRelativeId = _cursor.getString(_cursorIndexOfRelativeId);
            }
            final String _tmpVerdict;
            _tmpVerdict = _cursor.getString(_cursorIndexOfVerdict);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            final float _tmpSyntheticScore;
            _tmpSyntheticScore = _cursor.getFloat(_cursorIndexOfSyntheticScore);
            final float _tmpSpeakerScore;
            _tmpSpeakerScore = _cursor.getFloat(_cursorIndexOfSpeakerScore);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpCallDirection;
            _tmpCallDirection = _cursor.getString(_cursorIndexOfCallDirection);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final boolean _tmpIsUploaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUploaded);
            _tmpIsUploaded = _tmp != 0;
            final String _tmpReason;
            if (_cursor.isNull(_cursorIndexOfReason)) {
              _tmpReason = null;
            } else {
              _tmpReason = _cursor.getString(_cursorIndexOfReason);
            }
            _item = new CallRecordEntity(_tmpId,_tmpCallId,_tmpPhoneNumber,_tmpRelativeId,_tmpVerdict,_tmpConfidence,_tmpSyntheticScore,_tmpSpeakerScore,_tmpTimestamp,_tmpAudioPath,_tmpCallDirection,_tmpDurationMs,_tmpIsUploaded,_tmpReason);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getPending(final Continuation<? super List<CallRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM call_records WHERE isUploaded = 0 ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CallRecordEntity>>() {
      @Override
      @NonNull
      public List<CallRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCallId = CursorUtil.getColumnIndexOrThrow(_cursor, "callId");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfRelativeId = CursorUtil.getColumnIndexOrThrow(_cursor, "relativeId");
          final int _cursorIndexOfVerdict = CursorUtil.getColumnIndexOrThrow(_cursor, "verdict");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfSyntheticScore = CursorUtil.getColumnIndexOrThrow(_cursor, "syntheticScore");
          final int _cursorIndexOfSpeakerScore = CursorUtil.getColumnIndexOrThrow(_cursor, "speakerScore");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfCallDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "callDirection");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfIsUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "isUploaded");
          final int _cursorIndexOfReason = CursorUtil.getColumnIndexOrThrow(_cursor, "reason");
          final List<CallRecordEntity> _result = new ArrayList<CallRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CallRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCallId;
            _tmpCallId = _cursor.getString(_cursorIndexOfCallId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpRelativeId;
            if (_cursor.isNull(_cursorIndexOfRelativeId)) {
              _tmpRelativeId = null;
            } else {
              _tmpRelativeId = _cursor.getString(_cursorIndexOfRelativeId);
            }
            final String _tmpVerdict;
            _tmpVerdict = _cursor.getString(_cursorIndexOfVerdict);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            final float _tmpSyntheticScore;
            _tmpSyntheticScore = _cursor.getFloat(_cursorIndexOfSyntheticScore);
            final float _tmpSpeakerScore;
            _tmpSpeakerScore = _cursor.getFloat(_cursorIndexOfSpeakerScore);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpCallDirection;
            _tmpCallDirection = _cursor.getString(_cursorIndexOfCallDirection);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final boolean _tmpIsUploaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUploaded);
            _tmpIsUploaded = _tmp != 0;
            final String _tmpReason;
            if (_cursor.isNull(_cursorIndexOfReason)) {
              _tmpReason = null;
            } else {
              _tmpReason = _cursor.getString(_cursorIndexOfReason);
            }
            _item = new CallRecordEntity(_tmpId,_tmpCallId,_tmpPhoneNumber,_tmpRelativeId,_tmpVerdict,_tmpConfidence,_tmpSyntheticScore,_tmpSpeakerScore,_tmpTimestamp,_tmpAudioPath,_tmpCallDirection,_tmpDurationMs,_tmpIsUploaded,_tmpReason);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
