package com.atlas.reader.ui.base.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.atlas.reader.ui.base.delegate.SecureActivityDelegate
import com.atlas.reader.ui.base.delegate.SecureActivityDelegateImpl
import com.atlas.reader.ui.base.delegate.ThemingDelegate
import com.atlas.reader.ui.base.delegate.ThemingDelegateImpl
import com.atlas.reader.util.system.prepareTabletUiContext
import atlas.app.di.appGraph

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    override fun attachBaseContext(newBase: Context) {
        val uiPreferences = newBase.appGraph.uiPreferences
        super.attachBaseContext(newBase.prepareTabletUiContext(uiPreferences))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }
}
