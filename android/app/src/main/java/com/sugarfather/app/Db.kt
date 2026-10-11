package com.sugarfather.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本机数据库（PRD 六：records + settings；实现用原生 SQLite，行为与 Room 方案一致） */
class Db(context: Context) : SQLiteOpenHelper(context, "sugarfather.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE records(" +
                "id TEXT PRIMARY KEY," +
                "value REAL NOT NULL," +
                "period TEXT NOT NULL," +
                "note TEXT NOT NULL DEFAULT ''," +
                "measuredAt INTEGER NOT NULL," +
                "createdAt INTEGER NOT NULL," +
                "updatedAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_records_measured ON records(measuredAt)")
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 单一版本，暂无迁移
    }

    fun allRecords(): List<Record> {
        val out = mutableListOf<Record>()
        readableDatabase.rawQuery(
            "SELECT id, value, period, note, measuredAt, createdAt, updatedAt " +
                "FROM records ORDER BY measuredAt ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Record(
                        c.getString(0), c.getDouble(1), c.getString(2), c.getString(3),
                        c.getLong(4), c.getLong(5), c.getLong(6)
                    )
                )
            }
        }
        return out
    }

    fun upsert(r: Record) {
        val v = ContentValues().apply {
            put("id", r.id)
            put("value", r.value)
            put("period", r.period)
            put("note", r.note)
            put("measuredAt", r.measuredAt)
            put("createdAt", r.createdAt)
            put("updatedAt", r.updatedAt)
        }
        writableDatabase.insertWithOnConflict("records", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(id: String) {
        writableDatabase.delete("records", "id=?", arrayOf(id))
    }

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM records", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    fun getSetting(k: String): String? =
        readableDatabase.rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(k)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun setSetting(k: String, v: String) {
        val cv = ContentValues().apply {
            put("k", k)
            put("v", v)
        }
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun runInTransaction(block: () -> Unit) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            block()
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }
}
