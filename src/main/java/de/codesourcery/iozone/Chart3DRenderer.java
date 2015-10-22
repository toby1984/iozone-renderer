package de.codesourcery.iozone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.iozone.IOZoneFileParser.CsvReader;
import de.codesourcery.iozone.IOZoneFileParser.FileEntry;
import de.codesourcery.iozone.IOZoneFileParser.IOZoneReader;
import de.codesourcery.iozone.IOZoneFileParser.IOZoneReport;
import de.codesourcery.iozone.Mesh.Quad;

public class Chart3DRenderer
{
    protected static final float INC = 2.5f;
    protected static final float ROT = 1.25f;
    protected static final float MOUSE_ROT_DEG_PER_PIXEL = 0.1f;
    
    protected static final class MyPanel extends JPanel
    {
        private final Mesh plotData;
        private final Mesh groundPlane;
        private final Mesh yAxisPlane;
        private final Mesh xAxisPlane;

        private final Axis xAxis;
        private final Axis yAxis;
        private final Axis zAxis;

        private final PerspectiveCamera camera;
        
        private final MouseAdapter mouseAdapter = new MouseAdapter() 
        {
            private Point dragged = null;
            
            public void mouseDragged(java.awt.event.MouseEvent e) 
            {
                int dx = e.getX() - dragged.x;
                int dy = e.getY() - dragged.y;
                
                dragged.setLocation( e.getPoint() );
                
                float deltaX = -dx * MOUSE_ROT_DEG_PER_PIXEL;
                float deltaY = -dy * MOUSE_ROT_DEG_PER_PIXEL;
                
                Vector3 tmp = new Vector3();
                camera.direction.rotate(camera.up, deltaX);
                tmp.set(camera.direction).crs(camera.up).nor();
                camera.direction.rotate(tmp, deltaY);
                camera.normalizeUp();
                camera.update(true);
                System.out.println("Cam direction: "+camera.direction+" , len="+camera.direction.len());
                System.out.println("Cam UP: "+camera.up+" , len="+camera.up.len());
                repaint();
            }
            
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) 
            {
                final int rotation = e.getWheelRotation();
                if ( rotation > 0 ) {
                    for ( int i = 0 ; i < rotation ; i++ ) {
                        moveForward();
                    }
                } else if ( rotation < 0 ) {
                    for ( int i = -rotation ; i > 0 ; i-- ) {
                        moveBackward();
                    } 
                }
            }
            
            public void mousePressed(java.awt.event.MouseEvent e) 
            {
                System.out.println("Pressed");
                dragged = new Point(e.getPoint());
            }
            
            public void mouseReleased(java.awt.event.MouseEvent e) 
            {
                System.out.println("Released");
                dragged = null;
            };
        };

        private final KeyAdapter keyListener = new KeyAdapter()
        {
            @Override
            public void keyTyped(KeyEvent e)
            {
                switch( e.getKeyChar() ) {
                    case 'a': moveLeft(); break;
                    case 'd': moveRight(); break;
                    case 'w': moveForward(); break;
                    case 's': moveBackward(); break;
                    case '+': moveUp(); break;
                    case '-': moveDown(); break;
                    case 'q': rotLeft(); break;
                    case 'e': rotRight(); break;
                    default:
                }
            }
        };
        

        private void rotLeft() { rot(-ROT); }

        private void rotRight() { rot(ROT); }

        private void moveUp() { moveY(INC); }

        private void moveDown() { moveY(-INC); }

        private void moveForward() { moveZ(INC); }

        private void moveBackward() { moveZ(-INC); }

        private void moveLeft() { moveX(-INC); }

        private void moveRight() { moveX(INC ); }

        private void moveZ(float offset)
        {
            final Vector3 tmp = camera.direction.cpy();
            tmp.scl( offset );
            camera.position.add( tmp );
            camera.update(true);
            repaint();
        }

        private void moveX(float offset)
        {
            final Vector3 tmp = camera.direction.cpy();
            tmp.crs( camera.up );
            tmp.nor();
            tmp.scl( offset );
            camera.position.add( tmp );
            camera.update(true);
            repaint();
        }

        private void rot(float deg)
        {
            camera.direction.rotate( camera.up , deg );
            camera.direction.nor();
            camera.update(true);
            repaint();
        }

        private void moveY(float offset)
        {
            final Vector3 tmp = camera.up.cpy();
            tmp.scl( offset );
            camera.position.add( tmp );
            camera.update(true);
            repaint();
        }        

        public MyPanel(IOZoneReport report)
        {
            
            setMinimumSize(new Dimension(640,480));
            setPreferredSize(new Dimension(640,480));
            
            addMouseMotionListener( mouseAdapter );
            addMouseListener( mouseAdapter );
            addMouseWheelListener( mouseAdapter );

            final int quadWidth = 10;
            final int quadHeight = 5;

            final float groundPlaneYOffset = -0.1f; // Y distance groundplane <-> plot plane             
            final float magicYOffset = 30-groundPlaneYOffset; // TODO: Trial'n'error ... how is this computed ??

            int maxLen = 0;
            for ( int fileSize : report.getFileSizes() ) {
                FileEntry fileEntry = report.getFileEntry( fileSize );
                if ( fileEntry.values.length > maxLen ) {
                    maxLen = fileEntry.values.length;
                }
            }
            
            final List<Integer> fileSizes = report.getFileSizes();
            fileSizes.sort( Integer::compareTo );
            
            final int meshXSize = fileSizes.size();
            final int meshYSize = 9;
            final int meshZSize = maxLen;

            // setup plot plane
            plotData = new Mesh( "plot",meshXSize, meshZSize , quadWidth , quadHeight );

            plotData.populate( (x,z) ->  
            {
                final FileEntry entry = report.getFileEntry( fileSizes.get( x ) );
                return z < entry.values.length ? entry.values[z] : 0;
            });
            
            plotData.scaleTo( 0 ,  40 );
            
            final float yGroundPlane = plotData.getMinY() + groundPlaneYOffset;

            // setup X Axis plane
            xAxisPlane = new Mesh( "X plane",meshXSize, meshZSize , quadWidth , quadWidth );
            xAxisPlane.modelMatrix.translate( 0 , magicYOffset , -plotData.height()/2f );
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
            System.out.println("maxX = "+DF2.format( maxY )+" , minY = "+DF2.format(minY)+", step: "+DF2.format( step ) );
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

            camera = new PerspectiveCamera( 60 , 640 , 480 );
            camera.lookAt( 0 ,  0 ,  0 );
            camera.position.set( 50 , 50 , 100 );
            camera.near = 1f;
            camera.far = 500f;
            camera.update(true);

            setFocusable( true );
            requestFocus();

            addKeyListener( keyListener);
        }

        @Override
        protected void paintComponent(Graphics g) 
        {
            super.paintComponent(g);
            final Graphics2D gfx = (Graphics2D) g;

            System.out.println("repaint");

            final List<Quad> groundQuads = new ArrayList<>( groundPlane.sizeInQuads()+plotData.sizeInQuads() );
            final List<Quad> plotQuads = new ArrayList<>( plotData.sizeInQuads() );

            groundPlane.toQuads( camera , groundQuads , false );
            yAxisPlane.toQuads( camera , groundQuads , false );
            xAxisPlane.toQuads( camera , groundQuads , false );

            plotData.toQuads( camera , plotQuads , false );

            for ( Quad q : plotQuads ) 
            {
//                double color = ((q.angleRad* 180.0) / Math.PI) / 90f;
//                if ( color > 1 ) {
//                    color = 1;
//                } else if ( color < 0.2 ) {
//                    color = 0.2f;
//                }
//                q.color = new Color( 0.0f , 0.0f , (float) color );			    
                q.color = Color.BLUE;
            }
            groundQuads.addAll( plotQuads );

            Mesh.renderQuads( groundQuads , camera , gfx );

            xAxis.render(gfx, camera);
            yAxis.render(gfx, camera);
            zAxis.render(gfx, camera);
        }
    }
    
    public static IOZoneReader loadReports() throws IOException 
    {
        return loadReportsFromClasspath( "/iozone.txt" );
    }

    public static IOZoneReader loadReportsFromClasspath(String file) throws IOException 
    {
        try ( final InputStream in = Chart3DRenderer.class.getResourceAsStream(file ) ) {
            if ( in == null ) {
                throw new FileNotFoundException( "classpath:"+file);
            }
            return loadReports( in );
        }
    }

    public static IOZoneReader loadReports(InputStream in) throws IOException 
    {
        final CsvReader csvReader = new CsvReader( in );
        return new IOZoneReader( csvReader );
    }
    
    public static void main(String[] args) throws IOException
    {
        final JFrame frame = new JFrame();
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        frame.getContentPane().add( new MyPanel( loadReports().getReport("Reader report") ) );

        frame.pack();
        frame.setVisible(true);
        frame.setLocationRelativeTo( null );
    }    
}