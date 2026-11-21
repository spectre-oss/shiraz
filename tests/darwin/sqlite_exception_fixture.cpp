// SPDX-License-Identifier: GPL-3.0-only
// Exercise the real SQLite-to-JNI error boundary with controlled SQLite errors.
#include "../../native/sqlite.cpp"

namespace {
int injected_open_error = SQLITE_OK;
int failed_open(sqlite3_vfs*, const char*, sqlite3_file*, int, int*) {
    return injected_open_error;
}

int open_failure(int injected) {
    sqlite3_vfs* original = sqlite3_vfs_find(nullptr);
    if (!original)
        throw std::runtime_error("default SQLite VFS unavailable");
    sqlite3_vfs test = *original;
    test.zName = "shiraz-exception-fixture";
    test.xOpen = failed_open;
    injected_open_error = injected;
    if (sqlite3_vfs_register(&test, 0) != SQLITE_OK)
        throw std::runtime_error("VFS registration failed");
    sqlite3* db = nullptr;
    int rc =
        sqlite3_open_v2("fixture.db", &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE, test.zName);
    int extended = db ? sqlite3_extended_errcode(db) : rc;
    if (db)
        sqlite3_close(db);
    sqlite3_vfs_unregister(&test);
    return extended;
}

int table_lock() {
    sqlite3* db = nullptr;
    if (sqlite3_open(":memory:", &db) != SQLITE_OK)
        throw std::runtime_error("fixture open failed");
    sqlite3_stmt* statement = nullptr;
    int setup = sqlite3_exec(db, "CREATE TABLE t(x); INSERT INTO t VALUES(1),(2)", nullptr, nullptr,
                             nullptr);
    if (setup == SQLITE_OK)
        setup = sqlite3_prepare_v2(db, "SELECT x FROM t", -1, &statement, nullptr);
    if (setup == SQLITE_OK && sqlite3_step(statement) != SQLITE_ROW)
        setup = SQLITE_ERROR;
    int rc =
        setup == SQLITE_OK ? sqlite3_exec(db, "DROP TABLE t", nullptr, nullptr, nullptr) : setup;
    if (statement)
        sqlite3_finalize(statement);
    sqlite3_close(db);
    return rc;
}

int busy_lock() {
    char path[] = "/private/tmp/shiraz-sqlite-fixture-XXXXXX";
    int fd = mkstemp(path);
    if (fd < 0)
        throw std::runtime_error("fixture file creation failed");
    close(fd);
    sqlite3 *first = nullptr, *second = nullptr;
    int rc = sqlite3_open(path, &first);
    if (rc == SQLITE_OK)
        rc = sqlite3_open(path, &second);
    if (rc == SQLITE_OK)
        rc = sqlite3_exec(first, "CREATE TABLE t(x); BEGIN EXCLUSIVE", nullptr, nullptr, nullptr);
    if (rc == SQLITE_OK)
        rc = sqlite3_exec(second, "INSERT INTO t VALUES(1)", nullptr, nullptr, nullptr);
    if (first)
        sqlite3_close(first);
    if (second)
        sqlite3_close(second);
    unlink(path);
    return rc;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL Java_SqliteExceptionMappingSmoke_provoke(JNIEnv* env, jclass,
                                                                           jint kind) {
    return guarded(env, [&]() -> jint {
        int code = kind == 0   ? table_lock()
                   : kind == 3 ? busy_lock()
                               : open_failure(kind == 1 ? SQLITE_PERM : SQLITE_IOERR_READ);
        int expected = kind == 0   ? SQLITE_LOCKED
                       : kind == 1 ? SQLITE_PERM
                       : kind == 2 ? SQLITE_IOERR_READ
                                   : SQLITE_BUSY;
        if (code != expected)
            throw std::runtime_error("SQLite fixture returned an unexpected code");
        check(nullptr, code);
        return 0;
    });
}
