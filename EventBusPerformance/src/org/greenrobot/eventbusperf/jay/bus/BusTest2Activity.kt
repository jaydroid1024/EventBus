package org.greenrobot.eventbusperf.jay.bus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.greenrobot.eventbusperf.R

/**
 * @author jaydroid
 * @version 1.0
 * @date 6/11/21
 */
class BusTest2Activity : Activity() {

    var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_test_2)
        textView = findViewById(R.id.tv_sub)

    }


    override fun onStart() {
        super.onStart()
        //消费粘性事件方式一：
        val stickyEvent = EventBus.getDefault().getStickyEvent(MessageEvent::class.java)
        // 最好检查之前是否实际发布过事件
        if (stickyEvent != null) {
            // 消费掉粘性事件
            EventBus.getDefault().removeStickyEvent(stickyEvent)
        }
        //消费粘性事件方式二：
        val stickyEvent2 = EventBus.getDefault().removeStickyEvent(MessageEvent::class.java)
        // 最好检查之前是否实际发布过事件
        if (stickyEvent2 != null) {
            //已经消费了
        }

        //粘性事件是在register方法中会多次执行,除非 removeStickyEvent
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }


    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onMessageEvent_sticky(event: MessageEvent) {
        showMsg(event, "MAIN")
//        EventBus.getDefault().removeStickyEvent(event)
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun finish(event: MessageFinish) {
        Log.d("showMsg", "finish: ")
        this.finish()
    }

    private fun showMsg(event: MessageEvent, threadMode: String) {
        val t = Thread.currentThread().name
        textView!!.append("\n 发布者所在线程:${event.msg}, 订阅者所在线程: $t, 订阅者线程模式: $threadMode ")
        Log.d("showMsg", "showMsg: " + textView!!.text)
    }

    fun btnGo(view: View?) {
        startActivity(Intent(this, BusTest3Activity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}