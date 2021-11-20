/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.greenrobot.eventbus;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Subscribe {
    //用来指定指定订阅者方法所在的线程。
    ThreadMode threadMode() default ThreadMode.POSTING;
    //如果为 true，则将最近的粘性事件（通过EventBus.postSticky(Object) ）传递给该订阅者。
    boolean sticky() default false;
    //订阅者优先级影响事件传递的顺序。
    // 在同一个交付线程 ( ThreadMode ) 中，较高优先级的订阅者将在其他具有较低优先级的订阅者之前收到事件。 默认优先级为 0。
    // 注意：优先级不会影响具有不同ThreadMode的订阅者之间的传递顺序！
    int priority() default 0;
}

