package com.freshdigitable.yttt.data.source.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.inspectors.shouldForAll
import org.junit.runner.Description

class AppMigrationTestRule(
    private val versionFrom: Int,
    private val versionTo: Int,
    private val migration: Migration,
) : MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
) {
    init {
        check(versionFrom < versionTo)
        check(versionFrom <= migration.startVersion && migration.endVersion <= versionTo)
    }

    lateinit var oldDb: SupportSQLiteDatabase
    val newDb: SupportSQLiteDatabase by lazy {
        if (oldDb.isOpen) oldDb.close()
        runMigrationsAndValidate("test", versionTo, true, migration).also {
            it.setForeignKeyConstraintsEnabled(true)
        }
    }

    fun insertForSetup(vararg records: Pair<String, List<ContentValues>>) {
        oldDb.query("pragma defer_foreign_keys = true")
        records.forEach { (table, values) ->
            val res = values.map {
                oldDb.insert(table, SQLiteDatabase.CONFLICT_ABORT, it)
            }
            res.shouldForAll { it > -1 }
        }
        oldDb.query("pragma defer_foreign_keys = false")
    }

    override fun starting(description: Description?) {
        super.starting(description)
        oldDb = createDatabase("test", versionFrom).also { // to delete DB file for each tests
            it.setForeignKeyConstraintsEnabled(true) // as same as Room
        }
    }

    override fun finished(description: Description?) {
        super.finished(description)
        if (newDb.isOpen) newDb.close()
    }
}
