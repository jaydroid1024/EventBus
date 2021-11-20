package org.greenrobot.eventbus.jay_test;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.jay_test.myeventbus.MyEventBus;
import org.greenrobot.eventbus.jay_test.myeventbus.MySubscribe;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/16
 */
public class SubscriberTest extends SubscriberTestSupper {

    public void register() {
        EventBus.getDefault().register(this);
    }

    @MySubscribe()
    public void onEvent2(String e) {
        System.out.println("MySubscribe in sub e=" + e);
    }

    @Subscribe(sticky = true)
    public void onEvent(String e) {
        System.out.println("String in sub e=" + e);
    }

    @Subscribe(sticky = true)
    public void onEvent(Integer e) {
        System.out.println("int in sub e=" + e);
    }

    @Override
    @Subscribe(sticky = true)
    public void onEvent(EventT e) {
        System.out.println("onEvent in sub");
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    public void onEvent(EventT2 e) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        }
        System.out.println("EventT2 in sub " + Thread.currentThread().getName());
    }

}