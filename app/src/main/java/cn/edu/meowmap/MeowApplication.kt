package cn.edu.meowmap

import android.app.Application
import androidx.room.Room
import cn.edu.meowmap.data.*
import org.maplibre.android.MapLibre

class MeowApplication : Application() {
    lateinit var repository: MeowRepository
        private set
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        val db = Room.databaseBuilder(this, MeowDatabase::class.java, "meow-map.db").build()
        repository = LocalMeowRepository(this, db)
    }
}
