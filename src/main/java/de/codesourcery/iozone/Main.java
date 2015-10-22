package de.codesourcery.iozone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.iozone.Mesh.Quad;

public class Main
{
    protected static final float INC = 5;
    protected static final float ROT = 2.5f;

    protected static final class MyPanel extends JPanel
    {
        private final Mesh plotData;
        private final Mesh groundPlane;
        private final Mesh yAxisPlane;

        private final Axis xAxis;
        private final Axis yAxis;
        private final Axis zAxis;

        private final PerspectiveCamera camera;

        public MyPanel()
        {
            setMinimumSize(new Dimension(640,480));
            setPreferredSize(new Dimension(640,480));

            final int quadWidth = 10;
            final int quadHeight = 10;

            final int meshXSize = 9;
            final int meshYSize = 9;
            final int meshZSize = 9;
            
            final float groundPlaneYOffset = 5; // Y distance groundplane <-> plot plane 
            
            // setup plot plane
            plotData = new Mesh( "plot",meshXSize, meshZSize , quadWidth , quadHeight );
            
            final Random rnd = new Random(0xdeadbeef);
            plotData.populate( (x,z) -> -5+10*rnd.nextFloat() );
            
            // setup y Axis plane
            yAxisPlane = new Mesh( "Y axis",meshXSize, meshZSize , quadWidth , quadHeight );
            yAxisPlane.modelMatrix.translate( -plotData.width()/2f , 30 , 0 );
            yAxisPlane.modelMatrix.rotate( new Vector3( 0, 0, -1 ) , 90 );
            
            // setup ground plane
            groundPlane = new Mesh( "ground",meshXSize, meshZSize , quadWidth , quadHeight );
            groundPlane.modelMatrix.setToTranslation( 0, plotData.getMinY() - groundPlaneYOffset , 0 );

            // x axis
            xAxis = new Axis( "X axis", (meshXSize-1) * quadWidth );
            xAxis.color = Color.MAGENTA;

            for ( int i = 0 ; i < meshYSize-1 ; i++ ) 
            {
                xAxis.labels.add( Integer.toString( i*10) );
            }	

            // y axis
            yAxis = new Axis("Y axis", (meshXSize-1) * quadWidth );
            yAxis.modelMatrix.translate( -plotData.width()/2f , 30 , plotData.height()/2f );
            yAxis.modelMatrix.rotate( new Vector3( 0, 0, 1 ) , 90 );
            yAxis.color = Color.GREEN;

            for ( int i = 0 ; i < meshXSize-1 ; i++ ) 
            {
                yAxis.labels.add( Integer.toString( i*10) );
            }

            // z axis
            zAxis = new Axis("Z axis",meshXSize * quadWidth );
            zAxis.modelMatrix.rotate( new Vector3( 0, 1, 0 ) , 90 );            
            zAxis.color = Color.RED;

            for ( int i = 0 ; i < meshZSize-1 ; i++ ) 
            {
                zAxis.labels.add( Integer.toString( i*10) );
            }            

            camera = new PerspectiveCamera( 60 , 640 , 480 );
            camera.lookAt( 0 ,  0 ,  0 );
            camera.position.set( 0 , 50 , 100 );
            camera.update(true);

            setFocusable( true );
            setRequestFocusEnabled( true );
            requestFocus();

            addKeyListener( new KeyAdapter()
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
            });
        }
        
        @Override
        protected void paintComponent(Graphics g) 
        {
            super.paintComponent(g);
            final Graphics2D gfx = (Graphics2D) g;

            System.out.println("repaint");

            final List<Quad> groundQuads = new ArrayList<>( groundPlane.sizeInQuads()+plotData.sizeInQuads() );
            final List<Quad> plotQuads = new ArrayList<>( plotData.sizeInQuads() );
            
            groundPlane.toQuads( camera , groundQuads , true );
            yAxisPlane.toQuads( camera , groundQuads , false );
            
            plotData.toQuads( camera , plotQuads , true );
            
            for ( Quad q : plotQuads ) 
            {
                double color = ((q.angleRad* 180.0) / Math.PI) / 90f;
                if ( color > 1 ) {
                    color = 1;
                } else if ( color < 0.2 ) {
                    color = 0.2f;
                }
                q.color = new Color( 0.0f , 0.0f , (float) color );			    
            }
            groundQuads.addAll( plotQuads );

            Mesh.renderQuads( groundQuads , camera , gfx );

            xAxis.render(gfx, camera);
            yAxis.render(gfx, camera);
            zAxis.render(gfx, camera);
        }
    }

    public static void main(String[] args)
    {
        final JFrame frame = new JFrame();
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        frame.getContentPane().add( new MyPanel() );

        frame.pack();
        frame.setVisible(true);
        frame.setLocationRelativeTo( null );
    }
}
