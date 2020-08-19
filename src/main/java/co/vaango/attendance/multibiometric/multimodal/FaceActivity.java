package co.vaango.attendance.multibiometric.multimodal;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout.LayoutParams;
import android.widget.LinearLayout;

import com.neurotec.biometrics.NBiometricCaptureOption;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NERecord;
import com.neurotec.biometrics.NETemplate;
import com.neurotec.biometrics.NFRecord;
import com.neurotec.biometrics.NFTemplate;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLRecord;
import com.neurotec.biometrics.NLTemplate;
import com.neurotec.biometrics.NMatchingResult;
import com.neurotec.biometrics.NSRecord;
import com.neurotec.biometrics.NSTemplate;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplate;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.standards.BDIFStandard;
import com.neurotec.biometrics.standards.FCRFaceImage;
import com.neurotec.biometrics.standards.FCRecord;
import com.neurotec.biometrics.view.NFaceView;
import com.neurotec.devices.NCamera;
import com.neurotec.devices.NDevice;
import com.neurotec.devices.NDeviceType;
import com.neurotec.images.NImage;
import com.neurotec.io.NBuffer;
import com.neurotec.media.NMediaFormat;

import co.vaango.attendance.licensing.LicensingManager;
import co.vaango.attendance.multibiometric.Model;
import co.vaango.attendance.R;
import co.vaango.attendance.multibiometric.preferences.FacePreferences;
import co.vaango.attendance.multibiometric.view.CameraControlsView;
import co.vaango.attendance.multibiometric.view.CameraFormatFragment;
import co.vaango.attendance.util.IOUtils;
import co.vaango.attendance.util.NImageUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public final class FaceActivity extends BiometricActivity implements CameraControlsView.CameraControlsListener, CameraFormatFragment.CameraFormatSelectionListener {

    private enum Status {
        CAPTURING,
        OPENING_FILE,
        TEMPLATE_CREATED
    }

    // ===========================================================
    // Private static fields
    // ===========================================================

    private static final String TAG = "scheck";
    //	private static final String LICENSING_STATE = "licensing_state";
    private static final String MODALITY_ASSET_DIRECTORY = "faces";

    // ===========================================================
    // Private fields
    // ===========================================================

    private NFaceView mFaceView;
    private CameraControlsView controlsView;

    private boolean mLicensesObtained = false;
    private Status mStatus = Status.CAPTURING;

    // ===========================================================
    // Private methods
    // ===========================================================

    private void startCapturing() {
        Log.d("facecheck", "Face startCapturing...");
        NSubject subject = new NSubject();
        NFace face = new NFace();
        face.addPropertyChangeListener(biometricPropertyChanged);
        EnumSet<NBiometricCaptureOption> options = EnumSet.of(NBiometricCaptureOption.STREAM);
        if (FacePreferences.isShowIcaoWarnings(this) || FacePreferences.isShowIcaoTextWarnings(this)) {
            Log.d("facecheck", "Face startCapturing...isShowIcaoWarnings...isShowIcaoTextWarnings");
            options.add(NBiometricCaptureOption.STREAM);
            mFaceView.setShowIcaoArrows(FacePreferences.isShowIcaoWarnings(this));
            mFaceView.setShowIcaoTextWarnings(FacePreferences.isShowIcaoTextWarnings(this));
        }
        Log.d("facecheck", "Face startCapturing isUseLiveness..." + FacePreferences.isUseLiveness(this));
        if (!FacePreferences.isUseLiveness(this)) {
            if (!options.contains(NBiometricCaptureOption.STREAM)) {
                options.add(NBiometricCaptureOption.STREAM);
            }
            for (NDevice device : client.getDeviceManager().getDevices()) {
                Log.d("facecheck", "Face startCapturing device..." + device);
                if (device.getDeviceType().contains(NDeviceType.CAMERA)) {
                    Log.d("facecheck", "Face startCapturing device Front..." + ((NCamera) device).getDisplayName().contains("Front"));
                    if (((NCamera) device).getDisplayName().contains("Front")) {
                        Log.d("facecheck", "Face startCapturing client.getFaceCaptureDevice()..." + client.getFaceCaptureDevice());
                        if (!client.getFaceCaptureDevice().equals((NCamera) device)) {
                            client.setFaceCaptureDevice((NCamera) device);
                        }
                    }
                }
            }
        }
        face.setCaptureOptions(options);
        Log.d("facecheck", "Face startCapturing face..." + face);

        Log.d("facecheck", "Face isShowFaceRectangle..." + mFaceView.isShowFaceRectangle());
        mFaceView.setShowFaceRectangle(true);
        mFaceView.setFaceRectangleWidth(1);
        mFaceView.setShowBaseFeaturePoints(false);
        mFaceView.setShowEyes(false);
        mFaceView.setFace(face);
        subject.getFaces().add(face);
        Log.d("facecheck", "Face startCapturing subject..." + subject);
        isDetectStarted = false;
//		Log.d("facecheck","Base64 Face..."+ Base64.encodeToString(subject.getFaces().get(0).getImage().save().toByteArray(), Base64.NO_WRAP));
        capture(subject, (FacePreferences.isShowIcaoWarnings(this) || FacePreferences.isShowIcaoTextWarnings(this)) ? EnumSet.of(NBiometricOperation.ASSESS_QUALITY) : null);

    }

    private void setCameraControlsVisible(final boolean value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                controlsView.setVisibility(value ? View.VISIBLE : View.GONE);
            }
        });
    }

    private NSubject createSubjectFromImage(Uri uri) {
        NSubject subject = null;
        try {
            NImage image = NImageUtils.fromUri(this, uri);
            subject = new NSubject();
            NFace face = new NFace();
            face.setImage(image);
            subject.getFaces().add(face);
        } catch (Exception e) {
            Log.i(TAG, "Failed to load file as NImage");
        }
        return subject;
    }

    private NSubject createSubjectFromFCRecord(Uri uri) {
        NSubject subject = null;
        try {
            FCRecord fcRecord = new FCRecord(IOUtils.toByteBuffer(this, uri), BDIFStandard.ISO);
            subject = new NSubject();
            for (FCRFaceImage img : fcRecord.getFaceImages()) {
                NFace face = new NFace();
                face.setImage(img.toNImage());
                subject.getFaces().add(face);
            }
        } catch (Throwable th) {
            Log.i(TAG, "Failed to load file as FCRecord");
        }
        return subject;
    }

    private NSubject createSubjectFromFile(Uri uri) {
        NSubject subject = null;
        try {
            subject = NSubject.fromMemory(IOUtils.toByteBuffer(this, uri));
        } catch (IOException e) {
            Log.i(TAG, "Failed to load from file");
        }
        return subject;
    }

    // ===========================================================
    // Protected methods
    // ===========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("facecheck", "Face OnCreate");
        super.onCreate(savedInstanceState);
        try {
            PreferenceManager.setDefaultValues(this, R.xml.face_preferences, false);
            LinearLayout layout = (LinearLayout) findViewById(R.id.multimodal_biometric_layout);

            controlsView = new CameraControlsView(this, this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

            controlsView.setLayoutParams(params);
            //layout.addView(controlsView);

            mFaceView = new NFaceView(this);
            mFaceView.setShowAge(true);
            layout.addView(mFaceView);

//			Button retryButton = (Button) findViewById(R.id.multimodal_button_retry);
//			retryButton.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					startCapturing();
//					onBack();
//					mStatus = Status.CAPTURING;
//				}
//			});
//
//			Button backButton = (Button) findViewById(R.id.multimodal_button_back);
//			backButton.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					Intent faceActivity = new Intent(FaceActivity.this, MultiModalActivity.class);
//					startActivity(faceActivity);
//				}
//			});
//
//			Button refreshButton = (Button) findViewById(R.id.multimodal_button_refersh);
//			refreshButton.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					startCapturing();
//					onBack();
//					mStatus = Status.CAPTURING;
//				}
//			});
//
//			Button add = (Button) findViewById(R.id.multimodal_button_add);
//			add.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					Intent intent = new Intent();
//					Bundle b = new Bundle();
//					byte[] nLTemplate = subject.getTemplate().getFaces().save().toByteArray();
//					b.putByteArray(RECORD_REQUEST_FACE , Arrays.copyOf(nLTemplate, nLTemplate.length));
//					intent.putExtras(b);
//					setResult(Activity.RESULT_OK, intent);
//					finish();
//				}
//			});
//
//			Button enroll = (Button) findViewById(R.id.multimodal_button_enroll);
//			enroll.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					byte[] nLTemplateBytes = subject.getTemplate().getFaces().save().toByteArray();
//					NLTemplate nLTemplateFace = new NLTemplate(new NBuffer(nLTemplateBytes));
//					NSubject subject = new NSubject();
//					NTemplate template = new NTemplate();
//					template.setFaces(nLTemplateFace);
//					subject.setTemplate(template);
//					Long tsLong = System.currentTimeMillis()/1000;
//					String ts = tsLong.toString();
//					subject.setId("VS"+ts);
//					try {
//						Log.d(TAG, "enrollmentOnServer started Subject = "+subject);
//						enrollOnServer(subject);
//					} catch (Exception e) {
//						showError(e.getMessage());
//						Log.e(TAG, "enrollmentOnServer Exception", e);
//					}
//				}
//			});
//
//			Button identify = (Button) findViewById(R.id.multimodal_button_identify);
//			identify.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					byte[] nLTemplateBytes = subject.getTemplate().getFaces().save().toByteArray();
//					NLTemplate nLTemplateFace = new NLTemplate(new NBuffer(nLTemplateBytes));
//					NSubject subject = new NSubject();
//					NTemplate template = new NTemplate();
//					template.setFaces(nLTemplateFace);
//					subject.setTemplate(template);
//					Long tsLong = System.currentTimeMillis()/1000;
//					String ts = tsLong.toString();
//					subject.setId("VS"+ts);
//					try {
//						Log.d(TAG, "identifyOnServer started Subject = "+subject);
//						identifyOnServer(subject);
//					} catch (Exception e) {
//						showError(e.getMessage());
//						Log.e(TAG, "identifyOnServer Exception", e);
//					}
//				}
//			});
        } catch (Exception e) {
            showError(e);
        }
    }

    private void identifyOnServer(NSubject subject1) throws IOException {
        NSubject subject = null;
        NBiometricTask task = null;

        try {

            subject = subject1;
            // Create task
            task = Model.getInstance().getClient().createTask(EnumSet.of(NBiometricOperation.IDENTIFY), subject);

            // Perform task
            Model.getInstance().getClient().performTask(task);
            if (task.getError() != null) {
                showError(task.getError());
                return;
            }

            if (task.getStatus() != NBiometricStatus.OK) {
                showInfo(getString(R.string.format_identification_unsuccessful, task.getStatus().toString()));
            } else {
                for (NMatchingResult matchingResult : subject.getMatchingResults()) {
                    showInfo(getString(R.string.format_matched_with_id_and_score, matchingResult.getId(), matchingResult.getScore()));
                    matchingResult.dispose();
                }
            }

        } finally {
            if (task != null) task.dispose();
            if (subject != null) subject.dispose();
        }
    }

    private void enrollOnServer(NSubject subject1) throws IOException {
        NSubject subject = null;
        NBiometricTask task = null;
        try {
            subject = subject1;

            // Create task
            task = Model.getInstance().getClient().createTask(EnumSet.of(NBiometricOperation.ENROLL_WITH_DUPLICATE_CHECK), subject);

            // Perform task
            Model.getInstance().getClient().performTask(task);
            if (task.getError() != null) {
                Log.d("facecheck", "Face OnCreate");
                showError(task.getError());
                return;
            }

            if (task.getStatus() != NBiometricStatus.OK) {
//                showToast(R.string.format_enrollment_unsuccessful);
                showInfo(getString(R.string.format_enrollment_unsuccessful, task.getStatus().toString()));
            } else {
                showInfo(getString(R.string.format_enrollment_successful, task.getStatus().toString()));
            }
        } finally {
            if (task != null) task.dispose();
            if (subject != null) subject.dispose();
        }
    }

    @Override
    protected void onResume() {
        Log.d("facecheck", "Face onResume ==" + mLicensesObtained + "--" + mStatus);
        super.onResume();
        if (mLicensesObtained && mStatus == Status.CAPTURING) {
            Log.d("facecheck", "startCapturing called ==" + mLicensesObtained + "--" + mStatus);
            startCapturing();
        }
    }

    //TODO: Licensing state retrieving when unbound from screen orientation
//	@Override
//	protected void onRestoreInstanceState(Bundle savedInstanceState) {
//		super.onRestoreInstanceState(savedInstanceState);
//		mLicensesObtained = savedInstanceState.getBoolean(LICENSING_STATE);
//	}
//
    //TODO: Licensing state saving when unbound from screen orientation
//	@Override
//	protected void onSaveInstanceState(Bundle outState) {
//		super.onSaveInstanceState(outState);
//		outState.putBoolean(LICENSING_STATE, mLicensesObtained);
//	}

    @Override
    protected List<String> getAdditionalComponents() {
        return additionalComponents();
    }

    @Override
    protected List<String> getMandatoryComponents() {
        return mandatoryComponents();
    }

    @Override
    protected Class<?> getPreferences() {
        return FacePreferences.class;
    }

    @Override
    protected void updatePreferences(NBiometricClient client) {
        FacePreferences.updateClient(client, this);
    }

    @Override
    protected boolean isCheckForDuplicates() {
        return FacePreferences.isCheckForDuplicates(this);
    }

    @Override
    protected String getModalityAssetDirectory() {
        return MODALITY_ASSET_DIRECTORY;
    }

    @Override
    protected void onLicensesObtained() {
        Log.d("facecheck", "onLicensesObtained called ==" + mLicensesObtained + "--" + mStatus);
        mLicensesObtained = true;
        startCapturing();
    }

    protected void onStartCapturing() {
        stop();
    }

    @Override
    protected void onFileSelected(Uri uri) throws Exception {
        mStatus = Status.OPENING_FILE;

        NSubject subject = createSubjectFromImage(uri);

        if (subject == null) {
            subject = createSubjectFromFCRecord(uri);
        }

        if (subject == null) {
            subject = createSubjectFromFile(uri);
        }

        if (subject != null) {
            if (!subject.getFaces().isEmpty()) {
                mFaceView.setFace(subject.getFaces().get(0));
            }
            extract(subject);
        } else {
            mStatus = Status.CAPTURING;
            showInfo("File did not contain valid information for subject");
        }
    }

    @Override
    protected void onOperationStarted(NBiometricOperation operation) {
        super.onOperationStarted(operation);
        if (operation == NBiometricOperation.CAPTURE) {
            mStatus = Status.CAPTURING;
            setCameraControlsVisible(true);
        }
    }

    @Override
    protected void onOperationCompleted(NBiometricOperation operation, NBiometricTask task) {
        super.onOperationCompleted(operation, task);
        if (task != null && task.getStatus() == NBiometricStatus.OK && operation == NBiometricOperation.CREATE_TEMPLATE) {
            mStatus = Status.TEMPLATE_CREATED;
            setCameraControlsVisible(false);
        }

        if (task == null || (operation == NBiometricOperation.CREATE_TEMPLATE
                && task.getStatus() != NBiometricStatus.OK
                && task.getStatus() != NBiometricStatus.CANCELED
                && task.getStatus() != NBiometricStatus.OPERATION_NOT_ACTIVATED)) {
            if (!mAppIsGoingToBackground) {
                startCapturing();
            }
        }
    }

    @Override
    protected boolean isStopSupported() {
        return false;
    }

    // ===========================================================
    // 	Public methods
    // ===========================================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_FOCUS || keyCode == KeyEvent.KEYCODE_CAMERA) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            //TODO call autofocus
            return true;
        }
        return false;
    }

    @Override
    public void onCameraFormatSelected(final NMediaFormat format) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                NCamera camera = Model.getInstance().getClient().getFaceCaptureDevice();
                if (camera != null) {
                    camera.setCurrentFormat(format);
                }

            }
        }).start();
    }

    @Override
    public void onSwitchCamera() {
        if (!FacePreferences.isUseLiveness(this)) {
            NCamera currentCamera = client.getFaceCaptureDevice();
            for (NDevice device : client.getDeviceManager().getDevices()) {
                if (device.getDeviceType().contains(NDeviceType.CAMERA)) {
                    if (!device.equals(currentCamera) && currentCamera.isCapturing()) {
                        cancel();
                        client.setFaceCaptureDevice((NCamera) device);
                        startCapturing();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onChangeFormat() {
        CameraFormatFragment.newInstance().show(getFragmentManager(), "formats");
    }

//	public static List<String> mandatoryComponents() {
//		return Arrays.asList(LicensingManager.LICENSE_DEVICES_CAMERAS,
//				LicensingManager.LICENSE_FACE_DETECTION,
//				LicensingManager.LICENSE_FACE_EXTRACTION,
//				LicensingManager.LICENSE_FACE_MATCHING);
//	}
//
//	public static List<String> additionalComponents() {
//		return Arrays.asList(LicensingManager.LICENSE_FACE_STANDARDS,
//				LicensingManager.LICENSE_FACE_MATCHING_FAST,
//				LicensingManager.LICENSE_FACE_SEGMENTS_DETECTION);
//	}

    public static List<String> mandatoryComponents() {
        return Arrays.asList(LicensingManager.LICENSE_DEVICES_CAMERAS,
                LicensingManager.LICENSE_FACE_DETECTION,
                LicensingManager.LICENSE_FACE_EXTRACTION,
                LicensingManager.LICENSE_FACE_MATCHING);
    }

    public static List<String> additionalComponents() {
        return Arrays.asList(LicensingManager.LICENSE_FACE_STANDARDS,
                LicensingManager.LICENSE_FACE_MATCHING_FAST,
                LicensingManager.LICENSE_FACE_SEGMENTS_DETECTION);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MultiModalActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
