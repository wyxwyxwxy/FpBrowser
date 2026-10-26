package com.fpbrowser.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {

    val profiles: Flow<List<ProfileEntity>> = dao.observeAll()

    /** 首次启动写入预置环境 */
    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            Presets.seedProfiles().forEach { dao.upsert(it) }
        }
    }

    suspend fun get(id: String): ProfileEntity? = dao.getById(id)

    fun getSync(id: String): ProfileEntity? = dao.getByIdSync(id)

    suspend fun save(profile: ProfileEntity) = dao.upsert(profile)

    suspend fun delete(profile: ProfileEntity) = dao.delete(profile)

    companion object {
        @Volatile
        private var instance: ProfileRepository? = null

        fun get(context: Context): ProfileRepository =
            instance ?: synchronized(this) {
                instance ?: ProfileRepository(AppDatabase.get(context).profileDao())
                    .also { instance = it }
            }
    }
}
