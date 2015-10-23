package de.codesourcery.iozone;

import java.awt.Color;
import java.awt.Graphics2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.iozone.IOZoneFileParser.FileEntry;
import de.codesourcery.iozone.IOZoneFileParser.IOZoneReport;
import de.codesourcery.iozone.Mesh.Quad;

public class IOZone3DChart 
{
    protected static final float DATA_Y_MIN_VALUE = 0;
    protected static final float DATA_Y_MAX_VALUE = 40;
    
    protected static final int quadWidth = 10;
    protected static final int quadHeight = 5;
    
    protected static final int meshYSize = 9;
    
    private final Mesh plotData;
    private final Mesh groundPlane;
    private final Mesh yAxisPlane;
    private final Mesh xAxisPlane;

    private final Axis xAxis;
    private final Axis yAxis;
    private final Axis zAxis;
    
    public final Matrix4 modelMatrix = new Matrix4().idt();
    
    private final IOZoneReport report;
    
    private final Color[] yColors = new Color[ meshYSize-1 ];
    private final Interval[] yIntervals = new Interval[meshYSize-1];
    
    protected static final class Interval {
        
        private final float min;
        private final float max;
        
        public Interval(float min,float max) {
            if ( min > max )  {
                throw new IllegalArgumentException();
            }
            this.min = min;
            this.max = max;
        }
        public boolean contains( float v) {
            return min <= v & v < max;
        }
    }
    
    @Override
    public String toString() {
        return "3d chart [ "+report.reportName+" ] @ \n"+modelMatrix;
    }
    
    public Color generateRandomColor(Random random,Color mix) 
    {
        int red = random.nextInt(256);
        int green = random.nextInt(256);
        int blue = random.nextInt(256);

        // mix the color
        red = (red + mix.getRed()) / 2;
        green = (green + mix.getGreen()) / 2;
        blue = (blue + mix.getBlue()) / 2;
        return new Color(red, green, blue);
    }
    
    public IOZone3DChart(IOZoneReport report) 
    {
        this.report = report;
        
        final float groundPlaneYOffset = -0.1f; // Y distance groundplane <-> plot plane             
        final float magicYOffset = 30-groundPlaneYOffset; // TODO: Trial'n'error ... how is this computed ??

        int maxLen = 0;
        for ( int fileSize : report.getFileSizes() ) {
            FileEntry fileEntry = report.getFileEntry( fileSize );
            if ( fileEntry.values.length > maxLen ) {
                maxLen = fileEntry.values.length;
            }
        }
        
        final long seed = System.currentTimeMillis();
        System.out.println("SEED: 0x"+Long.toHexString( seed ) );
        final Random rnd = new Random(seed);
        
        Color mix = new Color( 0.0f , 0.0f, 0.6f );
        
        float yDataStartValue = DATA_Y_MIN_VALUE;
        float yDataStep = (DATA_Y_MAX_VALUE - DATA_Y_MIN_VALUE ) / ( meshYSize-1);
        for ( int i = 0 ; i < meshYSize-1 ; i++ ) 
        {
            yColors[i] = generateRandomColor(rnd,mix);
            yIntervals[i] = new Interval( yDataStartValue+(i*yDataStep) , yDataStartValue+((i+1)*yDataStep ) );
        }
        
        final List<Integer> fileSizes = report.getFileSizes();
        fileSizes.sort( Integer::compareTo );
        
        final int meshXSize = fileSizes.size();

        final int meshZSize = maxLen;

        // setup plot plane
        plotData = new Mesh( "plot",meshXSize, meshZSize , quadWidth , quadHeight );

        plotData.populate( (x,z) ->  
        {
            final FileEntry entry = report.getFileEntry( fileSizes.get( x ) );
            return z < entry.values.length ? entry.values[z] : 0;
        });
        
        plotData.scaleTo( DATA_Y_MIN_VALUE ,  DATA_Y_MAX_VALUE );
        
        final float yGroundPlane = plotData.getMinY() + groundPlaneYOffset;

        // setup X Axis plane
        xAxisPlane = new Mesh( "X plane",meshXSize, meshZSize , quadWidth , quadHeight );
//        xAxisPlane.modelMatrix.translate( 0 , magicYOffset , -plotData.height()/2f );
        xAxisPlane.modelMatrix.translate( 0 , plotData.height()/2f , -plotData.height()/2f );
        xAxisPlane.modelMatrix.rotate( new Vector3( 1, 0, 0 ) , 90 );

        // setup y Axis plane
        yAxisPlane = new Mesh( "Y plane",meshXSize, meshZSize , quadWidth , quadHeight );
        yAxisPlane.modelMatrix.translate( -plotData.width()/2f , magicYOffset , 0 );
        yAxisPlane.modelMatrix.rotate( new Vector3( 0, 0, 1 ) , 90 );

        // setup ground plane
        groundPlane = new Mesh( "ground",meshXSize, meshZSize , quadWidth , quadHeight );
        groundPlane.modelMatrix.setToTranslation( 0, yGroundPlane , 0 );

        // x axis
        xAxis = new Axis( "X axis", (meshXSize-1) * quadWidth );
        xAxis.modelMatrix.translate( 0 , yGroundPlane , (meshZSize-1)*quadHeight/2f );
        xAxis.axisLineColor = Color.MAGENTA;

        for ( int i = 0 ; i < meshXSize-1 ; i++ ) 
        {
            xAxis.labels.add( fileSizes.get(i)+"k" );
        }   

        // y axis
        yAxis = new Axis("Y axis", (meshXSize-1) * quadWidth );
        yAxis.modelMatrix.translate( -plotData.width()/2f , magicYOffset , plotData.height()/2f );
        yAxis.modelMatrix.rotate( new Vector3( 0, 0, 1 ) , 90 );
        yAxis.axisLineColor = Color.GREEN;

        final float maxY = plotData.getMaxY();
        final float minY = plotData.getMinY();
        final DecimalFormat DF2 = new DecimalFormat("##########0");
        float step = (maxY-minY) / (meshXSize-3);
        float value = minY;
        final DecimalFormat DF = new DecimalFormat("#####0");
        for ( int i = 0 ; i < meshXSize-1 ; i++ , value += step ) 
        {
            yAxis.labels.add( DF.format( value / 1024 )+" kb/s" );
        }

        // z axis
        zAxis = new Axis("Z axis", (meshZSize-1) * quadHeight );
        zAxis.modelMatrix.translate( plotData.width()/2f ,  yGroundPlane , 0);
        zAxis.modelMatrix.rotate( new Vector3( 0, 1, 0 ) , 90 );            
        zAxis.axisLineColor = Color.RED;

        for ( int i = 0 ; i < meshZSize-1 ; i++ ) 
        {
            zAxis.labels.add( report.recordLengths[ i]+"" );
        }         
    }
    
    public void toQuads(PerspectiveCamera camera, Graphics2D gfx,List<Quad> result) 
    {
        final Matrix4 cameraViewMatrix =  camera.view.cpy().mul( modelMatrix );
        
        final List<Quad> tmpList = new ArrayList<>( plotData.sizeInQuads() );
        plotData.toQuads( cameraViewMatrix , camera , tmpList , false , true );
        for ( Quad q : tmpList ) 
        {
            for ( int idx = 0 ; idx < yIntervals.length ; idx++ ) 
            {
                if ( yIntervals[idx].contains( q.avgDataValue ) ) 
                {
                    q.color = yColors[idx];
                    break;
                }
            }
        }
        result.addAll( tmpList );
        
        groundPlane.toQuads( cameraViewMatrix , camera , result , false , false );
        yAxisPlane.toQuads(  cameraViewMatrix , camera , result , false , false );
        xAxisPlane.toQuads(  cameraViewMatrix , camera , result , false , false );
    }    
    
    public void renderAxis(PerspectiveCamera camera, Graphics2D gfx) 
    {
        Matrix4 cameraViewMatrix =  camera.view.cpy().mul( modelMatrix );
        
        xAxis.render( cameraViewMatrix, gfx, camera);
        yAxis.render( cameraViewMatrix, gfx, camera);
        zAxis.render( cameraViewMatrix, gfx, camera);
    }
}
