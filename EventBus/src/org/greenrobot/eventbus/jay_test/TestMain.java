package org.greenrobot.eventbus.jay_test;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.jay_test.myeventbus.MyEventBus;

/**
 * @author jaydroid
 * @version 1.0
 * @date 2021/11/15
 */
class TestMain {

    public static final int STATIC = 0x00000008;

    /**
     * The {@code int} value representing the {@code final}
     * modifier.
     */
    public static final int FINAL = 0x00000010;

    /**
     * The {@code int} value representing the {@code synchronized}
     * modifier.
     */
    public static final int SYNCHRONIZED = 0x00000020;

    /**
     * The {@code int} value representing the {@code volatile}
     * modifier.
     */
    public static final int VOLATILE = 0x00000040;

    public static void main(String[] args) {

//        System.out.println("Java");
//        System.out.println("STATIC: " + Integer.toBinaryString(STATIC));
//        System.out.println("FINAL: " + Integer.toBinaryString(FINAL));
//        System.out.println("SYNCHRONIZED: " + Integer.toBinaryString(SYNCHRONIZED));
//        System.out.println("VOLATILE: " + Integer.toBinaryString(VOLATILE));
//        /*
//        0 001 000
//        0 010 000
//        0 100 000
//        1 000 000
//
//         */
//        int a = STATIC | FINAL | SYNCHRONIZED | VOLATILE;
//        System.out.println(a);
//        System.out.println(a & STATIC);

//        HashMap<String, String> hashMap = new HashMap<>(5);
//        String jay1 = hashMap.put("jay", "111");
//        String jay2 = hashMap.put("jay", "222");
//        String jay3 = hashMap.put("jay", "333");
//
//        for (Map.Entry<String, String> stringStringEntry : hashMap.entrySet()) {
//        }
//
//        System.out.println(hashMap.keySet().size());
//        System.out.println(hashMap.values().size());
//        System.out.println(hashMap.remove("jay"));
//        System.out.println(hashMap.remove("jay"));
//        System.out.println(hashMap.remove("jay"));
//        System.out.println(jay1);
//        System.out.println(jay2);
//        System.out.println(jay3);

        EventBus.builder()
//                .eventInheritance(false)
                .installDefaultEventBus();

//        EventBus.getDefault().postSticky(new EventT2());
        new SubscriberTest().register();

        EventBus.getDefault().post(new EventT2());
        EventBus.getDefault().post("bbb");
        EventBus.getDefault().post(1);
        System.out.println("aaa");

    }

}

class EventT {
}

class EventT2 extends EventT {
}


