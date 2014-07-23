package com.spazedog.xposed.additionsgb.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapList<KEY, VALUE> extends HashMap<KEY, VALUE> {

	private static final long serialVersionUID = 6379744889386085501L;
	
	private final List<KEY> mKeys = new ArrayList<KEY>();
	
	@Override
	public VALUE put(KEY key, VALUE value) {
		if (!mKeys.contains(key)) {
			mKeys.add(key);
		}
		
		return super.put(key, value);
	}
	
	@Override
	public void putAll(Map<? extends KEY, ? extends VALUE> map) {
		for (KEY key : map.keySet()) {
			if (!mKeys.contains(key)) {
				mKeys.add(key);
			}
		}
		
		super.putAll(map);
	}
	
	@Override
	public void clear() {
		mKeys.clear();
		super.clear();
	}
	
	@Override
	public VALUE remove(Object key) {
		if (mKeys.contains((KEY) key)) {
			mKeys.remove((KEY) key);
		}
		
		return super.remove(key);
	}
	
	public VALUE removeAt(int location) {
		if (location < size() && location >= 0) {
			return remove( mKeys.get(location) );
		}
		
		return null;
	}
	
	public VALUE getAt(int location) {
		if (location < size() && location >= 0) {
			return get( mKeys.get(location) );
		}
		
		return null;
	}
	
	public int indexOf(Object key) {
		return mKeys.indexOf((KEY) key);
	}
	
	public List<KEY> keyList() {
		return mKeys;
	}
	
	public String joinKeys(String separater) {
		StringBuilder builder = new StringBuilder();
		
		for (int i=0; i < mKeys.size(); i++) {
			if (i > 0 && separater != null) {
				builder.append(separater);
			}
			
			builder.append(mKeys.get(i));
		}
		
		return builder.toString();
	}
}
