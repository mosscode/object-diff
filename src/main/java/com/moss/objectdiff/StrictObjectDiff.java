/**
 * Copyright (C) 2013, Moss Computing Inc.
 *
 * This file is part of object-diff.
 *
 * object-diff is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * object-diff is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with object-diff; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 */
package com.moss.objectdiff;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A useful testing tool to detect differences between two hierarchies of objects
 */
public class StrictObjectDiff {

	private List<String> descendIntoClasses = new ArrayList<String>();
	private List<String> valueClasses = new ArrayList<String>();
	
	private List<Field> ignoreFields = new ArrayList<Field>();
	private boolean ignoreBigDecimalTrailingZeros = true;
	private Integer maxBigDecimalScale=null;
	
	public void addValueType(Class clazz){
		valueClasses.add(clazz.getName());
	}
	
	public void addDescendIntoClass(String className) {
		descendIntoClasses.add(className);
	}

	public void addDescendIntoClass(Class clazz) {
		if(Enum.class.isAssignableFrom(clazz))
			throw new IllegalArgumentException("Cannot descent into enum types");
		
		descendIntoClasses.add(clazz.getName());
	}

	public void addIgnoreField(Field field) {
		ignoreFields.add(field);
	}

	public void addIgnoreField(Class clazz, String fieldName) throws NoSuchFieldException {
		ignoreFields.add(clazz.getDeclaredField(fieldName));
	}

	public List<FieldDifference> getDifferences(Object o1, Object o2) throws Exception {
		return getDifferences(new ArrayList<String>(), new LinkedList<Object>(), o1, o2);
	}
	
	private List<FieldDifference> getDifferences(List<String> descentPath, List<Object> descentStack, Object o1, Object o2) throws Exception {
		if(descentStack.contains(o1)) return Collections.emptyList();
		else descentStack.add(o1);
		
		if (o1 == null || o2 == null){
			throw new RuntimeException("objects must not be null (" + o1 + ", " + o2 + ")");
		}else if(o1.getClass() != o2.getClass()){
			throw new ComparisonError("objects must be of same type (" + o1.getClass().getName() + ", " + o2.getClass().getName() + ")", descentPath, descentStack);
		}

		List<FieldDifference> differences = new ArrayList<FieldDifference>();

		for (Field field : o1.getClass().getDeclaredFields()) {
			if (ignoreFields.contains(field)) {
				continue;
			}

			boolean wasAccessible = field.isAccessible();
			field.setAccessible(true);

			Object value1 = field.get(o1);
			Object value2 = field.get(o2);

			List<String> nextDescentPath = new ArrayList<String>(descentPath);
			nextDescentPath.add(field.getName());

			if (value1 == null && value1 == value2) {
				continue; // both null
			}
			else if (value1 == null && value2 != null) {
				differences.add(new FieldDifference(nextDescentPath, "null", value2));
				continue;
			}
			else if (value1 != value2 && value2 == null) {
				differences.add(new FieldDifference(nextDescentPath, value1, "null"));
				continue;
			}

			if (value1 instanceof Iterable) {
				differences.addAll(getDifferences(nextDescentPath, descentStack, (Iterable)value1, (Iterable)value2));
			}
			else if (descendIntoClasses.contains(value1.getClass().getName())) {
				differences.addAll(getDifferences(nextDescentPath, descentStack, value1, value2));
			}else if(value1.getClass()==BigDecimal.class){
				if(!compareBigDecimals(value1, value2)){
					differences.add(new FieldDifference(nextDescentPath, value1, value2));
				}
			}
			else if (valueClasses.contains(value1.getClass().getName())){
				if(!value1.equals(value2)){
					differences.add(new FieldDifference(nextDescentPath, value1, value2));
				}
			}else {
				throw new ComparisonError("Error: the following class was not classified as value|descend-into: " + value1.getClass().getName(), descentPath, descentStack);
			}

			field.setAccessible(wasAccessible);
		}
		
		descentStack.remove(o1);
		return differences;
	}
	
	
	public static class ComparisonError extends RuntimeException {
		public ComparisonError(String message, List<String> path, List<Object> stack) {
			super(message + "\n  PATH: " + print(path, ".") + "\n  STACK: " + print(stack, "\n"));
		}
		
		private static String print(List<? extends Object> stack, String separator){
			StringBuffer text = new StringBuffer();
			for(int x=0;x<stack.size();x++){
				if(x!=0)
					text.append(separator);
				text.append(stack.get(x));
			}
			return text.toString();
		}
	}
	
		
	private boolean compareBigDecimals(Object aObj, Object bObj){
		BigDecimal a = (BigDecimal)aObj;
		BigDecimal b = (BigDecimal)bObj;
		
		return compareBigDecimals(a, b);
	}
	
	public boolean compareBigDecimals(BigDecimal a, BigDecimal b){
		
		if(ignoreBigDecimalTrailingZeros){
			a = a.stripTrailingZeros();
			b = b.stripTrailingZeros();
		}
		
		if(maxBigDecimalScale!=null){
			a = a.setScale(maxBigDecimalScale, BigDecimal.ROUND_HALF_UP);
			b = b.setScale(maxBigDecimalScale, BigDecimal.ROUND_HALF_UP);
		}
		
		return a.equals(b);
	}
	
	public void setMaxBigDecimalScale(int precision){
		maxBigDecimalScale = precision;
	}
	public void setIgnoreBigDecimalTrailingZeros(
			boolean ignoreBigDecimalTrailingZeros) {
		this.ignoreBigDecimalTrailingZeros = ignoreBigDecimalTrailingZeros;
	}

	private List<FieldDifference> getDifferences(List<String> descentPath, List<Object> descentStack, Iterable iterable1, Iterable iterable2) throws Exception {
		List<FieldDifference> differences = new ArrayList<FieldDifference>();

		Iterator itr1 = iterable1.iterator();
		Iterator itr2 = iterable2.iterator();

		int index = 0;
		while (true) {
			if (itr1.hasNext() != itr2.hasNext()) {
				int firstIndex = index;
				while (itr1.hasNext()) { itr1.next(); firstIndex++; }

				int secondIndex = index;
				while (itr2.hasNext()) { itr2.next(); secondIndex++; }

				List<String> nextDescentPath = new ArrayList<String>(descentPath);
				String currentFieldName = nextDescentPath.get(nextDescentPath.size() - 1);
				currentFieldName = currentFieldName + ".size()";
				nextDescentPath.set(nextDescentPath.size() - 1, currentFieldName);

				differences.add(new FieldDifference(nextDescentPath, firstIndex, secondIndex));
				break;
			}
			else if (!itr1.hasNext()) {
				break;
			}
			else {
				Object value1 = itr1.next();
				Object value2 = itr2.next();

				List<String> nextDescentPath = new ArrayList<String>(descentPath);
				String currentFieldName = nextDescentPath.get(nextDescentPath.size() - 1);
				currentFieldName = currentFieldName + "[" + index + "]";
				nextDescentPath.set(nextDescentPath.size() - 1, currentFieldName);

				differences.addAll(getDifferences(nextDescentPath, descentStack, value1, value2));

				index++;
			}
		}

		return differences;
	}

}
