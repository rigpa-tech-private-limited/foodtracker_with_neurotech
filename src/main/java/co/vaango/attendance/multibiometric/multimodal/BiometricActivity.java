package co.vaango.attendance.multibiometric.multimodal;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.neurotec.biometrics.NBiometric;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLRecord;
import com.neurotec.biometrics.NLTemplate;
import com.neurotec.biometrics.NMatchingResult;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplate;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.client.NClusterBiometricConnection;
import com.neurotec.images.NImage;
import com.neurotec.io.NBuffer;
import com.neurotec.lang.NCore;
import com.neurotec.licensing.gui.ActivationActivity;

import co.vaango.attendance.app.BaseActivity;
import co.vaango.attendance.app.DirectoryViewer;
import co.vaango.attendance.app.InfoActivity;
import co.vaango.attendance.licensing.LicensingManager.LicensingStateCallback;
import co.vaango.attendance.licensing.LicensingState;
import co.vaango.attendance.multibiometric.Database.DatabaseQueryClass;
import co.vaango.attendance.multibiometric.Features.CreateStudent.Student;
import co.vaango.attendance.multibiometric.Model;
import co.vaango.attendance.R;
import co.vaango.attendance.multibiometric.modals.User;
import co.vaango.attendance.multibiometric.modals.Visit;
import co.vaango.attendance.multibiometric.preferences.MultimodalPreferences;
import co.vaango.attendance.multibiometric.utils.AndroidUtils;
import co.vaango.attendance.multibiometric.utils.AppConstants;
import co.vaango.attendance.multibiometric.utils.HttpRequester;
import co.vaango.attendance.multibiometric.utils.PopUpHelper;
import co.vaango.attendance.multibiometric.view.EnrollmentDialogFragment;
import co.vaango.attendance.multibiometric.view.SubjectListFragment;

import com.neurotec.util.concurrent.CompletionHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.neurotec.lang.NCore.getContext;

public abstract class BiometricActivity extends BaseActivity implements EnrollmentDialogFragment.EnrollmentDialogListener, SubjectListFragment.SubjectSelectionListener, LicensingStateCallback {

    // ===========================================================
    // Private static fields
    // ===========================================================

    private static final int REQUEST_CODE_GET_FILE = 1;


    private static final String EXTRA_REQUEST_CODE = "request_code";
    private static final int VERIFICATION_REQUEST_CODE = 1;
    private static final int DATABASE_REQUEST_CODE = 2;

    protected static final String RECORD_REQUEST_FACE = "face";
    protected static final String RECORD_REQUEST_FINGER = "finger";
    protected static final String RECORD_REQUEST_IRIS = "iris";
    protected static final String RECORD_REQUEST_VOICE = "voice";

    private static final String TAG = "facecheck";

    private boolean checkout;

    private  String timeStr = "";

    private  String foodStatus = "";

    private String userName = "";

    private String userID = "";

    private String timeStamp = "";

    private String dateStamp = "";

    protected boolean isDetectStarted = false;

    // ===========================================================
    // Private fields
    // ===========================================================

    private CompletionHandler<NSubject[], ? super NBiometricOperation> subjectListHandler = new CompletionHandler<NSubject[], NBiometricOperation>() {

        @Override
        public void completed(NSubject[] result, NBiometricOperation attachment) {
            Model.getInstance().setSubjects(result);
        }

        @Override
        public void failed(Throwable exc, NBiometricOperation attachment) {
            Log.e(TAG, exc.toString(), exc);
        }

    };

    public String resizeBase64Image(String base64image) {
        byte[] encodeByte = Base64.decode(base64image.getBytes(), Base64.DEFAULT);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPurgeable = true;
        Bitmap image = BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length, options);


        if (image.getHeight() <= 400 && image.getWidth() <= 400) {
            return base64image;
        }
        image = Bitmap.createScaledBitmap(image, 300, 300, false);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 50, baos);

        byte[] b = baos.toByteArray();
        System.gc();
        return Base64.encodeToString(b, Base64.NO_WRAP);

    }

    public byte[] getBytes(String base_64_img) {
        return Base64.decode(base_64_img, Base64.DEFAULT);
    }

    private CompletionHandler<NBiometricTask, NBiometricOperation> completionHandler = new CompletionHandler<NBiometricTask, NBiometricOperation>() {

        @Override
        public void completed(NBiometricTask task, NBiometricOperation operation) {
            String message = null;
            NBiometricStatus status = task.getStatus();
            Log.i(TAG, String.format("Operation: %s, Status: %s", operation, status));

            onOperationCompleted(operation, task);
            if (status == NBiometricStatus.CANCELED) return;
            isDetectStarted = false;
            if (task.getError() != null) {
                Log.i(TAG, String.format("task.getError: %s", task.getError()));
                showError(task.getError());
                onBack();
            } else {
                subject = task.getSubjects().get(0);
                switch (operation) {
                    case CAPTURE:
                    case CREATE_TEMPLATE: {
                        if (status == NBiometricStatus.OK) {
                            NSubject subject1 = task.getSubjects().get(0);
                            Log.d("facecheck", "Base64 Face..." + Base64.encodeToString(subject1.getFaces().get(0).getImage().save().toByteArray(), Base64.NO_WRAP));
                            AndroidUtils.writePreference(BiometricActivity.this, "person_image", Base64.encodeToString(subject1.getFaces().get(0).getImage().save().toByteArray(), Base64.NO_WRAP));
                            Log.d("facecheck", "CREATE_TEMPLATE");

                            NBiometricTask task1 = Model.getInstance().getClient().createTask(EnumSet.of(NBiometricOperation.IDENTIFY), subject1);
                            Model.getInstance().getClient().performTask(task1, NBiometricOperation.IDENTIFY, completionHandler);
                            Log.d("facecheck", "identify task performed");

                        } else if (task.getSubjects().size() > 0 && task.getSubjects().get(0).getFaces().size() > 0 && task.getStatus() == NBiometricStatus.TIMEOUT) {
                            //message = getString(R.string.msg_extraction_failed, getString(R.string.msg_liveness_check_failed));
                            Log.i(TAG, String.format("msg_liveness_check_failed Operation: %s, Status: %s", operation, status));
                            onBack();
                        } else {
                            //message = getString(R.string.msg_extraction_failed, status.toString());
                            Log.i(TAG, String.format("msg_extraction_failed Operation: %s, Status: %s", operation, status));
                            onBack();
                        }
                    }
                    break;
                    case ENROLL:
                    case ENROLL_WITH_DUPLICATE_CHECK: {
                        if (status == NBiometricStatus.OK) {
                            message = getString(R.string.msg_enrollment_succeeded);
                        } else {
                            message = getString(R.string.msg_enrollment_failed, status.toString());
                        }
                        client.list(NBiometricOperation.LIST, subjectListHandler);
                        showMsg(message);
                    }
                    break;
                    case VERIFY: {
                        if (status == NBiometricStatus.OK) {
                            message = getString(R.string.msg_verification_succeeded);
                        } else {
                            message = getString(R.string.msg_verification_failed, status.toString());
                        }
                        showMsg(message);
                    }
                    break;
                    case IDENTIFY: {
                        boolean userexist;
                        if (status == NBiometricStatus.OK) {
                            userexist = true;
                            if(AppConstants.ATTENDANCE_FLAG) {
                                StringBuilder sb = new StringBuilder();
                                NSubject subject = task.getSubjects().get(0);
                                int face_id = -1;
                                for (NMatchingResult result : subject.getMatchingResults()) {
                                    Log.d("facecheck","NMatchingResult Face ID => "+result.getId());
                                    face_id = Integer.parseInt(result.getId());
                                }

                                DatabaseQueryClass databaseQueryClass = new DatabaseQueryClass(getContext());
                                User user = databaseQueryClass.getUserByFaceId(face_id);
                                Visit visitData = databaseQueryClass.getVisitByFaceId(face_id);
                                if (user != null) {
                                    userName = user.getUser_name();
                                    userID = String.valueOf(user.getFace_id());
                                    dateStamp = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                                    timeStamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                    if (visitData != null && visitData.getId() > 0 && visitData.getOut_time().equals("")) {
                                        Log.d("facecheck", visitData.toString());
                                        Visit visit = new Visit(-1, user.getUser_name(), user.getFace_id(), visitData.getIn_time(), timeStamp, 0);
                                        long id = databaseQueryClass.updateVisitInfo(visit);
                                        if (id > 0) {
                                            Log.d("facecheck", "user visit Details updated" + " visit id " + id + " ... " + user.getUser_name() + " - " + user.getFace_id() + " - " + timeStamp);
                                        }
                                        timeStr = "CHECKED OUT";
                                        checkout = true;

                                    } else {
                                        Visit visit = new Visit(-1, user.getUser_name(), user.getFace_id(), timeStamp, "", 0);
                                        Log.d("facecheck", visit.toString());
                                        long id = databaseQueryClass.insertVisit(visit);

                                        if (id > 0) {
                                            visit.setId(id);
                                            Log.d("facecheck", "user visit Details inserted" + " visit id " + id + " ... " + user.getUser_name() + " - " + user.getFace_id() + " - " + timeStamp);
                                        }
                                        timeStr = "CHECKED IN";
                                        checkout = false;
                                    }
                                    sb.append(getString(R.string.msg_identify_results, user.getUser_name(), String.valueOf(face_id), timeStr)).append('\n');
                                    message = sb.toString();
                                }
                                try {
                                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                                    Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
                                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                                    float batteryPct = (level * 100) / (float)scale;
                                    Map<String, String> parameters = new HashMap<String, String>();
                                    String token = AndroidUtils.readPreference(BiometricActivity.this, AppConstants.token, "");
                                    Log.d("facecheck","token = "+token);
                                    parameters.put("token", token);
                                    parameters.put("id", userID);
                                    parameters.put("time", dateStamp+" "+timeStamp);
                                    parameters.put("battery_status", String.valueOf(batteryPct));
                                    final JSONObject result = HttpRequester.IHttpPostRequest(BiometricActivity.this, AppConstants.ATTENDANCE, parameters, null, true, null);
                                    Log.d("facecheck", String.valueOf(result));
                                    if (result.getString("status").equals("success")) {
                                        Log.d("facecheck", result.getString("status"));
                                    }
                                    Intent i = new Intent(BiometricActivity.this, AttendanceViewActivity.class);
                                    i.putExtra("user_name", userName);
                                    i.putExtra("footer_text", timeStr);
                                    i.putExtra("check_out", checkout);
                                    i.putExtra("user_exist", userexist);
                                    startActivity(i);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            } else {

                                StringBuilder sb = new StringBuilder();
                                NSubject subject = task.getSubjects().get(0);
                                int face_id = -1;
                                for (NMatchingResult result : subject.getMatchingResults()) {
                                    Log.d("facecheck","Face Subject ID => "+result.getId());
                                    face_id = Integer.parseInt(result.getId());
                                }

                                DatabaseQueryClass databaseQueryClass = new DatabaseQueryClass(getContext());
                                User user = databaseQueryClass.getUserByFaceId(face_id);
                                Visit visitData = databaseQueryClass.getVisitByFaceId(face_id);
                                if (user != null) {
                                    userName = user.getUser_name();
                                    userID = String.valueOf(user.getFace_id());
                                    dateStamp = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                                    timeStamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                        Visit visit = new Visit(-1, user.getUser_name(), user.getFace_id(), timeStamp, "", 0);
                                        Log.d("facecheck", visit.toString());
                                        long id = databaseQueryClass.insertVisit(visit);

                                        if (id > 0) {
                                            visit.setId(id);
                                            Log.d("facecheck", "plate count Details inserted" + " visit id " + id + " ... " + user.getUser_name() + " - " + user.getFace_id() + " - " + timeStamp);
                                        }

                                    try {
                                        String string1 = "06:00:00";
                                        Date time1 = new SimpleDateFormat("HH:mm:ss").parse(string1);
                                        Calendar calendar1 = Calendar.getInstance();
                                        calendar1.setTime(time1);
                                        calendar1.add(Calendar.DATE, 1);


                                        String string2 = "11:59:00";
                                        Date time2 = new SimpleDateFormat("HH:mm:ss").parse(string2);
                                        Calendar calendar2 = Calendar.getInstance();
                                        calendar2.setTime(time2);
                                        calendar2.add(Calendar.DATE, 1);

                                        String string3 = "12:00:00";
                                        Date time3 = new SimpleDateFormat("HH:mm:ss").parse(string3);
                                        Calendar calendar3 = Calendar.getInstance();
                                        calendar3.setTime(time1);
                                        calendar3.add(Calendar.DATE, 1);


                                        String string4 = "18:59:00";
                                        Date time4 = new SimpleDateFormat("HH:mm:ss").parse(string4);
                                        Calendar calendar4 = Calendar.getInstance();
                                        calendar4.setTime(time4);
                                        calendar4.add(Calendar.DATE, 1);

                                        String string5 = "19:00:00";
                                        Date time5 = new SimpleDateFormat("HH:mm:ss").parse(string5);
                                        Calendar calendar5 = Calendar.getInstance();
                                        calendar5.setTime(time5);
                                        calendar5.add(Calendar.DATE, 1);


                                        String string6 = "23:59:00";
                                        Date time6 = new SimpleDateFormat("HH:mm:ss").parse(string6);
                                        Calendar calendar6 = Calendar.getInstance();
                                        calendar6.setTime(time6);
                                        calendar6.add(Calendar.DATE, 1);

                                        String someRandomTime = timeStamp;
                                        Date d = new SimpleDateFormat("HH:mm:ss").parse(someRandomTime);
                                        Calendar calendarX = Calendar.getInstance();
                                        calendarX.setTime(d);
                                        calendarX.add(Calendar.DATE, 1);

                                        Date x = calendarX.getTime();
                                        if (x.after(calendar1.getTime()) && x.before(calendar2.getTime())) {
                                            //checkes whether the current time is between 14:49:00 and 20:11:13.
                                            System.out.println(true);
                                            foodStatus = "breakfast";
                                            timeStr = "Good Morning. \nHave a good breakfast";
                                        } else if (x.after(calendar3.getTime()) && x.before(calendar4.getTime())) {
                                            //checkes whether the current time is between 14:49:00 and 20:11:13.
                                            System.out.println(true);
                                            foodStatus = "lunch";
                                            timeStr = "Good Noon. \nHave a good lunch";
                                        } else if (x.after(calendar5.getTime()) && x.before(calendar6.getTime())) {
                                            //checkes whether the current time is between 14:49:00 and 20:11:13.
                                            System.out.println(true);
                                            foodStatus = "dinner";
                                            timeStr = "Good Night. \nHave a good dinner";
                                        }
                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                    sb.append(getString(R.string.msg_identify_results, user.getUser_name(), String.valueOf(face_id), timeStr)).append('\n');
                                    message = sb.toString();
                                }

                                try {
                                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                                    Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
                                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                                    float batteryPct = (level * 100) / (float)scale;
                                    Map<String, String> parameters = new HashMap<String, String>();
                                    String token = AndroidUtils.readPreference(BiometricActivity.this, AppConstants.token, "");
                                    Log.d("facecheck","token = "+token);
                                    parameters.put("token", token);
                                    parameters.put("id", userID);
                                    parameters.put("time", dateStamp+" "+timeStamp);
                                    parameters.put("battery_status", String.valueOf(batteryPct));
                                    final JSONObject result = HttpRequester.IHttpPostRequest(BiometricActivity.this, AppConstants.PLATE_COUNT, parameters, null, true, null);
                                    Log.d("facecheck", String.valueOf(result));
                                    if (result.getString("status").equals("success")) {
                                        Log.d("facecheck", result.getString("status"));
                                    }
                                    Intent i = new Intent(BiometricActivity.this, FoodCountActivity.class);
                                    i.putExtra("user_name", userName);
                                    i.putExtra("footer_text", timeStr);
                                    i.putExtra("foodstatus", foodStatus);
                                    i.putExtra("user_exist", userexist);
                                    startActivity(i);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            userexist = false;
                            if(AppConstants.ATTENDANCE_FLAG) {
                                Intent i = new Intent(BiometricActivity.this, AttendanceViewActivity.class);
                                i.putExtra("user_name", userName);
                                i.putExtra("footer_text", timeStr);
                                i.putExtra("check_out", checkout);
                                i.putExtra("user_exist", userexist);
                                startActivity(i);
                            } else {
                                Intent i = new Intent(BiometricActivity.this, FoodCountActivity.class);
                                i.putExtra("user_name", userName);
                                i.putExtra("footer_text", timeStr);
                                i.putExtra("foodstatus", foodStatus);
                                i.putExtra("user_exist", userexist);
                                startActivity(i);
                            }
                        }

                    }
                    break;
                    default: {
                        throw new AssertionError("Invalid NBiometricOperation");
                    }
                }

            }
        }

        public void showMsg(String message){
            if (message != null) {

                showToast(message);
                Log.i(TAG, "show Message=>" + message);
                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                onBack();
                            }
                        }, 1000);
            }
        }

        @Override
        public void failed(Throwable th, NBiometricOperation operation) {
            onOperationCompleted(operation, null);
            Log.i(TAG, "NBiometricOperation faild=>" + th);
            //showError(th);
        }
    };

    private LinearLayout captureControls;
    private LinearLayout stopControls;
    private LinearLayout successControls;
    private LinearLayout actionControls;

    protected boolean mAppClosing = false;
    protected boolean mAppIsGoingToBackground = false;

    // ===========================================================
    // Protected fields
    // ===========================================================

    protected NBiometricClient client = null;
    protected NClusterBiometricConnection mConnection = null;
    protected NSubject subject = null;
    protected final PropertyChangeListener biometricPropertyChanged = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("Status".equals(evt.getPropertyName())) {
                onStatusChanged(((NBiometric) evt.getSource()).getStatus());
            }
        }
    };

    // ===========================================================
    // Protected abstract methods
    // ===========================================================

    protected abstract Class<?> getPreferences();

    protected abstract void updatePreferences(NBiometricClient client);

    protected abstract boolean isCheckForDuplicates();

    protected abstract List<String> getAdditionalComponents();

    protected abstract List<String> getMandatoryComponents();

    protected abstract String getModalityAssetDirectory();

    // ===========================================================
    // Protected methods
    // ===========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("facecheck", "BiometricActivity onCreate");
        super.onCreate(savedInstanceState);
        NCore.setContext(this);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setContentView(R.layout.multimodal_main_biometric);
            captureControls = (LinearLayout) findViewById(R.id.multimodal_capture_controls);
            successControls = (LinearLayout) findViewById(R.id.multimodal_success_controls);
            stopControls = (LinearLayout) findViewById(R.id.multimodal_stop_controls);
            actionControls = (LinearLayout) findViewById(R.id.multimodal_action_controls);
            new InitializationTask().execute(savedInstanceState == null);
        } catch (Exception e) {
            showError(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_GET_FILE) {
            if (resultCode == RESULT_OK) {
                try {
                    onFileSelected(data.getData());
                } catch (Throwable th) {
                    showError(th);
                }
            }
        }
    }

    protected void onStartCapturing() {
    }

    protected void onStopCapturing() {
        cancel();
    }

    protected void onOperationStarted(NBiometricOperation operation) {
        if (operation == NBiometricOperation.CAPTURE) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isStopSupported()) {
                        captureControls.setVisibility(View.GONE);
                        stopControls.setVisibility(View.VISIBLE);
                        successControls.setVisibility(View.GONE);
                        actionControls.setVisibility(View.GONE);
                    }
                }
            });
        } else {
            if (isActive()) {
                showProgress(R.string.msg_processing);
            }
        }
    }

    protected void onOperationCompleted(final NBiometricOperation operation, final NBiometricTask task) {
        hideProgress();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (task != null && (task.getStatus() == NBiometricStatus.OK || task.getOperations().contains(NBiometricOperation.IDENTIFY) || task.getOperations().contains(NBiometricOperation.VERIFY)
                        || task.getOperations().contains(NBiometricOperation.ENROLL_WITH_DUPLICATE_CHECK)
                        || task.getOperations().contains(NBiometricOperation.ENROLL))) {
                    captureControls.setVisibility(View.GONE);
                    stopControls.setVisibility(View.GONE);
                    successControls.setVisibility(View.GONE);
                    actionControls.setVisibility(View.VISIBLE);
                } else {
                    stopControls.setVisibility(View.GONE);
                    successControls.setVisibility(View.GONE);
                    successControls.setVisibility(View.GONE);
                    actionControls.setVisibility(View.GONE);
                }
            }
        });
    }

    protected void onLicensesObtained() {
    }

    protected void onFileSelected(Uri uri) throws Exception {
    }

    ;

    protected final boolean isActive() {
        return client.getCurrentBiometric() != null || client.getCurrentSubject() != null;
    }

    protected boolean isStopSupported() {
        return true;
    }

    protected void stop() {
        client.force();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAppIsGoingToBackground = false;
    }

    protected void cancel() {
        if (client != null) {
            client.cancel();
        }
    }

    protected void onLoad() {
        cancel();
        hideProgress();
        Intent intent = new Intent(this, DirectoryViewer.class);
        intent.putExtra(DirectoryViewer.ASSET_DIRECTORY_LOCATION, getModalityAssetDirectory());
        startActivityForResult(intent, REQUEST_CODE_GET_FILE);
    }

    protected void onBack() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopControls.setVisibility(View.GONE);
                successControls.setVisibility(View.GONE);
                successControls.setVisibility(View.GONE);
                actionControls.setVisibility(View.GONE);
            }
        });
    }

    protected void onEnroll() {
        new EnrollmentDialogFragment().show(getFragmentManager(), "enrollment");
    }

    protected void onIdentify() {
        if (subject == null) throw new NullPointerException("subject");
        NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.IDENTIFY), subject);
        client.performTask(task, NBiometricOperation.IDENTIFY, completionHandler);
        onOperationStarted(NBiometricOperation.IDENTIFY);
    }

    protected void onVerify() {
        Bundle bundle = new Bundle();
        bundle.putInt(EXTRA_REQUEST_CODE, VERIFICATION_REQUEST_CODE);
        SubjectListFragment.newInstance(Model.getInstance().getSubjects(), true, bundle).show(getFragmentManager(), "verification");
    }

    protected void onStatusChanged(final NBiometricStatus status) {
    }

    // ===========================================================
    // Public methods
    // ===========================================================

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mAppClosing = true;
    }

    @Override
    protected void onStop() {
        mAppIsGoingToBackground = true;
        cancel();
        if (mAppClosing) {
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_preferences: {
                startActivity(new Intent(this, getPreferences()));
                break;
            }
            case R.id.action_database: {
                Bundle bundle = new Bundle();
                bundle.putInt(EXTRA_REQUEST_CODE, DATABASE_REQUEST_CODE);
                SubjectListFragment.newInstance(Model.getInstance().getSubjects(), false, bundle).show(getFragmentManager(), "database");
                break;
            }
            case R.id.action_activation: {
                Intent activation = new Intent(this, ActivationActivity.class);
                Bundle params = new Bundle();
                params.putStringArrayList(ActivationActivity.LICENSES, new ArrayList<String>(MultiModalActivity.getAllComponentsInternal()));
                activation.putExtras(params);
                startActivity(activation);
                break;
            }
            case R.id.action_about: {
                startActivity(new Intent(this, InfoActivity.class));
                break;
            }
        }
        return true;
    }

    @Override
    public void onEnrollmentIDProvided(String id) {
        subject.setId(id);
        updatePreferences(client);
        NBiometricOperation operation = isCheckForDuplicates() ? NBiometricOperation.ENROLL_WITH_DUPLICATE_CHECK : NBiometricOperation.ENROLL;
        NBiometricTask task = client.createTask(EnumSet.of(operation), subject);
        client.performTask(task, NBiometricOperation.ENROLL, completionHandler);
        onOperationStarted(NBiometricOperation.ENROLL);
    }

    @Override
    public void onSubjectSelected(NSubject otherSubject, Bundle bundle) {
        if (bundle.getInt(EXTRA_REQUEST_CODE) == VERIFICATION_REQUEST_CODE) {
            subject.setId(otherSubject.getId());
            updatePreferences(client);
            NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.VERIFY), subject);
            client.performTask(task, NBiometricOperation.VERIFY, completionHandler);
            onOperationStarted(NBiometricOperation.VERIFY);
        }
    }

    @Override
    public void onLicensingStateChanged(LicensingState state) {
        switch (state) {
            case OBTAINING:
                showProgress(R.string.msg_obtaining_licenses);
                break;
            case OBTAINED:
                hideProgress();
                showToast(R.string.msg_licenses_obtained);
                break;
            case NOT_OBTAINED:
                hideProgress();
                showToast(R.string.msg_licenses_not_obtained);
                break;
        }
    }

    public void capture(NSubject subject, EnumSet<NBiometricOperation> additionalOperations) {
        Log.d("facecheck", "Face startCapturing capture subject..." + subject);

        if (subject == null) throw new NullPointerException("subject");
        this.subject = subject;
        updatePreferences(client);

        EnumSet<NBiometricOperation> operations = EnumSet.of(NBiometricOperation.CREATE_TEMPLATE);
        if (additionalOperations != null) {
            operations.addAll(additionalOperations);
        }
        NBiometricTask task = client.createTask(operations, subject);
        isDetectStarted = true;
        client.performTask(task, NBiometricOperation.CREATE_TEMPLATE, completionHandler);
        onOperationStarted(NBiometricOperation.CAPTURE);
    }


    public void createTemplateFromBase64() {
        String base_64_img = "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";

        byte[] data = getBytes(base_64_img);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        NImage image1 = NImage.fromMemory(buffer);

        NSubject subject1 = new NSubject();
        NFace face1 = new NFace();
        face1.setImage(image1);
        subject1.getFaces().add(face1);

        Log.d("facecheck", "createTemplateFromBase64..." + subject1);
        updatePreferences(client);
        EnumSet<NBiometricOperation> operations = EnumSet.of(NBiometricOperation.CREATE_TEMPLATE);
        NBiometricTask task = client.createTask(operations, subject1);
        client.performTask(task, NBiometricOperation.CREATE_TEMPLATE, completionHandler);
        onOperationStarted(NBiometricOperation.CAPTURE);
    }

    public void extract(NBiometric biometric) {
        if (biometric == null) throw new NullPointerException("biometric");
        subject.clear();
        updatePreferences(client);
        NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);
        task.setBiometric(biometric);
        client.performTask(task, NBiometricOperation.CREATE_TEMPLATE, completionHandler);
//		client.createTemplate(subject, NBiometricOperation.CREATE_TEMPLATE, completionHandler);
        onOperationStarted(NBiometricOperation.CREATE_TEMPLATE);
    }

    public void extract(NSubject subject) {
        if (subject == null) throw new NullPointerException("subject");
        this.subject = subject;
        updatePreferences(client);
        NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);
        client.performTask(task, NBiometricOperation.CREATE_TEMPLATE, completionHandler);
        onOperationStarted(NBiometricOperation.CREATE_TEMPLATE);
    }

    final class InitializationTask extends AsyncTask<Object, Boolean, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress(R.string.msg_initializing);
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            if (params.length < 1) {
                throw new IllegalArgumentException("Missing parameter if to obtain license");
            }
            // showProgress(R.string.msg_initializing_client);

            try {
                client = Model.getInstance().getClient();
                subject = Model.getInstance().getSubject();
                mAppClosing = false;
                client.list(NBiometricOperation.LIST, subjectListHandler);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            hideProgress();
            onLicensesObtained();
        }
    }
}
