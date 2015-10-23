package de.codesourcery.iozone;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class Axis 
{
    private final Vector3 start=new Vector3();
    private final Vector3 end=new Vector3();
    
    public final List<String> labels = new ArrayList<>();
    
    public final Matrix4 modelMatrix = new Matrix4().idt();
    
    public Color labelColor = Color.BLACK;
    public Color axisLineColor = Color.BLACK;
    
    public static float INITIAL_FONT_SIZE = 12;
    public static float FONT_SCALING_FACTOR = 80000f;
    
    public final float length;
    public final String name;
    
    public Axis(String name,float length) 
    {
        this.length = length;
        this.start.x = -length/2f;
        this.end.x = length/2f;
        this.name = name;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    public void render(Matrix4 viewMatrix , Graphics2D gfx,Camera camera) 
    {
        // calculate matrix to get from object space to world space
        final Matrix4 modelView = viewMatrix.cpy().mul( modelMatrix );

        // convert start & end into world space
        final Vector3 startView = start.cpy();
        final Vector3 endView = end.cpy();
        
        startView.mul( modelView );
        endView.mul( modelView );
        
        final Vector3 stepSize = endView.cpy().sub( startView );
        stepSize.scl( 1f / labels.size());

        final Vector3 current = startView.cpy();
        
        final Vector3 tmp = new Vector3();
        
        gfx.setColor( labelColor );
        
        final Font oldFont = gfx.getFont();
        
        for ( int step = 0 ; step < labels.size() ; step++ ) 
        {
            tmp.set( current );
            final float avgDist = 1+current.dst( camera.position );
            
            final float scale = FONT_SCALING_FACTOR / (avgDist*avgDist);
            final float fontSize = (INITIAL_FONT_SIZE *  scale);
//            System.out.println("font size: "+fontSize);
            final Font labelFont = oldFont.deriveFont( Font.BOLD , fontSize );
            gfx.setFont( labelFont );
            
            worldToScreen( tmp , camera );
            
            gfx.drawString( labels.get( step ) , (int) tmp.x, (int) tmp.y );
            current.add( stepSize );
        }
        gfx.setFont( oldFont );
        
        // draw line
        gfx.setColor( axisLineColor );
        
        worldToScreen( startView , camera ); 
        worldToScreen( endView , camera ); 
        
        gfx.drawLine( (int) startView.x,(int) startView.y,(int) endView.x,(int) endView.y );
    }
    
    public static Vector3 worldToScreen(Vector3 worldCoords,Camera camera) 
    {
        final float viewportWidth=camera.viewportWidth;
        final float viewportHeight=camera.viewportHeight;
        final int viewportX = 0;
        final int viewportY = 0;
        
        worldCoords.prj( camera.projection );
        
        worldCoords.x = viewportWidth * (worldCoords.x + 1) / 2 + viewportX;
        worldCoords.y = viewportHeight * (worldCoords.y + 1) / 2 + viewportY;
        worldCoords.z = (worldCoords.z + 1) / 2;
        
        // OpenGL puts origin of screen space into bottom-left corner...        
        worldCoords.x = worldCoords.x + (int) (viewportWidth/2f);
        worldCoords.y = (int) (viewportHeight/2f) - worldCoords.y;
        
        return worldCoords;
    }
}
