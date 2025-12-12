package com.lsh.doubao.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lsh.doubao.data.model.Message

@Database(entities = [Message::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class) // 挂载转换器
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database" // 数据库文件名
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}