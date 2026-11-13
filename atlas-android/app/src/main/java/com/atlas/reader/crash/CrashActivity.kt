package com.atlas.reader.crash

import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.atlas.presentation.crash.CrashScreen
import com.atlas.reader.ui.base.activity.BaseActivity
import com.atlas.reader.ui.main.MainActivity
import com.atlas.reader.util.view.setComposeContent

class CrashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val exception = GlobalExceptionHandler.getThrowableFromIntent(intent)
        setComposeContent {
            CrashScreen(
                exception = exception,
                onRestartClick = {
                    finishAffinity()
                    startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                },
            )
        }
    }
}
