package org.greenrobot.eventbusperf.jay.bus;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbusperf.R;

/**
 * @author jaydroid
 * @version 1.0
 * @date 6/11/21
 */
public class BusTest2Activity extends Activity {

    TextView textView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_test);
//        EventBus.getDefault().register(this);
        textView = findViewById(R.id.tv_sub);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        EventBus.getDefault().unregister(this);
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void sub(SubEvent event) {
//        String t = Thread.currentThread().getName();
//        textView.append("\n " + event.msg + ", currentThread: " + t);
//    }


    public void btnSend(View view) {
        EventBus.getDefault().postSticky(new SubEvent<String>().msg = "123");
        Toast.makeText(this, "btnSend", Toast.LENGTH_SHORT).show();

        EventBus.getDefault().post(new MessageEvent());


    }
}
