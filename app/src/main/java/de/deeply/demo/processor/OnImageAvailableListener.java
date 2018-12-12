package de.deeplyapp.demo.processor;

import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

import de.deeply.Deeply;
import de.deeply.Image;
import de.deeply.Processor;


/**
 * Created by rufto on 28.02.18.
 */

public class OnImageAvailableListener implements ImageReader.OnImageAvailableListener
{
   private enum Rotation
   {
      ROTATION_0,
      ROTATION_90,
      ROTATION_180,
      ROTATION_270
   }

   private static final String TAG = "OnImageAvailableListnr";

   private Rotation m_r;
   private final boolean m_flip;
   private final Processor m_processor;
   private Image m_image = null;

   public OnImageAvailableListener( int sensorOrientation, int dispalyRotation, boolean flip, Processor processor )
   {
      // Note: portrait factor is 0 rotation for the device, but 90 or 270 for the sensor.
      switch ( dispalyRotation )
      {
         case Surface.ROTATION_0:
            m_r = Rotation.ROTATION_270;

            if ( sensorOrientation == 270 )
            {
               m_r = Rotation.ROTATION_90;
            }

            break;
         case Surface.ROTATION_180:
            m_r = Rotation.ROTATION_90;

            if ( sensorOrientation == 90 )
            {
               m_r = Rotation.ROTATION_270;
            }

            break;
         case Surface.ROTATION_270:
            m_r = Rotation.ROTATION_180;

            if ( sensorOrientation == 180 )
            {
               m_r = Rotation.ROTATION_0;
            }

            break;
         case Surface.ROTATION_90:
         default:
            m_r = Rotation.ROTATION_0;

            if ( sensorOrientation == 0 )
            {
               m_r = Rotation.ROTATION_180;
            }
      }

      m_flip = flip;
      m_processor = processor;
   }

   @Override
   public void onImageAvailable( ImageReader reader )
   {
      if ( reader.getImageFormat() != ImageFormat.YUV_420_888 )
      {
         Log.e( TAG, "Currently only supporting YUV_420_888 format, image wont' be processed" );
         return;
      }

      android.media.Image yuv = reader.acquireLatestImage();

      if ( yuv == null )
      {
         //Log.e( TAG, "Acquiring latest image from ImageReader failed, won't process" );
         return;
      }

      android.media.Image.Plane yPlane = yuv.getPlanes()[0];

      int sw = yuv.getWidth();
      int sh = yuv.getHeight();
      int sp = 1;
      int spif = yPlane.getPixelStride();
      int slif = yPlane.getRowStride();
      int splf = 1;

      int o = 0;
      int w = 0;
      int h = 0;
      int p = sp;
      int pif = 0;
      int lif = 0;
      int plf = 0;

      switch ( m_r )
      {
         case ROTATION_90:
            o   = sw * spif - spif;
            w   = sh;
            h   = sw;
            pif = slif;
            lif = -spif;
            plf = splf;
            break;
         case ROTATION_180:
            o   = sw * sh * sp - spif;
            w   = sw;
            h   = sh;
            pif = -spif;
            lif = -slif;
            plf = splf;
            break;
         case ROTATION_270:
            o   = slif*(sh-1);
            w   = sh;
            h   = sw;
            pif = -slif;
            lif = spif;
            plf = splf;
            break;
         case ROTATION_0:
         default:
            o   = 0;
            w   = sw;
            h   = sh;
            pif = spif;
            lif = slif;
            plf = splf;
      }

      if ( m_image == null )
      {
         if ( !m_flip )
         {
            // allocate native memory with memory layout of src image
            m_image = Deeply.CreateImage( o, w, h, p, pif, lif, plf, Image.ColorSpace.GRAYSCALE );
         }
         else
         {
            // for flipped images we can allocate with the standard layout.
            m_image = Deeply.CreateImage( 0, w, h, p, 1, w, 1, Image.ColorSpace.GRAYSCALE );
         }
      }

      try
      {
         if ( !m_flip )
         {
            m_image.Data().put( yPlane.getBuffer() ); // direct mem copy
         }
         else
         {
            ByteBuffer src = yPlane.getBuffer();
            ByteBuffer trg = m_image.Data();
            int to   = m_image.GetOffset();
            int tpif = m_image.GetPixelFeed();
            int tlif = m_image.GetLineFeed();

            for ( int y = 0 ; y < h; ++y )
            {
               for ( int x = 0; x < w; ++x )
               {
                  trg.put( to + x*tpif + y*tlif, // position
                            src.get( o + ((w-1)-x)*pif + y*lif ) );  // get flipped position
               }
            }
         }

         m_processor.OnImage( m_image );
      }
      catch ( IllegalStateException e )
      {
         Log.e( TAG, e.toString() ); // TODO: fix this! The capture session and image reader get closed before the listener was finished??
      }
      finally
      {
         yuv.close();
      }
   }
}
