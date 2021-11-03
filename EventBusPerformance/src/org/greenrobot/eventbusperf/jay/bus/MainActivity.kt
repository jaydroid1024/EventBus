package org.greenrobot.eventbusperf.jay.bus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbusperf.R
import org.greenrobot.eventbusperf.jay.eventbuskotlin.BlankBaseFragment
import org.greenrobot.eventbusperf.jay.eventbuskotlin.EmptyActivity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        EventBus.builder().addIndex(MyEventBusIndex()).installDefaultEventBus()

    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(sticky = true)
    fun handleEvent(event: BlankBaseFragment.SampleEvent) {
        val className = this::class.simpleName
        val message = "#handleEvent: called for " + event::class.simpleName
        Toast.makeText(this, className + message, Toast.LENGTH_SHORT).show()
        Log.d(className, message)

        // prevent event from re-delivering, like when leaving and coming back to app
        EventBus.getDefault().removeStickyEvent(event)
    }


    @Subscribe(sticky = true)
    fun messageEvent(event: MessageEvent) {
        val t = Thread.currentThread().name
//        val msg = """${event.msg}, currentThread: $t"""
        val msg = "MessageEvent"
        Toast.makeText(this, "sub:msg$msg", Toast.LENGTH_LONG).show()

        // prevent event from re-delivering, like when leaving and coming back to app
        EventBus.getDefault().removeStickyEvent(event)
    }

    @Subscribe(sticky = true)
    fun subEvent(event: SubEvent<String>) {
        val t = Thread.currentThread().name
        val msg = """${event.msg}, currentThread: $t"""
//        val msg = "MessageEvent"
        Toast.makeText(this, "sub:msg$msg", Toast.LENGTH_LONG).show()

        // prevent event from re-delivering, like when leaving and coming back to app
        EventBus.getDefault().removeStickyEvent(event)
    }

    fun onPostButtonClick(view: View) {
        EventBus.getDefault().post(BlankBaseFragment.SampleEvent())
    }

    fun onLaunchButtonClick(view: View) {
        startActivity(Intent(this, EmptyActivity::class.java))
    }

    fun onBusTestActivity(view: View) {
        startActivity(Intent(this, BusTestActivity::class.java))

    }
}
