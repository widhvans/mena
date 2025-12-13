package com.provideoplayer.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.provideoplayer.model.VideoFolder
import com.provideoplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans device storage for video files using MediaStore
 */
object VideoScanner {
    
    /**
     * Get all videos from device storage
     */
    suspend fun getAllVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        val selection = "${MediaStore.Video.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("1000") // At least 1 second
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val path = cursor.getString(dataColumn) ?: ""
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val folderName = cursor.getString(bucketNameColumn) ?: "Unknown"
                val dateAdded = cursor.getLong(dateAddedColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                
                // Get folder path from file path
                val folderPath = path.substringBeforeLast("/")
                
                videos.add(
                    VideoItem(
                        id = id,
                        title = name.substringBeforeLast("."),
                        path = path,
                        uri = contentUri,
                        duration = duration,
                        size = size,
                        width = width,
                        height = height,
                        folderName = folderName,
                        folderPath = folderPath,
                        dateAdded = dateAdded,
                        dateModified = dateModified
                    )
                )
            }
        }
        
        videos
    }
    
    /**
     * Get videos grouped by folder
     */
    suspend fun getVideoFolders(context: Context): List<VideoFolder> = withContext(Dispatchers.IO) {
        val videos = getAllVideos(context)
        val foldersMap = mutableMapOf<String, VideoFolder>()
        
        videos.forEach { video ->
            val existing = foldersMap[video.folderPath]
            if (existing != null) {
                existing.videos.add(video)
            } else {
                foldersMap[video.folderPath] = VideoFolder(
                    name = video.folderName,
                    path = video.folderPath,
                    videoCount = 1,
                    videos = mutableListOf(video)
                )
            }
        }
        
        // Update video counts and sort by count
        foldersMap.values.map { folder ->
            folder.copy(videoCount = folder.videos.size)
        }.sortedByDescending { it.videoCount }
    }
    
    /**
     * Get videos from a specific folder
     */
    suspend fun getVideosInFolder(context: Context, folderPath: String): List<VideoItem> = withContext(Dispatchers.IO) {
        getAllVideos(context).filter { it.folderPath == folderPath }
    }
    
    /**
     * Get video thumbnail URI
     */
    fun getVideoThumbnailUri(videoId: Long): Uri {
        return ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId
        )
    }
}
