package org.greenrobot.eventbus.jay_test.myeventbus;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/20
 */
public class MySubscription {
    // 注册对象
    final Object subscriber;
    // 方法包装类
    final MySubscriberMethod subscriberMethod;

    public MySubscription(Object subscriber, MySubscriberMethod mySubscriberMethod) {
        this.subscriber = subscriber;
        this.subscriberMethod = mySubscriberMethod;
    }
}
