package org.greenrobot.eventbusperf.jay.bus

import android.app.Application
import com.example.myapp.MyEventBusIndex
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.greenrobot.eventbus.util.AsyncExecutor
import org.greenrobot.eventbus.util.ThrowableFailureEvent


/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/8
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        EventBus.builder()
            //将此与 BuildConfig.DEBUG 一起使用可让应用程序尽在在 DEBUG 模式下崩溃。默认为false
            // 这样就不会在开发过程中错过异常（Invoking subscriber failed）
            .throwSubscriberException(false)
            //如果发送了没有订阅者的event,是否需要打印提示哪一个 event bean 的log,默认为true
            //提示信息： No subscribers registered for event class org.greenrobot.eventbusperf.jay.bus.SubEvent
            .logNoSubscriberMessages(true)
            .installDefaultEventBus()

        //创建一个新实例并配置索引类
        val eventBus = EventBus.builder().addIndex(MyEventBusIndex()).build()
        //使用单例模式并配置索引类
        EventBus.builder().addIndex(MyEventBusIndex()).installDefaultEventBus()
        // Now the default instance uses the given index. Use it like this:
//        val eventBus = EventBus.getDefault()

        //AsyncExecutor类似于线程池，但具有失败(异常)处理。失败是抛出异常，AsyncExecutor将把这些异常封装在一个事件中，该事件将自动发布。
        AsyncExecutor.create().execute {
            EventBus.getDefault().postSticky(SubEvent<String>())
        }


    }


    //线程池中发出的时间
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun handleLoginEvent(event: SubEvent<String>) {
        // do something
    }

    //线程池中任务异常时发出的时间
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun handleFailureEvent(event: ThrowableFailureEvent) {
        // do something
    }


}