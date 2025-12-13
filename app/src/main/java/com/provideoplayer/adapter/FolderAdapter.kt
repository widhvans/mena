package com.provideoplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.provideoplayer.R
import com.provideoplayer.model.VideoFolder

/**
 * RecyclerView adapter for displaying video folders
 */
class FolderAdapter(
    private val onFolderClick: (VideoFolder) -> Unit
) : ListAdapter<VideoFolder, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFolderName: TextView = itemView.findViewById(R.id.tvFolderName)
        private val tvVideoCount: TextView = itemView.findViewById(R.id.tvVideoCount)
        
        fun bind(folder: VideoFolder) {
            tvFolderName.text = folder.name
            tvVideoCount.text = "${folder.videoCount} videos"
            
            itemView.setOnClickListener {
                onFolderClick(folder)
            }
        }
    }
    
    /**
     * DiffUtil callback for efficient list updates
     */
    private class FolderDiffCallback : DiffUtil.ItemCallback<VideoFolder>() {
        override fun areItemsTheSame(oldItem: VideoFolder, newItem: VideoFolder): Boolean {
            return oldItem.path == newItem.path
        }
        
        override fun areContentsTheSame(oldItem: VideoFolder, newItem: VideoFolder): Boolean {
            return oldItem == newItem
        }
    }
}
