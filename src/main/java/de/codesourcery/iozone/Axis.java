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
    
    public void render(Graphics2D gfx,Camera camera) 
    {
        // calculate matrix to get from object space to world space
        final Matrix4 modelView = camera.view.cpy();
        modelView.mul( modelMatrix );
        
        // convert start & end into world space
        final Vector3 startView = start.cpy();
        final Vector3 endView = end.cpy();
        
        startView.mul( modelView );
        endView.mul( modelView );
        
        System.out.println("Rendering "+name+" with length "+length+" that goes from "+startView+" to "+endView);
        
        final Vector3 stepSize = endView.cpy().sub( startView );
        stepSize.scl( 1f / labels.size());

        final Vector3 current = startView.cpy();
        
        final Vector3 tmp = new Vector3();
        
        float viewportHeight = camera.viewportHeight;
        float viewportWidth = camera.viewportWidth;
        gfx.setColor( labelColor );
        
        final Font oldFont = gfx.getFont();
        final Font labelFont = oldFont.deriveFont( Font.BOLD , (float) 13 );
        gfx.setFont( labelFont );
        
        for ( int step = 0 ; step < labels.size() ; step++ ) 
        {
            tmp.set( current );
            perspective( tmp , camera );
            
            // OpenGL puts origin of screen space into bottom-left corner...
            final int scrX = (int) (tmp.x + viewportWidth/2f);
            final int scrY = (int) (viewportHeight/2f - tmp.y);
            gfx.drawString( labels.get( step ) , scrX, scrY );
            current.add( stepSize );
        }
        gfx.setFont( oldFont );
        
        // draw line
        gfx.setColor( axisLineColor );
        
        perspective( startView , camera ); 
        perspective( endView , camera ); 
        
        // OpenGL puts origin of screen space into bottom-left corner...        
        final int scrX1 = (int) (startView.x + viewportWidth/2f);
        final int scrY1 = (int) (viewportHeight/2f - startView.y);
        final int scrX2 = (int) (endView.x + viewportWidth/2f);
        final int scrY2 = (int) (viewportHeight/2f - endView.y);
        
        gfx.drawLine( scrX1,scrY1,scrX2,scrY2 );
    }
    
    private Vector3 perspective(Vector3 worldCoords,Camera camera) 
    {
        final float viewportWidth=camera.viewportWidth;
        final float viewportHeight=camera.viewportHeight;
        final int viewportX = 0;
        final int viewportY = 0;
        
        worldCoords.prj( camera.projection );
        
        worldCoords.x = viewportWidth * (worldCoords.x + 1) / 2 + viewportX;
        worldCoords.y = viewportHeight * (worldCoords.y + 1) / 2 + viewportY;
        worldCoords.z = (worldCoords.z + 1) / 2;
        return worldCoords;
    }
}
