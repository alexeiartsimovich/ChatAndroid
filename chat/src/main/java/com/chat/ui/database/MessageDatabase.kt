package com.chat.ui.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chat.ui.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun obtainMessageDatabase(context: Context, key: String): MessageDatabase {
    return MessageDatabaseImpl(context, key)
}

internal interface MessageDatabase {
    fun getMessageListLiveData(): LiveData<PagedList<Message>>
    suspend fun insertMessage(message: Message): Long
    suspend fun deleteMessages(messages: Collection<Message>)
}

private class MessageDatabaseImpl(
    private val context: Context,
    private val key: String
): MessageDatabase {
    private val database: MessageRoomDatabase by lazy {
        Room.databaseBuilder(context, MessageRoomDatabase::class.java, "chat.$key.messages")
            .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun getMessageListLiveData(): LiveData<PagedList<Message>> {
        val dataSourceFactory =
            database.getMessageDao().getMessageDataSource()
                .map { value -> value as Message }
        val builder = LivePagedListBuilder(
            dataSourceFactory,
            PagedList.Config.Builder()
                .setPageSize(20)
                .setEnablePlaceholders(true)
                .build()
        )
        return builder
            .build()
    }

    override suspend fun insertMessage(message: Message): Long {
        val entity = MessageEntity(
            id = 0L,
            isFromUser = message.isFromUser,
            text = message.text,
            timestamp = message.timestamp,
            imageAttachments = message.imageAttachments
        )
        return database.getMessageDao().insertMessage(entity)
    }

    override suspend fun deleteMessages(messages: Collection<Message>) {
        val entityIds = withContext(Dispatchers.Default) {
            messages.map { it.id }
        }
        database.getMessageDao().deleteMessages(entityIds)
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD image_attachments text")
    }
}
