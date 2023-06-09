package com.chat.ui.database

import androidx.paging.DataSource
import androidx.room.*
import com.chat.ui.ImageAttachments
import com.chat.ui.Message


@Entity(
    tableName = "messages"
)
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    override val id: Long,
    @ColumnInfo("timestamp")
    override val timestamp: Long,
    @ColumnInfo("is_from_user")
    override val isFromUser: Boolean,
    @ColumnInfo("text")
    override val text: String,
    @ColumnInfo("image_attachments")
    override val imageAttachments: ImageAttachments?
) : Message

@Database(
    version = 2,
    exportSchema = true,
    entities = [MessageEntity::class]
)
@TypeConverters(MessageDatabaseConverters::class)
internal abstract class MessageRoomDatabase : RoomDatabase() {
    abstract fun getMessageDao(): MessageDao
}

@Dao
internal abstract class MessageDao {
    @Query("SELECT * FROM messages")
    abstract fun getMessageDataSource(): DataSource.Factory<Int, MessageEntity>

    @Insert
    abstract suspend fun insertMessage(entity: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id in (:entityIds)")
    abstract suspend fun deleteMessages(entityIds: Collection<Long>)
}