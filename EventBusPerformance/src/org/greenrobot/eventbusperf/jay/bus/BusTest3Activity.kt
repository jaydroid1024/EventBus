package org.greenrobot.eventbusperf.jay.bus

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbusperf.R

/**
 * @author jaydroid
 * @version 1.0
 * @date 6/11/21
 */
class BusTest3Activity : Activity() {

    var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_test_3)
        textView = findViewById(R.id.tv_sub)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun btnGo(view: View?) {
        EventBus.getDefault().post(MessageFinish(Thread.currentThread().name))
        this.finish()
    }
}