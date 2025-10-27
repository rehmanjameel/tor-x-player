package com.arconn.devicedesk.utils

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

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

        sharedPref = myContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    }


    fun saveString(KEY_NAME: String, text: String) {

        Log.e("Check ", "Here")

        val editor: SharedPreferences.Editor = sharedPref.edit()

        editor.putString(KEY_NAME, text)

        editor.apply()
    }

    fun saveInt(KEY_NAME: String, value: Int) {
        val editor: SharedPreferences.Editor = sharedPref.edit()

        editor.putInt(KEY_NAME, value)

        editor.apply()
    }

    fun saveLoginOrBoolean(value: String, status: Boolean) {

        val editor: SharedPreferences.Editor = sharedPref.edit()

        editor.putBoolean(value, status)

        editor.apply()
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
        val editor: SharedPreferences.Editor = sharedPref.edit()
        editor.clear().apply()
    }

}