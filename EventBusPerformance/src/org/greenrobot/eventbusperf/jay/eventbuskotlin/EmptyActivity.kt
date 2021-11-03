package org.greenrobot.eventbusperf.jay.eventbuskotlin

import android.app.Activity
import android.os.Bundle
import android.view.View
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbusperf.R

class EmptyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_empty)
    }

    fun onPostStickyButtonClick(view: View) {
        EventBus.getDefault().postSticky(BlankBaseFragment.SampleEvent())
        finish()
    }

}
