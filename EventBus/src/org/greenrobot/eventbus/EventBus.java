/*
 * Copyright (C) 2012-2020 Markus Junginger, greenrobot (http://greenrobot.org)
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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * EventBus is a central publish/subscribe event system for Java and Android.
 * Events are posted ({@link #post(Object)}) to the bus, which delivers it to subscribers that have a matching handler
 * method for the event type.
 * To receive events, subscribers must register themselves to the bus using {@link #register(Object)}.
 * Once registered, subscribers receive events until {@link #unregister(Object)} is called.
 * Event handling methods must be annotated by {@link Subscribe}, must be public, return nothing (void),
 * and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
public class EventBus {

    /** Log tag, apps may override it. */
    public static String TAG = "EventBus";

    static volatile EventBus defaultInstance;

    //静态常量为GC Root GC不会考虑他们
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();

    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    private final Map<Class<?>, Object> stickyEvents;

    private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState();
        }
    };

    // @Nullable
    private final MainThreadSupport mainThreadSupport;
    // @Nullable
    private final Poster mainThreadPoster;
    private final BackgroundPoster backgroundPoster;
    private final AsyncPoster asyncPoster;
    private final SubscriberMethodFinder subscriberMethodFinder;
    private final ExecutorService executorService;

    private final boolean throwSubscriberException;
    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    private final boolean eventInheritance;

    private final int indexCount;
    private final Logger logger;

    /** Convenience singleton for apps using a process-wide EventBus instance. */
    public static EventBus getDefault() {
        EventBus instance = defaultInstance;
        if (instance == null) {
            synchronized (EventBus.class) {
                instance = EventBus.defaultInstance;
                if (instance == null) {
                    instance = EventBus.defaultInstance = new EventBus();
                }
            }
        }
        return instance;
    }

    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

    /** For unit test primarily. */
    public static void clearCaches() {
        SubscriberMethodFinder.clearCaches();
        eventTypesCache.clear();
    }

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered. To use a
     * central bus, consider {@link #getDefault()}.
     */
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    EventBus(EventBusBuilder builder) {
        logger = builder.getLogger();
        //通过事件类找所有该事件的订阅者，
        subscriptionsByEventType = new HashMap<>();
        //通过订阅者类找所有Event，用于解注册等
        typesBySubscriber = new HashMap<>();
        //通过粘性事件类查找所有粘性事件对象
        stickyEvents = new ConcurrentHashMap<>();
        //构建 AndroidHandlerMainThreadSupport
        mainThreadSupport = builder.getMainThreadSupport();
        //构建 HandlerPoster
        mainThreadPoster = mainThreadSupport != null ? mainThreadSupport.createPoster(this) : null;
        //在后台发布事件
        backgroundPoster = new BackgroundPoster(this);
        //在后台发布事件
        asyncPoster = new AsyncPoster(this);
        indexCount = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
        //通过反射或APT查找订阅者
        subscriberMethodFinder = new SubscriberMethodFinder(
                //添加由 EventBus 的注释预处理器生成的索引。默认空集合
                builder.subscriberInfoIndexes,
                //启用严格的方法验证（默认值：false）
                builder.strictMethodVerification,
                //即使有APT生成的索引也强制使用反射（默认值：false）
                builder.ignoreGeneratedIndex);
        //无法分发事件时是否打印错误信息
        logSubscriberExceptions = builder.logSubscriberExceptions;
        //没有订阅者注册事件是否打印错误信息
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        //在调用订阅者时如果发生异常是否 发送一个 SubscriberExceptionEvent 通知订阅者
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        //没有订阅者注册事件是否是否通知订阅者类的父类
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        //在调用订阅者时如果发生异常是否抛出 RuntimeException
        throwSubscriberException = builder.throwSubscriberException;
        //是否通知订阅者类的父类中的订阅者方法
        eventInheritance = builder.eventInheritance;
        //订阅者执行在工作线程时用到的线程池：Executors.newCachedThreadPool()
        executorService = builder.executorService;
    }

    /**
     * 注册给定的订阅者以接收事件。 订阅者一旦对接收事件不再感兴趣，就必须调用 unregister(Object) 。
     *
     * 订阅者类中至少需要一个订阅者方法且必须由Subscribe注释标注。
     * Subscribe注解具有ThreadMode、优先级、粘性事件等配置
     * Registers the given subscriber to receive events. Subscribers must call {@link #unregister(Object)} once they
     * are no longer interested in receiving events.
     * <p/>
     * Subscribers have event handling methods that must be annotated by {@link Subscribe}.
     * The {@link Subscribe} annotation also allows configuration like {@link
     * ThreadMode} and priority.
     */
    public void register(Object subscriber) {
        Class<?> subscriberClass = subscriber.getClass();
        // 01查找订阅者方法流程
        // 通过订阅者类找出该类中所有的订阅者方法
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        synchronized (this) {
            //遍历该订阅者类中所有订阅者方法，执行订阅操作
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                // 02订阅流程
                // 收集订阅者，事件类，分发粘性事件等
                subscribe(subscriber, subscriberMethod);
            }
        }
    }


    // 发布者类
    // 发布者方法

    // 事件类
    // 事件类的父类

    // 订阅者类
    // 订阅者类的父类
    // 订阅者方法



    //向当前订阅者方法的 event 类对应的订阅者列表中添加当前订阅者
    //将当前订阅者方法的 event 类存到指定订阅者类下的列表里
    //当前订阅者中有粘性事件，在 register 的时候根据当前订阅者方法的 event 直接执行分发
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        //事件类
        Class<?> eventType = subscriberMethod.eventType;
        //封装订阅者=订阅者类+订阅者方法
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
        //Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
        //通过事件类找所有已经订阅过该事件的订阅者们，目的是为了进行优先级排序,全局缓存已注册订阅者等
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        //还没缓存该事件就存且只能存一次
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            //不能有重复订阅者：eventType+subscriber+subscriberMethod 是进程内唯一的
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }

        //该事件对应的所有订阅者进行排序
        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {
            //优先级排序，要么最小查到最后，要么在之前的某个位置
            if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                //向事件对应的订阅者列表中添加当前订阅者，完成排序操作
                subscriptions.add(i, newSubscription);
                break;
            }
        }



        //Map<Object, List<Class<?>>> typesBySubscriber;
        //通过订阅者类找所有已经注册过的 Event 们， 用于判断是否注册、解注册等
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        //将当前订阅者方法的 event 存到指定订阅者类下的列表里
        subscribedEvents.add(eventType);


        //粘性事件：先pst 后 订阅
        //当前订阅者中有粘性事件，在 register 的时候根据当前订阅者方法的 event 直接执行分发
        if (subscriberMethod.sticky) {
            //eventInheritance: 默认true
            // 默认情况下，EventBus 考虑事件类层次结构（将通知超类的订阅者）。 关闭此功能将改进事件的发布。
            // 对于直接扩展 Object 的简单事件类，我们测得事件发布速度提高了 20%。 对于更复杂的事件层次结构，加速应该大于 20%。
            //但是，请记住，事件发布通常只消耗应用程序内一小部分 CPU 时间，除非它以高速率发布，例如每秒数百/数千个事件

            if (eventInheritance) { //
                // Existing sticky events of all subclasses of eventType have to be considered.
                // Note: Iterating over all events may be inefficient with lots of sticky events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                //必须考虑 eventType 的所有子类的现有粘性事件。注意：对于大量粘性事件，迭代所有事件可能效率低下，
                // 因此应更改数据结构以允许更有效的查找（例如，存储超类的子类的附加映射：Class -> List<Class>）。

                //stickyEvents = new ConcurrentHashMap<>();
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    //key: event.getClass(), value: event
                    Class<?> candidateEventType = entry.getKey();
                    //isAssignableFrom: 确定此Class对象表示的类或接口是否与指定的Class参数表示的类或接口相同，或者是其超类或超接口。 如果是，则返回true ； 否则返回false 。 如果此Class对象表示原始类型，则如果指定的Class参数正是此Class对象，则此方法返回true ； 否则返回false 。
                    //具体来说，此方法测试是否可以通过标识转换或通过扩展引用转换将指定Class参数表示的类型转换为此Class对象表示的类型。 有关详细信息，请参阅Java 语言规范5.1.1 和 5.1.4 节
                    //事件类和它的父类都会收到该订阅
                    if (eventType.isAssignableFrom(candidateEventType)) { //比较class
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                //通过粘性事件类查找所有粘性事件对象
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
            // --> Strange corner case, which we don't take care of here.
            //如果订阅者试图中止事件，它将失败（事件在发布状态下不被跟踪）--> 奇怪的极端情况，我们在这里不处理。
            postToSubscription(newSubscription, stickyEvent, isMainThread());
        }
    }

    /**
     * Checks if the current thread is running in the main thread.
     * If there is no main thread support (e.g. non-Android), "true" is always returned. In that case MAIN thread
     * subscribers are always called in posting thread, and BACKGROUND subscribers are always called from a background
     * poster.
     */
    private boolean isMainThread() {
        return mainThreadSupport == null || mainThreadSupport.isMainThread();
    }

    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    //根据每个事件类解除每个订阅者
    //只更新 subscriptionsByEventType，而不是 typesBySubscriber！调用者必须更新 typesBySubscriber。
    /** Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber. */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        //根据每个事件类找到所有该事件的订阅者
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                //每个订阅者：类+方法
                Subscription subscription = subscriptions.get(i);
                //确认是当前类的订阅者
                if (subscription.subscriber == subscriber) {
                    //修改解除订阅标志位
                    subscription.active = false;
                    subscriptions.remove(i);
                    i--; //防止越界
                    size--;
                }
            }
        }
    }

    /** Unregisters the given subscriber from all event classes. */
    public synchronized void unregister(Object subscriber) {
        //找到订阅者类对应的事件类列表
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            //根据每个事件类解除每个订阅者
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            //从内存map 移除
            typesBySubscriber.remove(subscriber);
        } else {
            logger.log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /** Posts the given event to the event bus. */
    public void post(Object event) {
        //currentPostingThreadState = new ThreadLocal<PostingThreadState>()
        //每个线程都有一份 postingState 实例，
        //封装 PostingThreadState 对于 ThreadLocal，设置（并获得多个值）要快得多。
        PostingThreadState postingState = currentPostingThreadState.get();
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);
        if (!postingState.isPosting) { // 默认 false
            postingState.isMainThread = isMainThread(); //判断主线程还是子线程
            postingState.isPosting = true; //这里保证 cancelEventDelivery 是在同一个线程调用的
            if (postingState.canceled) { //cancelEventDelivery
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                //可能发送了多个事件
                while (!eventQueue.isEmpty()) {
                    //发送队列依次取出第一个事件执行发布
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                //发送完事件后重置标志位
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    /**
     * Called from a subscriber's event handling method, further event delivery will be canceled. Subsequent
     * subscribers
     * won't receive the event. Events are usually canceled by higher priority subscribers (see
     * {@link Subscribe#priority()}). Canceling is restricted to event handling methods running in posting thread
     * {@link ThreadMode#POSTING}.
     */
    public void cancelEventDelivery(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();
        if (!postingState.isPosting) {
            throw new EventBusException(
                    "This method may only be called from inside event handling methods on the posting thread");
        } else if (event == null) {
            throw new EventBusException("Event may not be null");
        } else if (postingState.event != event) {
            throw new EventBusException("Only the currently handled event may be aborted");
        } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.POSTING) {
            throw new EventBusException(" event handlers may only abort the incoming event");
        }

        postingState.canceled = true;
    }

    /**
     * 将给定的事件发布到事件总线并保留该事件（因为它是粘性的）。 事件类型的最新粘性事件保存在内存中，供订阅者使用Subscribe.sticky()将来访问
     * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
     * event of an event's type is kept in memory for future access by subscribers using {@link Subscribe#sticky()}.
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        //放置后应发布，以防订阅者想立即删除
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);
    }

    /**
     * Gets the most recent sticky event for the given type.
     *
     * @see #postSticky(Object)
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            //将对象强制转换为由此Class对象表示的类或接口。
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * Remove and gets the recent sticky event for the given event type.
     *
     * @see #postSticky(Object)
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * Removes the sticky event if it equals to the given event.
     *
     * @return true if the events matched and the sticky event was removed.
     */
    public boolean removeStickyEvent(Object event) {
        synchronized (stickyEvents) {
            Class<?> eventType = event.getClass();
            Object existingEvent = stickyEvents.get(eventType);
            if (event.equals(existingEvent)) {
                stickyEvents.remove(eventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    public boolean hasSubscriberForEvent(Class<?> eventClass) {
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        if (eventTypes != null) {
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                CopyOnWriteArrayList<Subscription> subscriptions;
                synchronized (this) {
                    subscriptions = subscriptionsByEventType.get(clazz);
                }
                if (subscriptions != null && !subscriptions.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    //发送队列的第一个事件
    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        //是否考虑订阅者的继承关系
        if (eventInheritance) {
            //事件类和事件类的父类们
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                //当前类和父类有一个没收到就算失败
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
            }
            //兜底方案，发送一个通知事件，告诉订阅者刚才的事件没发送成功
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            //通过单事件，找到所有观察者
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            //遍历所有观察该事件的观察者，执行发布操作
            for (Subscription subscription : subscriptions) {
                //存入线程
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    //重置状态
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    //在 EventBus 中，您可以使用四种 ThreadMode 来指定订阅者方法所在的线程。
    //
    //- [1 ThreadMode: POSTING](https://greenrobot.org/eventbus/documentation/delivery-threads-threadmode/#ThreadMode_POSTING) ：发布者和订阅者在同一个线程。
    //  - 这是默认设置。事件传递是同步完成的，需要注意避免阻塞主线程。
    //  - 避免了线程切换意味着开销较小。
    //- [2 ThreadMode: MAIN](https://greenrobot.org/eventbus/documentation/delivery-threads-threadmode/#ThreadMode_MAIN) ：订阅者将在 Android 的主线程（UI 线程）中调用。
    //  - 事件传递是同步完成的，需要注意避免阻塞主线程。
    //- [3 ThreadMode: MAIN_ORDERED](https://greenrobot.org/eventbus/documentation/delivery-threads-threadmode/#ThreadMode_MAIN_ORDERED) ：订阅者将在 Android 的主线程中被调用，该事件总是通过Handler排队等待稍后传递给订阅者。
    //  - 为事件处理提供了更严格和更一致的顺序。
    //  - 如果前一个也是main_ordered 需要等前一个执行完成后才执行。
    //  - 事件传递是异步完成的。
    //- [4 ThreadMode: BACKGROUND](https://greenrobot.org/eventbus/documentation/delivery-threads-threadmode/#ThreadMode_BACKGROUND) ：如果发帖线程非主线程则订阅者的处理会在工作线程中执行否则和发布者同一个线程处理。
    //  - 事件传递是异步完成的。
    //- [5 ThreadMode: ASYNC](https://greenrobot.org/eventbus/documentation/delivery-threads-threadmode/#ThreadMode_ASYNC) ：无论事件在哪个线程发布，订阅者都会在新建的工作线程中执行。
    //  - EventBus 使用线程池来有效地重用线程。
    //  - 事件传递是异步完成的。
    //  - 如果事件处理程序方法的执行可能需要一些时间，则应使用此模式，例如用于网络访问
    //发布到订阅
    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        //根据线程模型不同处理
        switch (subscription.subscriberMethod.threadMode) {
            case POSTING: //订阅和发布是同一个线程
                invokeSubscriber(subscription, event);
                break;
            case MAIN: //订阅在主线程
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    //通过Handler 发送到主线程
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case MAIN_ORDERED: //订阅在主线程排队
                if (mainThreadPoster != null) {
                    mainThreadPoster.enqueue(subscription, event);
                } else {
                    //临时：技术上不正确，因为海报没有与订阅者分离
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeSubscriber(subscription, event);
                }
                break;
            case BACKGROUND: //如果发帖线程非主线程则订阅者的处理会在工作线程中执行否则和发布者同一个线程处理。
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case ASYNC://无论事件在哪个线程发布，订阅者都会在新建的工作线程中执行。
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }

    /**
     * 查找所有 Class 对象，包括超类和接口。 也应该适用于接口。
     *  Looks up all Class objects including super classes and interfaces. Should also work for interfaces. */
    private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    eventTypes.add(clazz);
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                eventTypesCache.put(eventClass, eventTypes);
            }
            return eventTypes;
        }
    }

    //通过超级接口递归。
    /** Recurses through super interfaces. */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    /**
     * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions prevents race conditions
     * between {@link #unregister(Object)} and event delivery. Otherwise the event might be delivered after the
     * subscriber unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    //同一个线程执行订阅者方法
    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            //方法、类、参数 反射调用观察者
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof SubscriberExceptionEvent) {
            if (logSubscriberExceptions) {
                // Don't send another SubscriberExceptionEvent to avoid infinite event recursion, just log
                logger.log(Level.SEVERE, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                logger.log(Level.SEVERE, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                logger.log(Level.SEVERE, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
                post(exEvent);
            }
        }
    }

    /**
     * 对于 ThreadLocal，设置（并获得多个值）要快得多。
     * For ThreadLocal, much faster to set (and get multiple values). */
    final static class PostingThreadState {
        final List<Object> eventQueue = new ArrayList<>();
        boolean isPosting;
        boolean isMainThread;
        Subscription subscription;
        Object event;
        boolean canceled;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * For internal use only.
     */
    public Logger getLogger() {
        return logger;
    }

    // Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
    /* public */interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

    @Override
    public String toString() {
        return "EventBus[indexCount=" + indexCount + ", eventInheritance=" + eventInheritance + "]";
    }
}
