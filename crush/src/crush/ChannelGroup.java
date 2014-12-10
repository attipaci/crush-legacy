/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.lang.reflect.*;
import java.util.*;

import kovacs.util.Copiable;

public class ChannelGroup<ChannelType extends Channel> extends ArrayList<ChannelType> 
implements Copiable<ChannelGroup<ChannelType>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -922794075467674753L;
	
	private String name;
	
	public ChannelGroup(String name) {
		this.name = name;
	}
	
	public ChannelGroup(String name, int size) {
		super(size);
		this.name = name;
	}
	
	public ChannelGroup(String name, Vector<? extends ChannelType> channelList) {
		this(name);
		addAll(channelList);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public ChannelGroup<ChannelType> copy() {
		ChannelGroup<ChannelType> copy = (ChannelGroup<ChannelType>) clone();
		copy.clear();
		for(ChannelType channel : this) copy.add((ChannelType) channel.copy());
		return copy;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!super.equals(o)) return false;
		ChannelGroup<?> group = (ChannelGroup<?>) o;
		if(size() != group.size()) return false;
		if(!group.name.equals(name)) return false;
		for(int i=size(); --i >=0; ) if(!group.get(i).equals(get(i))) return false;
		return true;
	}
	
	@Override
	public int hashCode() { return name.hashCode() ^ size(); }

	public String getName() { return name; }
	
	public void setName(String value) { name = value; }

	public ChannelGroup<ChannelType> copyGroup() {
		// All good channels
		ChannelGroup<ChannelType> channels = new ChannelGroup<ChannelType>(name, size());
		channels.addAll(this);
		return channels;
	}
	
	public void kill(int flagPattern) {
		for(Channel channel : this) if(channel.isFlagged(flagPattern)) channel.flag(Channel.FLAG_DEAD);
	}
	
	
	public synchronized boolean slim() {
		final int fromSize = size();
		final int pattern = Channel.FLAG_DEAD | Channel.FLAG_DISCARD;
		for(int i=fromSize; --i >= 0; ) if(get(i).isFlagged(pattern)) remove(i);
		trimToSize();
		return size() < fromSize;
	}

	public void order(final Field field) {
		
		Comparator<ChannelType> ordering = new Comparator<ChannelType>() {
			@Override
			public int compare(ChannelType c1, ChannelType c2) {
				try {
					if(field.getInt(c1) < field.getInt(c2)) return -1;
					else if(field.getInt(c1) > field.getInt(c2)) return 1;
					else return 0;
				}
				catch(IllegalAccessException e) { e.printStackTrace(); }
				return 0;
			}				
		};
			
		Collections.sort(this, ordering);
	}
	
	public static final int DISCARD_ANY_FLAG = 0;
	public static final int DISCARD_ALL_FLAGS = 1;
	public static final int DISCARD_MATCH_FLAGS = 2;
	public static final int KEEP_ANY_FLAG = 3;
	public static final int KEEP_ALL_FLAGS = 4;
	public static final int KEEP_MATCH_FLAGS = 5;
	
	public ChannelGroup<ChannelType> discard(int flagPattern) {
		return discard(flagPattern, DISCARD_ANY_FLAG);
	}
		
		
	public ChannelGroup<ChannelType> discard(int flagPattern, int criterion) {
		
		for(int i=size(); --i >= 0; ) {
			Channel channel = get(i);
			switch(criterion) {
			case DISCARD_ANY_FLAG:
				if(channel.isFlagged(flagPattern)) remove(i); break; 
			case DISCARD_ALL_FLAGS:
				if((channel.flag & flagPattern) == flagPattern) remove(i); break; 
			case DISCARD_MATCH_FLAGS:
				if(channel.flag == flagPattern) remove(i); break;
			case KEEP_ANY_FLAG:
				if(channel.isUnflagged(flagPattern)) remove(i); break; 
			case KEEP_ALL_FLAGS:
				if((channel.flag & flagPattern) != flagPattern) remove(i); break; 
			case KEEP_MATCH_FLAGS:
				if(channel.flag != flagPattern) remove(i); break;
			}	
		}
			
		trimToSize();
		return this;
	}
	
	
}
