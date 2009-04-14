/*
 *  GeoBatch - Open Source geospatial batch processing system
 *  http://geobatch.codehaus.org/
 *  Copyright (C) 2007-2008-2009 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.geosolutions.geobatch.mosaic;

import it.geosolutions.filesystemmonitor.monitor.FileSystemMonitorEvent;
import it.geosolutions.geobatch.flow.event.action.Action;

import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.IOException;

import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.LookupDescriptor;

import org.geotools.coverage.grid.GridCoverage2D;

/**
 * Comments here ...
 * 
 * @author Daniele Romagnoli, GeoSolutions
 */
public class Mosaicer extends AbstractMosaicer implements
        Action<FileSystemMonitorEvent> {

    private final static boolean IMAGE_IS_LINEAR;

    static{
        final String cl = System.getenv("SAS_COMPUTE_LOG");
        IMAGE_IS_LINEAR = !Boolean.parseBoolean(cl);
    }
    
    public static final String MOSAIC_PREFIX = "rawm_";
    public static final String BALANCED_PREFIX = "balm_";
    
    private double extrema[] = new double[]{Double.MAX_VALUE,Double.MIN_VALUE} ;

    public Mosaicer(MosaicerConfiguration configuration) throws IOException {
        super(configuration);
    }


    protected void updates(GridCoverage2D gc) {
        RenderedImage sourceImage = gc.getRenderedImage();
        if (IMAGE_IS_LINEAR){
            sourceImage = computeLog(sourceImage);
        }
        
        final ROI roi = new ROI(sourceImage, 0);
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(sourceImage); // The source image
        if (roi != null)
            pb.add(roi); // The region of the image to scan

        // Perform the extrema operation on the source image
        RenderedOp ex = JAI.create("extrema", pb);

        // Retrieve both the maximum and minimum pixel value
        final double[][] ext = (double[][]) ex.getProperty("extrema");
        
        if(extrema[0]>ext[0][0])
            extrema[0]=ext[0][0];
        if (extrema[1]<ext[1][0])
            extrema[1]=ext[1][0];
    }

    private RenderedImage computeLog(RenderedImage sourceImage) {
        final ParameterBlockJAI pbLog = new ParameterBlockJAI("Log");
        pbLog.addSource(sourceImage);
        RenderedOp logarithm = JAI.create("Log", pbLog);

        // //
        //
        // Applying a rescale to handle Decimal Logarithm.
        //
        // //
        final ParameterBlock pbRescale = new ParameterBlock();
        
        // Using logarithmic properties 
        final double scaleFactor = 20 / Math.log(10);

        final double[] scaleF = new double[] { scaleFactor };
        final double[] offsetF = new double[] { 0 };

        pbRescale.add(scaleF);
        pbRescale.add(offsetF);
        pbRescale.addSource(logarithm);

        return JAI.create("Rescale", pbRescale);
    }

    protected RenderedImage balanceMosaic(RenderedImage mosaicImage) {
        RenderedImage inputImage = mosaicImage;
        if (IMAGE_IS_LINEAR){
            inputImage = computeLog(inputImage);
        }
        final double[] scale = new double[] { (255) / (extrema[1] - extrema[0]) };
        final double[] offset = new double[] { ((255) * extrema[0])
                / (extrema[0] - extrema[1]) };

        // Preparing to rescaling values
        ParameterBlock pbRescale = new ParameterBlock();
        pbRescale.add(scale);
        pbRescale.add(offset);
        pbRescale.addSource(inputImage);
        RenderedOp rescaledImage = JAI.create("Rescale", pbRescale);

        ParameterBlock pbConvert = new ParameterBlock();
        pbConvert.addSource(rescaledImage);
        pbConvert.add(DataBuffer.TYPE_BYTE);
        RenderedOp destImage = JAI.create("format", pbConvert);
        
        final byte lut[] = new byte[256];
        final double normalizationFactor=255.0;
        final double correctionFactor=100.0;
        for (int i = 1; i < lut.length; i++)
                lut[i] = (byte) (0.5f + normalizationFactor * Math.log((i * correctionFactor / normalizationFactor+ 1.0)));
        return LookupDescriptor.create(inputImage,
                        new LookupTableJAI(lut),null);        
        
//        return destImage;
    }


    /**
     * 
     * @param outputLocation
     * @return
     */
    protected String buildOutputDirName(final String outputLocation){
    	String dirName = "";
    	final File outputDir = new File(outputLocation);
         final String channelName = outputDir.getName();
         final String leg = outputDir.getParent();
         final File legF = new File(leg);
         final String legName = legF.getName();
         final String mission = legF.getParent();
         final File missionF = new File(mission);
         final String missionName = missionF.getName();
         final String time = configuration.getTime();
         dirName = new StringBuilder(outputLocation).append(File.separatorChar).append(MOSAIC_PREFIX)
         .append(time).append("_")
         .append(missionName).append("_L")
         .append(legName.substring(3,legName.length())).append("_")
         .append(channelName.substring(0,1)).append(File.separatorChar).toString();
         return dirName;
    }
    
    protected String buildFileName(final String outputLocation, final int i, final int j,
            final int chunkWidth) {
        final String name = new StringBuilder(outputLocation).append("m_")
        .append(Integer.toString(i * chunkWidth + j)).append(
                        ".").append("tif").toString();
        return name;
    }
}
