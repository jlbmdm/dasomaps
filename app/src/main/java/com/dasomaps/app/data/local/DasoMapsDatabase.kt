package com.dasomaps.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dasomaps.app.data.local.dao.GeometryDao
import com.dasomaps.app.data.local.dao.LayerDao
import com.dasomaps.app.data.local.entity.GeometryEntity
import com.dasomaps.app.data.local.entity.LayerEntity

/**
 * Base de datos principal de DasoMaps usando Room.
 * 
 * Almacena:
 * - Información de capas (locales y remotas)
 * - Geometrías capturadas por el usuario
 * - Cache de datos para funcionamiento offline
 */
@Database(
    entities = [
        LayerEntity::class,
        GeometryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DasoMapsDatabase : RoomDatabase() {
    
    /**
     * DAO para operaciones con capas.
     */
    abstract fun layerDao(): LayerDao
    
    /**
     * DAO para operaciones con geometrías.
     */
    abstract fun geometryDao(): GeometryDao
    
    companion object {
        @Volatile
        private var INSTANCE: DasoMapsDatabase? = null
        
        private const val DATABASE_NAME = "dasomaps_database"
        
        /**
         * Obtiene la instancia Singleton de la base de datos.
         * 
         * @param context Contexto de la aplicación
         * @return Instancia de la base de datos
         */
        fun getInstance(context: Context): DasoMapsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DasoMapsDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // TODO: Implementar migraciones en producción
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Limpia la instancia de la base de datos (para testing).
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
