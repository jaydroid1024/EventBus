package org.greenrobot.eventbus.jay_test;

import org.greenrobot.eventbus.Subscribe;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/16
 */
public class SubscriberTestSupper {

    @Subscribe()
    public void onEvent(EventT e) {
        System.out.println("onEvent in supper");
    }

    @Subscribe()
    public void onEvent2(EventT e) {
        System.out.println("onEvent2");
    }
}