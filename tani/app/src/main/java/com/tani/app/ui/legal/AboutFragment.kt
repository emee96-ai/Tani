package com.tani.app.ui.legal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import com.tani.app.BuildConfig
import com.tani.app.R
import com.tani.app.ui.common.ScreenUi

class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val content = ScreenUi.root(context).apply {
            addView(ScreenUi.title(context, getString(R.string.main_25)))
            addView(ScreenUi.subtitle(context, getString(R.string.about_1)))
            addSection(
                getString(R.string.about_2),
                getString(R.string.about_3)
            )
            addSection(
                getString(R.string.about_4),
                getString(R.string.about_5)
            )
            addSection(
                getString(R.string.about_6),
                getString(R.string.about_7)
            )
            addSection(
                getString(R.string.about_8),
                getString(R.string.about_9)
            )
            addView(ScreenUi.muted(context, getString(R.string.about_10, BuildConfig.VERSION_NAME)))
        }
        return ScrollView(context).apply {
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(content)
        }
    }

    private fun LinearLayout.addSection(title: String, body: String) {
        val context = this.context
        addView(ScreenUi.card(context).apply {
            addView(ScreenUi.text(context, title, 22f, true))
            addView(ScreenUi.text(context, body, 16f))
        })
    }
}
