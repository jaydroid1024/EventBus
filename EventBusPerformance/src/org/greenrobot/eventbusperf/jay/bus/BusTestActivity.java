package org.greenrobot.eventbusperf.jay.bus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbusperf.R;

/**
 * @author jaydroid
 * @version 1.0
 * @date 6/11/21
 */
public class BusTestActivity extends Activity {

    TextView textView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_test);
        textView = findViewById(R.id.tv_sub);

//        EventBus.builder().addIndex(new MyEventBusIndex()).installDefaultEventBus();

    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void sub(SubEvent<String> event) {
        String t = Thread.currentThread().getName();
        textView.append("\n " + event.msg + ", currentThread: " + t);
        Toast.makeText(this, "sub", Toast.LENGTH_SHORT).show();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void sub2(SubEvent<String> event) {
        String t = Thread.currentThread().getName();
        textView.append("\n " + event.msg + ", currentThread: " + t);
        Toast.makeText(this, "sub2", Toast.LENGTH_SHORT).show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        /* Do something */
        Toast.makeText(this, "onMessageEvent", Toast.LENGTH_SHORT).show();

    }

    public void btnSend(View view) {
//        EventBus.getDefault().postSticky(new BlankBaseFragment.SampleEvent());

//        EventBus.getDefault().postSticky(new MessageEvent());
        SubEvent subEvent = new SubEvent();
        EventBus.getDefault().postSticky(subEvent);
        finish();
    }

    public void btnGo(View view) {
        startActivity(new Intent(this, BusTest2Activity.class));
    }
}
