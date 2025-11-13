package com.dasomaps.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystem

/**
 * Un proveedor de teselas personalizado que reescala teselas de un nivel de zoom inferior
 * cuando no hay teselas disponibles en el nivel de zoom solicitado.
 *
 * @param context El contexto de la aplicación, necesario para crear Drawables.
 * @param pSourceProvider El proveedor de teselas que contiene los datos reales (el archivo MBTiles).
 * @param pMaxZoomWithData El nivel de zoom máximo que está realmente presente en el archivo MBTiles.
 */
class MBTilesUpscalingProvider(
    private val context: Context,
    private val pSourceProvider: MapTileModuleProviderBase,
    private val pMaxZoomWithData: Int
) : MapTileModuleProviderBase(
    Configuration.getInstance().tileDownloadThreads.toInt(),
    Configuration.getInstance().tileDownloadMaxQueueSize.toInt()
) {

    inner class TileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)

            if (zoom <= pMaxZoomWithData) {
                return try {
                    pSourceProvider.tileLoader.loadTile(pMapTileIndex)
                } catch (e: Exception) {
                    null
                }
            }

            // --- Lógica de reescalado ---
            val zoomDifference = zoom - pMaxZoomWithData
            val parentX = MapTileIndex.getX(pMapTileIndex) shr zoomDifference
            val parentY = MapTileIndex.getY(pMapTileIndex) shr zoomDifference
            val parentTileIndex = MapTileIndex.getTileIndex(pMaxZoomWithData, parentX, parentY)

            val parentDrawable = try {
                pSourceProvider.tileLoader.loadTile(parentTileIndex)
            } catch (e: Exception) {
                null
            } ?: return null

            if (parentDrawable !is BitmapDrawable) {
                return null
            }

            val parentBitmap = parentDrawable.bitmap
            val tileSize = TileSystem.getTileSize()
            val subTileSize = tileSize / (1 shl zoomDifference)
            val subTileX = (MapTileIndex.getX(pMapTileIndex) % (1 shl zoomDifference)) * subTileSize
            val subTileY = (MapTileIndex.getY(pMapTileIndex) % (1 shl zoomDifference)) * subTileSize

            val sourceRect = Rect(subTileX, subTileY, subTileX + subTileSize, subTileY + subTileSize)
            val destRect = Rect(0, 0, tileSize, tileSize)

            val finalBitmap = Bitmap.createBitmap(tileSize, tileSize, parentBitmap.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)
            canvas.drawBitmap(parentBitmap, sourceRect, destRect, null)

            // Usar el contexto inyectado en el constructor
            return BitmapDrawable(context.resources, finalBitmap)
        }
    }

    override fun getTileLoader(): MapTileModuleProviderBase.TileLoader = TileLoader()

    override fun getUsesDataConnection(): Boolean = false

    override fun getMinimumZoomLevel(): Int = pSourceProvider.minimumZoomLevel

    override fun getMaximumZoomLevel(): Int = TileSystem.getMaximumZoomLevel()

    override fun setTileSource(pTileSource: ITileSource) {
        pSourceProvider.setTileSource(pTileSource)
    }

    override fun getName(): String = "MBTilesUpscalingProvider"

    override fun getThreadGroupName(): String = "mbtiles-upscaler"

    override fun detach() {
        pSourceProvider.detach()
    }
}
