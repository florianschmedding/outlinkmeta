/**
 * Copyright (c) 2014, Averbis GmbH. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * @author Florian Schmedding
 *
 * Description: The counterpart for the outlinkmeta parsefilter. It collects the annotations from the crawlDatum 
 * and adds them to the nutch document.
 */

package de.averbis.eucases.outlinkmeta.nutch.indexer;

import org.apache.hadoop.io.Text;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.Inlinks;
import org.apache.nutch.indexer.IndexingException;
import org.apache.nutch.indexer.IndexingFilter;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.indexer.NutchField;
import org.apache.nutch.parse.Parse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.averbis.eucases.outlinkmeta.nutch.common.AbstractOutlinkMeta;
import de.averbis.eucases.outlinkmeta.nutch.common.OutlinkMetaConfig;

public class OutlinkMetaIndexingFilter extends AbstractOutlinkMeta implements IndexingFilter {

	private final static Logger logger = LoggerFactory.getLogger(OutlinkMetaIndexingFilter.class);


	public OutlinkMetaIndexingFilter() {

		super();
	}


	@Override
	public NutchDocument filter(NutchDocument doc, Parse parse, Text url, CrawlDatum datum, Inlinks inlinks) throws IndexingException {

		// OutlinkMetaIndexingFilter.logger.info("Receiving document {}", url.toString());

		if (doc == null || !(this.shoudProcess(datum))) {
			return doc;
		}
		OutlinkMetaIndexingFilter.logger.info("Processing document {}", url.toString());

		if (this.getIndexBinary()) {
			this.addBinaryContent(doc, parse);
		}

		this.addMetadata(doc, datum);

		return doc;
	}


	/**
	 * Add the binary content that was added to the parse metadata by the indexing filter to the Nutch document. If the binary content is not present in the parse metadata nothing
	 * will be changed.
	 * 
	 * @param doc
	 *            (a field is added if the binary content is present)
	 * @param parse
	 */
	private void addBinaryContent(NutchDocument doc, Parse parse) {

		String binary = parse.getData().getParseMeta().get(OutlinkMetaConfig.BINARY_CONTENT);
		if (binary != null) {
			doc.add(OutlinkMetaConfig.BINARY_CONTENT, binary);
		}
	}


	/**
	 * Add the metadata from the original outlink to the current document to the Nutch document. The metadata is stored in the CrawlDatum.
	 * 
	 * @param doc
	 * @param datum
	 */
	private void addMetadata(NutchDocument doc, CrawlDatum datum) {

		for (String field : this.getFields()) {
			Object value = datum.getMetaData().get(new Text(field));

			// All fields added by outlinkmeta have type NutchField
			if (value instanceof NutchField) {
				NutchField metadata = (NutchField) value;
				// Add each collection entry separately because doc.add() adds the collection elements separately
				// if the field does not yet exists but if the field exists only the whole collection will be added to it.
				for (Object val : metadata.getValues()) {
					doc.add(field, val);
				}
			} else if (value != null) {
				OutlinkMetaIndexingFilter.logger.warn("Field {} should have type NutchField but has type {}", field, value.getClass().getCanonicalName());
			}
			// skip the field if value is null the field is not set
		}
	}


	/**
	 * Check if the CrawlDatum should be processed. A CrawlDatum should only be processed if the field OutlinkMetaConfig.URL_FIELD is set in its metadata.
	 * 
	 * @param datum
	 * @return
	 */
	private boolean shoudProcess(CrawlDatum datum) {

		return datum.getMetaData().get(new Text(this.getUrlField())) instanceof NutchField;
	}

}
