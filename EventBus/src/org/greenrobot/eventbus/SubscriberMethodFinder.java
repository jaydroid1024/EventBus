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

import org.greenrobot.eventbus.meta.SubscriberInfo;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SubscriberMethodFinder {
    /*
    在较新的类文件中，编译器可能会添加方法。这些被称为桥接或合成方法。 EventBus 必须忽略两者。修饰符不是公共的，而是以 Java 类文件格式定义的：
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

    private List<SubscriberInfoIndex> subscriberInfoIndexes;
    private final boolean strictMethodVerification;
    private final boolean ignoreGeneratedIndex;

    private static final int POOL_SIZE = 4;
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes, boolean strictMethodVerification,
                           boolean ignoreGeneratedIndex) {
        this.subscriberInfoIndexes = subscriberInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    //查找订阅者方法
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        //先从内存缓存取，节省查找开销
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        //即使有APT生成的索引也强制使用反射（默认值：false）
        if (ignoreGeneratedIndex) {
            //运行时反射查找
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            //通过APT中收集数据中查找
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        if (subscriberMethods.isEmpty()) {
            //订阅者类中至少有一个订阅者方法，否则运行时报错
            throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {
            //找到后缓存到内存
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }

    //官方说的使用索引类的优势：
    //使用订阅者索引可以避免在运行时使用反射对订阅者方法进行昂贵的查找。相反，EventBus 注释处理器在构建时查找它们。
    //建议Android应用程序在生产环境使用索引类方式。它更快并避免由于反射而导致的崩溃。

    //todo EventBus 索引类并不是避免反射而只是避免反射过程中对所有方法的查找，因为索引类中存放的一定是有效的订阅者方法，
    // 而反射的方式是反射订阅者类的所有方法再过滤订阅者方法，甚至还会反射整个派生系中的方法再过滤。
    // 所以索引类的优势只有在订阅者类中非订阅者方法比较多时才会发挥他的优势，比如 Activity类：继承层次多，方法多，这种情况采用索引类的方式会比较快,
    // 如果一个订阅者类中只有订阅者方法，那两种方式的查找效率是一样的。

    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            //通过订阅者类从apt生成的索引类中查找订阅者方法信息:subscriberInfo
            findState.subscriberInfo = getSubscriberInfo(findState);
            if (findState.subscriberInfo != null) {
                //通过反射获取订阅者的 Method 对象并封装 SubscriberMethod 数组
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                for (SubscriberMethod subscriberMethod : array) {
                    // 检查重名方法（本类或父类之间都可能重复
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
                //apt 没有正常收集该类，降级为反射方式查找
                findUsingReflectionInSingleClass(findState);
            }
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        findState.recycle(); //findState 回收数据
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                if (FIND_STATE_POOL[i] == null) {
                    //将当前用过的 findState 缓存回去，下次注册时不用 new
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        return subscriberMethods;
    }

    //先从对象池中随便找一个，没有才创建
    private FindState prepareFindState() {
        //用的时候隔离开，用完了放回去。
        synchronized (FIND_STATE_POOL) { //FindState[] FIND_STATE_POOL
            for (int i = 0; i < POOL_SIZE; i++) { //POOL_SIZE = 4
                FindState state = FIND_STATE_POOL[i];
                if (state != null) {
                    FIND_STATE_POOL[i] = null; //在原来的位置用null占位
                    return state; // 遍历FindState对象池，只要找到一个空对象就返回，
                }
            }
        }
        return new FindState();
    }

    //
    private SubscriberInfo getSubscriberInfo(FindState findState) {

        //findState 中是否缓存
        if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
            SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
            if (findState.clazz == superclassInfo.getSubscriberClass()) {
                return superclassInfo;
            }
        }

        //apt 收集的索引类
        if (subscriberInfoIndexes != null) {
            //通过订阅者类从索引类中查找订阅者方法信息
            for (SubscriberInfoIndex index : subscriberInfoIndexes) {
                SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    //反射查找
    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        //准备一个 FindState 实例，如果对象池中没有就new
        //FindState:一个FindState对应一个订阅者类，用于表示
        FindState findState = prepareFindState();
        //存入订阅者 class
        findState.initForSubscriber(subscriberClass);
        //从当前类到它的父类一直向上查找
        //遍历subscriberClass的超类体系，调用findUsingReflectionInSingleClass查找当前clazz的所有订阅函数
        while (findState.clazz != null) {
            //在订阅者类中通过反射的方式查找订阅者方法
            findUsingReflectionInSingleClass(findState);
            findState.moveToSuperclass();//获取父类继续查找
        }
        //循环结束 findState.subscriberMethods 中保存了这个类中的所有订阅者方法
        return getMethodsAndRelease(findState);
    }

    //在订阅者类中通过反射的方式查找订阅者方法
    private void findUsingReflectionInSingleClass(FindState findState) {
       //为啥不直接用Class.getMethods直接获取该类的全部方法呢？
        // 作者用getDeclaredMethods其实是做过深层次考虑的，如果这个类比较庞大，
        // 用getMethods查找所有的方法就显得很笨重了，
        // 如果使用的是getDeclaredMethods（该类声明的方法不包括从父类那里继承来的public方法），
        // 速度就会快一些，因为找的方法变少了，没有什么 equals,toString,hashCode等Object类的方法。

        Method[] methods;
        //getDeclaredMethods 在某些设备上也会出现 NoClassDefFoundError
        try {
            //getDeclaredMethods: 该对象反映了此Class对象表示的类或接口的所有声明方法，
            // 包括公共、受保护、默认（包）访问和私有方法，但不包括继承的方法

            //getDeclaredMethods 要比 getMethods 快，尤其是当订阅者是像 Activities 这样的胖类时
            //Class#getMethods()，不检查方法签名（对于诸如不存在的参数类型之类的东西）。这已更改为 use Class#getDeclaredMethods()，它会检查并在出现问题时抛出异常。
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            //getMethods 在某些设备上也会出现 NoClassDefFoundError，可能会在 getMethods 周围添加 catch
            try {
                //这些对象反映了此Class对象表示的类或接口的所有公共方法，
                // 包括由类或接口声明的方法以及从超类和超接口继承的方法。
                // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
                methods = findState.clazz.getMethods();
            } catch (LinkageError error) { // super class of NoClassDefFoundError to be a bit more broad...
                String msg = "Could not inspect methods of " + findState.clazz.getName();
                if (ignoreGeneratedIndex) {
                    msg += ". Please consider using EventBus annotation processor to avoid reflection.";
                } else {
                    msg += ". Please make this class visible to EventBus annotation processor to avoid reflection.";
                }
                throw new EventBusException(msg, error);
            }
            //clazz.getDeclaredMethods()只返回当前clazz中声明的函数，
            // 而clazz.getMethods()将返回clazz的所有函数(包括继承自父类和接口的函数)，
            // 因此，此时skipSuperClasses被置为true，阻止递归查找父类。
            findState.skipSuperClasses = true;
        }

        //遍历所有方法
        for (Method method : methods) {
            int modifiers = method.getModifiers(); //修饰符
            //MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
            //校验订阅者方法：must be public, non-static, and non-abstract"
            //
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                Class<?>[] parameterTypes = method.getParameterTypes();//参数类型
                if (parameterTypes.length == 1) {//正好一个参数
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    if (subscribeAnnotation != null) {
                        // 恰好一个参数的非静态的公开类并且有Subscribe注解标记
                        Class<?> eventType = parameterTypes[0];// 参数为事件类
                        //todo 检查重名方法（本类或父类之间都可能重复）并添加 用于控制findState.subscriberMethods是否添加找到的method
                        //如果不校验，如果子类重写订阅者方法会导致执行两次子类的订阅者方法
                        if (findState.checkAdd(method, eventType)) { // 没有添加过的
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            //收集订阅者方法，封装 SubscriberMethod
                            findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                    subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }
                } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }
        }
    }

    static void clearCaches() {
        METHOD_CACHE.clear();
    }


    static class FindState {
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        Class<?> subscriberClass;
        Class<?> clazz;
        boolean skipSuperClasses;
        SubscriberInfo subscriberInfo;

        void initForSubscriber(Class<?> subscriberClass) {
            this.subscriberClass = clazz = subscriberClass;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        void recycle() {
            subscriberMethods.clear();
            anyMethodByEventType.clear();
            subscriberClassByMethodKey.clear();
            methodKeyBuilder.setLength(0);
            subscriberClass = null;
            clazz = null;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        // 检查是否已经添加过这个订阅者方法
        // 方法对象、参数为事件类
        boolean checkAdd(Method method, Class<?> eventType) {
            // 2 level check: 1st level with event type only (fast), 2nd level with complete signature when required.
            // Usually a subscriber doesn't have methods listening to the same event type.
            //2 级检查：仅具有事件类型的第一级（快速），在需要时具有完整签名的第二级。
            // 通常订阅者没有侦听相同事件类型的方法。
            //第一层判断有无method监听此eventType,如果没有则可直接把找到的method加到subscriberMethods中。
            //第二层检查的是从MethodSignature（方法签名）判断能否把找到的method加进去。是为了防止在找父类时覆盖了子类的方法，因为此方法是子类是重写，方法名参数名完全一样（方法签名）；另一个原因是可能是当一个类有多个方法监听同一个event(尽管一般不会这样做)，也能将这些方法加进去。
            Object existing = anyMethodByEventType.put(eventType, method);
            if (existing == null) { //没有添加过，
                //anyMethodByEventType存储<eventType, method>映射关系，
                // 若existing为空，则表示eventType第一次出现。
                // 一般情况下，一个对象只会有一个订阅函数处理特定eventType。
                return true;
            } else {//一个类有多个方法监听同一个事件类型
                if (existing instanceof Method) {
                    //处理一个对象有多个订阅函数处理eventType的情况，
                    // 此时，anyMethodByEventType中eventType被映射到一个非Method对象(即this)。
                    if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                        // Paranoia check
                        throw new IllegalStateException();
                    }
                    // Put any non-Method object to "consume" the existing Method
                    //将任何非 Method 对象“使用”现有的 Method
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        //由于存在多个订阅函数处理eventType，此时，单纯使用eventType作为key已经无法满足要求了，
        // 因此，使用method.getName() + ">" + eventType.getName()作为methodKey，
        // 并使用subscriberClassByMethodKey存储<methodKey, methodClass>的映射关系。
        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            methodKeyBuilder.setLength(0);
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());
            //onEvent>TestEvent
            String methodKey = methodKeyBuilder.toString();
            //getDeclaringClass: 返回表示类或接口的 Class 对象，该类或接口声明了由此对象表示的可执行文件。
            Class<?> methodClass = method.getDeclaringClass();
            //map["onEvent>TestEvent"]=
            //如果methodClassOld或者methodClass是methodClassOld的子类，
            // 则将<methodKey, methodClass>放入，否则不放入。
            // 满足函数名相同、参数类型相同且被@Subscribe修饰的函数，
            // 在一个类中不可能存在两个；考虑类继承体系，若这样的两个函数分别来自父类和子类，
            // 则最终被加入的是子类的函数。
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
            //确定此Class对象表示的类或接口是否与指定的Class参数表示的类或接口相同，或者是其超类或超接口。
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                // Only add if not already found in a sub class
                //仅在子类中未找到时才添加
                return true;
            } else {
                // Revert the put, old class is further down the class hierarchy
                //还原放置，旧类在类层次结构中更靠后
                subscriberClassByMethodKey.put(methodKey, methodClassOld);
                return false;
            }
        }

        void moveToSuperclass() {
            if (skipSuperClasses) { //反射方法时是通过getMethod 方式，已经包含父类方法了
                clazz = null;
            } else {
                clazz = clazz.getSuperclass();
                String clazzName = clazz.getName();
                // Skip system classes, this degrades performance.
                // Also we might avoid some ClassNotFoundException (see FAQ for background).
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") ||
                        clazzName.startsWith("android.") || clazzName.startsWith("androidx.")) {
                    clazz = null;
                }
            }
        }
    }

}
