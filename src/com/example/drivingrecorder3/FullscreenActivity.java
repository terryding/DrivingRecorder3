package com.example.drivingrecorder3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import com.example.drivingrecorder3.R;
import com.example.drivingrecorder3.util.SystemUiHider;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.TextTrackImpl;



public class FullscreenActivity extends Activity implements 
		SurfaceHolder.Callback,
		LocationListener,
		SensorEventListener,
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener,
		com.google.android.gms.location.LocationListener {
	
	private static final String SERVER_ADDRESS = "68.181.32.94";
	private static final int COLLISION_THRESHOLD = 12; // m/s^2
	
	private static final boolean AUTO_HIDE = true;
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
	private static final boolean TOGGLE_ON_CLICK = true;	
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;	
	private SystemUiHider mSystemUiHider;
    
    public static final String TAG = "DrivingRecorder";
    
    public static final String EXTRA_MESSAGE = "com.example.drivingrecorder3.MESSAGE";
	
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private SurfaceView mPreview;
    private MediaRecorder mMediaRecorder;
    
    private Button recordButton;
    private Button playbackButton;
    private Button callButton;
    
    private boolean isRecording = false;
    

    //Accelerometer related
  	private boolean mInitialized; 
  	private SensorManager mSensorManager; 
  	private Sensor mAccelerometer; 
  	private final float NOISE = (float) 2.0;
  	private float mLastX, mLastY, mLastZ;
  	private float deltaX, deltaY, deltaZ;

	private TextView mDirection;
	private Sensor gsensor;
	private Sensor msensor;
	private float[] mGravity = new float[3];
	private float[] mGeomagnetic = new float[3];
	private float degree = 0f;
	
  	
  	//Alert prompt related
  	final Context context = this;
  	public boolean collisionDetected = false;
  	
  	//Subtitle related
  	String currentSub = " ";
  	String [] subRecorder = null;
  	
	// Update frequency in seconds
    public static final int UPDATE_INTERVAL_IN_SECONDS = 1;
    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL =
    500 * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL =
    		500 * FASTEST_INTERVAL_IN_SECONDS;
    
    private TextView mAddress;
    private LocationClient mLocationClient;
	private LocationRequest mLocationRequest;
	boolean mUpdatesRequested;
	double latitude, longitude;
	static final String APPTAG = "LocationSample";
	String addressText;
	
	/*Speed*/
	private TextView mSpeed;
	String mySpeed; 
	String mybearing;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_fullscreen);
		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.fullscreen_content);	
		
		mPreview = (SurfaceView) contentView;
		mHolder = mPreview.getHolder();
        mHolder.addCallback(this);

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(
                LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mAddress = (TextView) findViewById(R.id.address);
        
        mDirection = (TextView) findViewById(R.id.direction);
        
		mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
		mSystemUiHider.setup();

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});
		
		mLocationClient = new LocationClient(this, this, this);
		mSpeed = (TextView) findViewById(R.id.speed);

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.

		recordButton = (Button) findViewById(R.id.record_button);
		recordButton.setOnClickListener(
		    new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		            if (isRecording) {
		            	stopLoopRecorder();
		            } else {
		            	startRecorder();
		            	recordHandler.postDelayed(recordRunnable, 10000);
		            }
		        }
		    }
		);
		
		playbackButton = (Button) findViewById(R.id.playback_button);
		playbackButton.setOnClickListener(
		    new View.OnClickListener() {
		        @SuppressLint("NewApi")
				@Override
		        public void onClick(View v) {
		        	stopLoopRecorder();
		        	startPlayback();
		            
		        }
		    }
		);
		
		
		callButton = (Button) findViewById(R.id.call_button);
		callButton.setOnClickListener(
		    new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		        	stopLoopRecorder();
		        	makeCall();
		        }
		    });
		
		controlsView.requestFocus();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_GAME);
		mSensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_GAME);
		
		
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
	
		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}

	@Override
	protected void onStart() {
	    super.onStart();
	    mLocationClient.connect();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
		super.onResume();
		try {
			mCamera = getCameraInstance();
		} catch (Exception e) {
			Log.d(TAG, "Error reconnceting camera: " + e.getMessage());
		}
	
		collisionDetected = false;
		// for the system's orientation sensor registered listeners
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
						SensorManager.SENSOR_DELAY_GAME);
	}

	@Override
	protected void onStop() {
		// If the client is connected
	    if (mLocationClient.isConnected()) {
	        /*
	         * Remove location updates for a listener.
	         * The current Activity is the listener, so
	         * the argument is "this".
	         */
	        mLocationClient.removeLocationUpdates(this);
	    }
	    /*
	     * After disconnect() is called, the client is
	     * considered "dead".
	     */
	    mLocationClient.disconnect();
	    super.onStop();
	    mSensorManager.unregisterListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopLoopRecorder();
		releaseCamera();
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};
	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}

	/**
	 * These methods below are related to Loop recorder
	 */
	
	/**
	 * Camera Preview
	 */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void surfaceCreated(SurfaceHolder holder) {
	    try {
	    	CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
	
	    	Camera.Parameters parameters = mCamera.getParameters();
	    	parameters.setPreviewSize(profile.videoFrameWidth,profile.videoFrameHeight);
	    	mCamera.setParameters(parameters);
	    	
	        mCamera.setPreviewDisplay(holder);
	        mCamera.startPreview();
	    } catch (IOException e) {
	        Log.d(TAG, "Error setting camera preview: " + e.getMessage());
	    } catch (NullPointerException e) {
	    	Log.d(TAG, "Error setting camera preview: " + e.getMessage());
	    }
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	    // empty. Take care of releasing the Camera preview in your activity.
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	    // If your preview can change or rotate, take care of those events here.
	    // Make sure to stop the preview before resizing or reformatting it.
	
	    if (mHolder.getSurface() == null){
	      // preview surface does not exist
	      return;
	    }
	    
	    if (mCamera == null) {
			//mCamera = getCameraInstance();
	    	Log.d(TAG, "Camera is NUUUUUUUUUUUUUULL");
	    	
		}
	
	}

	/**
	 * Single period operation
	 */
	public void startRecorder() {
		if (prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            mMediaRecorder.start();

            // inform the user that recording has started
            setRecordButtonText("Stop");
            isRecording = true;
        } else {
            // prepare didn't work, release the camera
            releaseMediaRecorder();
            // inform user
            Toast.makeText(this, "Fail getting recorder, try later.", Toast.LENGTH_LONG).show();
        }
		
		//Subtitle related
    	currentSubNumber = 0;
		subRecorder = new String [12];
		for (int i = 0; i < 12; i++) {
			subRecorder[i] = " ... ";
		}
		subHandler.postDelayed(subRunnable, 500);
	}
	
	public void stopRecorder() {
		// stop recording and release camera
		try {
	        mMediaRecorder.stop();  // stop the recording
		} catch (RuntimeException e){
    		e.printStackTrace();
    		new File(latestClipName).delete();
    		latestClipName = null;
    	} finally {
    		// inform the user that recording has stopped
    		releaseMediaRecorder(); // release the MediaRecorder object
	        mCamera.lock();         // take camera access back from MediaRecorder
	        if (latestClipName != null) {
		        try {
					addSub();
				} catch (IOException e) {
					e.printStackTrace();
				}
	        }
            setRecordButtonText("Record");
        	subHandler.removeCallbacks(subRunnable);
    	}
	}

	public void setRecordButtonText(String s) {
	    recordButton.setText(s);
	}

	
	
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private boolean prepareVideoRecorder(){
	
	   // mCamera = getCameraInstance();
	    mMediaRecorder = new MediaRecorder();
	
	    // Step 1: Unlock and set camera to MediaRecorder
	    mCamera.unlock();
	    mMediaRecorder.setCamera(mCamera);
	
	    // Step 2: Set sources
	    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
	    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
	
	    // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
	    mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));
	
	    // Step 4: Set output file
	    mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
	
	    // Step 5: Set the preview output
	    mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
	
	    // Step 6: Prepare configured MediaRecorder
	    try {
	        mMediaRecorder.prepare();
	    } catch (IllegalStateException e) {
	        Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
	        releaseMediaRecorder();
	        return false;
	    } catch (IOException e) {
	        Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
	        releaseMediaRecorder();
	        return false;
	    }
	    return true;
	}

	/** Create a file Uri for saving an image or video */
	/*private static Uri getOutputMediaFileUri(int type){
	      return Uri.fromFile(getOutputMediaFile(type));
	}*/
	
	private void releaseMediaRecorder(){
	    if (mMediaRecorder != null) {
	        mMediaRecorder.reset();   // clear recorder configuration
	        mMediaRecorder.release(); // release the recorder object
	        mMediaRecorder = null;
	        mCamera.lock();           // lock camera for later use
	    }
	}

	public static Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		}
		catch (Exception e){
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	private void releaseCamera(){
	    if (mCamera != null){
	        mCamera.release();        // release the camera for other applications
	        mCamera = null;
	    }
	}

	
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
	private static String[] fileNames = new String[4];
	private static String deprecatedFileName = null;
	private static Integer fileNumber = 0;
	private static Integer fileCount = 0;
	private static String latestClipName = null;
	private static String latestBundleName = null;

	/** Create a File for saving an image or video */
	@SuppressLint("SimpleDateFormat")
	private static File getOutputMediaFile(int type){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.
	
	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_MOVIES), "DrivingRecorder");
	    // This location works best if you want the created images to be shared
	    // between applications and persist after your app has been uninstalled.
	
	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	            Log.d(TAG, "failed to create directory");
	            return null;
	        }
	    }
	
	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "IMG_"+ timeStamp + ".jpg");
	    } else if(type == MEDIA_TYPE_VIDEO) {
	    	latestClipName = mediaStorageDir.getPath() + File.separator + "VID_"+ timeStamp + ".mp4";
	    	mediaFile = new File(latestClipName);
	    } else {
	        return null;
	    }
	
	    return mediaFile;
	}

	int currentSubNumber = 0;
	private Handler subHandler = new Handler();
	private Runnable subRunnable = new Runnable() {
	   @Override
	   public void run() {
		   subRecorder[currentSubNumber] = currentSub;
		   currentSubNumber ++;
		   subHandler.postDelayed(this, 1000);
	   }
	};

	@SuppressLint("SimpleDateFormat")
	public void addSub() throws IOException{
	    //String audioEnglish = RemoveSomeSamplesExample.class.getProtectionDomain().getCodeSource().getLocation().getFile() + "/count-video.mp4";
	    Movie countVideo = MovieCreator.build(latestClipName);
	
	    double endTime = (double) getDuration(countVideo.getTracks().get(0)) 
	    		/ countVideo.getTracks().get(0).getTrackMetaData().getTimescale();
	    TextTrackImpl subTitleEng = new TextTrackImpl();
	    subTitleEng.getTrackMetaData().setLanguage("eng");
	
	    for (int i = 0; i < 10; i++) {
	    	if ((double)(i + 1) < endTime) {
	    		subTitleEng.getSubs().add(new TextTrackImpl.Line(
	    				(long)(i * 1000), (long)((i + 1) * 1000), subRecorder[i]));
	    	} else if ((double)i < endTime) {
	    		subTitleEng.getSubs().add(new TextTrackImpl.Line(
	    				(long)(i * 1000), (long)(endTime * 1000), subRecorder[i]));
	    	}
	    }
	
	    countVideo.addTrack(subTitleEng);
	
	
	    Container out = new DefaultMp4Builder().build(countVideo);
	    
	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	            Environment.DIRECTORY_MOVIES), "DrivingRecorder");
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    String fileName = mediaStorageDir.getPath() + File.separator + "SUB_"+ timeStamp + ".mp4";
	    fileNames[fileNumber] = fileName;
	    Log.d(TAG, "Current fileName = " + fileNames[fileNumber]);
	    if (deprecatedFileName != null) {
	    	new File(deprecatedFileName).delete();
	    }
	    if (fileNumber >= 3) {
	    	fileNumber = 0;
	    	deprecatedFileName = fileNames[fileNumber];
	    } else {
	    	fileNumber ++;
	    	if (fileCount >=4) {
	    		deprecatedFileName = fileNames[fileNumber];
	    	}
	    }
	    fileCount ++;
	    Log.d(TAG, "fileNumber = " + fileNumber + "\t fileCount = " + fileCount);
	    
	    FileOutputStream fos = new FileOutputStream(new File(fileName));
	    FileChannel fc = fos.getChannel();
	    out.writeContainer(fc);
	    fos.close();
	    fc.close();
	}

	protected static long getDuration(Track track) {
	    long duration = 0;
	    for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
	        duration += entry.getCount() * entry.getDelta();
	    }
	    return duration;
	}

	/**
	 * Loop Recording operation
	 */
	private Handler recordHandler = new Handler();
	private Runnable recordRunnable = new Runnable() {
	   @Override
	   public void run() {
		   stopRecorder();
		   startRecorder();
		   recordHandler.postDelayed(this, 10000);
	   }
	};

	public void stopLoopRecorder() {
		if (isRecording) {
        	recordHandler.removeCallbacks(recordRunnable);
        	stopRecorder();
        	stitchFiles();
            isRecording = false;
            fileNumber = 0;
            fileCount = 0;
        }
	}
	
	/** Create a file Uri for saving an image or video */
	/*private static Uri getOutputMediaFileUri(int type){
	      return Uri.fromFile(getOutputMediaFile(type));
	}*/
	
	@SuppressLint("SimpleDateFormat")
	private void stitchFiles (){
		int i;
		Movie[] inMovies;
		try {
	    	if (fileCount <= 4) {
	    		inMovies = new Movie[fileCount];
	    		for (i = 0; i < fileCount; i++) {
	    			inMovies[i] = MovieCreator.build(fileNames[i]);
	    			new File(fileNames[i]).delete();
	    		}
	    	} else {
	    		inMovies = new Movie[4];
	    		for (i = 0; i <= 3; i++) {
	    			inMovies[i] = MovieCreator.build(fileNames[fileNumber + i <= 3? 
	    					fileNumber + i: fileNumber + i - 4]);
	    		}
	    	}
		
		
	    	List<Track> videoTracks = new LinkedList<Track>();
	        List<Track> audioTracks = new LinkedList<Track>();
	        List<Track> subTracks = new LinkedList<Track>();
	
	        for (Movie m : inMovies) {
	            for (Track t : m.getTracks()) {
	                if (t.getHandler().equals("soun")) {
	                    audioTracks.add(t);
	                }
	                if (t.getHandler().equals("vide")) {
	                    videoTracks.add(t);
	                }
	                if (t.getHandler().equals("sbtl")) {
	                	subTracks.add(t);
	                }
	            }
	        }
	
	        Movie result = new Movie();
	        
	        if (audioTracks.size() > 0) {
	            result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
	        }
	        if (videoTracks.size() > 0) {
	            result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
	        }
	        if (subTracks.size() > 0) {
	            result.addTrack(new AppendTrack(subTracks.toArray(new Track[subTracks.size()])));
	        }
	
	        Container out = new DefaultMp4Builder().build(result);
	
	        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	                Environment.DIRECTORY_MOVIES), "DrivingRecorder");
	        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	        latestBundleName = mediaStorageDir.getPath() + File.separator + "BUD_"+ timeStamp + ".mp4";
	        RandomAccessFile rf = new RandomAccessFile(latestBundleName, "rw");
	        FileChannel fc = rf.getChannel();
	        out.writeContainer(fc);
	        rf.close();
	        fc.close();
	        
	        
		} catch (IOException e) {
			Toast.makeText(this, "IOException occured while stiching files.", Toast.LENGTH_SHORT).show();
			Log.d(TAG, "IOException preparing for stiching: " + e.getMessage());
		}
		
	}

	/**
	 * This method below is related to Playback
	 */
	
	public void startPlayback() {
		Intent intent = new Intent(this, VideoPlayerActivity.class);
	    String message = latestBundleName;
	    intent.putExtra(EXTRA_MESSAGE, message);
	    startActivity(intent);
	}
	
	/**
	 * These methods below are related to Collision Detection and Compass
	 */
	
	CountDownTimer mCountDownTimer;
	
	public void makeAlertPrompt() {			
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				context);

		// set dialog message
		alertDialogBuilder
			.setCancelable(false)
			.setPositiveButton("Call",
			  new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog,int id) {
				// get user input and set it to result
				// edit text
			    	makeCall();
			    }
			  })
			.setNegativeButton("Cancel",
			  new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog,int id) {
				dialog.cancel();
				mCountDownTimer.cancel();
				collisionDetected = false;
			    }
			  });

		// create alert dialog
		final AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.setTitle("COLLISION DETECTED");
		alertDialog.setMessage("Call in 5 secs");
		alertDialog.show();
		
		mCountDownTimer = new CountDownTimer(6000, 1000) {
		    @Override
		    public void onTick(long millisUntilFinished) {
		    	alertDialog.setMessage("Call in " + (millisUntilFinished/1000) 
		    			+ ((millisUntilFinished/1000) == 1 ?" sec":" secs"));
		    }

		    @Override
		    public void onFinish() {
		        alertDialog.cancel();
		        makeCall();
		    }
		}.start();
		
		//countdownHandler.post(countdownRunnable);
	}
	
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		float x = 0;
		float y = 0;
		float z = 0;
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			x = event.values[0];
			y = event.values[1];
			z = event.values[2];
		}
		if (!mInitialized) {
		mLastX = x;
		mLastY = y;
		mLastZ = z;
		mInitialized = true;
		} else {
			deltaX = Math.abs(mLastX - x);
			deltaY = Math.abs(mLastY - y);
			deltaZ = Math.abs(mLastZ - z);
			if (deltaX < NOISE) deltaX = (float)0.0;
			if (deltaY < NOISE) deltaY = (float)0.0;
			if (deltaZ < NOISE) deltaZ = (float)0.0;
			mLastX = x;
			mLastY = y;
			mLastZ = z;
		}
		if (deltaZ > COLLISION_THRESHOLD && collisionDetected == false) {
			collisionDetected = true;
	    	Toast.makeText(this, "Collision detected!!!", Toast.LENGTH_SHORT).show();
			stopLoopRecorder();
			makeAlertPrompt();
		}
		
		final float alpha = 0.97f;
	
		synchronized (this) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				mGravity[0] = alpha * mGravity[0] + (1 - alpha)
						* event.values[0];
				mGravity[1] = alpha * mGravity[1] + (1 - alpha)
						* event.values[1];
				mGravity[2] = alpha * mGravity[2] + (1 - alpha)
						* event.values[2];
			}
	
			if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
				mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha)
						* event.values[0];
				mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha)
						* event.values[1];
				mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha)
						* event.values[2];
			}
	
			float R[] = new float[9];
			float I[] = new float[9];
			boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
			if (success) {
				float orientation[] = new float[3];
				SensorManager.getOrientation(R, orientation);
				degree = (float) Math.toDegrees(orientation[0]); // orientation
				degree = degree + 180;
				if (degree >= 22.5 && degree < 77.5) 
					mDirection.setText("SW");
				else if (degree >= 77.5 && degree < 112.5)
					mDirection.setText("W ");
				else if (degree >= 112.5 && degree < 157.5)
					mDirection.setText("NW");
				else if (degree >= 157.5 && degree < 202.5)
					mDirection.setText("N ");
				else if (degree >= 202.5 && degree < 247.5)
					mDirection.setText("NE");
				else if (degree >= 247.5 && degree < 292.5)
					mDirection.setText("E ");
				else if (degree >= 292.5 && degree < 337.5)
					mDirection.setText("SE");
				else mDirection.setText("S ");
			}
		}
		
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Auto-generated method stub
		
	}

	private void makeCall(){
		String server = SERVER_ADDRESS;
		SendDataTask sendDataTask = new SendDataTask();
		
			sendDataTask.execute(latestBundleName, server, context);
		
		Toast.makeText(this, latestBundleName + " sent to " + server, Toast.LENGTH_SHORT).show();
		
	}

	/**
	 * These methods below are related to Localization and Speed
	 */
	@Override
	public void onLocationChanged(Location location) {		
        if(location.hasSpeed()) { 
        	mySpeed = getString(R.string.my_speed, location.getSpeed()*2.24);
        	mSpeed.setText(mySpeed + " MPH");
        } else {
        	mSpeed.setText("0.0 MPH");
        }
        
		Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        // Get the current location from the input parameter list
        //Location location = mLocationClient.getLastLocation();

        // Create a list to contain the result address
        List <Address> addresses = null;

        // Try to get an address for the current location. Catch IO or network problems.
        try {

            /*
             * Call the synchronous getFromLocation() method with the latitude and
             * longitude of the current location. Return at most 1 address.
             */
            addresses = geocoder.getFromLocation(location.getLatitude(),
                location.getLongitude(), 1);

            // Catch network or other I/O problems.
            } catch (IOException exception1) {

                // Log an error and return an error message
                Log.e(APPTAG, getString(R.string.IO_Exception_getFromLocation));

                // print the stack trace
                //exception1.printStackTrace();
                Toast.makeText(this, "Unable to resolve address: Service not available", Toast.LENGTH_SHORT).show();
                // Return an error message
                //return (getString(R.string.IO_Exception_getFromLocation));

            // Catch incorrect latitude or longitude values
            } catch (IllegalArgumentException exception2) {

                // Construct a message containing the invalid arguments
                String errorString = getString(
                        R.string.illegal_argument_exception,
                        location.getLatitude(),
                        location.getLongitude()
                );
                // Log the error and print the stack trace
                Log.e(APPTAG, errorString);
                exception2.printStackTrace();

                //
                //return errorString;
            }
            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {

                // Get the first address
                Address address = addresses.get(0);

                // Format the first line of address
                String addressText = getString(R.string.address_output_string,

                        // If there's a street address, add it
                        address.getMaxAddressLineIndex() > 0 ?
                                address.getAddressLine(0) : "",

                        // Locality is usually a city
                        address.getLocality(),

                        // The country of the address
                        address.getCountryName()
                );

                // Return the text
                //return addressText;
                mAddress.setText(addressText);

            // If there aren't any addresses, post a message
            } else {
            	mAddress.setText("Lat: " +location.getLatitude() 
            			+ "Long: " + location.getLongitude());
            }
            
            currentSub = "Addr: " + mAddress.getText() + "\nSpeed: " + mSpeed.getText();        
	}

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		// 
		
	}

	@Override
	public void onConnected(Bundle arg0) {
		mLocationClient.requestLocationUpdates(mLocationRequest, this);
		
	}

	@Override
	public void onDisconnected() {
		// 
		
	}
	
	@Override
	public void onProviderDisabled(String provider) {
		//
		
	}

	@Override
	public void onProviderEnabled(String provider) {
		// 
		
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// 
		
	}    
}

/**
 * AsyncTask to send video file
 */

class SendDataTask extends AsyncTask<Object, Void, Integer> {

    protected Integer doInBackground(Object...params) {
    	String file = (String) params[0];
    	String server = (String) params[1];
    	Context context = (Context) params[2];
    	FileSend.send(file, server);

 	   	String numberString = "3526653317";
 		Uri number = Uri.parse("tel:" + numberString);
 	    Intent dial = new Intent(Intent.ACTION_CALL, number);
 	    context.startActivity(dial);
 	    
    	return 0;
    }

   protected void onPostExecute() {
    }
}
