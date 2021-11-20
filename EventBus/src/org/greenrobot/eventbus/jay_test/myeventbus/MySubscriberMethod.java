package org.greenrobot.eventbus.jay_test.myeventbus;

import java.lang.reflect.Method;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/20
 */
public class MySubscriberMethod {
    final Method method;//方法
    final MyThreadMode threadMode;//线程模式
    final Class<?> eventType;//方法的入参参数类型

    public MySubscriberMethod(Method method, MyThreadMode threadMode, Class<?> eventType) {
        this.method = method;
        this.threadMode = threadMode;
        this.eventType = eventType;
    }
}

