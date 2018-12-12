package de.deeply.demo.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

import de.deeply.Content;

/**
 * Created by goofy on 04.01.16.
 */
public class ContentView extends View
{
   private RectF m_viewport = null;
   private Object m_lock = new Object();

   private Set<ObjectGraphic> m_objects = new HashSet<ObjectGraphic>();

   public class Processor // extends ContentProcessor // TODO: how fit best to deeply-sdk
   {
      private ContentView m_view;

      private Processor(ContentView view )
      {
         m_view = view;
      }

      public void Process( Content c )
      {
         if ( c != null )
         {
            int w = Integer.valueOf( c.GetInfoByKey( "ImageWidth" ) );
            int h = Integer.valueOf( c.GetInfoByKey( "ImageHeight" ) );

            m_view.clear();

            for ( int i = 0; i < c.GetObjectCount(); ++i )
            {
               m_view.add( new ObjectGraphic( c.GetObject( i ), w, h, m_view.GetViewport(),
                                                    m_view.getResources().getDisplayMetrics() ) );
            }
         }
      }
   }

   public ContentView( Context context, AttributeSet attrs )
   {
      super( context, attrs );
   }

   public Processor CreateProcessor()
   {
      return new Processor( this );
   }

   public void SetViewport(RectF viewport)
   {
      m_viewport = viewport;
   }

   public RectF GetViewport()
   {
      if ( m_viewport != null )
      {
         return m_viewport;
      }
      else
      {
         return new RectF( getX(), getY(), getRight(), getBottom() );
      }

   }

   /**
    * Removes all graphics from the overlay.
    */
   public void clear()
   {
      synchronized ( m_lock )
      {
         m_objects.clear();
      }
      postInvalidate();
   }

   /**
    * Adds a object to the overlay.
    */
   public void add( ObjectGraphic object )
   {
      synchronized ( m_lock )
      {
         m_objects.add( object );
      }
      postInvalidate();
   }

   /**
    * Removes a object from the overlay.
    */
   public void remove( ObjectGraphic object )
   {
      synchronized ( m_lock )
      {
         m_objects.remove( object );
      }
   }

   /**
    * Draws the overlay with its associated graphic objects.
    */
   @Override
   protected void onDraw( Canvas canvas )
   {
      super.onDraw( canvas );

      synchronized ( m_lock )
      {
         for ( ObjectGraphic o : m_objects )
         {
            o.draw( canvas );
         }
      }
   }
}
