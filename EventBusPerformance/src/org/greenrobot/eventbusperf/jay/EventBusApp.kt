package org.greenrobot.eventbusperf.jay

import android.app.Application
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbusperf.MyEventBusIndex

public class EventBusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventBus.builder()
                // have a look at the index class to see which methods are picked up
                // if not in the index @Subscribe methods will be looked up at runtime (expensive)
                .addIndex(MyEventBusIndex())
                .installDefaultEventBus()
    }
}