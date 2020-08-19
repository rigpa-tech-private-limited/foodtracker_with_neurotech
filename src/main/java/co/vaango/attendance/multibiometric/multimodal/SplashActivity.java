package co.vaango.attendance.multibiometric.multimodal;

import co.vaango.attendance.R;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import co.vaango.attendance.multibiometric.utils.BaseActivity;
import co.vaango.attendance.multibiometric.utils.AndroidUtils;
import co.vaango.attendance.multibiometric.utils.AppConstants;

public class SplashActivity extends BaseActivity {

    private boolean killed;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);
        if (AndroidUtils.isApiLevelFrom(21)) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(AndroidUtils.getColor(getResources(), R.color.app_bg));
        }
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!killed){
                    Log.d("facecheck","splashscreen");
                    String token = AndroidUtils.readPreference(SplashActivity.this, AppConstants.token, "");
                    Log.d("facecheck","token = "+token);
                    if(token.isEmpty()){
                        Intent intent = new Intent(SplashActivity.this, DeviceOTPActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(SplashActivity.this, MultiModalActivity.class);
                        intent.putExtra("syncing",true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        killed = true;
    }

}
