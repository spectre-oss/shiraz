// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <sqlite3.h>
#include <algorithm>
#include <chrono>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <limits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <unistd.h>
#include <vector>

namespace {
constexpr std::size_t maxValue = 16 * 1024 * 1024, maxResult = 32 * 1024 * 1024;
struct Pending {};
struct SqlError : std::runtime_error {
    int code;
    SqlError(int code, const std::string& message) : std::runtime_error(message), code(code) { }
};
void pending(JNIEnv* env) { if (env->ExceptionCheck()) throw Pending{}; }
void check(sqlite3* db, int code) { if (code != SQLITE_OK) throw SqlError(code, db ? sqlite3_errmsg(db) : "SQLite failure"); }
void error(JNIEnv* env, const char* cls, const char* message) {
    if (env->ExceptionCheck()) return;
    // SQLite errors use real UTF-8, whereas ThrowNew expects JNI modified UTF-8.
    // Decode into bounded stack storage so error reporting cannot allocate C++ memory.
    jchar decoded[4096]; jsize count = 0;
    const auto* input = reinterpret_cast<const unsigned char*>(message);
    while (*input && count < 4094) {
        uint32_t point = *input; int width = 1;
        if (point >= 0x80) {
            int wanted = point >= 0xf0 && point <= 0xf4 ? 4 : point >= 0xe0 && point <= 0xef ? 3 : point >= 0xc2 && point <= 0xdf ? 2 : 0;
            bool valid = wanted != 0;
            point = wanted ? point & ((1u << (7 - wanted)) - 1) : 0xfffd;
            for (int next = 1; valid && next < wanted; ++next) {
                if ((input[next] & 0xc0) != 0x80) valid = false;
                else point = (point << 6) | (input[next] & 63);
            }
            if (valid && (point > 0x10ffff || (point >= 0xd800 && point <= 0xdfff)
                    || (wanted == 2 && point < 0x80) || (wanted == 3 && point < 0x800) || (wanted == 4 && point < 0x10000))) valid = false;
            if (valid) width = wanted; else point = 0xfffd;
        }
        input += width;
        if (point > 0xffff) { point -= 0x10000; decoded[count++] = static_cast<jchar>(0xd800 + (point >> 10)); decoded[count++] = static_cast<jchar>(0xdc00 + (point & 1023)); }
        else decoded[count++] = static_cast<jchar>(point);
    }
    auto type = env->FindClass(cls); if (!type) return;
    auto ctor = env->GetMethodID(type, "<init>", "(Ljava/lang/String;)V"); if (!ctor) { env->DeleteLocalRef(type); return; }
    auto text = env->NewString(decoded, count); if (!text) { env->DeleteLocalRef(type); return; }
    auto exception = env->NewObject(type, ctor, text);
    if (exception) { env->Throw(static_cast<jthrowable>(exception)); env->DeleteLocalRef(exception); }
    env->DeleteLocalRef(text); env->DeleteLocalRef(type);
}
const char* errorClass(int code) {
    switch (code & 255) {
        case SQLITE_RANGE: return "android/database/sqlite/SQLiteBindOrColumnIndexOutOfRangeException";
        case SQLITE_CONSTRAINT: return "android/database/sqlite/SQLiteConstraintException";
        case SQLITE_READONLY: return "android/database/sqlite/SQLiteReadOnlyDatabaseException";
        case SQLITE_CORRUPT: case SQLITE_NOTADB: return "android/database/sqlite/SQLiteDatabaseCorruptException";
        case SQLITE_BUSY: case SQLITE_LOCKED: return "android/database/sqlite/SQLiteDatabaseLockedException";
        case SQLITE_FULL: return "android/database/sqlite/SQLiteFullException";
        case SQLITE_CANTOPEN: return "android/database/sqlite/SQLiteCantOpenDatabaseException";
        case SQLITE_TOOBIG: return "android/database/sqlite/SQLiteBlobTooBigException";
        case SQLITE_ABORT: return "android/database/sqlite/SQLiteAbortException";
        case SQLITE_DONE: return "android/database/sqlite/SQLiteDoneException";
        case SQLITE_NOMEM: return "java/lang/OutOfMemoryError";
        default: return "android/database/sqlite/SQLiteException";
    }
}
template<class F> auto guarded(JNIEnv* env, F function) -> decltype(function()) {
    try { return function(); }
    catch (const Pending&) { }
    catch (const SqlError& issue) { error(env, errorClass(issue.code), issue.what()); }
    catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate SQLite data"); }
    catch (const std::invalid_argument& issue) { error(env, "java/lang/IllegalArgumentException", issue.what()); }
    catch (const std::exception& issue) { error(env, "java/lang/IllegalStateException", issue.what()); }
    return {};
}
struct Connection {
    sqlite3* db = nullptr;
    std::mutex mutex;
    ~Connection() { if (db) sqlite3_close_v2(db); }
    void live() { if (!db) throw std::logic_error("Database is closed"); }
};
struct Statement {
    std::shared_ptr<Connection> owner;
    sqlite3_stmt* sql = nullptr;
    ~Statement() { if (sql) sqlite3_finalize(sql); }
    void live() { owner->live(); if (!sql) throw std::logic_error("Statement is closed"); }
};
std::mutex registry;
std::unordered_map<jlong, std::shared_ptr<Connection>> connections;
std::unordered_map<jlong, std::shared_ptr<Statement>> statements;
jlong nextId = 1;
jlong id() { if (nextId == std::numeric_limits<jlong>::max()) throw std::logic_error("SQLite handles exhausted"); return nextId++; }
std::shared_ptr<Connection> connection(jlong handle) {
    std::lock_guard<std::mutex> lock(registry); auto found = connections.find(handle);
    if (found == connections.end()) throw std::logic_error("Database is closed");
    return found->second;
}
std::shared_ptr<Statement> statement(jlong handle) {
    std::lock_guard<std::mutex> lock(registry); auto found = statements.find(handle);
    if (found == statements.end()) throw std::logic_error("Statement is closed");
    return found->second;
}
std::u16string text(JNIEnv* env, jstring value, std::size_t cap) {
    if (!value) throw std::invalid_argument("Null SQLite text");
    auto length = env->GetStringLength(value); if (static_cast<std::size_t>(length) > cap) throw SqlError(SQLITE_TOOBIG, "SQLite text exceeds host limit");
    std::u16string copy(static_cast<std::size_t>(length), 0);
    if (length) env->GetStringRegion(value, 0, length, reinterpret_cast<jchar*>(&copy[0]));
    pending(env); return copy;
}
struct Progress {
    sqlite3* db;
    unsigned calls = 0;
    std::chrono::steady_clock::time_point end = std::chrono::steady_clock::now() + std::chrono::seconds(10);
    explicit Progress(sqlite3* db) : db(db) { sqlite3_progress_handler(db, 1000, callback, this); }
    static int callback(void* opaque) {
        auto& self = *static_cast<Progress*>(opaque);
        return ++self.calls > 50000 || std::chrono::steady_clock::now() > self.end;
    }
    ~Progress() { sqlite3_progress_handler(db, 0, nullptr, nullptr); }
};
struct Reset {
    sqlite3_stmt* sql;
    ~Reset() { sqlite3_reset(sql); sqlite3_clear_bindings(sql); }
};
void bind(JNIEnv* env, Statement& stmt, jobjectArray args) {
    int count = args ? env->GetArrayLength(args) : 0;
    if (count != sqlite3_bind_parameter_count(stmt.sql)) throw std::invalid_argument("SQLite binding count mismatch");
    check(stmt.owner->db, sqlite3_clear_bindings(stmt.sql));
    jclass longs = env->FindClass("java/lang/Long"); pending(env);
    jclass doubles = env->FindClass("java/lang/Double"); pending(env);
    jclass strings = env->FindClass("java/lang/String"); pending(env);
    jclass bytes = env->FindClass("[B"); pending(env);
    auto longValue = env->GetMethodID(longs, "longValue", "()J"); pending(env);
    auto doubleValue = env->GetMethodID(doubles, "doubleValue", "()D"); pending(env);
    for (int i = 0; i < count; ++i) {
        auto value = env->GetObjectArrayElement(args, i); pending(env); int code;
        if (!value) code = sqlite3_bind_null(stmt.sql, i + 1);
        else if (env->IsInstanceOf(value, longs)) {
            auto number = env->CallLongMethod(value, longValue); pending(env); code = sqlite3_bind_int64(stmt.sql, i + 1, number);
        } else if (env->IsInstanceOf(value, doubles)) {
            auto number = env->CallDoubleMethod(value, doubleValue); pending(env); code = sqlite3_bind_double(stmt.sql, i + 1, number);
        } else if (env->IsInstanceOf(value, strings)) {
            auto copy = text(env, static_cast<jstring>(value), maxValue / 2);
            code = sqlite3_bind_text16(stmt.sql, i + 1, copy.data(), static_cast<int>(copy.size() * 2), SQLITE_TRANSIENT);
        } else if (env->IsInstanceOf(value, bytes)) {
            auto array = static_cast<jbyteArray>(value); auto length = env->GetArrayLength(array);
            if (static_cast<std::size_t>(length) > maxValue) throw SqlError(SQLITE_TOOBIG, "SQLite blob exceeds host limit");
            std::vector<jbyte> copy(static_cast<std::size_t>(length));
            if (length) env->GetByteArrayRegion(array, 0, length, copy.data());
            pending(env);
            code = length ? sqlite3_bind_blob(stmt.sql, i + 1, copy.data(), length, SQLITE_TRANSIENT) : sqlite3_bind_zeroblob(stmt.sql, i + 1, 0);
        } else throw std::invalid_argument("Unsupported SQLite binding type");
        if (value) env->DeleteLocalRef(value);
        check(stmt.owner->db, code);
    }
    env->DeleteLocalRef(longs); env->DeleteLocalRef(doubles); env->DeleteLocalRef(strings); env->DeleteLocalRef(bytes);
}
struct Cell {
    int type;
    jlong integer = 0;
    jdouble real = 0;
    std::u16string string;
    std::vector<jbyte> bytes;
};
std::u16string columnText(sqlite3_stmt* sql, int column) {
    const auto* data = static_cast<const char16_t*>(sqlite3_column_text16(sql, column));
    int bytes = sqlite3_column_bytes16(sql, column);
    if (!data) throw SqlError(SQLITE_NOMEM, "Cannot read SQLite text");
    if (bytes < 0 || static_cast<std::size_t>(bytes) > maxValue) throw SqlError(SQLITE_TOOBIG, "SQLite text exceeds host limit");
    return std::u16string(data, static_cast<std::size_t>(bytes) / 2);
}
jstring string(JNIEnv* env, const std::u16string& value) {
    auto result = env->NewString(reinterpret_cast<const jchar*>(value.data()), static_cast<jsize>(value.size())); pending(env); return result;
}
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSqlite_open(JNIEnv* env, jclass, jbyteArray path, jint flags) {
    return guarded(env, [&]() -> jlong {
        if (!path || (flags & ~0x10000001) || ((flags & 1) && (flags & 0x10000000))) throw std::invalid_argument("Invalid SQLite path or flags");
        auto length = env->GetArrayLength(path); if (length < 1 || length > 1048576) throw std::invalid_argument("Invalid SQLite path size");
        std::string filename(static_cast<std::size_t>(length), 0); env->GetByteArrayRegion(path, 0, length, reinterpret_cast<jbyte*>(&filename[0])); pending(env);
        if (filename.find('\0') != std::string::npos) throw std::invalid_argument("NUL in SQLite path");
        if ((flags & 0x10000000) && filename != ":memory:") {
            int fd = ::open(filename.c_str(), O_CREAT | O_EXCL | O_WRONLY | O_CLOEXEC, 0600);
            if (fd >= 0) ::close(fd); else if (errno != EEXIST) throw SqlError(SQLITE_CANTOPEN, "Cannot create database file");
        }
        if (!sqlite3_threadsafe()) throw std::logic_error("SQLite must be built with thread safety");
        auto value = std::make_shared<Connection>();
        int mode = flags & 1 ? SQLITE_OPEN_READONLY : SQLITE_OPEN_READWRITE;
        if (flags & 0x10000000) mode |= SQLITE_OPEN_CREATE;
        check(value->db, sqlite3_open_v2(filename.c_str(), &value->db, mode | SQLITE_OPEN_FULLMUTEX, nullptr));
        if (!(flags & 1) && sqlite3_db_readonly(value->db, "main") == 1) throw SqlError(SQLITE_CANTOPEN, "Database could not be opened read/write");
        sqlite3_extended_result_codes(value->db, 1); sqlite3_busy_timeout(value->db, 2500);
        sqlite3_limit(value->db, SQLITE_LIMIT_LENGTH, static_cast<int>(maxValue));
        sqlite3_limit(value->db, SQLITE_LIMIT_SQL_LENGTH, 2 * 1024 * 1024);
        sqlite3_limit(value->db, SQLITE_LIMIT_VARIABLE_NUMBER, 4096);
        sqlite3_limit(value->db, SQLITE_LIMIT_COLUMN, 256); sqlite3_limit(value->db, SQLITE_LIMIT_EXPR_DEPTH, 256);
        std::lock_guard<std::mutex> lock(registry); if (connections.size() >= 128) throw std::logic_error("SQLite connection limit exceeded");
        auto handle = id(); connections.emplace(handle, std::move(value)); return handle;
    });
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSqlite_close(JNIEnv* env, jclass, jlong handle) {
    guarded(env, [&]() {
        std::shared_ptr<Connection> owner;
        { std::lock_guard<std::mutex> lock(registry); auto found = connections.find(handle); if (found == connections.end()) return 0; owner = found->second; connections.erase(found); }
        std::lock_guard<std::mutex> lock(owner->mutex);
        { std::lock_guard<std::mutex> mapLock(registry);
          for (auto it = statements.begin(); it != statements.end();) {
            if (it->second->owner != owner) { ++it; continue; }
            sqlite3_finalize(it->second->sql); it->second->sql = nullptr; it = statements.erase(it);
          }
        }
        int status = sqlite3_close_v2(owner->db); owner->db = nullptr; check(nullptr, status); return 0;
    });
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSqlite_prepare(JNIEnv* env, jclass, jlong handle, jstring source) {
    return guarded(env, [&]() -> jlong {
        auto owner = connection(handle); auto sql = text(env, source, 1024 * 1024);
        if (sql.empty() || sql.find(u'\0') != std::u16string::npos) throw std::invalid_argument("Empty or NUL-containing SQL");
        std::lock_guard<std::mutex> lock(owner->mutex); owner->live(); Progress progress(owner->db);
        auto stmt = std::make_shared<Statement>(); stmt->owner = owner;
        const void* tail = nullptr;
        check(owner->db, sqlite3_prepare16_v3(owner->db, sql.c_str(), static_cast<int>((sql.size() + 1) * 2), 0, &stmt->sql, &tail));
        if (!stmt->sql) throw std::invalid_argument("SQL contains no statement");
        auto consumed = static_cast<const char16_t*>(tail) - sql.data();
        if (consumed < 0 || static_cast<std::size_t>(consumed) > sql.size()) throw std::logic_error("Invalid SQLite SQL tail");
        // Android's statement API compiles and runs the first statement only.
        // Never prepare the tail: PRAGMAs may change connection state at prepare time.
        std::lock_guard<std::mutex> mapLock(registry); if (statements.size() >= 4096) throw std::logic_error("SQLite statement limit exceeded");
        auto result = id(); statements.emplace(result, std::move(stmt)); return result;
    });
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeSqlite_parameterCount(JNIEnv* env, jclass, jlong handle) {
    return guarded(env, [&]() -> jint { auto stmt = statement(handle); std::lock_guard<std::mutex> lock(stmt->owner->mutex); stmt->live(); return sqlite3_bind_parameter_count(stmt->sql); });
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSqlite_finalizeStatement(JNIEnv* env, jclass, jlong handle) {
    guarded(env, [&]() {
        std::shared_ptr<Statement> stmt;
        { std::lock_guard<std::mutex> lock(registry); auto found = statements.find(handle); if (found == statements.end()) return 0; stmt = found->second; statements.erase(found); }
        std::lock_guard<std::mutex> lock(stmt->owner->mutex);
        if (stmt->sql) { sqlite3_finalize(stmt->sql); stmt->sql = nullptr; } return 0;
    });
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSqlite_execute(JNIEnv* env, jclass, jlong ownerHandle, jlong handle, jobjectArray args, jint mode) {
    return guarded(env, [&]() -> jlong {
        auto owner = connection(ownerHandle); auto stmt = statement(handle);
        if (stmt->owner != owner || mode < 0 || mode > 3) throw std::invalid_argument("Invalid statement owner or execution mode");
        std::lock_guard<std::mutex> lock(owner->mutex); stmt->live(); Reset reset{stmt->sql}; Progress progress(owner->db); bind(env, *stmt, args);
        int status = sqlite3_step(stmt->sql);
        if (mode == 3) { if (status == SQLITE_DONE) throw SqlError(SQLITE_DONE, "Scalar query returned no rows"); if (status != SQLITE_ROW) check(owner->db, status); return sqlite3_column_int64(stmt->sql, 0); }
        while (status == SQLITE_ROW) status = sqlite3_step(stmt->sql);
        if (status != SQLITE_DONE) check(owner->db, status);
        if (mode == 1) return sqlite3_changes(owner->db) ? sqlite3_last_insert_rowid(owner->db) : -1;
        return mode == 2 ? sqlite3_changes(owner->db) : 0;
    });
}
extern "C" JNIEXPORT jobject JNICALL Java_shiraz_runtime_NativeSqlite_query(JNIEnv* env, jclass, jlong ownerHandle, jlong handle, jobjectArray args) {
    return guarded(env, [&]() -> jobject {
        auto owner = connection(ownerHandle); auto stmt = statement(handle);
        if (stmt->owner != owner) throw std::invalid_argument("Invalid statement owner");
        std::lock_guard<std::mutex> lock(owner->mutex); stmt->live(); Reset reset{stmt->sql}; Progress progress(owner->db); bind(env, *stmt, args);
        // Android cursor queries also execute writable statements (e.g. Room UPDATE RETURNING).
        // The owning database enforces read-only mode and write-thread policy.
        int status = sqlite3_step(stmt->sql);
        if (status != SQLITE_ROW && status != SQLITE_DONE) check(owner->db, status);
        // Stepping may recompile a cached statement after a schema change.
        const int columns = sqlite3_column_count(stmt->sql);
        std::vector<std::u16string> names;
        for (int col = 0; col < columns; ++col) {
            const auto* name = static_cast<const char16_t*>(sqlite3_column_name16(stmt->sql, col));
            if (!name) throw std::bad_alloc();
            names.emplace_back(name);
        }
        std::vector<std::vector<Cell>> rows; std::size_t size = 0;
        while (status == SQLITE_ROW) {
            if (rows.size() >= 100000 || size + sizeof(Cell) * static_cast<std::size_t>(columns) > maxResult) throw SqlError(SQLITE_TOOBIG, "SQLite result exceeds bounded snapshot limit");
            size += sizeof(Cell) * static_cast<std::size_t>(columns);
            std::vector<Cell> row(static_cast<std::size_t>(columns));
            for (int col = 0; col < columns; ++col) {
                auto& cell = row[static_cast<std::size_t>(col)]; cell.type = sqlite3_column_type(stmt->sql, col);
                if (cell.type == SQLITE_INTEGER) cell.integer = sqlite3_column_int64(stmt->sql, col);
                else if (cell.type == SQLITE_FLOAT) cell.real = sqlite3_column_double(stmt->sql, col);
                else if (cell.type == SQLITE_TEXT) { cell.string = columnText(stmt->sql, col); size += cell.string.size() * 2; }
                else if (cell.type == SQLITE_BLOB) {
                    int count = sqlite3_column_bytes(stmt->sql, col); auto data = static_cast<const jbyte*>(sqlite3_column_blob(stmt->sql, col));
                    if (count < 0 || static_cast<std::size_t>(count) > maxValue) throw SqlError(SQLITE_TOOBIG, "SQLite blob exceeds host limit");
                    if (count && !data) throw std::bad_alloc();
                    if (count) cell.bytes.assign(data, data + count);
                    size += static_cast<std::size_t>(count);
                }
                if (size > maxResult) throw SqlError(SQLITE_TOOBIG, "SQLite result exceeds bounded snapshot limit");
            }
            rows.push_back(std::move(row)); status = sqlite3_step(stmt->sql);
        }
        if (status != SQLITE_DONE) check(owner->db, status);
        auto stringClass = env->FindClass("java/lang/String"); pending(env);
        auto objectClass = env->FindClass("java/lang/Object"); pending(env);
        auto rowClass = env->FindClass("[Ljava/lang/Object;"); pending(env);
        auto resultClass = env->FindClass("shiraz/runtime/NativeSqlite$Result"); pending(env);
        auto longClass = env->FindClass("java/lang/Long"); pending(env);
        auto doubleClass = env->FindClass("java/lang/Double"); pending(env);
        auto longCtor = env->GetMethodID(longClass, "<init>", "(J)V"); pending(env);
        auto doubleCtor = env->GetMethodID(doubleClass, "<init>", "(D)V"); pending(env);
        auto resultCtor = env->GetMethodID(resultClass, "<init>", "([Ljava/lang/String;[[Ljava/lang/Object;)V"); pending(env);
        auto columnArray = env->NewObjectArray(columns, stringClass, nullptr); pending(env);
        for (int col = 0; col < columns; ++col) { auto name = string(env, names[static_cast<std::size_t>(col)]); env->SetObjectArrayElement(columnArray, col, name); env->DeleteLocalRef(name); pending(env); }
        auto allRows = env->NewObjectArray(static_cast<jsize>(rows.size()), rowClass, nullptr); pending(env);
        for (std::size_t index = 0; index < rows.size(); ++index) {
            auto row = env->NewObjectArray(columns, objectClass, nullptr); pending(env);
            for (int col = 0; col < columns; ++col) {
                auto& cell = rows[index][static_cast<std::size_t>(col)]; jobject value = nullptr;
                if (cell.type == SQLITE_INTEGER) value = env->NewObject(longClass, longCtor, cell.integer);
                else if (cell.type == SQLITE_FLOAT) value = env->NewObject(doubleClass, doubleCtor, cell.real);
                else if (cell.type == SQLITE_TEXT) value = string(env, cell.string);
                else if (cell.type == SQLITE_BLOB) {
                    auto bytes = env->NewByteArray(static_cast<jsize>(cell.bytes.size())); pending(env);
                    if (!cell.bytes.empty()) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(cell.bytes.size()), cell.bytes.data());
                    value = bytes;
                }
                pending(env); env->SetObjectArrayElement(row, col, value); if (value) env->DeleteLocalRef(value); pending(env);
            }
            env->SetObjectArrayElement(allRows, static_cast<jsize>(index), row); env->DeleteLocalRef(row); pending(env);
        }
        return env->NewObject(resultClass, resultCtor, columnArray, allRows);
    });
}

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSqlite_isReadOnly(JNIEnv* env, jclass, jlong handle) {
    return guarded(env, [&]() -> jboolean { auto stmt = statement(handle); std::lock_guard<std::mutex> lock(stmt->owner->mutex); stmt->live(); return sqlite3_stmt_readonly(stmt->sql); });
}
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSqlite_autocommit(JNIEnv* env, jclass, jlong handle) {
    return guarded(env, [&]() -> jboolean { auto owner = connection(handle); std::lock_guard<std::mutex> lock(owner->mutex); owner->live(); return sqlite3_get_autocommit(owner->db); });
}
extern "C" JNIEXPORT jstring JNICALL Java_shiraz_runtime_NativeSqlite_scalarString(JNIEnv* env, jclass, jlong ownerHandle, jlong handle, jobjectArray args) {
    return guarded(env, [&]() -> jstring {
        auto owner = connection(ownerHandle); auto stmt = statement(handle);
        if (stmt->owner != owner) throw std::invalid_argument("Invalid statement owner");
        std::lock_guard<std::mutex> lock(owner->mutex); stmt->live(); Reset reset{stmt->sql}; Progress progress(owner->db); bind(env, *stmt, args);
        int status = sqlite3_step(stmt->sql);
        if (status == SQLITE_DONE) throw SqlError(SQLITE_DONE, "Scalar query returned no rows");
        if (status != SQLITE_ROW) check(owner->db, status);
        return sqlite3_column_type(stmt->sql, 0) == SQLITE_NULL ? nullptr : string(env, columnText(stmt->sql, 0));
    });
}
