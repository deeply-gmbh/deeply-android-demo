package de.deeply.demo.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.util.HashMap;
import java.util.Map;

import de.deeply.Object;
import de.deeply.Marker;
import de.deeply.Region;


/**
 * Created by goofy on 04.01.16.
 */
public class ObjectGraphic implements Graphic
{
   private static final float STROKE_WIDTH_SP = 2.0f;
   private static final float TEXT_SIZE_SP = 14.0f;
   private static final float TEXT_LINE_HEIGHT_DP = 18.0f;
   private static final float RATING_WIDTH_DP = 100.0f;

   private final Object m_object;
   private final int m_imageWidth;
   private final int m_imageHeight;
   private final RectF m_viewport;
   private final DisplayMetrics m_metrics;

   private Paint m_regionPaint = new Paint();
   private Paint m_textPaint = new Paint();
   private Paint m_ratingBoundboxPaint = new Paint();
   private Paint m_ratingBarPaint = new Paint();

   private final Map<String,String> m_settings = new HashMap<String,String>();
   private Map<String,String> m_attributes = new HashMap<String,String>();
   private Map<String,Float> m_ratings = new HashMap<String,Float>();
   private Map<String,Marker> m_markers = new HashMap<String,Marker>();

   private final float m_strokeWitdh;

   public ObjectGraphic(Object object, int imageWidth, int imageHeight, RectF viewport,
                        DisplayMetrics metrics )
   {
      m_object = object;
      m_imageWidth = imageWidth;
      m_imageHeight = imageHeight;
      m_viewport = viewport;
      m_metrics = metrics;

      m_strokeWitdh = sp2px( STROKE_WIDTH_SP );

      m_regionPaint.setColor( Color.WHITE );
      m_regionPaint.setStyle( Paint.Style.STROKE );
      m_regionPaint.setStrokeWidth( m_strokeWitdh );
      m_regionPaint.setAntiAlias( true );

      float textSize = sp2px( TEXT_SIZE_SP );

      m_textPaint.setColor( Color.WHITE );
      m_textPaint.setTextSize( textSize );
      m_textPaint.setAntiAlias( true );

      m_ratingBoundboxPaint.set( m_regionPaint );
      m_ratingBoundboxPaint.setColor( Color.parseColor( "#88ffffff") );
      m_ratingBoundboxPaint.setStyle( Paint.Style.FILL );

      m_ratingBarPaint.setColor( Color.parseColor( "#bd68e2") );
      m_ratingBarPaint.setStyle( Paint.Style.FILL );

      Map<String,String> allAttributes = new HashMap<String,String>();

      // Store attributes and settings
      for ( int a = 0; a < object.GetAttributeCount(); ++a )
      {
         String key = object.GetAttributeKey( a );
         String attr = object.GetAttribute( a );

         if ( key.startsWith( "_" ) )
         {
            m_settings.put( key, attr );
         }
         else
         {
            allAttributes.put( key, attr );
         }
      }

      for( Map.Entry<String, String> s : m_settings.entrySet() )
      {
         String k = s.getKey();
         if ( k.startsWith( "_AttributeDisplay" ) )
         {
            if ( k.equals( "_AttributeDisplay" ) && s.getValue().equals( "On" ) )
            {
               m_attributes = allAttributes;
            }
            else
            {
               String a = k.substring( k.lastIndexOf( "_" ) + 1 );

               if ( s.getValue().equals( "On" ) && allAttributes.containsKey( a ) )
               {
                  m_attributes.put( a, allAttributes.get( a ) );
               }
               else if ( s.getValue().equals( "Off" ) && m_attributes.containsKey( a ) )
               {
                  m_attributes.remove( a );
               }
            }
         }
      }

      Map<String,Float> allRatings = new HashMap<String,Float>();

      // Store attributes and settings
      for ( int r = 0; r < object.GetRatingCount(); ++r )
      {
         allRatings.put( object.GetRatingKey( r ), object.GetRating( r ) );
      }

      for( Map.Entry<String, String> s : m_settings.entrySet() )
      {
         String k = s.getKey();
         if ( k.startsWith( "_RatingDisplay" ) )
         {
            if ( k.equals( "_RatingDisplay" ) && s.getValue().equals( "On" ) )
            {
               m_ratings = allRatings;
            }
            else
            {
               String r = k.substring( k.lastIndexOf( "_" ) + 1 );

               if ( s.getValue().equals( "On" ) && allRatings.containsKey( r ) )
               {
                  m_ratings.put( r, allRatings.get( r ) );
               }
               else if ( s.getValue().equals( "Off" ) && m_ratings.containsKey( r ) )
               {
                  m_ratings.remove( r );
               }
            }
         }
      }

      m_markers.clear();

      for( Map.Entry<String, String> s : m_settings.entrySet() )
      {
         String k = s.getKey();
         if ( k.startsWith( "_MarkerDisplay" ) )
         {
            if ( k.equals( "_MarkerDisplay" ) && s.getValue().equals( "On" ) )
            {
               for ( int m = 0; m < object.GetMarkerCount(); ++m )
               {
                  m_markers.put( object.GetMarkerKey( m ), object.GetMarker( m ) );
               }
            }
            else
            {
               String m = k.substring( k.lastIndexOf( "_" ) + 1 );

               if ( s.getValue().equals( "On" ) && !m_markers.containsKey( m ) )
               {
                  Marker marker = object.GetMarkerByKey( m );

                  if ( marker != null )
                  {
                     m_markers.put( m, marker );
                  }
               }
               else if ( s.getValue().equals( "Off" ) && m_markers.containsKey( m ) )
               {
                  m_markers.remove( m );
               }
            }
         }
      }

   }

   @Override
   public void draw( Canvas canvas )
   {
      // TODO: Align Values to largest Key
      // TODO: Check drawing of borders -> size etc.
      Region region = m_object.GetRegion();

      float scaleX = m_viewport.width() / m_imageWidth;
      float scaleY = m_viewport.height() / m_imageHeight;

      float offsetX = m_viewport.left;
      float offsetY = m_viewport.top;

      float top = scaleY * region.getTop() + offsetY;
      float left = scaleX * region.getLeft() + offsetX;
      float right = scaleX * region.getRight() + offsetX;
      float bottom = scaleY * region.getBottom() + offsetY;

      canvas.drawRoundRect( new RectF( left, top, right, bottom ),
                             5.0f, 5.0f, m_regionPaint );

      float lineHeight = dp2px( TEXT_LINE_HEIGHT_DP );
      float ratingHeight = sp2px( TEXT_SIZE_SP - 2.0f );

      float baseline = bottom + lineHeight;

      float valueOffset = 0;

      // Draw keys first and get need width for keys
      Rect textBound = new Rect();

      for( String k : m_attributes.keySet() )
      {
         m_textPaint.getTextBounds( k, 0, k.length(), textBound );

         canvas.drawText( k, left, baseline, m_textPaint );
         baseline += lineHeight;

         valueOffset = Math.max( valueOffset, textBound.width() );
      }

      for ( String k : m_ratings.keySet() )
      {
         m_textPaint.getTextBounds( k, 0, k.length(), textBound );

         canvas.drawText( k, left, baseline, m_textPaint );
         baseline += lineHeight;

         valueOffset = Math.max( valueOffset, textBound.width() );
      }

      // Draw values with same y start and new offset for x
      baseline = bottom + lineHeight;
      left += valueOffset + dp2px( 5.0f);

      for( String a : m_attributes.values() )
      {
         canvas.drawText( a, left, baseline, m_textPaint );
         baseline += lineHeight;
      }

      float ratingWidth = dp2px( RATING_WIDTH_DP );

      for ( Float r : m_ratings.values() )
      {
         float barLeft = left;
         float barTop = baseline - ratingHeight;
         float barRight = barLeft + ratingWidth;
         float barBottom = baseline;

         float rating = ( ratingWidth / 100.0f ) * Math.min( r, 100.0f );
         float ratingRight = barLeft + rating;

         canvas.drawRoundRect( new RectF( barLeft, barTop, barRight, barBottom ), 3.0f, 3.0f, m_ratingBoundboxPaint );
         canvas.drawRoundRect( new RectF( barLeft, barTop, ratingRight, barBottom ), 3.0f, 3.0f, m_ratingBarPaint );

         baseline += lineHeight;
      }

      // Draw markers
      for ( Marker m : m_markers.values() )
      {
         float x = scaleX * m.getX() + offsetX;
         float y = scaleY * m.getY() + offsetY;

         canvas.drawPoint( x, y, m_regionPaint );
      }
   }

   private float sp2px( float scaledIndependentPixel )
   {
      // TODO: Correct solution? http://stackoverflow.com/a/1102454
      return TypedValue
                .applyDimension( TypedValue.COMPLEX_UNIT_SP, scaledIndependentPixel, m_metrics );
   }

   private float dp2px( float densityIndependentPixel )
   {
      // TODO: Correct solution? http://stackoverflow.com/a/1102454
      return TypedValue
                .applyDimension( TypedValue.COMPLEX_UNIT_DIP, densityIndependentPixel, m_metrics );
   }
}
