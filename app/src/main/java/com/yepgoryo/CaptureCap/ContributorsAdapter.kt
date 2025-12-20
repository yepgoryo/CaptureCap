package com.yepgoryo.CaptureCap

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView

abstract class ContributorsAdapter(context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val contributorsLinks: Array<String> = context.resources.getStringArray(R.array.contributors_profiles)
    private val contributorsNames: Array<String> = context.resources.getStringArray(R.array.contributors_names)
    private val contributorsRoles: Array<String> = context.resources.getStringArray(R.array.contributors_roles)
    private val mainContext: Context = context

    open inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.contributor_name)
        private val roleText: TextView = view.findViewById(R.id.contributor_role)

        init {
            view.setOnClickListener(object : View.OnClickListener {
                override fun onClick(view: View) {
                    val link: String = this@ContributorsAdapter.contributorsLinks[this@ViewHolder.getAdapterPosition()]
                    if (link.isEmpty()) {
                        return
                    }
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setData(link.toUri())
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    this@ContributorsAdapter.mainContext.startActivity(intent)
                }
            })
        }

        fun getNameText(): TextView {
            return this.nameText
        }

        fun getRoleText(): TextView {
            return this.roleText
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.contributors_layout, viewGroup, false))
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        (viewHolder as ViewHolder).getNameText().text = this.contributorsNames[position]
        viewHolder.getRoleText().text = this.contributorsRoles[position]
    }

    override fun getItemCount(): Int {
        return this.contributorsNames.size
    }
}
