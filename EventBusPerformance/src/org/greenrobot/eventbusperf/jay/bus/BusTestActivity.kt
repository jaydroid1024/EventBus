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
import org.greenrobot.eventbus.jay_test.myeventbus.MyEventBus
import org.greenrobot.eventbus.jay_test.myeventbus.MySubscribe
import org.greenrobot.eventbus.jay_test.myeventbus.MyThreadMode
import org.greenrobot.eventbusperf.R
import kotlin.concurrent.thread

/**
 * @author jaydroid
 * @version 1.0
 * @date 6/11/21
 */
class BusTestActivity : Activity() {
    var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_test)
        textView = findViewById(R.id.tv_sub)
    }

    public override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        MyEventBus.getInstance().register(this)
    }

    public override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        MyEventBus.getInstance().unregister(this)

    }

    //java.lang.ClassCastException: com.sun.tools.javac.code.Type$JCPrimitiveType cannot be cast to javax.lang.model.type.DeclaredType
//    @Subscribe(sticky = true, threadMode = ThreadMode.POSTING, priority = 2)
//    fun test1(event: Int) {
//
//    }


    @Subscribe(sticky = true, threadMode = ThreadMode.POSTING, priority = 2)
    fun test2(event: () -> Unit) {
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.POSTING, priority = 2)
    fun test3(event: List<String>) {
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.POSTING, priority = 2)
    fun test4(event: List<Boolean>) {
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.POSTING, priority = 2)
    fun test5(event: HashMap<String, Boolean>) {
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.POSTING, priority = 2)
    fun test6(event: HashMap<String, MessageEvent>) {
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.POSTING, priority = 2)
    fun onMessageEvent_POSTING1(event: MessageEvent) {
        showMsg(event, "POSTING1")
    }

    @Subscribe(threadMode = ThreadMode.MAIN, priority = 1)
    fun onMessageEvent_MAIN1(event: MessageEvent) {
        showMsg(event, "MAIN1")
    }

    @Subscribe(threadMode = ThreadMode.MAIN, priority = 3)
    fun onMessageEvent_MAIN2(event: MessageEvent) {
        showMsg(event, "MAIN2")
        // 取消事件向下传递
        EventBus.getDefault().cancelEventDelivery(event)
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onMessageEvent_MAIN_ORDERED1(event: MessageEvent) {
        showMsg(event, "MAIN_ORDERED1")
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent_BACKGROUND(event: MessageEvent) {
        showMsg(event, "BACKGROUND")
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onMessageEvent_ASYNC(event: MessageEvent) {
        showMsg(event, "ASYNC")
    }

    private fun showMsg(event: MessageEvent, threadMode: String) {
        val t = Thread.currentThread().name
        runOnUiThread {
            textView!!.append("\n 发布者所在线程:${event.msg}, 订阅者所在线程: $t, 订阅者线程模式: $threadMode ")
        }
        Log.d("showMsg", "showMsg: " + textView!!.text)
    }

    fun btnSendOnMain(view: View?) {
        textView!!.append("\nbtnSendOnMain-start")
//        EventBus.getDefault().postSticky(MessageEvent(Thread.currentThread().name))
        EventBus.getDefault().postSticky(SubEvent<String>())
        textView!!.append("\nbtnSendOnMain-end")
    }

    fun btnSendOnThread(view: View?) {
        textView!!.append("\nbtnSendOnThread-start")
        thread {
            EventBus.getDefault().post(MessageEvent(Thread.currentThread().name))
        }
        textView!!.append("\nbtnSendOnThread-end")

    }

    fun btnMyEventBus(view: View?) {
        textView!!.append("\nbtnMyEventBus-start")

        MyEventBus.getInstance().post(MessageEvent(Thread.currentThread().name))

        textView!!.append("\nbtnMyEventBus-end")

    }

    @MySubscribe()
    fun onMessageEvent(event: MessageEvent) {
        textView!!.append("\nonMessageEvent 收到了")
    }


    fun btnGo(view: View?) {

        startActivity(Intent(this, BusTest2Activity::class.java))
    }
}