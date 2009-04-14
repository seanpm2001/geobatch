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
import it.geosolutions.geobatch.configuration.event.action.ActionConfiguration;
import it.geosolutions.geobatch.flow.event.action.Action;
import it.geosolutions.geobatch.flow.event.action.BaseAction;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageWriteParam;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.MosaicDescriptor;

import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.referencing.operation.matrix.GeneralMatrix;
import org.geotools.referencing.operation.matrix.XAffineTransform;
import org.geotools.referencing.operation.transform.ProjectiveTransform;
import org.geotools.utils.imageoverviews.OverviewsEmbedder;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;

import com.sun.org.apache.bcel.internal.verifier.statics.DOUBLE_Upper;

/**
 * Comments here ...
 * 
 * @author Daniele Romagnoli, GeoSolutions
 */
public class Mosaicer extends BaseAction<FileSystemMonitorEvent> implements
        Action<FileSystemMonitorEvent> {

    private final static boolean IMAGE_IS_LINEAR;

    static{
        final String cl = System.getenv("SAS_COMPUTE_LOG");
        IMAGE_IS_LINEAR = !Boolean.parseBoolean(cl);
    }
    
    private MosaicerConfiguration configuration;

    private final static Logger LOGGER = Logger.getLogger(Mosaicer.class
            .toString());

    public static final String MOSAIC_PREFIX = "rawm_";
    public static final String BALANCED_PREFIX = "balm_";
    
    private double extrema[] = new double[]{Double.MAX_VALUE,Double.MIN_VALUE} ;

    public Mosaicer(MosaicerConfiguration configuration) throws IOException {
        this.configuration = configuration;
    }

    public Queue<FileSystemMonitorEvent> execute(
            Queue<FileSystemMonitorEvent> events) throws Exception {
        try {
        	
            // looking for file
            // if (events.size() != 1)
            // throw new IllegalArgumentException(
            // "Wrong number of elements for this action: "
            // + events.size());
            //
            // // get the first event
            // final FileSystemMonitorEvent event = events.peek();
            // final File inputFile = event.getSource();
            //            
            // ////////////////////////////////////////////////////////////////////
            //
            // Checking input files.
            //
            // ////////////////////////////////////////////////////////////////////

            GeneralEnvelope globEnvelope = null;

            final String directory = configuration.getWorkingDirectory();
            final double compressionRatio = configuration.getCompressionRatio();
            final String compressionType = configuration.getCompressionScheme();
            final int tileW = configuration.getTileW();
            final int tileH = configuration.getTileH();
            final int chunkW = configuration.getChunkWidth();
            final int chunkH = configuration.getChunkHeight();

            final File fileDir = new File(directory);
            if (fileDir != null && fileDir.isDirectory()) {
                File[] files = fileDir.listFiles();

                final String outputDirectory = buildOutputDirName(directory);
                final String outputBalanced = outputDirectory.replace(MOSAIC_PREFIX, BALANCED_PREFIX);
                final File dir = new File(outputDirectory);
                final File balDir = new File(outputBalanced);
                configuration.setMosaicDirectory(outputDirectory);
                if (!dir.exists())
                    dir.mkdir();
                if(!balDir.exists())
                    balDir.mkdir();

                if (files != null) {
                    final int numFiles = files.length;
//                    double rotation=0.0d;
                    for (int i = 0; i < numFiles; i++) {
                        final String path = files[i].getAbsolutePath()
                                .toLowerCase();
                        if (!path.endsWith("tif"))
                            continue;

                        // get a reader
                        final File file = files[i];
                        final GeoTiffReader reader = new GeoTiffReader(file,
                                null);

                        GeneralEnvelope envelope = (GeneralEnvelope) reader
                                .getOriginalEnvelope();
                        if (globEnvelope == null) {
                            globEnvelope = new GeneralEnvelope(envelope);
                            globEnvelope.setCoordinateReferenceSystem(envelope
                                    .getCoordinateReferenceSystem());
//                            AffineTransform at = (AffineTransform)reader.getOriginalGridToWorld(PixelInCell.CELL_CENTER);
//                            rotation = XAffineTransform.getRotation(at);
                        } else
                            globEnvelope.add(envelope);

                        reader.dispose();
                    }

                    // compute final g2w
                    final GeneralMatrix gm = new GeneralMatrix(3);

                    // change this Leverage on XML metadata
                    gm.setElement(0, 0, 0.025);
                    gm.setElement(1, 1, -0.015);
                    gm.setElement(0, 1, 0);
                    gm.setElement(1, 0, 0);
                    gm.setElement(0, 2, globEnvelope.getLowerCorner()
                            .getOrdinate(0));
                    gm.setElement(1, 2, globEnvelope.getUpperCorner()
                            .getOrdinate(1));
                    MathTransform mosaicTransform = ProjectiveTransform
                            .create(gm);
//                    MathTransform tempTransform =PixelTranslation.translate(mosaicTransform, PixelInCell.CELL_CORNER, PixelInCell.CELL_CENTER);
                    
                    MathTransform world2GridTransform = mosaicTransform
                            .inverse();

                    GridCoverageFactory coverageFactory = CoverageFactoryFinder
                            .getGridCoverageFactory(null);

                    // final GridGeometry2D gg2d = new GridGeometry2D(
                    // PixelInCell.CELL_CORNER, mosaicTransform, globEnvelope,
                    // null);

                    // read them all
                    final List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
                    for (File file : files) {
                        final String path = file.getAbsolutePath()
                                .toLowerCase();
                        if (!path.endsWith("tif"))
                            continue;

                        final GeoTiffReader reader = new GeoTiffReader(file,
                                null);

                        final GridCoverage2D gc = (GridCoverage2D) reader.read(null);
                        coverages.add(gc);
                        updateExtrema(gc);
                        reader.dispose();
                    }

                    
                    RenderedImage mosaicImage = createMosaic(coverages,world2GridTransform);
                    RenderedImage balancedMosaic = balanceMosaic(mosaicImage);
                    
                    GridCoverage2D balancedGc = coverageFactory.create("balanced", balancedMosaic, globEnvelope);
                    LOGGER.log(Level.INFO, "Retiling the balanced mosaic");
                    retileMosaic(balancedGc, chunkW, chunkH, tileW, tileH,
                            compressionRatio, compressionType, outputBalanced);

                    
                    GridCoverage2D gc = coverageFactory.create("mosaiced", mosaicImage, globEnvelope);

                    // //
                    //
                    // Retiling Mosaic to smaller Coverages
                    //
                    // //
                    LOGGER.log(Level.INFO, "Retiling the raw mosaic");
                    retileMosaic(gc, chunkW, chunkH, tileW, tileH,
                            compressionRatio, compressionType, outputDirectory);

                }
            }

            return events;
        } catch (Throwable t) {
            if (LOGGER.isLoggable(Level.SEVERE))
                LOGGER.log(Level.SEVERE, t.getLocalizedMessage(), t);
            return null;
        }
    }

    private void updateExtrema(GridCoverage2D gc) {
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

    private RenderedImage balanceMosaic(RenderedImage mosaicImage) {
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
        return destImage;
    }

    private RenderedImage createMosaic(
            final List<GridCoverage2D> coverages,
            final MathTransform world2GridTransform) {
        final int nCov = coverages.size();

        final ParameterBlockJAI pbMosaic = new ParameterBlockJAI("Mosaic");
        pbMosaic.setParameter("mosaicType", MosaicDescriptor.MOSAIC_TYPE_BLEND);

        if (LOGGER.isLoggable(Level.INFO))
        	LOGGER.log(Level.INFO, new StringBuffer("Found ").append(nCov).append(" tiles").toString());
        
        for (int i = 0; i < nCov; i++) {
            final GridCoverage2D coverage = coverages.get(i);
            final ParameterBlockJAI pbAffine = new ParameterBlockJAI("Affine");
            pbAffine.addSource(coverage.getRenderedImage());
            AffineTransform at = (AffineTransform) coverage.getGridGeometry()
                    .getGridToCRS2D();
            AffineTransform chained = (AffineTransform) at.clone();
            chained.preConcatenate((AffineTransform) world2GridTransform);
            pbAffine.setParameter("transform", chained);
            final RenderedOp affine = JAI.create("Affine", pbAffine);
            pbMosaic.addSource(affine);
        }

        RenderedOp mosaicImage = JAI.create("Mosaic", pbMosaic);
        return mosaicImage;
    }

    private void retileMosaic(GridCoverage2D gc, int chunkWidth,
            int chunkHeight, int internalTileWidth, int internalTileHeight,
            final double compressionRatio, final String compressionScheme,
            final String outputLocation) {

        // //
        //
        // getting source size and checking tile dimensions to be not
        // bigger than the original coverage size
        //
        // //
        final RenderedImage rImage = gc.getRenderedImage();
        final int w = rImage.getWidth();
        final int h = rImage.getHeight();
        chunkWidth = chunkWidth > w ? w : chunkWidth;
        chunkHeight = chunkHeight > h ? h : chunkHeight;

        // ///////////////////////////////////////////////////////////////////
        //
        // MAIN LOOP
        //
        // ///////////////////////////////////////////////////////////////////
        if (LOGGER.isLoggable(Level.INFO))
        	LOGGER.log(Level.INFO, "Retiling mosaic to separated files");
        final int numTileX = w!=chunkWidth? (int) (w / (chunkWidth * 1.0) + 1):1;
        final int numTileY = h!=chunkHeight? (int) (h / (chunkHeight * 1.0) + 1):1;
        final List<String> filesToAddOverviews = new ArrayList<String>(numTileX*numTileY);
        
        for (int i = 0; i < numTileX; i++)
            for (int j = 0; j < numTileY; j++) {

                // //
                //
                // computing the bbox for this tile
                //
                // //
                final Rectangle sourceRegion = new Rectangle(i * chunkWidth, j
                        * chunkHeight, chunkWidth, chunkHeight);

                // //
                //
                // building gridgeometry for the read operation with the actual
                // envelope
                //
                // //
                final String fileName = buildFileName(outputLocation,i,j,chunkWidth);
                final File fileOut = new File(fileName);
                // remove an old output file if it exists
                if (fileOut.exists())
                    fileOut.delete();

                // //
                //
                // Write this coverage out as a geotiff
                //
                // //
                final AbstractGridFormat outFormat = new GeoTiffFormat();
                try {

                    final GeoTiffWriteParams wp = new GeoTiffWriteParams();
                    wp.setTilingMode(GeoToolsWriteParams.MODE_EXPLICIT);
                    wp.setTiling(internalTileWidth, internalTileHeight);
                    wp.setSourceRegion(sourceRegion);
                    if (compressionScheme != null
                            && !Double.isNaN(compressionRatio)) {
                        wp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        wp.setCompressionType(compressionScheme);
                        wp.setCompressionQuality((float) compressionRatio);
                    }
                    final ParameterValueGroup params = outFormat
                            .getWriteParameters();
                    params.parameter(
                            AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName()
                                    .toString()).setValue(wp);

                    if (LOGGER.isLoggable(Level.INFO))
                    	LOGGER.log(Level.INFO, new StringBuilder("Writing tile: ").append(i+1)
                    			.append(" of ").append(numTileX).append(" [X] -- ")
                    			.append(j+1)
                    			.append(" of ").append(numTileY).append(" [Y]").toString());
                    
                    final GeoTiffWriter writerWI = new GeoTiffWriter(fileOut);
                    writerWI.write(gc, (GeneralParameterValue[]) params
                            .values().toArray(new GeneralParameterValue[1]));
                    writerWI.dispose();
                    filesToAddOverviews.add(fileName);
                } catch (IOException e) {
                    return;
                }
            }
        
        //Overviews are added as a last step to minimize TileCache updates
        for (String fileOverviews: filesToAddOverviews){
            // TODO: Leverage on GeoTiffOverviewsEmbedder when involving
            // no more FileSystemEvent only
            // Or merge retiling and overviews adding to a single step 
            addOverviews(fileOverviews);
        }
        
        
    }

    private String buildOutputDirName(final String outputLocation){
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
    
    private String buildFileName(final String outputLocation, final int i, final int j,
            final int chunkWidth) {
        final String name = new StringBuilder(outputLocation).append("m_")
        .append(Integer.toString(i * chunkWidth + j)).append(
                        ".").append("tif").toString();
        return name;
    }

    private void addOverviews(final String inputFileName) {
    	
    	LOGGER.log(Level.INFO, "Adding overviews");
        final int downsampleStep = configuration.getDownsampleStep();
        if (downsampleStep <= 0)
            throw new IllegalArgumentException("Illegal downsampleStep: "
                    + downsampleStep);
        final int numberOfSteps = configuration.getNumSteps();
        if (numberOfSteps <= 0)
            throw new IllegalArgumentException("Illegal numberOfSteps: "
                    + numberOfSteps);

        final OverviewsEmbedder oe = new OverviewsEmbedder();
        oe.setDownsampleStep(downsampleStep);
        oe.setNumSteps(numberOfSteps);
        oe.setInterp(Interpolation.getInstance(Interpolation.INTERP_NEAREST));
        oe.setScaleAlgorithm(configuration.getScaleAlgorithm());
        oe.setTileHeight(configuration.getTileH());
        oe.setTileWidth(configuration.getTileW());
        oe.setSourcePath(inputFileName);
        final String compressionScheme = configuration.getCompressionScheme();
        final double compressionRatio = configuration.getCompressionRatio();
        if (compressionScheme != null
                && !Double.isNaN(compressionRatio)) {
            oe.setCompressionRatio(compressionRatio);
            oe.setCompressionScheme(compressionScheme);
        }
       
        oe.run();
    }

    public ActionConfiguration getConfiguration() {
        return configuration;
    }
}
