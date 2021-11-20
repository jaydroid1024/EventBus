package org.greenrobot.eventbus.jay_test.myeventbus;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/20
 */
public enum MyThreadMode {
    Main,//主线程执行订阅方法
    POST,//默认
    BACKGROUND//后台线程订阅执行
}
