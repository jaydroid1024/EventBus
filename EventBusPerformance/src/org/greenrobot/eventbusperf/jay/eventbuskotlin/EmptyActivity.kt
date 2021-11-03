package org.greenrobot.eventbusperf.jay.eventbuskotlin

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbusperf.R

class EmptyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_empty)
    }

    fun onPostStickyButtonClick(view: View) {
        EventBus.getDefault().postSticky(BlankBaseFragment.SampleEvent())
        finish()
    }

}
