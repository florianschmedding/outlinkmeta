/**
 * Copyright (c) 2014, Averbis GmbH. All rights reserved.
 *
 * @author Florian Schmedding
 *
 * Description: TODO
 */

package de.averbis.eucases.outlinkmeta.nutch.util;

import java.util.List;

import org.apache.nutch.indexer.NutchField;

public class NutchFieldComparer {

	private final NutchField field;


	public NutchFieldComparer(NutchField field) {

		this.field = field;
	}


	/**
	 * Compare the wrapped NutchField instance to another object.
	 * 
	 * @return True if the other object is also a NutchField and has the same weight and equal values in the same order. The values are compared with their equals(), too.
	 */
	@Override
	public boolean equals(Object other) {

		if (!(other instanceof NutchField)) {
			return false;
		}
		NutchField field2 = (NutchField) other;
		if (this.field.getWeight() != field2.getWeight()) {
			return false;
		}
		List<Object> values = this.field.getValues();
		List<Object> values2 = field2.getValues();
		if (values.size() != values2.size()) {
			return false;
		}
		for (int i = 0; i < values.size(); i++) {
			if (!values.get(i).equals(values2.get(i))) {
				return false;
			}
		}
		return true;
	}

}
