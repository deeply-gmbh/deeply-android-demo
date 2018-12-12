package de.deeply.demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import de.deeply.Content;
import de.deeply.Processor;
import de.deeply.demo.R;
import de.deeplyapp.demo.processor.OnImageAvailableListener;
import de.deeply.demo.ui.AutoFitTextureView;
import de.deeply.demo.ui.ContentView;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link CameraFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link CameraFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CameraFragment extends Fragment
{
   private static final int REQUEST_CAMERA_PERMISSION = 1;
   private static final String FRAGMENT_DIALOG = "dialog";
   private static final String TAG = "CameraFragment";

   private static final int MAX_PREVIEW_WIDTH = 1920;
   private static final int MAX_PREVIEW_HEIGHT = 1080;
   private static final String CAMERA_ID = "CameraId";

   private AutoFitTextureView m_textureView;
   private ContentView m_preview;
   private ImageView m_changeCamera;
   private Size m_previewSize;
   private Semaphore m_cameraOpenCloseLock = new Semaphore( 1 );
   private int m_SensorOrientation;
   private String m_cameraId;

   private ImageReader m_processingImageReader;

   private CameraDevice m_cameraDevice;

   /**
    * An additional thread for running tasks that shouldn't block the UI.
    */
   private HandlerThread m_backgroundThread;

   /**
    * A {@link Handler} for running tasks in the background.
    */
   private Handler m_backgroundHandler;

   /**
    * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
    */
   private final CameraDevice.StateCallback m_stateCallback = new CameraDevice.StateCallback()
   {

      @Override
      public void onOpened( @NonNull CameraDevice cameraDevice )
      {
         // This method is called when the camera is opened.  We start camera preview here.
         m_cameraOpenCloseLock.release();
         m_cameraDevice = cameraDevice;
         CreateCameraPreviewSession();
      }

      @Override
      public void onDisconnected( @NonNull CameraDevice cameraDevice )
      {
         m_cameraOpenCloseLock.release();
         cameraDevice.close();
         m_cameraDevice = null;
      }

      @Override
      public void onError( @NonNull CameraDevice cameraDevice, int error )
      {
         m_cameraOpenCloseLock.release();
         cameraDevice.close();
         m_cameraDevice = null;
         Activity activity = getActivity();
         if ( null != activity )
         {
            activity.finish();
         }
      }

   };

   /**
    * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
    * {@link TextureView}.
    */
   private final TextureView.SurfaceTextureListener m_surfaceTextureListener
         = new TextureView.SurfaceTextureListener()
   {

      @Override
      public void onSurfaceTextureAvailable( SurfaceTexture texture, int width, int height )
      {
         OpenCamera( width, height );
      }

      @Override
      public void onSurfaceTextureSizeChanged( SurfaceTexture texture, int width, int height )
      {
         ConfigureTransform( width, height );
      }

      @Override
      public boolean onSurfaceTextureDestroyed( SurfaceTexture texture )
      {
         return true;
      }

      @Override
      public void onSurfaceTextureUpdated( SurfaceTexture texture )
      {
      }

   };

   private Processor m_processor;
   private Processor.OnContentListener m_contentListener;
   private OnImageAvailableListener m_onImageAvailableListener;

   /*
   private final ImageReader.OnImageAvailableListener m_onProcessingImageAvailableListener =
         new ImageReader.OnImageAvailableListener()
   {
      @Override
      public void onImageAvailable( ImageReader reader )
      {
         Image i = reader.acquireLatestImage();
         Log.d( TAG, "new image is here " + i.getWidth() + "x" + i.getHeight() );
         i.close();
      }
   };
   */

   private CaptureRequest.Builder m_previewRequestBuilder;
   private CameraCaptureSession m_captureSession;
   private CaptureRequest m_previewRequest;
   private CameraCaptureSession.CaptureCallback m_captureCallback = null; // TODO
   private String m_cameraIdFront;
   private String m_cameraIdBack;

   public CameraFragment()
   {
      // Required empty public constructor
   }

   /**
    * Use this factory method to create a new instance of
    * this fragment using the provided parameters.
    *
    * @return A new instance of fragment CameraFragment.
    */
   public static CameraFragment newInstance()
   {
      CameraFragment fragment = new CameraFragment();

      //Bundle args = new Bundle();
      //fragment.setArguments( args );

      return fragment;
   }

   @Override
   public void onCreate( Bundle savedInstanceState )
   {
      super.onCreate( savedInstanceState );

      Activity activity = getActivity();

      CameraManager manager = (CameraManager) activity.getSystemService( Context.CAMERA_SERVICE );

      try
      {
         for ( String cameraId : manager.getCameraIdList() )
         {
            CameraCharacteristics characteristics
                  = manager.getCameraCharacteristics( cameraId );

            // We don't use a front facing camera in this sample.
            Integer facing = characteristics.get( CameraCharacteristics.LENS_FACING );

            if ( facing != null )
            {
               if ( facing == CameraCharacteristics.LENS_FACING_FRONT )
               {
                  m_cameraIdFront = cameraId;
               }

               if ( facing == CameraCharacteristics.LENS_FACING_BACK )
               {
                  m_cameraIdBack = cameraId;
                  m_cameraId = m_cameraIdBack;
               }
            }
         }
      }
      catch ( CameraAccessException e )
      {
         e.printStackTrace();
      }
   }

   @Override
   public View onCreateView( LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState )
   {
      // Inflate the layout for this fragment
      return inflater.inflate( R.layout.fragment_camera, container, false );
   }

   @Override
   public void onViewCreated( final View view, Bundle savedInstanceState )
   {
      m_textureView = (AutoFitTextureView) view.findViewById( R.id.surface );
      m_preview = (ContentView) view.findViewById( R.id.preview );
      m_changeCamera = (ImageView) view.findViewById( R.id.change_camera );

      m_changeCamera.setOnClickListener( new View.OnClickListener()
      {
         @Override
         public void onClick( View v )
         {
            Bundle b = new Bundle();

            if ( m_cameraId.equals( m_cameraIdBack ) )
            {
               m_cameraId = m_cameraIdFront;
               b.putString( "camera_facing", "front" );
            }
            else
            {
               m_cameraId = m_cameraIdBack;
               b.putString( "camera_facing", "back" );
            }

            CloseCamera();
            StopBackgroundThread();
            StartBackgroundThread();
            OpenCamera( m_textureView.getWidth(), m_textureView.getHeight() );
         }
      } );
   }

   @Override
   public void onActivityCreated( Bundle savedInstanceState )
   {
      super.onActivityCreated( savedInstanceState );

      if ( savedInstanceState != null )
      {
         String id = savedInstanceState.getString( CAMERA_ID );

         if ( id != null )
         {
            m_cameraId = id;
         }
      }
   }

   @Override
   public void onSaveInstanceState( Bundle outState )
   {
      super.onSaveInstanceState( outState );

      outState.putString( CAMERA_ID, m_cameraId );
   }

   @Override
   public void onPause()
   {
      CloseCamera();
      StopBackgroundThread();
      super.onPause();
   }

   @Override
   public void onResume()
   {
      super.onResume();
      StartBackgroundThread();

      // When the screen is turned off and turned back on, the SurfaceTexture is already
      // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
      // a camera and start preview from here (otherwise, we wait until the surface is ready in
      // the SurfaceTextureListener).
      if ( m_textureView.isAvailable() )
      {
         OpenCamera( m_textureView.getWidth(), m_textureView.getHeight() );
      }
      else
      {
         m_textureView.setSurfaceTextureListener( m_surfaceTextureListener );
      }

      m_contentListener = new Processor.OnContentListener()
      {
         private final ContentView.Processor m_processor = m_preview.CreateProcessor();

         @Override
         public void OnContent( Content c, de.deeply.Image i )
         {
            m_processor.Process( c );
         }
      };
   }

   @Override
   public void onAttach( Context context )
   {
      super.onAttach( context );
   }

   @Override
   public void onDetach()
   {
      super.onDetach();
   }

   private void OpenCamera( int width, int height )
   {
      Activity activity = getActivity();

      if ( ContextCompat.checkSelfPermission( activity, Manifest.permission.CAMERA )
            != PackageManager.PERMISSION_GRANTED )
      {
         RequestCameraPermission();
         return;
      }

      SetUpCameraOutputs( m_cameraId, width, height );
      ConfigureTransform( width, height );

      CameraManager manager = (CameraManager) activity.getSystemService( Context.CAMERA_SERVICE );
      try
      {
         if ( !m_cameraOpenCloseLock.tryAcquire( 2500, TimeUnit.MILLISECONDS ) )
         {
            throw new RuntimeException( "Time out waiting to lock camera opening." );
         }
         manager.openCamera( m_cameraId, m_stateCallback, m_backgroundHandler );
      }
      catch ( CameraAccessException e )
      {
         e.printStackTrace();
      }
      catch ( InterruptedException e )
      {
         throw new RuntimeException( "Interrupted while trying to lock camera opening.", e );
      }
   }

   /**
    * Closes the current {@link CameraDevice}.
    */
   private void CloseCamera()
   {
      try
      {
         m_cameraOpenCloseLock.acquire();

         if ( null != m_captureSession )
         {
            m_captureSession.close();
            m_captureSession = null;
         }

         if ( null != m_processingImageReader )
         {
            m_processingImageReader.close();
            m_processingImageReader = null;
         }

         if ( null != m_cameraDevice )
         {
            m_cameraDevice.close();
            m_cameraDevice = null;
         }
      }
      catch ( InterruptedException e )
      {
         throw new RuntimeException( "Interrupted while trying to lock camera closing.", e );
      }
      finally
      {
         m_cameraOpenCloseLock.release();
      }
   }

   private void RequestCameraPermission()
   {
      if ( shouldShowRequestPermissionRationale( Manifest.permission.CAMERA ) )
      {
         new ConfirmationDialog().show( getChildFragmentManager(), FRAGMENT_DIALOG );
      }
      else
      {
         requestPermissions( new String[]{ Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION );
      }
   }

   /**
    * Sets up member variables related to camera.
    *
    * @param width  The width of available size for camera preview
    * @param height The height of available size for camera preview
    */
   @SuppressWarnings( "SuspiciousNameCombination" )
   private void SetUpCameraOutputs( String cameraId, int width, int height )
   {
      Activity activity = getActivity();
      CameraManager manager = (CameraManager) activity.getSystemService( Context.CAMERA_SERVICE );

      try
      {
         CameraCharacteristics characteristics = manager.getCameraCharacteristics( cameraId );

         StreamConfigurationMap map = characteristics.get(
               CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP );
         if ( map == null )
         {
            Log.e( TAG, "camera " + cameraId + "does not support SCALER_STREAM_CONFIGURATION_MAP" );
            return;
         }

         Size largest = Collections.max( Arrays.asList( map.getOutputSizes( ImageFormat.YUV_420_888 ) ),
               new CompareSizesByArea() );

         // Find out if we need to swap dimension to get the preview size relative to sensor
         // coordinate.
         int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

         //noinspection ConstantConditions
         m_SensorOrientation = characteristics.get( CameraCharacteristics.SENSOR_ORIENTATION );
         boolean swappedDimensions = false;
         switch ( displayRotation )
         {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
               if ( m_SensorOrientation == 90 || m_SensorOrientation == 270 )
               {
                  swappedDimensions = true;
               }
               break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
               if ( m_SensorOrientation == 0 || m_SensorOrientation == 180 )
               {
                  swappedDimensions = true;
               }
               break;
            default:
               Log.e( TAG, "Display rotation is invalid: " + displayRotation );
         }

         Point displaySize = new Point();
         activity.getWindowManager().getDefaultDisplay().getSize( displaySize );
         int rotatedPreviewWidth = width;
         int rotatedPreviewHeight = height;
         int maxPreviewWidth = displaySize.x;
         int maxPreviewHeight = displaySize.y;

         if ( swappedDimensions )
         {
            rotatedPreviewWidth = height;
            rotatedPreviewHeight = width;
            maxPreviewWidth = displaySize.y;
            maxPreviewHeight = displaySize.x;
         }

         if ( maxPreviewWidth > MAX_PREVIEW_WIDTH )
         {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
         }

         if ( maxPreviewHeight > MAX_PREVIEW_HEIGHT )
         {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
         }

         // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
         // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
         // garbage capture data.
         m_previewSize = chooseOptimalSize( map.getOutputSizes( SurfaceTexture.class ),
               rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
               maxPreviewHeight, largest );

         // We fit the aspect ratio of TextureView to the size of preview we picked.
         int orientation = getResources().getConfiguration().orientation;
         if ( orientation == Configuration.ORIENTATION_LANDSCAPE )
         {
            m_textureView.setAspectRatio(
                  m_previewSize.getWidth(), m_previewSize.getHeight() );
         }
         else
         {
            m_textureView.setAspectRatio(
                  m_previewSize.getHeight(), m_previewSize.getWidth() );
         }
      }
      catch ( CameraAccessException e )
      {
         e.printStackTrace();
      }
      //catch ( NullPointerException e )
      //{
      //   // Currently an NPE is thrown when the Camera2API is used but not supported on the
      //   // device this code runs.
      //   ErrorDialog.newInstance( getString( R.string.camera_error ) )
      //          .show( getChildFragmentManager(), FRAGMENT_DIALOG );
      //}
   }

   /**
    * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
    * This method should be called after the camera preview size is determined in
    * setUpCameraOutputs and also the size of `mTextureView` is fixed.
    *
    * @param viewWidth  The width of `mTextureView`
    * @param viewHeight The height of `mTextureView`
    */
   private void ConfigureTransform( int viewWidth, int viewHeight )
   {
      Activity activity = getActivity();
      if ( null == m_textureView || null == m_previewSize || null == activity )
      {
         return;
      }
      int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
      Matrix matrix = new Matrix();
      RectF viewRect = new RectF( 0, 0, viewWidth, viewHeight );
      RectF bufferRect = new RectF( 0, 0, m_previewSize.getHeight(), m_previewSize.getWidth() );
      float centerX = viewRect.centerX();
      float centerY = viewRect.centerY();
      if ( Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation )
      {
         bufferRect.offset( centerX - bufferRect.centerX(), centerY - bufferRect.centerY() );
         matrix.setRectToRect( viewRect, bufferRect, Matrix.ScaleToFit.FILL );
         float scale = Math.max(
               (float) viewHeight / m_previewSize.getHeight(),
               (float) viewWidth / m_previewSize.getWidth() );
         matrix.postScale( scale, scale, centerX, centerY );
         matrix.postRotate( 90 * (rotation - 2), centerX, centerY );
      }
      else if ( Surface.ROTATION_180 == rotation )
      {
         matrix.postRotate( 180, centerX, centerY );
      }
      m_textureView.setTransform( matrix );

      viewRect.offsetTo( m_textureView.getLeft(), m_textureView.getTop() );

      m_preview.SetViewport( viewRect );
   }

   /**
    * Creates a new {@link CameraCaptureSession} for camera preview.
    */
   private void CreateCameraPreviewSession()
   {
      try
      {
         SurfaceTexture texture = m_textureView.getSurfaceTexture();
         assert texture != null;

         // We configure the size of default buffer to be the size of camera preview we want.
         texture.setDefaultBufferSize( m_previewSize.getWidth(), m_previewSize.getHeight() );

         // This is the output Surface we need to start preview.
         Surface surface = new Surface( texture );

         m_processingImageReader = ImageReader.newInstance( m_previewSize.getWidth(),
               m_previewSize.getHeight(),
               ImageFormat.YUV_420_888,
               2 );

         UseProcessor(); // use the process in OnImageAvailableListener for the ImageReader

         // We set up a CaptureRequest.Builder with the output Surface.
         m_previewRequestBuilder = m_cameraDevice.createCaptureRequest( CameraDevice.TEMPLATE_PREVIEW );
         m_previewRequestBuilder.addTarget( surface );
         m_previewRequestBuilder.addTarget( m_processingImageReader.getSurface() );

         // Here, we create a CameraCaptureSession for camera preview.
         m_cameraDevice.createCaptureSession( Arrays.asList( surface,
               m_processingImageReader.getSurface() ),
               new CameraCaptureSession.StateCallback()
               {

                  @Override
                  public void onConfigured( @NonNull CameraCaptureSession cameraCaptureSession )
                  {
                     // The camera is already closed
                     if ( null == m_cameraDevice )
                     {
                        return;
                     }

                     // When the session is ready, we start displaying the preview.
                     m_captureSession = cameraCaptureSession;
                     try
                     {
                        // Auto focus should be continuous for camera preview.
                        m_previewRequestBuilder.set( CaptureRequest.CONTROL_AF_MODE,
                              CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE );

                        // Finally, we start displaying the camera preview.
                        m_previewRequest = m_previewRequestBuilder.build();
                        m_captureSession.setRepeatingRequest( m_previewRequest,
                              m_captureCallback, m_backgroundHandler );

                     }
                     catch ( CameraAccessException e )
                     {
                        e.printStackTrace();
                     }
                  }

                  @Override
                  public void onConfigureFailed(
                        @NonNull CameraCaptureSession cameraCaptureSession )
                  {
                     Log.e( TAG, "onConfigureFailed" );
                  }
               }, null
         );
      }
      catch ( CameraAccessException e )
      {
         e.printStackTrace();
      }
   }

   /**
    * Starts a background thread and its {@link Handler}.
    */
   private void StartBackgroundThread()
   {
      m_backgroundThread = new HandlerThread( "CameraBackground" );
      m_backgroundThread.start();
      m_backgroundHandler = new Handler( m_backgroundThread.getLooper() );
   }

   /**
    * Stops the background thread and its {@link Handler}.
    */
   private void StopBackgroundThread()
   {
      m_backgroundThread.quitSafely();
      try
      {
         m_backgroundThread.join();
         m_backgroundThread = null;
         m_backgroundHandler = null;
      }
      catch ( InterruptedException e )
      {
         e.printStackTrace();
      }
   }

   /**
    * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
    * is at least as large as the respective texture view size, and that is at most as large as the
    * respective max size, and whose aspect ratio matches with the specified value. If such size
    * doesn't exist, choose the largest one that is at most as large as the respective max size,
    * and whose aspect ratio matches with the specified value.
    *
    * @param choices           The list of sizes that the camera supports for the intended output
    *                          class
    * @param textureViewWidth  The width of the texture view relative to sensor coordinate
    * @param textureViewHeight The height of the texture view relative to sensor coordinate
    * @param maxWidth          The maximum width that can be chosen
    * @param maxHeight         The maximum height that can be chosen
    * @param aspectRatio       The aspect ratio
    * @return The optimal {@code Size}, or an arbitrary one if none were big enough
    */
   private static Size chooseOptimalSize( Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio )
   {

      // Collect the supported resolutions that are at least as big as the preview Surface
      List<Size> bigEnough = new ArrayList<>();
      // Collect the supported resolutions that are smaller than the preview Surface
      List<Size> notBigEnough = new ArrayList<>();
      int w = aspectRatio.getWidth();
      int h = aspectRatio.getHeight();
      for ( Size option : choices )
      {
         if ( option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
               option.getHeight() == option.getWidth() * h / w )
         {
            if ( option.getWidth() >= textureViewWidth &&
                  option.getHeight() >= textureViewHeight )
            {
               bigEnough.add( option );
            }
            else
            {
               notBigEnough.add( option );
            }
         }
      }

      // Pick the smallest of those big enough. If there is no one big enough, pick the
      // largest of those not big enough.
      if ( bigEnough.size() > 0 )
      {
         return Collections.min( bigEnough, new CompareSizesByArea() );
      }
      else if ( notBigEnough.size() > 0 )
      {
         return Collections.max( notBigEnough, new CompareSizesByArea() );
      }
      else
      {
         Log.e( TAG, "Couldn't find any suitable preview size" );
         return choices[0];
      }
   }

   public void NewProcessorReady()
   {
      UseProcessor();
   }

   private void UseProcessor()
   {
      Processor p = ((ProcessorProvider) getActivity()).GetProcessor();

      if ( p != null )
      {
         m_processor = p;

         m_processor.SetOnContentListener( m_contentListener );

         if ( m_processingImageReader != null )
         {
            m_onImageAvailableListener = new OnImageAvailableListener( m_SensorOrientation,
                  getActivity().getWindowManager().getDefaultDisplay().getRotation(),
                  m_cameraId.equals( m_cameraIdFront ),
                  m_processor );

            m_processingImageReader.setOnImageAvailableListener( m_onImageAvailableListener,
                  m_backgroundHandler );
         }
      }
      else
      {
         Log.e( TAG, "Could not get new processor, using old one." );
      }
   }

   /**
    * Shows OK/Cancel confirmation dialog about camera permission.
    */
   public static class ConfirmationDialog extends DialogFragment
   {
      @NonNull
      @Override
      public Dialog onCreateDialog( Bundle savedInstanceState )
      {
         final Fragment parent = getParentFragment();
         return new AlertDialog.Builder( getActivity() )
               .setMessage( R.string.request_permission )
               .setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener()
               {
                  @Override
                  public void onClick( DialogInterface dialog, int which )
                  {
                     parent.requestPermissions( new String[]{ Manifest.permission.CAMERA },
                           REQUEST_CAMERA_PERMISSION );
                  }
               } )
               .setNegativeButton( android.R.string.cancel,
                     new DialogInterface.OnClickListener()
                     {
                        @Override
                        public void onClick( DialogInterface dialog, int which )
                        {
                           Activity activity = parent.getActivity();
                           if ( activity != null )
                           {
                              activity.finish();
                           }
                        }
                     } )
               .create();
      }
   }

   /**
    * Shows an error message dialog.
    */
   public static class ErrorDialog extends DialogFragment
   {
      private static final String ARG_MESSAGE = "message";

      public static ErrorDialog newInstance( String message )
      {
         ErrorDialog dialog = new ErrorDialog();
         Bundle args = new Bundle();
         args.putString( ARG_MESSAGE, message );
         dialog.setArguments( args );
         return dialog;
      }

      @NonNull
      @Override
      public Dialog onCreateDialog( Bundle savedInstanceState )
      {
         final Activity activity = getActivity();
         return new AlertDialog.Builder( activity )
               .setMessage( getArguments().getString( ARG_MESSAGE ) )
               .setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener()
               {
                  @Override
                  public void onClick( DialogInterface dialogInterface, int i )
                  {
                     activity.finish();
                  }
               } )
               .create();
      }

   }

   /**
    * Compares two {@code Size}s based on their areas.
    */
   static class CompareSizesByArea implements Comparator<Size>
   {
      @Override
      public int compare( Size lhs, Size rhs )
      {
         // We cast here to ensure the multiplications won't overflow
         return Long.signum( (long) lhs.getWidth() * lhs.getHeight() -
               (long) rhs.getWidth() * rhs.getHeight() );
      }

   }

   /**
    * This interface must be implemented by activities that contain this
    * fragment to allow an interaction in this fragment to be communicated
    * to the activity and potentially other fragments contained in that
    * activity.
    * <p>
    * See the Android Training lesson <a href=
    * "http://developer.android.com/training/basics/fragments/communicating.html"
    * >Communicating with Other Fragments</a> for more information.
    */
   public interface OnFragmentInteractionListener
   {
      // TODO: Update argument type and name
      void onFragmentInteraction( Uri uri );
   }

   public interface ProcessorProvider
   {
      Processor GetProcessor();
   }

}
