package de.codesourcery.iozone;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import de.codesourcery.iozone.IOZoneFileParser.FileEntry;
import de.codesourcery.iozone.IOZoneFileParser.IOZoneReader;
import de.codesourcery.iozone.IOZoneFileParser.IOZoneReport;

public class Chart2DRenderer 
{
    public static void main(String[] args) throws FileNotFoundException, IOException
    {
        //Get the workbook instance for XLS file
        final IOZoneReader ioReader = Chart3DRenderer.loadReports();

        final ChartPanel p = new ChartPanel( createChart( ioReader.getReport("Reader report") ) );
        
        final JFrame frame = new JFrame();
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        frame.getContentPane().add( p );

        frame.pack();
        frame.setVisible(true);
        frame.setLocationRelativeTo( null );        
    }

    protected static void writeChart(final IOZoneReport report,File outputDir) throws IOException
    {
        final BufferedImage image = renderImage(report);
        final String fileName = report.reportName.replace(" ","_")+".png";
        final File outputFile = new File( outputDir , fileName );
        ImageIO.write( image , "png" , outputFile );
        System.out.println("Image written to "+outputFile );
    }

    public static BufferedImage renderImage(final IOZoneReport report) throws IOException 
    {
        return toImage( createChart(report) );
    }

    private static JFreeChart createChart(final IOZoneReport report) 
    {
        final XYSeriesCollection xyDataset = new XYSeriesCollection();
        String title = report.reportName;
        String xAxisLabel = "Transfer size in kb";
        String yAxisLabel = "MB/s";

        for ( int fileSize : report.getFileSizes() )
        {
            System.out.println("Got file size "+fileSize+"k ...");
            final FileEntry fileEntry = report.getFileEntry( fileSize );

            final XYSeries s1 = new XYSeries( fileEntry.fileSize+"k" );
            for ( int i = 0 ; i < fileEntry.values.length ; i++ )
            {
                final int recordLen = report.recordLengths[i];
                final int value = fileEntry.values[i]; // value is in bytes/second
                s1.add( recordLen , value/(1024f*1024f) );
            }
            xyDataset.addSeries( s1 );
        }

        final PlotOrientation plotOrientation = PlotOrientation.VERTICAL;
        boolean legend = true;
        boolean tooltip = false;
        boolean urls = false;

        final JFreeChart chart = ChartFactory.createXYLineChart(title,xAxisLabel,yAxisLabel,xyDataset,plotOrientation,legend,tooltip,urls);

        final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesShapesVisible( 0 , true );
        renderer.setSeriesItemLabelsVisible( 0 , true );
        renderer.setBaseItemLabelsVisible( true );

        chart.getXYPlot().setRenderer( renderer );
        return chart;
    }

    private static BufferedImage toImage(JFreeChart chart) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChartUtilities.writeChartAsPNG(out, chart, 640 , 480 );

        return ImageIO.read( new ByteArrayInputStream( out.toByteArray() ) );
    }
}
