package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.consora_mobile.R

/** Pestaña que todavía no está implementada (Chats, Asistentes...). */
class PlaceholderFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val args = requireArguments()
        val view = inflateSection(
            inflater, container,
            getString(args.getInt(ARG_TITLE)), getString(args.getInt(ARG_SUBTITLE)),
        )
        view.findViewById<LinearLayout>(R.id.sectionContent).addEmptyState(
            args.getInt(ARG_ICON), getString(R.string.proximamente), getString(R.string.proximamente_mensaje),
        )
        return view
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_ICON = "icon"

        fun newInstance(@StringRes title: Int, @StringRes subtitle: Int, @DrawableRes icon: Int) =
            PlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE, title)
                    putInt(ARG_SUBTITLE, subtitle)
                    putInt(ARG_ICON, icon)
                }
            }
    }
}
