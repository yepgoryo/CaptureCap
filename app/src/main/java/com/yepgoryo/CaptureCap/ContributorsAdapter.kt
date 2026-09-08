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
    private val contributorsGithubLinks: Array<String> = context.resources.getStringArray(R.array.contributors_profiles)
    private val contributorsMastodonLinks: Array<String> = context.resources.getStringArray(R.array.contributors_mastodon_profiles)
    private val contributorsTelegramLinks: Array<String> = context.resources.getStringArray(R.array.contributors_telegram_profiles)
    private val contributorsEmails: Array<String> = context.resources.getStringArray(R.array.contributors_emails)
    private val contributorsNames: Array<String> = context.resources.getStringArray(R.array.contributors_names)
    private val contributorsRoles: Array<String> = context.resources.getStringArray(R.array.contributors_roles)
    private val contributorsContributedTo: Array<String> = context.resources.getStringArray(R.array.contributors_contributed_to)
    private val mainContext: Context = context

    open inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.contributor_name)
        private val roleText: TextView = view.findViewById(R.id.contributor_role)
        private val contributedToText: TextView = view.findViewById(R.id.contributed_to_apps)

        private val githubButton: TextView = view.findViewById(R.id.contributor_github)
        private val mastodonButton: TextView = view.findViewById(R.id.contributor_mastodon)
        private val telegramButton: TextView = view.findViewById(R.id.contributor_telegram)
        private val emailButton: TextView = view.findViewById(R.id.contributor_email)

        fun showGithubLink() {
            if (contributorsGithubLinks.size > bindingAdapterPosition) {
                val githubLink: String =
                    this@ContributorsAdapter.contributorsGithubLinks[bindingAdapterPosition]
                if (!githubLink.isEmpty()) {
                    githubButton.visibility = View.VISIBLE
                    githubButton.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(githubLink.toUri())
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        this@ContributorsAdapter.mainContext.startActivity(intent)
                    }
                }
            }
        }

        fun showMastodonLink() {
            if (contributorsMastodonLinks.size > bindingAdapterPosition) {
                val mastodonLink: String =
                    this@ContributorsAdapter.contributorsMastodonLinks[bindingAdapterPosition]
                if (!mastodonLink.isEmpty()) {
                    mastodonButton.visibility = View.VISIBLE
                    mastodonButton.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(mastodonLink.toUri())
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        this@ContributorsAdapter.mainContext.startActivity(intent)
                    }
                }
            }
        }

        fun showTelegramLink() {
            if (contributorsTelegramLinks.size > bindingAdapterPosition) {
                val telegramLink: String =
                    this@ContributorsAdapter.contributorsTelegramLinks[bindingAdapterPosition]
                if (!telegramLink.isEmpty()) {
                    telegramButton.visibility = View.VISIBLE
                    telegramButton.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(telegramLink.toUri())
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        this@ContributorsAdapter.mainContext.startActivity(intent)
                    }
                }
            }
        }

        fun showEmailLink() {
            if (contributorsEmails.size > bindingAdapterPosition) {
                val email: String = this@ContributorsAdapter.contributorsEmails[bindingAdapterPosition]
                if (!email.isEmpty()) {
                    emailButton.visibility = View.VISIBLE
                    emailButton.setOnClickListener {
                        val emailMailto = "mailto:$email"
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(emailMailto.toUri())
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        this@ContributorsAdapter.mainContext.startActivity(intent)
                    }
                }
            }
        }

        fun getNameText(): TextView {
            return this.nameText
        }

        fun getRoleText(): TextView {
            return this.roleText
        }

        fun getContributedToText(): TextView {
            return this.contributedToText
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.contributors_layout, viewGroup, false))
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        (viewHolder as ViewHolder).getNameText().text = this.contributorsNames[position]
        viewHolder.getRoleText().text = this.contributorsRoles[position]
        viewHolder.getContributedToText().text = this.contributorsContributedTo[position]
        viewHolder.showGithubLink()
        viewHolder.showMastodonLink()
        viewHolder.showTelegramLink()
        viewHolder.showEmailLink()
    }

    override fun getItemCount(): Int {
        return this.contributorsNames.size
    }
}
