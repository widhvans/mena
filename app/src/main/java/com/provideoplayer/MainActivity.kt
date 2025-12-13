package com.provideoplayer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.provideoplayer.adapter.FolderAdapter
import com.provideoplayer.adapter.VideoAdapter
import com.provideoplayer.databinding.ActivityMainBinding
import com.provideoplayer.model.VideoFolder
import com.provideoplayer.model.VideoItem
import com.provideoplayer.utils.PermissionManager
import com.provideoplayer.utils.VideoScanner
import kotlinx.coroutines.launch

/**
 * Main Activity - Video browser with permission handling
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    private val videoAdapter = VideoAdapter { video ->
        openPlayer(video)
    }
    
    private val folderAdapter = FolderAdapter { folder ->
        showFolderVideos(folder)
    }
    
    private var allVideos: List<VideoItem> = emptyList()
    private var folders: List<VideoFolder> = emptyList()
    private var currentTab = 0 // 0 = All Videos, 1 = Folders
    private var currentFolder: VideoFolder? = null
    
    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            loadVideos()
        } else {
            showPermissionUI()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissionAndLoad()
    }
    
    private fun setupUI() {
        // Setup RecyclerView with grid layout for videos
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = videoAdapter
            setHasFixedSize(true)
        }
        
        // Tab selection listener
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                currentFolder = null
                updateToolbarTitle()
                updateList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // If in folder view, go back to folder list
                if (currentFolder != null) {
                    currentFolder = null
                    updateToolbarTitle()
                    updateList()
                }
            }
        })
        
        // Grant permission button
        binding.root.findViewById<MaterialButton>(R.id.btnGrantPermission)?.setOnClickListener {
            if (PermissionManager.isPermissionPermanentlyDenied(this)) {
                PermissionManager.openAppSettings(this)
            } else {
                PermissionManager.requestStoragePermission(permissionLauncher)
            }
        }
    }
    
    private fun checkPermissionAndLoad() {
        if (PermissionManager.hasStoragePermission(this)) {
            loadVideos()
        } else {
            PermissionManager.requestStoragePermission(permissionLauncher)
        }
    }
    
    private fun loadVideos() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                // Load all videos
                allVideos = VideoScanner.getAllVideos(this@MainActivity)
                
                // Load folders
                folders = VideoScanner.getVideoFolders(this@MainActivity)
                
                showLoading(false)
                
                if (allVideos.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                    updateList()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@MainActivity,
                    "Error loading videos: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun updateList() {
        when (currentTab) {
            0 -> { // All Videos tab
                binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
                binding.recyclerView.adapter = videoAdapter
                
                if (currentFolder != null) {
                    videoAdapter.submitList(currentFolder!!.videos)
                } else {
                    videoAdapter.submitList(allVideos)
                }
            }
            1 -> { // Folders tab
                if (currentFolder != null) {
                    // Show videos in folder
                    binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
                    binding.recyclerView.adapter = videoAdapter
                    videoAdapter.submitList(currentFolder!!.videos)
                } else {
                    // Show folder list
                    binding.recyclerView.layoutManager = LinearLayoutManager(this)
                    binding.recyclerView.adapter = folderAdapter
                    folderAdapter.submitList(folders)
                }
            }
        }
    }
    
    private fun showFolderVideos(folder: VideoFolder) {
        currentFolder = folder
        updateToolbarTitle()
        updateList()
    }
    
    private fun updateToolbarTitle() {
        binding.toolbar.title = when {
            currentFolder != null -> currentFolder!!.name
            else -> getString(R.string.app_name)
        }
    }
    
    private fun openPlayer(video: VideoItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_VIDEO_URI, video.uri.toString())
            putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.title)
            putExtra(PlayerActivity.EXTRA_VIDEO_PATH, video.path)
        }
        startActivity(intent)
    }
    
    private fun showLoading(show: Boolean) {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        binding.permissionLayout.visibility = View.GONE
        binding.emptyLayout.visibility = View.GONE
    }
    
    private fun showPermissionUI() {
        binding.loadingLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.permissionLayout.visibility = View.VISIBLE
        binding.emptyLayout.visibility = View.GONE
    }
    
    private fun showEmptyState() {
        binding.loadingLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.permissionLayout.visibility = View.GONE
        binding.emptyLayout.visibility = View.VISIBLE
    }
    
    private fun showContent() {
        binding.loadingLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.permissionLayout.visibility = View.GONE
        binding.emptyLayout.visibility = View.GONE
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Handle back navigation in folder view
        if (currentFolder != null) {
            currentFolder = null
            updateToolbarTitle()
            updateList()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh if permission was granted in settings
        if (PermissionManager.hasStoragePermission(this) && allVideos.isEmpty()) {
            loadVideos()
        }
    }
    
    // Handle legacy permission request results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadVideos()
            } else {
                showPermissionUI()
            }
        }
    }
}
