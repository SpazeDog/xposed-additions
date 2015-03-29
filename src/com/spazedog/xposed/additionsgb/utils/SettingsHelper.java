/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2015 Daniel Bergløv
 *
 * Xposed Additions is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Xposed Additions is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Xposed Additions. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.xposed.additionsgb.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/*
 * This class defines a common structure for data while traveling across IPC. 
 * It defines the same structure when adding data to a preference file or database and adds 
 * tools to make this procedure much easier to work with.
 * 
 * Due to SELinux restrictions in Android 5 we needed a new way to deal with data. 
 * At the same time a common class and structure makes it easier to adapt future issues 
 * if such should arise. And also merging all data handling in a shared class simply looks better. 
 */

public class SettingsHelper {
	public static final Integer SCHEMA_VERSION = 2;
	
	public static class Type {
		public static final int UNKNOWN = -2;
		public static final int NULL = -1;
		public static final int STRING = 1;
		public static final int INTEGER = 2;
		public static final int BOOLEAN = 3;
		public static final int LIST = 4;

		public static int getType(Object object) {
			return 
				object == null ? NULL : 
				object instanceof String ? STRING : 
				object instanceof Integer ? INTEGER : 
				object instanceof Boolean ? BOOLEAN : 
				object instanceof ArrayList<?> ? LIST : UNKNOWN;
		}
		
		public static String getIdentifier(Integer type) {
			switch (type) {
				case LIST: return "array";
				case BOOLEAN: return "boolean";
				case INTEGER: return "int";
				case STRING: return "string";
				case NULL: return "null";
				default: return "unknown";
			}
		}
	}
	
	public static class SettingsData implements Parcelable {
		protected Map<String, Object> mData = new HashMap<String, Object>();
		protected Set<String> mPersistent = new HashSet<String>();
		protected Boolean mHasChanges = false;
		
		public static final Parcelable.Creator<SettingsData> CREATOR = new Parcelable.Creator<SettingsData>() {
			@Override
			public SettingsData createFromParcel(Parcel in) {
				return new SettingsData(in);
			}
			
			@Override
			public SettingsData[] newArray(int size) {
				return new SettingsData[size];
			}
		};
		
		@Override
		public void writeToParcel(Parcel out, int flags) {
			synchronized (mData) {
				out.writeInt(mData.size());
				
				for (String key : mData.keySet()) {
					Object value = mData.get(key);
					Integer type = Type.getType(value);

					out.writeInt(type);
					out.writeString(key);

					switch (type) {
						case Type.LIST: 
							out.writeList((ArrayList<?>) value); break;
							
						case Type.BOOLEAN: 
							out.writeInt((Boolean) value ? 1 : 0); break;
							
						case Type.INTEGER: 
							out.writeInt((Integer) value); break;
							
						case Type.STRING: 
							out.writeString((String) value);
							
					}
				}
				
				out.writeInt(mPersistent.size());
				
				for (String key : mPersistent) {
					out.writeString(key);
				}
				
				out.writeInt(mHasChanges ? 1 : 0);
			}
		}
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		public SettingsData() {}
		
		public SettingsData(Map<String, Object> data) {
			mData = data;
		}
		
		public SettingsData(Parcel in) {
			Integer dataSize = in.readInt();
			
			for (int i=0; i < dataSize; i++) {
				Integer type = in.readInt();
				String key = in.readString();

				switch (type) {
					case Type.LIST: 
						mData.put(key, in.readArrayList(ArrayList.class.getClassLoader())); break;
						
					case Type.BOOLEAN: 
						mData.put(key, in.readInt() == 1); break;
						
					case Type.INTEGER: 
						mData.put(key, in.readInt()); break;
						
					case Type.STRING: 
						mData.put(key, in.readString()); break;
						
					case Type.NULL: 
						mData.put(key, null);
						
				}
			}
			
			Integer persistSize = in.readInt();
			
			for (int i=0; i < persistSize; i++) {
				mPersistent.add(in.readString());
			}
			
			mHasChanges = in.readInt() == 1;
		}
		
		public Boolean changed() {
			return mHasChanges;
		}
		
		public Boolean contains(String key) {
			return mData.containsKey(key);
		}
		
		public Integer type(String key) {
			return Type.getType( mData.get(key) );
		}
		
		public Integer size() {
			return mData.size();
		}
		
		public Boolean persistent(String key) {
			return mPersistent.contains(key);
		}
		
		public Set<String> keySet() {
			return mData.keySet();
		}
		
		public void put(String key, Object value) {
			put(key, value, false);
		}
		
		public void put(String key, Object value, Boolean persistent) {
			synchronized (mData) {
				if (persistent) {
					mPersistent.add(key);
				}
				
				mData.put(key, value);
				mHasChanges = true;
			}
		}
		
		public Object remove(String key) {
			synchronized (mData) {
				mPersistent.remove(key);
				mHasChanges = true;
				
				return mData.remove(key);
			}
		}
		
		public Object get(String key) {
			return mData.get(key);
		}
		
		public String getString(String key) {
			return (String) mData.get(key);
		}
		
		public Integer getInteger(String key) {
			return (Integer) mData.get(key);
		}
		
		public Boolean getBoolean(String key) {
			return (Boolean) mData.get(key);
		}
		
		@SuppressWarnings("unchecked")
		public ArrayList<Integer> getIntegerList(String key) {
			return (ArrayList<Integer>) mData.get(key);
		}
		
		@SuppressWarnings("unchecked")
		public ArrayList<String> getStringList(String key) {
			return (ArrayList<String>) mData.get(key);
		}
	}
	
	public static void pack(SettingsData in) {
		Map<String, Object> oldData = in.mData;
		Map<String, Object> newData = new HashMap<String, Object>();
		
		for (String key : oldData.keySet()) {
			Object value = oldData.get(key);
			Integer type = Type.getType(value);
			String packKey = "@" + SCHEMA_VERSION + "|" + type + "|";
			
			switch (type) {
				case Type.LIST: 
					for (int i=0; i < ((ArrayList<?>) value).size(); i++) {
						Object listValue = ((ArrayList<?>) value).get(i);
						Integer listType = Type.getType(listValue);
						String listKey = listType + "#" + i + "," + ((ArrayList<?>) value).size() + "|" + key;
						
						switch (listType) {
							case Type.INTEGER:
								newData.put(packKey + listKey, ((Integer) listValue).toString()); break;
								
							case Type.STRING:
							case Type.NULL:
								newData.put(packKey + listKey, listType == Type.STRING ? listValue : "");
						}
					}
					
					break;
					
				case Type.BOOLEAN: 
					newData.put(packKey + key, (Boolean) value ? "1" : "0"); break;
					
				case Type.INTEGER:
					newData.put(packKey + key, ((Integer) value).toString()); break;
					
				case Type.STRING:
				case Type.NULL:
					newData.put(packKey + key, type == Type.STRING ? value : "");
			}
		}
		
		in.mData = newData;
	}
	
	public static void unpack(SettingsData in) {
		Map<String, Object> oldData = in.mData;
		Map<String, Object> newData = new HashMap<String, Object>();
		String persistent = null;
		
		for (String metaData : oldData.keySet()) {
			if (metaData.indexOf("@") == 0) {
				persistent = unpackItem(newData, metaData, oldData.get(metaData));
				
			} else {
				persistent = unpackItemCombatV1(newData, metaData, oldData.get(metaData));
			}
			
			if (persistent != null) {
				in.mPersistent.add(persistent);
			}
		}
		
		in.mData = newData;
	}
	
	/*
	 * Schema:
	 * 		
	 * 		Arrays: "@schemaVersion|dataType|itemDataType#location,totalArraySize|name"
	 * 		Default: "@schemaVersion|dataType|name"
	 */
	@SuppressWarnings("unchecked")
	private static String unpackItem(Map<String, Object> data, String key, Object value) {
		Integer schemaPos = key.indexOf("|");
		Integer typePos = key.indexOf("|", schemaPos+1);
		Integer type = Integer.valueOf( key.substring(schemaPos+1, typePos) );
		String itemKey = key.substring(typePos+1);
		
		switch (type) {
			case Type.LIST: 
				Integer listTypePos = itemKey.indexOf("#");
				Integer listSizePos = itemKey.indexOf(",");
				Integer listNamePos = itemKey.indexOf("|");
				Integer listItemType = Integer.valueOf( itemKey.substring(0, listTypePos) );
				Integer listItemLoc = Integer.valueOf( itemKey.substring(listTypePos+1, listSizePos) );
				Integer listTotalSize = Integer.valueOf( itemKey.substring(listSizePos+1, listNamePos) );
				String listName = (String) itemKey.substring(listNamePos+1);
				
				switch (listItemType) {
					case Type.INTEGER:
						ArrayList<Integer> intList = (ArrayList<Integer>) data.get(listName);
						
						if (intList == null) {
							intList = (ArrayList<Integer>) expandList(new ArrayList<Integer>(listTotalSize), listTotalSize);
							
						} else if (intList.size() < listTotalSize) {
							intList = (ArrayList<Integer>) expandList(intList, listTotalSize);
						}
						
						intList.set(listItemLoc, (Integer) value);
						data.put(listName, intList);
						
						break;
						
					case Type.STRING:
					case Type.NULL:
						ArrayList<String> stringList = (ArrayList<String>) data.get(listName);
						
						if (stringList == null) {
							stringList = (ArrayList<String>) expandList(new ArrayList<String>(listTotalSize), listTotalSize);
							
						} else if (stringList.size() < listTotalSize) {
							stringList = (ArrayList<String>) expandList(stringList, listTotalSize);
						}
						
						stringList.set(listItemLoc, listItemType == Type.NULL ? null : (String) value);
						data.put(listName, stringList);
						
						break;
						
					default:
						return null;
				}
				
				return listName;
				
			case Type.BOOLEAN: 
				data.put(itemKey, Integer.valueOf( (String) value ) == 1); break;
				
			case Type.INTEGER:
				data.put(itemKey, Integer.valueOf( (String) value )); break;
				
			case Type.STRING:
				data.put(itemKey, value); break;
				
			case Type.NULL:
				data.put(itemKey, null); break;
				
			default:
				return null;
		}
		
		return itemKey;
	}
	
	/*
	 * Convert config files from <= 3.5.2 (64)
	 * 
	 * Schema: 
	 * 
	 * 		Arrays = "#location:name"
	 */
	@SuppressWarnings("unchecked")
	private static String unpackItemCombatV1(Map<String, Object> data, String key, Object value) {
		if (key.indexOf("#") == 0) {
			Integer listNamePos = key.indexOf(":");
			Integer listItemLoc = Integer.valueOf( key.substring(1, listNamePos) ) - 1;
			String listName = key.substring(listNamePos+1);
			ArrayList<String> stringList = (ArrayList<String>) data.get(listName);
			
			if (stringList == null) {
				stringList = (ArrayList<String>) expandList(new ArrayList<String>(listItemLoc+1), listItemLoc+1);
				
			} else if (stringList.size() < listItemLoc+1) {
				stringList = (ArrayList<String>) expandList(stringList, listItemLoc+1);
			}
			
			stringList.set(listItemLoc, "@null".equals(value) ? null : (String) value);
			data.put(listName, stringList);
			
			return listName;
			
		} else {
			data.put(key, "@null".equals(value) ? null : value);
		}
		
		return key;
	}
	
	private static <T> List<T> expandList(List<T> list, Integer newSize) {
		for (int i = list.size(); i < newSize; i++) {
			list.add(null);
		}
		
		return list;
	}
}
