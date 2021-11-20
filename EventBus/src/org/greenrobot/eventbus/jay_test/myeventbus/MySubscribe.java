package org.greenrobot.eventbus.jay_test.myeventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/20
 */

/**
 * 该注解表示方法需要被EventBus订阅
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MySubscribe {
    MyThreadMode threadMode() default MyThreadMode.POST;
}