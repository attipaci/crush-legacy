/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.instrument.mako;

import nom.tam.fits.*;
import crush.fits.HDUReader;
import crush.telescope.cso.CSOIntegration;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.math.Vector2D;

public class MakoIntegration<MakoType extends AbstractMako<? extends AbstractMakoPixel>> extends CSOIntegration<MakoType, MakoFrame> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2439173655341594018L;
	
	public MakoIntegration(MakoScan<MakoType> parent) {
		super(parent);
	}

	@Override
	public void validate() {		
		// Tau is set here...
		super.validate();	
		
		boolean directTau = false;
		if(hasOption("tau")) directTau = option("tau").is("direct"); 
		
		if(!directTau) {		
			double measuredLoad = instrument.getLoadTemperature(); 
			//double eps = (measuredLoad - instrument.excessLoad) / ((MakoScan) scan).ambientT;
			//double tauLOS = -Math.log(1.0-eps);
			
			// TODO
			//info("Tau from bolometers (not used):");
			//printEquivalentTaus(tauLOS * scan.horizontal.sinLat());	
			
			if(!hasOption("excessload")) instrument.excessLoad = measuredLoad - getSkyLoadTemperature();
			//info("Excess optical load on bolometers is " + Util.f1.format(instrument.excessLoad) + " K. (not used)");		
		}
	}
	
	
	@Override
	public MakoFrame getFrameInstance() {
		return new MakoFrame((MakoScan<?>) scan);
	}
		
	
	protected void read(BasicHDU<?>[] HDU, int firstDataHDU) throws Exception {
		
		int nDataHDUs = HDU.length - firstDataHDU, records = 0;
		
		if(hasOption("skiplast")) {
		    warning("Skipping last stream HDU...");
			nDataHDUs--; 
		}
	
		for(int datahdu=0; datahdu<nDataHDUs; datahdu++) records += HDU[firstDataHDU + datahdu].getAxes()[0];

		scan.info("Processing scan data:");
		
		info(nDataHDUs + " HDUs,  " + records + " x " +
				(int)(instrument.integrationTime/Unit.ms) + "ms frames" + " -> " + 
				Util.f1.format(records*instrument.integrationTime/Unit.min) + " minutes total."); 
	
			
		clear();
		ensureCapacity(records);
		for(int t=records; --t>=0; ) add(null);
	
		for(int n=0, startIndex = 0; n<nDataHDUs; n++) {
			BinaryTableHDU hdu = (BinaryTableHDU) HDU[firstDataHDU+n]; 
			
			if(hasOption("chirp")) new MakoChirpReader(hdu, startIndex).read();
			else new MakoReader(hdu, startIndex).read();
			
			startIndex += hdu.getNRows();
		}
		
	}
		
	class MakoReader extends HDUReader {	
		private int offset;

		private byte[] ch;
		private float[] data; //intTime, chop;
		private int[] SN, AZ, EL, dX, dY, AZE, ELE, LST, PA, MJD, ticks; //, UTseconds, UTnanosec;
		private int channels;
		
		private final MakoScan<?> makoscan = (MakoScan<?>) scan;
		
		public MakoReader(TableHDU<?> hdu, int offset) throws FitsException {
			super(hdu);
			this.offset = offset;			

			int cData = hdu.findColumn("Shift");
			channels = table.getSizes()[cData];
			
			data = (float[]) table.getColumn(cData);
			
			SN = (int[]) table.getColumn(hdu.findColumn("Sequence Number"));
			//UTseconds = (int[]) table.getColumn(hdu.findColumn("Detector UTC seconds (2000/1/1)"));
			//UTnanosec = (int[]) table.getColumn(hdu.findColumn("Detector UTC nanoseconds"));
			//intTime = (float[]) table.getColumn(hdu.findColumn("Integration Time"));
			
			ch = (byte[]) table.getColumn(hdu.findColumn("Channel"));
			
			int iAZ = hdu.findColumn("Requested AZ");
			if(iAZ < 0) {
				error("Scan does not seem to contain telescope information. Perhaps it is a lab scan. CRUSH cannot reduce it.");
				throw new IllegalStateException("No telescope information in scan " + getID());
			}
			
			AZ = (int[]) table.getColumn(iAZ);
			EL = (int[]) table.getColumn(hdu.findColumn("Requested EL"));
			AZE = (int[]) table.getColumn(hdu.findColumn("Error In AZ"));
			ELE = (int[]) table.getColumn(hdu.findColumn("Error In EL"));
			PA = (int[]) table.getColumn(hdu.findColumn("Parallactic Angle"));
			LST = (int[]) table.getColumn(hdu.findColumn("LST"));
			dX = (int[]) table.getColumn(hdu.findColumn("X Offset"));
			dY = (int[]) table.getColumn(hdu.findColumn("Y Offset"));
			MJD = (int[]) table.getColumn(hdu.findColumn("Antenna MJD"));
			ticks = (int[]) table.getColumn(hdu.findColumn("N Ticks From Midnight"));
			//chop = (float[]) table.getColumn(hdu.findColumn("CHOP_OFFSET"));
			
		}
	
		@Override
		public Reader getReader() {
			return new Reader() {
				private Vector2D equatorialOffset;
				private boolean isEquatorial = EquatorialCoordinates.class.isAssignableFrom(((MakoScan<?>) scan).scanSystem);
				//AstroTime time = new AstroTime();
				
				@Override
				public void init() { 
					super.init();
					equatorialOffset = new Vector2D();
				}
				
				@Override
				public void processRow(int i) throws FitsException {	
					if((0xff & ch[i]) == 255) return;
					
					final MakoFrame frame = new MakoFrame(makoscan);
					frame.index = i;
					
					frame.parseData(data, i*channels, instrument);

					//time.setMillis(AstroTime.millisJ2000 + 1000L * UTseconds[i] + (UTnanosec[i] / 1000000L));
					//frame.MJD = time.getMJD();	
					
					frame.MJD = MJD[i] + ticks[i] * antennaTick / Unit.day;
					
					// Enforce the calculation of the equatorial coordinates
					frame.equatorial = null;

					frame.horizontal = new HorizontalCoordinates(
							(AZ[i] + AZE[i]) * tenthArcsec,
							(EL[i] + ELE[i]) * tenthArcsec);
					
					final double pa = PA[i] * tenthArcsec;
					frame.sinPA = Math.sin(pa);
					frame.cosPA = Math.cos(pa);

					frame.LST = LST[i] * antennaTick;
			
					frame.frameNumber = SN[i];
					
					if(isEquatorial) {
						frame.horizontalOffset = new Vector2D(
							AZE[i] * frame.horizontal.cosLat() * tenthArcsec,
							ELE[i] * tenthArcsec);
						equatorialOffset.set(dX[i] * tenthArcsec, dY[i] * tenthArcsec);	
					}
					else {
						frame.horizontalOffset = new Vector2D(
							(dX[i] + AZE[i] * frame.horizontal.cosLat()) * tenthArcsec,
							(dY[i] + ELE[i]) * tenthArcsec);
						equatorialOffset.zero();
					}
						
					//frame.chopperPosition.setX(chop[i] * Unit.arcsec);
					//chopZero.add(frame.chopperPosition.getX());
					//chopZero.addWeight(1.0);

					// Add in the scanning offsets...
					if(makoscan.addStaticOffsets) frame.horizontalOffset.add(makoscan.horizontalOffset);		
					
					frame.equatorialToHorizontal(equatorialOffset);
					frame.horizontalOffset.add(equatorialOffset);
	
					set(offset + i, frame);
				}
			};
		}
	}

	
	class MakoChirpReader extends HDUReader {	
		private int offset;

		private byte[] ch;
		private float[] data; //intTime, chop;
		private double[] MJD;
		private int[] SN;
		private float[] AZ, EL, dX, dY, AZE, ELE, LST, PA; // UTseconds, UTnanosec;
		private int channels;
		
		private final MakoScan<?> makoscan = (MakoScan<?>) scan;
		
		public MakoChirpReader(TableHDU<?> hdu, int offset) throws FitsException {
			super(hdu);
			this.offset = offset;			

			int cData = hdu.findColumn("Data");
			if(cData < 0) cData = hdu.findColumn("Shift");
			channels = table.getSizes()[cData];
			
			data = (float[]) table.getColumn(cData);
			
			SN = (int[]) table.getColumn(hdu.findColumn("Serial Number"));
			ch = (byte[]) table.getColumn(hdu.findColumn("Channel"));
			AZ = (float[]) table.getColumn(hdu.findColumn("Azimuth"));
			EL = (float[]) table.getColumn(hdu.findColumn("Elevation"));
			AZE = (float[]) table.getColumn(hdu.findColumn("AZ Error"));
			ELE = (float[]) table.getColumn(hdu.findColumn("EL Error"));
			PA = (float[]) table.getColumn(hdu.findColumn("Parallactic Angle"));
			LST = (float[]) table.getColumn(hdu.findColumn("LST"));
			dX = (float[]) table.getColumn(hdu.findColumn("X Offset"));
			dY = (float[]) table.getColumn(hdu.findColumn("Y Offset"));
			MJD = (double[]) table.getColumn(hdu.findColumn("MJD"));
			
		}
	
		@Override
		public Reader getReader() {
			return new Reader() {
				private Vector2D equatorialOffset;
				private boolean isEquatorial = EquatorialCoordinates.class.isAssignableFrom(((MakoScan<?>) scan).scanSystem);
				//AstroTime time = new AstroTime();
				
				@Override
				public void init() { 
					super.init();
					equatorialOffset = new Vector2D();
				}
				@Override
				public void processRow(int i) throws FitsException {	
					if((0xff & ch[i]) == 255) return;
					
					final MakoFrame frame = new MakoFrame(makoscan);
					frame.index = i;
					
					frame.parseData(data, i*channels, instrument);
	
					frame.MJD = MJD[i];
					
					// Enforce the calculation of the equatorial coordinates
					frame.equatorial = null;

					frame.horizontal = new HorizontalCoordinates(
							AZ[i] * Unit.deg + AZE[i] * Unit.arcsec,
							EL[i] * Unit.deg + ELE[i] * Unit.arcsec);
					
					final double pa = -PA[i] * Unit.deg;
					frame.sinPA = Math.sin(pa);
					frame.cosPA = Math.cos(pa);

					frame.LST = LST[i] * Unit.hourAngle;
			
					frame.frameNumber = SN[i];
					
					if(isEquatorial) {
						frame.horizontalOffset = new Vector2D(
							AZE[i] * frame.horizontal.cosLat() * Unit.arcsec,
							ELE[i] * Unit.arcsec);
						equatorialOffset.set(dX[i] * Unit.arcsec, dY[i] * Unit.arcsec);	
					}
					else {
						frame.horizontalOffset = new Vector2D(
							(dX[i] + AZE[i] * frame.horizontal.cosLat()) * Unit.arcsec,
							(dY[i] + ELE[i]) * Unit.arcsec);
						equatorialOffset.zero();
					}
						
					//frame.chopperPosition.setX(chop[i] * Unit.arcsec);
					//chopZero.add(frame.chopperPosition.getX());
					//chopZero.addWeight(1.0);

					// Add in the scanning offsets...
					if(makoscan.addStaticOffsets) frame.horizontalOffset.add(makoscan.horizontalOffset);		
					
					frame.equatorialToHorizontal(equatorialOffset);
					frame.horizontalOffset.add(equatorialOffset);
	
					set(offset + i, frame);
				}
			};
		}
	}

	
	
	
	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}
	

}
