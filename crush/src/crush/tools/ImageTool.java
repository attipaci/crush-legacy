/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of crush.
 * 
 *     crush is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     crush is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with crush.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/

package crush.tools;

import crush.*;
import crush.sourcemodel.*;
import util.*;

import java.io.File;
import java.util.*;
import nom.tam.fits.*;

public class ImageTool {
	static String version = "0.1-1";

	static AstroMap image;

	static Vector<Region> regions = new Vector<Region>();
	
	public static void main(String args[]) {
		versionInfo();

		if(args.length == 0) usage();
		else if(args[0].equalsIgnoreCase("-help")) usage();

		try {	    
			image = new AstroMap();
			image.read(args[args.length-1]);

			for(int i=0; i<args.length-1; i++) option(args[i]);
			
			System.out.println(" Updated image information: \n");
			
			image.info();
			
			image.write();
		}
		catch(Exception e) { e.printStackTrace(); }
	}

	public static boolean option(String optionString) {
		StringTokenizer tokens = new StringTokenizer(optionString, "=,");

		String key = tokens.nextToken();

		if(key.equalsIgnoreCase("-crop") || key.equalsIgnoreCase("-clip")) {
			if(!tokens.hasMoreTokens()) image.autoCrop();
			else {
				double dXmin = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
				double dYmin = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
				double dXmax = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
				double dYmax = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;

				image.crop(dXmin, dYmin, dXmax, dYmax);
			}
		}
		else if(key.equalsIgnoreCase("-out")) {
			image.fileName = Util.getSystemPath(tokens.nextToken());
		}
		else if(key.equalsIgnoreCase("-pick")) {
			final String id = tokens.nextToken().toLowerCase();
			final String coreName = CRUSH.workPath + File.separator + image.sourceName;
			
			try {
				if(id.equals("flux")) image.getFluxImage().write(coreName + ".flux.fits");
				else if(id.equals("rms")) image.getRMSImage().write(coreName + "rms.fits");
				else if(id.equals("time")) image.getTimeImage().write(coreName + "time.fits");	
				else if(id.equals("s2n")) image.getS2NImage().write(coreName + "s2n.fits");	
				else System.out.println("WARNING! No image with id " + id + ".");
			}
			catch(Exception e) { e.printStackTrace(); }
			System.exit(0);
		}
		else if(key.equalsIgnoreCase("-replace")) {
			StringTokenizer values = new StringTokenizer(tokens.nextToken(), ":= \t");
			String spec = values.nextToken().toLowerCase();
			String fileName = values.nextToken();
			
			try {
				BasicHDU hdu = new Fits(fileName).getHDU(0);
				AstroImage plane = new AstroImage();
				
				if(spec.equals("flux")) {
					System.err.println("Replacing flux plane.");
					plane.unit = image.unit;
					plane.read(hdu);
					image.data = plane.data;
					image.flag = plane.flag;
				}
				else if(spec.equals("weight")) {
					System.err.println("Replacing weight plane.");
					plane.unit = new Unit("weight", 1.0 / (image.unit.value * image.unit.value));
					plane.read(hdu);
					image.weight = plane.data;
				}
				else if(spec.equals("time")) {
					System.err.println("Replacing integration-time plane.");
					plane.unit = Unit.get("s");
					plane.read(hdu);
					image.count = plane.data;
				}
				else if(spec.equals("flag")) {
					System.err.println("Replacing integration-time plane.");
					plane.unit = Unit.unity;
					plane.read(hdu);
					image.flag = plane.flag;
				}
				// TODO integer flag?
				else System.err.println("Unknown image plane: " + spec);
			}
			catch(Exception e) { e.printStackTrace(); }
		}
		else if(key.equalsIgnoreCase("-flag")) {
			try {
				BasicHDU hdu = new Fits(tokens.nextToken()).getHDU(0);
				AstroImage plane = new AstroImage();
				
				plane.unit = Unit.unity;
				plane.read(hdu);
				image.flag = plane.flag;
				
				System.err.println("Applying new flag image.");
			}
			catch(Exception e) { e.printStackTrace(); }
		}
		else if(key.equalsIgnoreCase("-name")) {
			image.fileName = CRUSH.workPath + Util.getSystemPath(tokens.nextToken());
		}
		else if(key.equalsIgnoreCase("-correct")) {
			String value = tokens.nextToken();
			if(value.equalsIgnoreCase("point")) image.filterCorrect();
			else if(value.equalsIgnoreCase("none")) image.undoFilterCorrect();
			else image.filterCorrect();
		}	
		else if(key.equalsIgnoreCase("-regrid")) {
			image.regrid(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
		}
		else if(key.equalsIgnoreCase("-convolve")) {
			image.convolve(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
		}
		else if(key.equalsIgnoreCase("-smooth")) {
			image.convolveTo(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
		}
		else if(key.equalsIgnoreCase("-extFilter")) {
			double fwhm = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
			double blanking = tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) : Double.NaN;
			image.filterAbove(fwhm, blanking);
		}
		else if(key.equalsIgnoreCase("-fftFilter")) {
			double fwhm = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
			double blanking = tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) : Double.NaN;
			image.fftFilterAbove(fwhm, blanking);
		}

		else if(key.equalsIgnoreCase("-deconvolve")) {
			double[][] beam = null;
			double replacementFWHM = image.instrument.resolution / 2.0;
			
			if(tokens.hasMoreTokens()) {
				
				StringTokenizer specs = new StringTokenizer(tokens.nextToken(), ":");
				String beamSpec = specs.nextToken(); 

				try { beam = image.getBeam(Double.parseDouble(beamSpec) * Unit.arcsec); }

				catch(NumberFormatException e)  {
					try {
						AstroMap beamImage = new AstroMap(beamSpec);
						beamImage.scale(1.0 / beamImage.getMax());
						beam = beamImage.data;
					}
					catch(Exception e2) { System.err.println("ERROR! Cannot open beam FITS file " + beamSpec); }		
				}		

				if(specs.hasMoreTokens()) replacementFWHM = Double.parseDouble(specs.nextToken()) * Unit.arcsec;

				if(beam != null) image.clean(beam, 0.1, replacementFWHM);
			}
			else image.clean();
		}
		else if(key.equalsIgnoreCase("-minexp")) {
			image.exposureClip(Double.parseDouble(tokens.nextToken()));
		}
		else if(key.equalsIgnoreCase("-maxnoise")) {
			image.rmsClip(Double.parseDouble(tokens.nextToken()));
		}
		else if(key.equalsIgnoreCase("-s2nclip")) {
			image.s2nClip(Double.parseDouble(tokens.nextToken()));
		}
		else if(key.equalsIgnoreCase("-growflags")) {
			double radius = image.getImageFWHM();
			int pattern = ~0;
			if(tokens.hasMoreTokens()) radius = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
			if(tokens.hasMoreTokens()) pattern = Integer.decode(tokens.nextToken());
			image.growFlags(radius, pattern);			
		}
		else if(key.equalsIgnoreCase("-rmsscale")) {
			image.rmsScale(Double.parseDouble(tokens.nextToken()));
		}
		else if(key.equalsIgnoreCase("-scale")) {
			image.scale(Double.parseDouble(tokens.nextToken()));
		}
		else if(key.equalsIgnoreCase("-noise")) {
			String value = tokens.nextToken();
			if(value.equalsIgnoreCase("data")) image.dataWeight();
			else if(value.equalsIgnoreCase("image")) image.weight(true);
			else return false;
		}
		else if(key.equalsIgnoreCase("-offset")) {
			image.addValue(Double.parseDouble(tokens.nextToken()) * image.unit.value);
		}
		else if(key.equalsIgnoreCase("-origin")) {
			String x = tokens.nextToken();
			String y = tokens.nextToken();
			try {
				Vector2D offset = new Vector2D(Double.parseDouble(x), Double.parseDouble(y));
				offset.scale(Unit.arcsec);
				image.getReference().addOffset(offset);
			}
			catch(NumberFormatException e) {
				SphericalCoordinates origin = new SphericalCoordinates();
				origin.parse(x + " " + y);
				Vector2D index = new Vector2D();
				image.grid.getIndex(origin, index);
				image.grid.refIndex = index;
				image.getReference().copy(origin);
			}
		}
		else if(key.equalsIgnoreCase("-random")) {
			Random random = new BufferedRandom();
			image.smoothFWHM = Math.sqrt(image.getPixelArea()) / AstroImage.fwhm2size;
			image.extFilterFWHM = Double.NaN;
			for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) 
				image.data[i][j] = random.nextGaussian() / Math.sqrt(image.weight[i][j]);
		}
		else if(key.equalsIgnoreCase("-masks")) {
			StringTokenizer actions = new StringTokenizer(tokens.nextToken(), ", \t");
			
			while(actions.hasMoreTokens()) {
				String action = actions.nextToken();
				if(action.equalsIgnoreCase("add")) image.addSources();
				else if(action.equalsIgnoreCase("remove")) image.removeSources();
				else if(action.equalsIgnoreCase("pointadd")) image.addPointSources();
				else if(action.equalsIgnoreCase("pointremove")) image.removePointSources();
				else if(action.equalsIgnoreCase("flag")) image.flagSources();			
				else if(action.equalsIgnoreCase("forget")) image.clearRegions();
				else if(action.equalsIgnoreCase("image")) {
					image.extFilterFWHM = Double.NaN;
					for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) 
						image.data[i][j] = 0.0;
					image.addSources();
				}
				else if(action.equalsIgnoreCase("stack")) {
					image = image.stack();
					int iExt = image.fileName.lastIndexOf(".");
					if(iExt < 0) iExt = image.fileName.length();
					image.fileName = image.fileName.substring(0, iExt) + ".stacked" + image.fileName.substring(iExt);
					image.regions.clear();
					image.polygons.clear();
				}
				else if(action.equalsIgnoreCase("match")) {
					double pointingRMS = image.delta;
					if(action.length() > "match:".length()) {
						pointingRMS = Double.parseDouble(action.substring("match:".length())) * Unit.arcsec;
					}
					image.matchPeaks(pointingRMS);
					System.exit(0);
				}
				else if(action.toLowerCase().startsWith("extract")) {
					double pointingRMS = Math.sqrt(image.getPixelArea());
					if(action.length() > "extract:".length()) {
						pointingRMS = Double.parseDouble(action.substring("extract:".length())) * Unit.arcsec;
					}
					image.extractRegions(pointingRMS);
					System.exit(0);
				}
			}
		}
		else if(key.equalsIgnoreCase("-shift")) {
			Vector2D offset = new Vector2D(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken()));
			offset.scale(Unit.arcsec);
			image.grid.shift(offset);
		}
		else if(key.equalsIgnoreCase("-unit")) {
			image.setUnit(tokens.nextToken());
		}
		else if(key.equalsIgnoreCase("-source")) {
			image.sourceName = tokens.nextToken();
		}
		else if(key.equalsIgnoreCase("-mask")) {
			String maskName = Util.getSystemPath(tokens.nextToken());
			SourceCatalog sources = new SourceCatalog();
		    try { sources.read(maskName, image); }
		    catch(Exception e) {
		    	try { sources.read(CRUSH.workPath + maskName, image); }
		    	catch(Exception e2) { System.err.println("ERROR!: " + e2.getMessage()); }
		    }
		}
		else if(key.equalsIgnoreCase("-area")) {
			image.assumedArea = Double.parseDouble(tokens.nextToken()) * Unit.arcmin2; 
		}
		else if(key.equalsIgnoreCase("-polygon")) {
			String fileName = Util.getSystemPath(tokens.nextToken());
		    try { image.addPolygon(fileName); }
		    catch(Exception e) {
		    	try { image.addPolygon(CRUSH.workPath + fileName); }
		    	catch(Exception e2) { System.err.println("ERROR!: " + e2.getMessage()); }
		    }
		}
		else if(key.equalsIgnoreCase("-setKey") || key.equalsIgnoreCase("-changeKey")) {
			StringTokenizer args = new StringTokenizer(tokens.nextToken(), ":");
			if(args.countTokens() < 2) return false;

			String fitsKey = args.nextToken();
			String fitsValue = args.nextToken();

			System.out.println("Changing value of FITS key " + fitsKey + " to " + fitsValue);

			try { image.setKey(fitsKey, fitsValue); }
			catch(Exception e) { System.err.println("ERROR!: " + e.getMessage()); }
		}
		else if(key.equalsIgnoreCase("-printHeader")) {
			System.out.println("\nFITS Header:\n"); 
			image.printHeader(); 
			System.exit(0);
		}
		else if(key.equalsIgnoreCase("-depth")) {
			System.err.println("Average depth " + Util.e2.format(image.getTypicalRMS()/image.unit.value) + " " + image.unit.name);
		}
		else if(key.equalsIgnoreCase("-beam")) {
			image.instrument.resolution = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
		}
		else if(key.equalsIgnoreCase("-level")) {
			image.level(true);
		}
		else if(key.equalsIgnoreCase("-profile")) {
			image.profile();
		}
		
			
		else if(key.equalsIgnoreCase("-help")) usage();
		else return false;

		return true;

	}


	public static void versionInfo() {
		System.out.println("imagetool -- Image Manipulation Utility for CRUSH maps and more.");
		System.out.println("             Version: " + version);
		System.out.println("             part of crush " + CRUSH.getFullVersion());
		System.out.println("             http://www.submm.caltech.edu/~sharc/crush");
		System.out.println("             Copyright (C)2011 Attila Kovacs <kovacs[AT]astro.umn.edu>");
		System.out.println();
	}

	public static void usage() {
		System.out.println("Usage: imagetool [options] <filename>");
		System.out.println();
		System.out.println("Options:");
		System.out.println();

		optionsSummary();

		System.out.println();	
		System.exit(0);
	}

	public static void optionsSummary() {
		System.out.println("  [Output Options]");
		System.out.println("    -out=          Full output file name with path. Default overwrites input.");
		System.out.println("    -name=         Output file name relative to input path.");
		System.out.println("    -pick=         Writes a single image (flux|s2n|rms|time) then exits.");
		System.out.println();
		System.out.println("  [Clipping/Cropping]");
		System.out.println("    -crop          Automatically crop the image at its edges.");
		System.out.println("    -crop=         Crop at offsets dX1,dY1,dX2,dY2 (in arcsec).");
		System.out.println("    -s2nClip=      Flag pixels below the specified significance level.");
		System.out.println("    -minExp=       Flag pixels with smaller realtive exposures.");
		System.out.println("    -maxNoise=     Flag pixels with higher relative noise levels.");
		System.out.println("    -growFlags=    Specify a radius (in arcsec) by which flagged areas are increased.");
		System.out.println();
		System.out.println("  [Smoothing/Filtering]");
		System.out.println("    -convolve=     Convolve with the specified FWHM beam size (arcsec). ");
		System.out.println("    -smooth=       Smooth map to the specified FWHM beam size (arcsec). ");
		System.out.println("    -extFilter=    Filter extended structures above the specified FWHM (arcsec). ");
		System.out.println("    -fftFilter=    Same as -extFilter but using FFT. ");
		System.out.println("    -deconvolve[=] Deconvolve image. Optional argument is either:");
		System.out.println("                     BeamFWHM[:replacementFWHM]");
		System.out.println("                     <BeamFITSFile>[:replacementFWHM]");
		System.out.println();
		System.out.println("  [Masking Options]");
		System.out.println("    -mask=         Specify a region mask file.");
		System.out.println("    -masks=        Perform tasks with mask files. Defined tasks are:");
		System.out.println("                        forget                Delete existing masks.");
		System.out.println("                        add/remove            Add/Remove as Gaussian sources.");
		System.out.println("                        pointadd/pointremove  Add/Remove as point sources.");
		System.out.println("                        flag                  Flag circular regions.");
		System.out.println("                        stack                 Stack sources from mask.");
		System.out.println("                        match                 Match sources to mask.");
		System.out.println("    -flag=		   Specify a FITS image for image flagging.");
		System.out.println("    -profile       Obtain radial profiles around the sources.");
		System.out.println();
		System.out.println("  [FITS Header]");
		System.out.println("    -source=       Change the source name.");
		System.out.println("    -Jy=           Specify the voltage response for a 1 Jy point source.");	
		System.out.println("    -shift=        Shift map alignment by the comma separated offsets (arcsec).");
		System.out.println("    -changeKey=    Change an entry in the FITS header (key:value)");
		System.out.println("    -printHeader   Print the FITS image header.");
		System.out.println();
		System.out.println("  [Miscellaneous]");
		System.out.println("    -noise=        Select the source of the noise estimate. ('data' or 'image')");
		System.out.println("    -regrid=       Regrid image to the specified grid size (arcsec).");
		System.out.println("    -correct=      Apply flux corrections to the specified FWHM source(s).");
		System.out.println("    -scale=        Scale the map and noise with the specified factor.");
		System.out.println("    -rmsScale=     Scale the map noise with the specified factor.");
		System.out.println("    -offset=       Add a specified offset (in map units).");
		System.out.println("    -origin=       Comma separated offsets or coordinates of the new origin.");
		System.out.println("    -unit=         Specify the output map unit.");		
		System.out.println("    -random        Creates an unsmoothed random map based on the supplied map.");
		System.out.println("    -replace=      <plane>:<file> to replace an image <plane> ('flux','weight',");
		System.out.println("                   'time' or 'rms') with the FITS image from <file>.");
		System.out.println("    -help          Provides this help screen.");
		System.out.println();
	}
}

