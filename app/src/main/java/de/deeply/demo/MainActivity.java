package de.deeply.demo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.deeply.Deeply;
import de.deeply.ExtendableGallery;
import de.deeply.FaceProcessorBuilder;
import de.deeply.Image;
import de.deeply.LogLevel;
import de.deeply.Marker;
import de.deeply.Processor;
import de.deeplyapp.demo.R;


public class MainActivity extends AppCompatActivity
      implements CameraFragment.ProcessorProvider
{
   private static final String TAG = "MainActivity";
   public static final String GENDER = "Gender";
   public static final String AGE = "Age";
   public static final String HAPPY = "Happy";
   public static final String SURPRISED = "Surprised";
   public static final String ANGRY = "Angry";
   public static final String SAD = "Sad";
   public static final String EYES = "Eyes";
   public static final String MOUTH = "Mouth";
   public static final String SMALL_FACES = "SmallFaces";
   public static final String PROFILE = "Profile";
   public static final String POSE = "POSE";
   private static final int RC_SIGN_IN = 1;

   static
   {
      System.loadLibrary( "deeply-java" );
   }

   private ExtendableGallery m_gallery;
   private Processor m_processor;

   @Override
   protected void onCreate( Bundle savedInstanceState )
   {
      super.onCreate( savedInstanceState );

      Deeply.SetLogLevel( LogLevel.Debug );

      String internStorage = getFilesDir().toString();

      m_gallery = EnrollUsers( internStorage );

      m_processor = new FaceProcessorBuilder()
                           .VideoAnalysis()
                           .UseThreads( 2 )
                           .DetectionType( "Face.Front" )
                           .MinFaceSize( 40.0f * 40.0f )
                           .EstimatePose( true )
                           .SearchEyes( true )
                           .AnalyzeGender( true )
                           .AnalyzeAge( true, true  )
                           .AnalyzeHappy( true )
                           .RecognizeFaces( m_gallery )
                           .Extension( "demo = true; "  +
                                       "function Extend(e) return e end" )
                           .Build();

      setContentView( R.layout.activity_main );

      if ( null == savedInstanceState )
      {
         getSupportFragmentManager().beginTransaction()
               .replace( R.id.container, CameraFragment.newInstance() )
               .commit();
      }

      //Toolbar toolbar = (Toolbar) findViewById( R.id.toolbar );
      //setSupportActionBar( toolbar );
   }

   @NonNull
   private ExtendableGallery EnrollUsers( String internStorage )
   {
      File teamImage = new File( internStorage + "/team.jpg");

      if ( !teamImage.exists() )
      {
         WriteFileToPrivateStorage( R.drawable.team, "team.jpg" );
      }

      Image team = Deeply.LoadImage( teamImage.getAbsolutePath() );

      ExtendableGallery gallery = ExtendableGallery.Create();

      Marker ele = new Marker();
      ele.setX( 201.7f );
      ele.setY( 340.9f );

      Marker ere = new Marker();
      ere.setX( 152.1f );
      ere.setY( 338.0f );

      gallery.AddSample( "Esther", ele, ere, team );

      Marker jle = new Marker();
      jle.setX( 529.4f );
      jle.setY( 349.7f );

      Marker jre = new Marker();
      jre.setX( 472.8f );
      jre.setY( 350.6f );

      gallery.AddSample( "Julian", jle, jre, team );

      Marker tle = new Marker();
      tle.setX( 882.6f );
      tle.setY( 336.0f );

      Marker tre = new Marker();
      tre.setX( 734.7f );
      tre.setY( 334.1f );

      gallery.AddSample( "Tobias", tle, tre, team );
      return gallery;
   }

   @Override
   public void onStart()
   {
      super.onStart();
   }

   @Override
   public Processor GetProcessor()
   {
      return m_processor;
   }


   private void WriteFileToPrivateStorage( int fromFile, String toFile )
   {
      InputStream is = getResources().openRawResource( fromFile );
      int bytes_read = 0;
      byte[] buffer = new byte[4096];

      try
      {
         FileOutputStream fos = openFileOutput( toFile, Context.MODE_PRIVATE );

         while ( (bytes_read = is.read( buffer )) > 0 )
         {
            fos.write( buffer, 0, bytes_read ); // write

         }

         fos.flush();
         fos.close();
         is.close();
      }
      catch ( FileNotFoundException e )
      {
         e.printStackTrace();
      }
      catch ( IOException e )
      {
         e.printStackTrace();
      }
   }
}
