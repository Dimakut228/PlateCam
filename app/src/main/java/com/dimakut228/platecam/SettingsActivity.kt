package com.dimakut228.platecam

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
            findPreference<Preference>("check_updates")?.setOnPreferenceClickListener {
                val activity = requireActivity() as AppCompatActivity
                Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()
                UpdateFlow.check(activity) { result ->
                    activity.runOnUiThread {
                        val message = when (result) {
                            UpdateFlow.Result.UP_TO_DATE -> R.string.update_uptodate
                            UpdateFlow.Result.FAILED -> R.string.update_failed
                            UpdateFlow.Result.UPDATE_AVAILABLE -> return@runOnUiThread
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            findPreference<Preference>("about")?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
                    .setMessage(getString(R.string.about_text))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
        }
    }
}
