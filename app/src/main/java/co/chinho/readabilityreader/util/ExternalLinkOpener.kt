package co.chinho.readabilityreader.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object ExternalLinkOpener {

    fun open(context: Context, url: String, handler: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return

        when (handler) {
            CUSTOM_TABS -> openCustomTabs(context, uri)
            else -> openSystemBrowser(context, uri)
        }
    }

    private fun openCustomTabs(context: Context, uri: Uri) {
        runCatching {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        }.onFailure {
            openSystemBrowser(context, uri)
        }
    }

    private fun openSystemBrowser(context: Context, uri: Uri) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            if (it !is ActivityNotFoundException) {
                throw it
            }
        }
    }

    private const val CUSTOM_TABS = "custom_tabs"
}
