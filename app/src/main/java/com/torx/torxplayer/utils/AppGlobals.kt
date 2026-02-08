package com.torx.torxplayer.utils

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

@SuppressLint("CommitPrefEdits", "StaticFieldLeak")
class AppGlobals : Application() {

    init {
        context = this
    }

    companion object {
        private var context: Context? = null

        private lateinit var sharedPref: SharedPreferences

        fun applicationContext(): Context {
            return context!!.applicationContext
        }

        val PREFS_NAME = "sharedPrefs"
        val KEY_LOGGED_IN = "login_key"
        //        const val SERVER = "http://192.168.100.150:8000"

    }

    override fun onCreate() {
        super.onCreate()
        val myContext: Context = applicationContext()
        Log.e("Check ", "yes")

        sharedPref = myContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    }


    fun saveString(KEY_NAME: String, text: String?) {

        Log.e("Check ", "Here")

        sharedPref.edit {

            putString(KEY_NAME, text)

        }
    }

    fun saveInt(KEY_NAME: String, value: Int) {
        sharedPref.edit {

            putInt(KEY_NAME, value)

        }
    }

    fun saveLoginOrBoolean(value: String, status: Boolean) {

        sharedPref.edit {

            putBoolean(value, status)

        }
    }

    fun getValueString(KEY_NAME: String): String? {

        return sharedPref.getString(KEY_NAME, null)

    }

    fun getValueInt(KEY_NAME: String): Int {

        return sharedPref.getInt(KEY_NAME, 0)
    }


    fun getValueBoolean(value: String): Boolean {

        return sharedPref.getBoolean(value, false)

    }

    fun logoutOrClearSharedPreference() {
        sharedPref.edit {
            clear()
        }
    }

    fun saveStringList(key: String, list: Array<String?>) {
        sharedPref.edit {
            putStringSet(key, list.toSet())
        }
    }

    fun getValueStringList(key: String): Array<String> {
        return sharedPref.getStringSet(key, emptySet())?.toTypedArray() ?: emptyArray()
    }

}