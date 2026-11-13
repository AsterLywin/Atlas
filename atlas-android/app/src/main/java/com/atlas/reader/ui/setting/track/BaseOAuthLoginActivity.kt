package com.atlas.reader.ui.setting.track

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import com.atlas.reader.data.track.TrackerManager
import com.atlas.reader.ui.base.activity.BaseActivity
import com.atlas.reader.ui.main.MainActivity
import com.atlas.reader.util.view.setComposeContent
import atlas.app.di.appGraph
import atlas.presentation.core.screens.LoadingScreen

@HasMemberInjections
abstract class BaseOAuthLoginActivity : BaseActivity() {

    @Inject protected lateinit var trackerManager: TrackerManager

    abstract fun handleResult(uri: Uri)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appGraph.inject(this)

        setComposeContent {
            LoadingScreen()
        }

        val data = intent.data
        if (data == null) {
            returnToSettings()
        } else {
            handleResult(data)
        }
    }

    internal fun returnToSettings() {
        finish()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
