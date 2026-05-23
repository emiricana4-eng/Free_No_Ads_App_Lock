package com.applock.free

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.applock.free.databinding.ItemAppBinding

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable
)

class AppListAdapter(
    private val prefManager: PrefManager,
    private val onToggle: (String) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private val apps = mutableListOf<AppInfo>()

    fun setApps(list: List<AppInfo>) {
        apps.clear()
        apps.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.ivIcon.setImageDrawable(app.icon)
            binding.tvName.text = app.name

            // Set without listener to avoid triggering during bind
            binding.switchLock.setOnCheckedChangeListener(null)
            binding.switchLock.isChecked = prefManager.isLocked(app.packageName)

            binding.switchLock.setOnCheckedChangeListener { _, _ ->
                onToggle(app.packageName)
            }

            // Allow tapping the whole row as well
            binding.root.setOnClickListener {
                binding.switchLock.toggle()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount() = apps.size
}
