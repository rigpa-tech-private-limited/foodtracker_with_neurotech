package co.vaango.attendance.multibiometric.multimodal;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLAttributes;
import com.neurotec.biometrics.NLRecord;
import com.neurotec.biometrics.NLTemplate;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplate;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.images.NImage;
import com.neurotec.io.NBuffer;
import com.neurotec.io.NFile;
import com.neurotec.lang.NCore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import co.vaango.attendance.multibiometric.Database.DatabaseQueryClass;
import co.vaango.attendance.multibiometric.modals.User;
import co.vaango.attendance.multibiometric.utils.AndroidUtils;
import co.vaango.attendance.multibiometric.utils.AppConstants;
import co.vaango.attendance.multibiometric.utils.BaseActivity;
import co.vaango.attendance.licensing.LicensingManager;
import co.vaango.attendance.R;
import co.vaango.attendance.multibiometric.Model;
import co.vaango.attendance.multibiometric.utils.HttpRequester;
import co.vaango.attendance.util.NImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.neurotec.lang.NCore.getContext;

public final class MultiModalActivity extends BaseActivity implements ActivityCompat.OnRequestPermissionsResultCallback, View.OnClickListener {

    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
    private static final String WARNING_PROCEED_WITH_NOT_GRANTED_PERMISSIONS = "Do you wish to proceed without granting all permissions?";
    private static final String WARNING_NOT_ALL_GRANTED = "Some permissions are not granted.";
    private static final String MESSAGE_ALL_PERMISSIONS_GRANTED = "All permissions granted";

    private static String TAG = "facecheck";

    private final int MODALITY_CODE_FACE = 1;

    private Map<String, Integer> mPermissions = new HashMap<String, Integer>();
    private DatabaseQueryClass databaseQueryClass = new DatabaseQueryClass(this);

    private static List<String> getMandatoryComponentsInternal() {
        List<String> components = new ArrayList<String>();
        for (String component : FaceActivity.mandatoryComponents()) {
            if (!components.contains(component)) {
                components.add(component);
            }
        }
        return components;
    }

    private NBiometricClient client;

    private static List<String> getAdditionalComponentsInternal() {
        List<String> components = new ArrayList<String>();
        for (String component : FaceActivity.additionalComponents()) {
            if (!components.contains(component)) {
                components.add(component);
            }
        }
        return components;
    }

    public static List<String> getAllComponentsInternal() {
        List<String> combinedComponents = getMandatoryComponentsInternal();
        combinedComponents.addAll(getAdditionalComponentsInternal());
        return combinedComponents;
    }


    private void showDialogOK(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", okListener)
                .create()
                .show();
    }

    private String[] getNotGrantedPermissions() {
        List<String> neededPermissions = new ArrayList<String>();

        int storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int phonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
        int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int microphonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);

        if (phonePermission != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (storagePermission != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.CAMERA);
        }
        if (microphonePermission != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO);
        }

        return neededPermissions.toArray(new String[neededPermissions.size()]);
    }

    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_ID_MULTIPLE_PERMISSIONS);
    }

    private TextView welcome_text;
    private ImageView welcome_image;
    private LinearLayout welcome_screen_ll;
    private ImageView company_logo;
    private Boolean needToSync = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NCore.setContext(this);
        setContentView(R.layout.multi_modal_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        needToSync = (Boolean) getIntent().getSerializableExtra("syncing");
        ImageView imageFace = (ImageView) findViewById(R.id.face);
        imageFace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent faceActivity = new Intent(MultiModalActivity.this, FaceActivity.class);
                startActivityForResult(faceActivity, MODALITY_CODE_FACE);
            }
        });

        TextView continueBtn = (TextView) findViewById(R.id.multimodal_button_continue);
        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent faceActivity = new Intent(MultiModalActivity.this, FaceActivity.class);
                startActivityForResult(faceActivity, MODALITY_CODE_FACE);
            }
        });

        welcome_text = (TextView) findViewById(R.id.welcome_text);
        welcome_image = (ImageView) findViewById(R.id.welcome_image);
        welcome_screen_ll = (LinearLayout) findViewById(R.id.welcome_screen_ll);
        company_logo = (ImageView) findViewById(R.id.company_logo);
        welcome_screen_ll.setOnClickListener(MultiModalActivity.this);
        Log.d("facecheck", "Btn Click");
        AndroidUtils.showLoadingDialog(this);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("facecheck", "Start API Call Device Info ");
                try {
                    Map<String, String> parameters = new HashMap<String, String>();
                    String token = AndroidUtils.readPreference(MultiModalActivity.this, AppConstants.token, "");
                    Log.d("facecheck", "token = " + token);
                    parameters.put("token", token);
                    final JSONObject result = HttpRequester.IHttpPostRequest(MultiModalActivity.this, AppConstants.DEVICE_INFO, parameters, null, true, null);
                    Log.d("facecheck", String.valueOf(result));
                    if (!result.getString("status").equals("success")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AndroidUtils.dismissLoadingDialog();
                                try {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MultiModalActivity.this);
                                    builder.setMessage(result.getString("message"))
                                            .setCancelable(false)
                                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    AndroidUtils.writePreference(MultiModalActivity.this, AppConstants.token, "");
                                                    Intent intent = new Intent(MultiModalActivity.this, DeviceOTPActivity.class);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                    startActivity(intent);
                                                }
                                            });
                                    AlertDialog alert = builder.create();
                                    alert.show();
                                    //PopUpHelper.ok(BiometricActivity.this, result.getString("error"), false);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("facecheck", String.valueOf(result));
                                try {
                                    JSONObject company_settings = result.getJSONObject("company");
                                    Log.d("facecheck", "welcome_text_plain = " + company_settings.getString("welcome_text_plain"));
                                    Log.d("facecheck", "welcome_screen = " + company_settings.getString("welcome_screen"));
                                    Log.d("facecheck", "logo = " + company_settings.getString("logo"));

                                    welcome_text.setText(company_settings.getString("welcome_text_plain"));
                                    welcome_screen_ll.setBackgroundColor(Color.parseColor(company_settings.getString("welcome_color")));
//                                    new MultiModalActivity.ImageLoadTask(company_settings.getString("welcome_screen"), welcome_image).execute();
                                    new MultiModalActivity.ImageLoadTask(company_settings.getString("logo"), company_logo).execute();

                                    String[] neededPermissions = getNotGrantedPermissions();
                                    if (neededPermissions.length == 0) {
                                        new InitializationTask().execute();
                                    } else {
                                        requestPermissions(neededPermissions);
                                    }
                                    AndroidUtils.dismissLoadingDialog();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.welcome_screen_ll:
                Log.d("facecheck", "welcome_screen_ll Click");
                Intent faceActivity = new Intent(MultiModalActivity.this, FaceActivity.class);
                startActivityForResult(faceActivity, MODALITY_CODE_FACE);
                break;

        }
    }

    public class ImageLoadTask extends AsyncTask<Void, Void, Bitmap> {

        private String url;
        private ImageView imageView;

        public ImageLoadTask(String url, ImageView imageView) {
            this.url = url;
            this.imageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                URL urlConnection = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) urlConnection
                        .openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap myBitmap = BitmapFactory.decodeStream(input);
                return myBitmap;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            imageView.setImageBitmap(result);
        }

    }

    private String getByteArrayFromImageURL(String url) {

        try {
            URL imageUrl = new URL(url);
            URLConnection ucon = imageUrl.openConnection();
            InputStream is = ucon.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read = 0;
            while ((read = is.read(buffer, 0, buffer.length)) != -1) {
                baos.write(buffer, 0, read);
            }
            baos.flush();
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            Log.d("Error", e.toString());
        }
        return null;
    }


    private void extractTemplate(String base64Str, String userId, String userName) throws IOException {

        NSubject subject = null;
        NFace face = null;
        NImage image = null;
        NBiometricTask task = null;
        NBiometricStatus status = null;

        try {
            subject = new NSubject();
            face = new NFace();
            byte[] data = Base64.decode(base64Str, Base64.DEFAULT);
            if (data != null) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                image = NImage.fromMemory(buffer);

                face.setImage(image);
                // Add face image to NSubject
                subject.getFaces().add(face);
                // Create task
                task = client.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);
                // Perform task
                client.performTask(task);
                status = task.getStatus();
                if (task.getError() != null) {
                    showError(task.getError());
                    return;
                }

                if (subject.getFaces().size() > 1)
                    Log.d("facecheck", String.format("Found %d faces\n", subject.getFaces().size() - 1));

                // List attributes for all located faces
                for (NFace nface : subject.getFaces()) {
                    for (NLAttributes attribute : nface.getObjects()) {
                        Rect rect = attribute.getBoundingRect();
                        Log.d("facecheck", ("msg_face_found"));
                        Log.d("facecheck", ("format_face_rect " + " left - " + rect.left + " top - " + rect.top + " right - " + rect.right + " bottom - " + rect.bottom));

                        if ((attribute.getRightEyeCenter().confidence > 0) || (attribute.getLeftEyeCenter().confidence > 0)) {
                            Log.d("facecheck", ("msg_eyes_found"));
                            if (attribute.getRightEyeCenter().confidence > 0) {
                                Log.d("facecheck", ("format_first_eye_location_confidence " + " x - " + attribute.getRightEyeCenter().x + " y - " + attribute.getRightEyeCenter().y + " confidence - " + attribute.getRightEyeCenter().confidence));
                            }
                            if (attribute.getLeftEyeCenter().confidence > 0) {
                                Log.d("facecheck", ("format_second_eye_location_confidence " + " x - " + attribute.getLeftEyeCenter().x + " y - " + attribute.getLeftEyeCenter().y + " confidence - " + attribute.getLeftEyeCenter().confidence));
                            }
                        }
                    }
                }

                if (status == NBiometricStatus.OK) {
                    Log.d("facecheck", String.format("msg_extraction_success Operation: %s, Status: %s", "template creation", status));
                    enroll(task.getSubjects().get(0), userId, userName);
                } else {
                    Log.d("facecheck", String.format("msg_extraction_failed Operation: %s, Status: %s", "template creation", status));
                }
            }
        } finally {
            if (subject != null) subject.dispose();
            if (face != null) face.dispose();
        }
    }

    private void enroll(NSubject subject1, String userId, String userName) throws IOException {

        NSubject subject = null;
        NFace face = null;
        NImage image = null;
        NBiometricTask task = null;
        NBiometricStatus status = null;

        try {
            subject = new NSubject();
            byte[] template1 = subject1.getTemplate().getFaces().save().toByteArray();
            Log.d("facecheck", "template1 " + template1);
            NLTemplate nLTemplate = new NLTemplate(new NBuffer(template1));
            Log.d("facecheck", "nLTemplate " + nLTemplate);
            Log.d("facecheck", "nLTemplate not null " + nLTemplate);
            NTemplate template = new NTemplate();
            NLTemplate faceTemplate = new NLTemplate();
            for (NLRecord rec : nLTemplate.getRecords()) {
                Log.d("facecheck", "mFaces add rec " + rec);
                faceTemplate.getRecords().add(rec);
            }
            template.setFaces(faceTemplate);

            subject.setTemplate(template);
            int lower = 1;
            int higher = 100;

            int random = (int) (Math.random() * (higher - lower)) + lower;
            subject.setId(userId);
            Log.d("facecheck", "UserID-" + userId);

            // Create task
            task = client.createTask(EnumSet.of(NBiometricOperation.ENROLL_WITH_DUPLICATE_CHECK), subject);

            // Perform task
            client.performTask(task);
            status = task.getStatus();
            if (task.getError() != null) {
                showError(task.getError());
                return;
            }

            if (status == NBiometricStatus.OK) {
                DatabaseQueryClass databaseQueryClass = new DatabaseQueryClass(getContext());
                int faceId = Integer.parseInt(userId);


                User user = new User(-1, userName, 1, faceId, "", 0);

                long id = databaseQueryClass.insertUser(user);

                if (id > 0) {
                    user.setId(id);
                    Log.d("facecheck", String.format("Inserted User Name: %s, ID: %s", userName, id));
                }
                Log.d("facecheck", String.format("enrollment_success Operation: %s, Status: %s", "enrollment", status));
            } else {
                Log.d("facecheck", String.format("enrollment_failed Operation: %s, Status: %s", "enrollment", status));
            }

        } finally {
            if (subject != null) subject.dispose();
            if (face != null) face.dispose();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult code: " + resultCode);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case MODALITY_CODE_FACE: {
                    if (data != null) {
                        Log.d("facecheck", String.valueOf(data));
                    }
                }
                break;
                default: {
                    throw new AssertionError("Unrecognised request code");
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public static String toLowerCase(String string) {
        StringBuilder sb = new StringBuilder();
        sb.append(string.substring(0, 1).toUpperCase());
        sb.append(string.substring(1).toLowerCase());
        return sb.toString().replaceAll("_", " ");
    }

    public void onRequestPermissionsResult(int requestCode, final String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {
                // Initialize the map with permissions
                mPermissions.clear();
                // Fill with actual results from user
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++) {
                        mPermissions.put(permissions[i], grantResults[i]);
                    }
                    // Check if at least one is not granted
                    if (mPermissions.get(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                            || mPermissions.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                            || mPermissions.get(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                            || mPermissions.get(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        showDialogOK(WARNING_PROCEED_WITH_NOT_GRANTED_PERMISSIONS,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                            case DialogInterface.BUTTON_POSITIVE:
                                                Log.w(TAG, WARNING_NOT_ALL_GRANTED);
                                                for (Map.Entry<String, Integer> entry : mPermissions.entrySet()) {
                                                    if (entry.getValue() != PackageManager.PERMISSION_GRANTED) {
                                                        Log.w(TAG, entry.getKey() + ": PERMISSION_DENIED");
                                                    }
                                                }
                                                new InitializationTask().execute();
                                                break;
                                            case DialogInterface.BUTTON_NEGATIVE:
                                                requestPermissions(permissions);
                                                break;
                                            default:
                                                throw new AssertionError("Unrecognised permission dialog parameter value");
                                        }
                                    }
                                });
                    } else {
                        Log.i(TAG, MESSAGE_ALL_PERMISSIONS_GRANTED);
                        new InitializationTask().execute();
                    }
                }
            }
        }
    }

    final class InitializationTask extends AsyncTask<Object, Boolean, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress(R.string.msg_initializing);
        }

        @Override
        protected Boolean doInBackground(Object... params) {
//            showProgress(R.string.msg_obtaining_licenses);
            showProgress(R.string.msg_sync);
            try {
//                LicensingManager.getInstance().obtain(MultiModalActivity.this, getAdditionalComponentsInternal());
                if (LicensingManager.getInstance().obtain(MultiModalActivity.this, getMandatoryComponentsInternal())) {
                    showToast(R.string.msg_licenses_obtained);
                    Log.d(TAG, "msg_licenses_obtained");
                } else {
                    showToast(R.string.msg_licenses_partially_obtained);
                    Log.d(TAG, "msg_licenses_partially_obtained");
                }
            } catch (Exception e) {
                showError(e.getMessage(), false);
            }

            try {
                client = Model.getInstance().getClient();
                if (needToSync!=null && needToSync) {
                    Log.d("facecheck", "Start API Call Device Info ");
                    try {
                        Map<String, String> parameters = new HashMap<String, String>();
                        String token = AndroidUtils.readPreference(MultiModalActivity.this, AppConstants.token, "");
                        Log.d("facecheck", "token = " + token);
                        parameters.put("token", token);
                        final JSONObject result = HttpRequester.IHttpPostRequest(MultiModalActivity.this, AppConstants.ALL_VISITORS, parameters, null, true, null);
                        Log.d("facecheck", String.valueOf(result));
                        if (result.getString("status").equals("success")) {
                        /**
                         String template = "{\"header\": \"Colors\", " +
                         "\"visits\": [ " +
                         "{\"id\": \"1\", \"name\": \"kannan\", \"image\": \"https://scheck.vaango.co/1.jpeg\"}, " +
                         "{\"id\": \"2\", \"name\": \"Vinoth\", \"image\": \"https://scheck.vaango.co/6.jpeg\"}, " +
                         "{\"id\": \"3\", \"name\": \"Raj\", \"image\": \"https://scheck.vaango.co/3.jpeg\"}," +
                         "{\"id\": \"4\", \"name\": \"Bharad\", \"image\": \"https://scheck.vaango.co/4.jpeg\"}," +
                         "{\"id\": \"5\", \"name\": \"Vivek\", \"image\": \"https://scheck.vaango.co/5.jpeg\"}" +
                         " ], \"empty\": false }";

                         JSONObject jsonWithArrayInIt = new JSONObject(template); //JSONObject created for the template.
                         JSONArray visits = jsonWithArrayInIt.getJSONArray("visits"); //JSONArray of Items got from the JSONObject.
                         */
                            JSONArray visits = result.getJSONArray("members");
                            Log.d("facecheck", String.valueOf(visits));
                            Model.getInstance().getClient().clear();

                            Boolean userDeleted = databaseQueryClass.deleteAllUsers();
                            Log.d("facecheck", "users Table Deleted " + (userDeleted));
                            long usersDataCount = databaseQueryClass.getNumberOfUsers();
                            Boolean visitsDeleted = databaseQueryClass.deleteAllVisits();
                            Log.d("facecheck", "visits Table Deleted " + (visitsDeleted));
                            long visitsDataCount = databaseQueryClass.getNumberOfVisits();
                            Log.d("facecheck", "user Table Count " + (usersDataCount));
                            Log.d("facecheck", "visit Table Count " + (visitsDataCount));
                            for (int i = 0; i < visits.length(); i++) {
                                JSONObject objects = visits.getJSONObject(i);
                                String base64str = getByteArrayFromImageURL(objects.getString("image"));
                                extractTemplate(base64str, objects.getString("id"), objects.getString("name"));
                                if (i == (visits.length() - 1)) {
                                    showToast(R.string.msg_sync_completed);
                                    Log.d("facecheck", "-----ENROLLMENT COMPLETED------");
                                }
                            }
                            Log.d("facecheck", "Database elements (" + Model.getInstance().getClient().listIds().length + ")");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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
        }
    }
}
