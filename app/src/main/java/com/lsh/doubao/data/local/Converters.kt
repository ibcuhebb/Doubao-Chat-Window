package com.lsh.doubao.data.local

import androidx.room.TypeConverter
import com.lsh.doubao.data.model.MessageRole
import com.lsh.doubao.data.model.MessageStatus

class Converters {

    // --- Role 转换 ---
    @TypeConverter
    fun fromRole(role: MessageRole): String {
        return role.name
    }

    @TypeConverter
    fun toRole(value: String): MessageRole {
        return try {
            MessageRole.valueOf(value)
        } catch (e: Exception) {
            MessageRole.ASSISTANT // 默认值防崩溃
        }
    }

    // --- Status 转换 ---
    @TypeConverter
    fun fromStatus(status: MessageStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): MessageStatus {
        return try {
            MessageStatus.valueOf(value)
        } catch (e: Exception) {
            MessageStatus.SUCCESS
        }
    }
}