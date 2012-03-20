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


package crush.devel;

// TODO message types: error, warning, info, detail, comment
// TODO identation?
// TODO message buffers for each object (lookup) + global
// TODO buffer.flush() release all previously unshown messages...
// TODO console.errors .warnings .info .details .comments


public abstract class Message {
	private Object sender;
	private String message;
	private long time;
	
	public Message(Object o, String text) {
		time = System.currentTimeMillis();
		this.sender = o;
		this.message = text;		
	}
	
	public Object getSender() { return sender; }
	
	public String getMessage() { return message; }
	
	public long getTime() { return time; }
}