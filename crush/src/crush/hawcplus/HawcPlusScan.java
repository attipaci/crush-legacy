/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.hawcplus;

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import crush.sofia.SofiaHeaderData;
import crush.sofia.SofiaScan;

public class HawcPlusScan extends SofiaScan<HawcPlus, HawcPlusIntegration> {	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3732251029215505308L;
	
	String priorPipelineStep;
	
	public HawcPlusScan(HawcPlus instrument) {
		super(instrument);
	}

	@Override
	public HawcPlusIntegration getIntegrationInstance() {
		return new HawcPlusIntegration(this);
	}

	@Override
	public void parseHeader(Header header) throws Exception {
		super.parseHeader(header);
		priorPipelineStep = SofiaHeaderData.getStringValue(header, "PROCLEVL");
	}
	
	@Override
	public void editScanHeader(Header header) throws HeaderCardException {
		super.editScanHeader(header);
		if(priorPipelineStep != null) header.addLine(new HeaderCard("PROCLEVL", priorPipelineStep, "Last processing step on input scan."));	
	}
	
}
