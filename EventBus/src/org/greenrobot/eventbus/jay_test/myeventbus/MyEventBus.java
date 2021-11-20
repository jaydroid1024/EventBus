package org.greenrobot.eventbus.jay_test.myeventbus;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/20
 */
public class MyEventBus {
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;
    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    private static final MyEventBus instance = new MyEventBus();
    //以注册对象的Class为key，以包装方法类SubscriberMethod列表为value
    private final Map<Class<?>, List<MySubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
    //以订阅方法的唯一入参参数：Class为key，以Subscription（包装了注册对象和SubscriberMethod（包装了Method、注解信息等））列表为value
    private final Map<Class<?>, CopyOnWriteArrayList<MySubscription>> subscriptionsByEventType = new HashMap<>();
    //以注册对象为key，以订阅方法的参数类型Class集合为value
    private final Map<Object, List<Class<?>>> typesBySubscriber = new HashMap<>();
    //线程池
    private final ExecutorService executorService;

    private MyEventBus() {
        executorService = Executors.newCachedThreadPool();
    }

    public static MyEventBus getInstance() {
        return instance;
    }

    /**
     * 订阅
     *
     * @param obj
     */
    public void register(Object obj) {
        Class<?> clz = obj.getClass();
        //查找订阅方法
        List<MySubscriberMethod> methods = findSubscriberMethod(clz);

        //加入订阅map
        if (!methods.isEmpty()) {
            synchronized (this) {
                subscribe(obj, methods);
            }
        }
    }

    /**
     * 将订阅方法加入两个map缓存
     *
     * @param obj
     * @param methods
     */
    private void subscribe(Object obj, List<MySubscriberMethod> methods) {
        //对象中订阅方法的入参类型集合
        Set<Class<?>> ObjEventTypes = new HashSet<>();

        for (MySubscriberMethod subscriberMethod : methods) {
            //根据入参类型获取Subscription列表
            Class<?> eventType = subscriberMethod.eventType;
            CopyOnWriteArrayList<MySubscription> subscriptions = subscriptionsByEventType.get(eventType);


            if (subscriptions == null) {
                subscriptions = new CopyOnWriteArrayList<>();
                subscriptionsByEventType.put(eventType, subscriptions);
            }

            //包装成Subscription存入
            subscriptions.add(new MySubscription(obj, subscriberMethod));

            //记录入参类型
            ObjEventTypes.add(eventType);
        }


        // 将对象：入参类型列表存入map
        if (typesBySubscriber.get(obj) == null) {
            List<Class<?>> eventTypes = new ArrayList<>(ObjEventTypes);

            typesBySubscriber.put(obj, eventTypes);
        }
    }

    /**
     * 根据class查找订阅方法
     *
     * @param clz
     * @return
     */
    private List<MySubscriberMethod> findSubscriberMethod(Class<?> clz) {
        List<MySubscriberMethod> methods = METHOD_CACHE.get(clz);
        if (methods != null) {
            return methods;
        }

        // 利用反射获取
        return getMethodsByReflect(clz);
    }

    /**
     * 根据class反射获取订阅方法
     *
     * @param clz
     * @return
     */
    private List<MySubscriberMethod> getMethodsByReflect(Class<?> clz) {
        // 订阅方法集合
        List<MySubscriberMethod> subscriberMethods = new ArrayList<>();

        while (clz != null && clz != Object.class) {
            Method[] declaredMethods = clz.getDeclaredMethods();

            for (Method method : declaredMethods) {
                MySubscribe annotation = method.getAnnotation(MySubscribe.class);
                if (!Modifier.isPublic(method.getModifiers()) ||
                        (method.getModifiers() & MODIFIERS_IGNORE) != 0 || annotation == null)
                    continue;

                //获取线程模式
                MyThreadMode threadMode = annotation.threadMode();
                //获取入参参数类型
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1) throw new RuntimeException("入参参数必须为一个");
                Class<?> eventType = parameterTypes[0];

                // 构造方法包装类
                MySubscriberMethod subscriberMethod = new MySubscriberMethod(method, threadMode, eventType);

                //加入缓存
                subscriberMethods.add(subscriberMethod);
            }

            clz = clz.getSuperclass();
        }

        if (subscriberMethods.isEmpty()) throw new RuntimeException("该类及其父类没有任何没有订阅方法");

        //加入缓存
        METHOD_CACHE.put(clz, subscriberMethods);
        return subscriberMethods;
    }

    /**
     * 取消订阅
     *
     * @param obj
     */
    public synchronized void unregister(Object obj) {
        //获取该对象订阅方法的所有参数类型
        List<Class<?>> eventTypes = typesBySubscriber.get(obj);
        if (eventTypes != null) {
            for (Class<?> eventType : eventTypes) {
                //获取Subscription集合
                CopyOnWriteArrayList<MySubscription> subscriptions = subscriptionsByEventType.get(eventType);
                for (int i = 0; i < subscriptions.size(); i++) {
                    if (subscriptions.get(i).subscriber == obj) {//判断是不是该对象的方法
                        subscriptions.remove(i);//移除
                        i--;
                    }
                }
            }

            // 移除
            typesBySubscriber.remove(obj);
        }
    }

    /**
     * 发送消息
     *
     * @param event
     */
    public void post(Object event) {
        //获取到订阅方法
        CopyOnWriteArrayList<MySubscription> subscriptions = subscriptionsByEventType.get(event.getClass());
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (MySubscription subscription : subscriptions) {
                postToSubscription(event, subscription.subscriber, subscription.subscriberMethod);
            }
        }
    }

    /**
     * 线程调度
     *
     * @param event
     * @param subscriberMethod
     */
    private void postToSubscription(final Object event, final Object subscriber, final MySubscriberMethod subscriberMethod) {
        // 当前线程是否是主线程
        boolean isMainThread = Looper.myLooper() == Looper.getMainLooper();
        switch (subscriberMethod.threadMode) {
            case Main:
                if (isMainThread) {//主线程直接调用
                    invokeSubscriberMethod(event, subscriber, subscriberMethod);
                } else {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            invokeSubscriberMethod(event, subscriber, subscriberMethod);
                        }
                    });
                }
                break;
            case POST:
                invokeSubscriberMethod(event, subscriber, subscriberMethod);
                break;
            case BACKGROUND:
                // 偷懒
                getExecutorService().execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeSubscriberMethod(event, subscriber, subscriberMethod);
                    }
                });
                break;
        }
    }

    /**
     * 反射调用订阅方法
     *
     * @param event
     * @param subscriber
     * @param subscriberMethod
     */
    private void invokeSubscriberMethod(Object event, Object subscriber, MySubscriberMethod subscriberMethod) {
        try {
            subscriberMethod.method.invoke(subscriber, event);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取线程池
     *
     * @return ExecutorService
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

}

