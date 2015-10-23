package de.codesourcery.iozone;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class Mesh
{
	protected static final Vector3 Y_AXIS = new Vector3(0,1,0);

	static {
		System.loadLibrary("gdx64");
	}

	private final float[] coords;
	private final float[] tmpCoords;

	public final float quadWidth;
	public final float quadHeight;
	
	private final int xSize;
	private final int zSize;
	public final String name;
	
	public float minY;
	public float maxY;
	
	public Color defaultColor = Color.WHITE;
	
	public final Matrix4 modelMatrix = new Matrix4().idt();
	
	public interface DataProvider 
	{
	    public float getCellValue(int xIndex,int zIndex);
	}

	public Mesh(String name,int xSize,int zSize,float quadWidth,float quadHeight)
	{
	    this.name = name;
		this.xSize = xSize;
		this.zSize = zSize;
		
		this.quadWidth = quadWidth;
		this.quadHeight = quadHeight;

		this.coords = new float[ xSize*zSize*3 ];
		this.tmpCoords = new float[ coords.length ];

		float vx = -(xSize/2)*quadWidth;
		for ( int x = 0 ; x < xSize ; x++ , vx += quadWidth )
		{
			float vz = -(zSize/2)*quadHeight;
			for ( int z = 0 ; z < zSize ; z++ , vz += quadHeight )
			{
				final int ptr = arrayOffset( x , z );
				coords[ptr]=vx; // x
				coords[ptr+1]= 0;
				coords[ptr+2]=vz;  // z
			}
		}
	}
	
	public void populate(DataProvider provider) 
	{
	    for ( int x = 0 ; x < xSize ; x++ ) 
	    {
	        for ( int z = 0 ; z < zSize ; z++ ) 
	        {
	            coords[ arrayOffset( x ,  z )+1 ] = provider.getCellValue( x ,  z );
	        }
	    }
	    scanData();
	}
	
	public void scanData() 
	{
       float min = coords[1];
       float max = min;
        for ( int ptr = 0 ; ptr < coords.length ; ptr += 3 ) {
            float value = coords[ptr+1];
            if ( value < min ) {
                min = value;
            }
            if ( value > max ) {
                max = value;
            }
        }
        this.minY = min;
        this.maxY = max;
	}
	
	public float getMinY() 
	{
	    return minY;
	}
	
    public float getMaxY() 
    {
        return maxY;
    }	
    
    public void scaleTo(float yMin,float yMax) 
    {
        float currentRange = getMaxY() - getMinY();
        float desiredRange = yMax - yMin;
        float scale = desiredRange/currentRange;
        float yOffset = yMin - getMinY();
        
        for ( int ptr = 0 ; ptr < coords.length ; ptr += 3 ) 
        {
            coords[ptr+1] = (coords[ptr+1] + yOffset ) * scale; 
        }
    }
	
	public float width() 
	{
	    return (xSize-1)*quadWidth;
	}
	
    public float height() 
    {
        return (zSize-1)*quadHeight;
    }	
    
    public float getAbsYHeight() {
        return Math.abs( getMinY() ) + Math.abs( getMaxY() );
    }
	
	public int sizeInQuads() {
	    return xSize*zSize;
	}

	private int arrayOffset(int xIndex,int zIndex)  {
		return 3*( zIndex * xSize + xIndex ); // 3 floats per vector
	}

	public void setValue(int xIndex,int zIndex,float yValue)
	{
		this.coords[ arrayOffset(xIndex,zIndex) + 1] = yValue;
	}

	private void readVector3(final float[] array, int xIndex,int zIndex,Vector3 out)
	{
		final int offset = arrayOffset(xIndex,zIndex); 
		out.x = array[offset];
		out.y = array[offset+1];
		out.z = array[offset+2];
	}

	public void toQuads(Matrix4 viewMatrix , Camera camera,List<Quad> out, boolean backfaceCulling,boolean isDataPlane)
	{
	    System.arraycopy( coords , 0 , tmpCoords , 0 , coords.length );
	    
	    Matrix4 tmp = viewMatrix.cpy();
	    tmp.mul( modelMatrix );
	    
	    Matrix4.mulVec(tmp.val , tmpCoords , 0 , coords.length/3 , 3 );
	    
		for ( int z = 0 ; z < zSize-1 ; z++ )
		{
			for ( int x = 0 ; x < xSize-1 ; x++ )
			{
				/*
				 *   3+-------+2
				 *    |       |
				 *    |       |
				 *   0+-------+1
				 */
				final Quad quad = new Quad();
				quad.color = defaultColor;
				final int offset0 = arrayOffset( x   , z );
				final int offset1 = arrayOffset( x+1 , z );
				final int offset2 = arrayOffset( x+1 , z+1 );
				final int offset3 = arrayOffset( x   , z+1 );
				quad.avgDataValue = (coords[offset0+1] + coords[offset1+1] + coords[offset2+1] + coords[offset3+1])/4f;
				
				readVector3( tmpCoords , x   , z   , quad.c0  );
				readVector3( tmpCoords , x+1 , z   , quad.c1 );
				readVector3( tmpCoords , x+1 , z+1 , quad.c2 );
				readVector3( tmpCoords , x   , z+1 , quad.c3 );
//				if ( isDataPlane ) 
//				{
//    				final float avgYCoordinate1 = (quad.c0.y + quad.c1.y )/2f;
//    				final float avgYCoordinate2 = (quad.c2.y + quad.c3.y)/2f;
//    				quad.c0.y = quad.c1.y = avgYCoordinate1;
//    				quad.c2.y = quad.c3.y = avgYCoordinate2;
//				}
				quad.update( camera );
				if ( ! backfaceCulling || quad.visible ) {
				    out.add( quad );
				}
			}
		}
	}
	
	public static void renderQuads(List<Quad> quads,Camera camera,Graphics2D gfx)
	{
		// sort back-to-front and render
		Collections.sort( quads );

		// render along view direction
		final int[] vx = new int[4];
		final int[] vz = new int[4];
		for ( Quad quad : quads )
		{
			Axis.worldToScreen( quad.c0 , camera );
			Axis.worldToScreen( quad.c1 , camera );
			Axis.worldToScreen( quad.c2 , camera );
			Axis.worldToScreen( quad.c3 , camera );
			
			vx[0] = (int) quad.c0.x;
			vz[0] = (int) quad.c0.y;

			vx[1] = (int) quad.c1.x;
			vz[1] = (int) quad.c1.y;

			vx[2] = (int) quad.c2.x;
			vz[2] = (int) quad.c2.y;

			vx[3] = (int) quad.c3.x;
			vz[3] = (int) quad.c3.y;
			
			gfx.setColor( quad.color );
			gfx.fillPolygon( vx , vz , 4 );
			
			gfx.setColor( Color.BLUE);
			gfx.drawPolygon( vx , vz , 4 );
		}
	}

	protected static final class Quad implements Comparable<Quad>
	{
		public final Vector3 c0 = new Vector3();
		public final Vector3 c1 = new Vector3();
		public final Vector3 c2 = new Vector3();
		public final Vector3 c3 = new Vector3();
		public float dist;
		public float angleRad;
		public Color color;
		public boolean visible;
		
		public float avgDataValue;

		public void update(Camera camera)
		{
			final Vector3 center = c0.cpy();
			center.add( c1 );
			center.add( c2 );
			center.add( c3 );
			center.scl(1/4f);
			
			// calculate surface normal for visibility check
			Vector3 vp1 = c0.cpy().sub( c1 );
			Vector3 vp2 = c2.cpy().sub( c1 );
			
			Vector3 surfaceNormal = vp1.crs( vp2 ).nor();
			
			float angle = center.cpy().sub( camera.position ).dot( surfaceNormal );
			visible = angle <= 0;
			
			final Vector3 planeNormal = camera.direction;
			final Vector3 pointOnPlane = camera.position;
			
//			dist = planeNormal.dot( center.sub( pointOnPlane ) );
			dist = center.cpy().sub( camera.position ).len();

			// calculate angle between normal and Y axis
			Vector3 v1 = c0.cpy().sub( c1 );
			Vector3 v2 = c2.cpy().sub( c1 );
			final Vector3 normal = v1.crs( v2 ).nor();

			angleRad = (float) Math.acos( normal.dot( Y_AXIS ) );
		}

		@Override
		public int compareTo(Quad o)
		{
			if ( this.dist > o.dist ) {
				return -1;
			}
			if ( this.dist < o.dist ) {
				return 1;
			}
			return 0;
		}
	}
}