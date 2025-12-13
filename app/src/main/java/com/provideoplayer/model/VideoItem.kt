package com.provideoplayer.model

import android.net.Uri

/**
 * Data class representing a video file
 */
data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val uri: Uri,
    val duration: Long,      // Duration in milliseconds
    val size: Long,          // Size in bytes
    val width: Int,          // Video width
    val height: Int,         // Video height
    val folderName: String,  // Parent folder name
    val folderPath: String,  // Parent folder path
    val dateAdded: Long,     // Date added timestamp
    val dateModified: Long   // Date modified timestamp
) {
    /**
     * Get formatted duration string (HH:MM:SS or MM:SS)
     */
    fun getFormattedDuration(): String {
        val totalSeconds = duration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    /**
     * Get formatted file size string
     */
    fun getFormattedSize(): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", kb)
        }
    }
    
    /**
     * Get resolution string (e.g., "1080p", "720p", "4K")
     */
    fun getResolutionString(): String {
        return when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            height >= 360 -> "360p"
            else -> "${height}p"
        }
    }
}

/**
 * Data class representing a folder containing videos
 */
data class VideoFolder(
    val name: String,
    val path: String,
    val videoCount: Int,
    val videos: MutableList<VideoItem> = mutableListOf()
)
