package de.codesourcery.iozone;

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.iozone.IOZoneFileParser.CsvReader;
import de.codesourcery.iozone.IOZoneFileParser.IOZoneReader;
import de.codesourcery.iozone.IOZoneFileParser.IOZoneReport;
import de.codesourcery.iozone.Mesh.Quad;

public class Chart3DViewer
{
    protected static final float INC = 2.5f;
    protected static final float ROT = 1.25f;
    protected static final float MOUSE_ROT_DEG_PER_PIXEL = 0.1f;
    
    protected static final class MyPanel extends JPanel
    {
        private final PerspectiveCamera camera;
        
        private final List<IOZone3DChart> charts;
        
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
                camera.update(true);
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
                dragged = new Point(e.getPoint());
            }
            
            public void mouseReleased(java.awt.event.MouseEvent e) 
            {
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

        public MyPanel(List<IOZone3DChart> charts)
        {
            this.charts = new ArrayList<>(charts);
            
            setMinimumSize(new Dimension(640,480));
            setPreferredSize(new Dimension(640,480));
            
            addMouseMotionListener( mouseAdapter );
            addMouseListener( mouseAdapter );
            addMouseWheelListener( mouseAdapter );

            camera = new PerspectiveCamera( 40 , 640 , 480 );
            camera.lookAt( 0 ,  0 ,  0 );
            camera.position.set( 50 , 50 , 100 );
            camera.near = 0.1f;
            camera.far = 1000f;
            camera.update(true);

            setFocusable( true );
            requestFocus();

            addKeyListener( keyListener);
        }

        @Override
        protected void paintComponent(Graphics g) 
        {
            long time = -System.currentTimeMillis();
            super.paintComponent(g);
            
            final Graphics2D gfx = (Graphics2D) g;

            final List<Quad> groundQuads = new ArrayList<>();
            for ( int i = 0, len = charts.size() ; i < len ; i++ ) 
            {
                final IOZone3DChart chart = charts.get(i);
                chart.toQuads(camera, gfx,groundQuads);
                chart.renderAxis(camera, gfx);
            }
            
            Mesh.renderQuads( groundQuads , camera , gfx );
            
            for ( int i = 0, len = charts.size() ; i < len ; i++ ) 
            {
                final IOZone3DChart chart = charts.get(i);
                chart.renderAxis(camera, gfx);
            }            
            
            time += System.currentTimeMillis();
            System.out.println("Rendering "+groundQuads.size()+" quads in "+time+" ms");            
        }
    }
    
    public static IOZoneReader loadReports() throws IOException 
    {
        return loadReportsFromClasspath( "/iozone.txt" );
    }

    public static IOZoneReader loadReportsFromClasspath(String file) throws IOException 
    {
        try ( final InputStream in = Chart3DViewer.class.getResourceAsStream(file ) ) {
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
        
        List<IOZoneReport> reports = loadReports().getReports();
        
        System.out.println("Loaded "+reports.size()+" reports");
        
        // >>>>>> TODO: remove this debug code <<<<<<<
//        reports = reports.stream().limit(1).collect( Collectors.toList() );
        
        // arrange charts in a 3D table
        final int chartWidth  = 100;
        final int chartHeight = 100;
        
        final int gapWidth = 10;
        final int gapHeight = 10;
        
        final int chartsPerRow = 4;
        final int rows = Math.max(1, reports.size()/chartsPerRow);
        
        final List<IOZone3DChart> charts=new ArrayList<>();
        int ptr = 0;
        for ( int y = 0 ; y < rows && ptr < reports.size() ; y ++ ) 
        {
            for ( int x = 0 ; x < chartsPerRow && ptr < reports.size() ; x++ ) 
            {
                final IOZoneReport report = reports.get( ptr++ );
                final IOZone3DChart chart = new IOZone3DChart( report );
                final int xOffset;
                if ( x > 0 ) {
                    xOffset = x*chartWidth+(x-1)*gapWidth;
                } else {
                    xOffset = 0;
                }
                final int yOffset;
                if ( y > 0 ) {
                    yOffset = y*chartHeight+(y-1)*gapHeight;
                } else {
                    yOffset = 0;
                }                
                System.out.println("Chart "+report.reportName+" is at ("+xOffset+", -"+yOffset+")");
                chart.modelMatrix.setToTranslation( xOffset ,-yOffset , 0 );
                charts.add(chart);
            }
        }

        frame.getContentPane().add( new MyPanel( charts ) );

        frame.pack();
        frame.setVisible(true);
        frame.setLocationRelativeTo( null );
    }    
}