package com.provideoplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.provideoplayer.R
import com.provideoplayer.model.VideoItem

/**
 * RecyclerView adapter for displaying video items in a grid
 */
class VideoAdapter(
    private val onVideoClick: (VideoItem) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvResolution: TextView = itemView.findViewById(R.id.tvResolution)
        private val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        
        fun bind(video: VideoItem) {
            // Load thumbnail with Glide
            Glide.with(itemView.context)
                .load(video.uri)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .placeholder(R.drawable.ic_video)
                .error(R.drawable.ic_video)
                .into(ivThumbnail)
            
            tvDuration.text = video.getFormattedDuration()
            tvTitle.text = video.title
            tvResolution.text = video.getResolutionString()
            tvSize.text = video.getFormattedSize()
            
            itemView.setOnClickListener {
                onVideoClick(video)
            }
        }
    }
    
    /**
     * DiffUtil callback for efficient list updates
     */
    private class VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem == newItem
        }
    }
}
