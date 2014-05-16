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
 * Description: A nutch plugin that transfers metadata from the parsed document to an outlink. The metadata tags 
 * and the outlink can be configured. The outlink's metadata will be available in the processing of the linked document.
 */

package de.averbis.eucases.outlinkmeta.nutch.parse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.nutch.indexer.NutchField;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.HtmlParseFilter;
import org.apache.nutch.parse.Outlink;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.protocol.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DocumentFragment;

import de.averbis.eucases.outlinkmeta.nutch.common.AbstractOutlinkMeta;
import de.averbis.eucases.outlinkmeta.nutch.common.OutlinkMetaConfig;

public class OutlinkMetaParseFilter extends AbstractOutlinkMeta implements HtmlParseFilter {

	private final static Logger logger = LoggerFactory.getLogger(OutlinkMetaParseFilter.class);


	public OutlinkMetaParseFilter() {

		super();
	}


	@Override
	public ParseResult filter(Content content, ParseResult parseResult, HTMLMetaTags metaTags, DocumentFragment node) {

		Parse parse = parseResult.get(content.getUrl());

		if (!this.shouldProcess(content, parse)) {
			OutlinkMetaParseFilter.logger.debug("Not processing {}", content.getUrl());
			return parseResult;
		}

		OutlinkMetaParseFilter.logger.info("Processing {}", content.getUrl());

		if (this.getIndexBinary() && this.isOutlinkTarget(content)) {
			this.addBinaryContentToParseMetadata(parse, content.getContent());
		}

		if (this.isOutlinkSource(parse)) {
			this.addParseMetadataToOutlink(content.getBaseUrl(), parse);
		}

		return parseResult;
	}


	/**
	 * Put the base64 encoded binary content into the parse metadata.
	 * 
	 * @param parse
	 * @param content
	 */
	private void addBinaryContentToParseMetadata(Parse parse, byte[] content) {

		// If outlinkmeta is configured to provide the binary content for indexing and the url field is present
		// in the content metadata (see OutlinkMetaScoringFilter) then put the base64-encoded content to the parse metadata.
		parse.getData().getParseMeta().add(OutlinkMetaConfig.BINARY_CONTENT, Base64.encodeBase64String(content));
	}


	/**
	 * 
	 * @param baseUrl
	 * @param parse
	 */
	private void addParseMetadataToOutlink(String baseUrl, Parse parse) {

		Metadata metadata = parse.getData().getParseMeta();
		MapWritable annotations = this.createOutlinkAnnotations(metadata);
		String url = metadata.get(this.getUrlField());

		try {
			// make metadata url absolute
			url = (new URL(new URL(baseUrl), url)).toString();
			// update url in metadata (not really necessary because URL gets replaced in OutlinkMetaScroringFilter.initialScore())
			metadata.set(this.getUrlField(), url);
			Outlink annotatedOutlink = this.createAnnotatedOutlink(url, this.getUrlDescription(), annotations);
			Outlink[] outlinks = parse.getData().getOutlinks();
			parse.getData().setOutlinks(this.addOutlink(outlinks, annotatedOutlink));
		} catch (MalformedURLException e) {
			OutlinkMetaParseFilter.logger.warn("Malformed outlink url: {}", url);
		}
	}


	/**
	 * Creates the metadata for an outlink according to the configured fields. Empty fields are not added.
	 * 
	 * @param metadata
	 *            The metadata created by the previous parsers
	 * @return The metadata for the outlink
	 */
	private MapWritable createOutlinkAnnotations(Metadata metadata) {

		MapWritable md = new MapWritable();

		for (String field : this.getFields()) {
			NutchField nutchField = new NutchField();
			for (String value : metadata.getValues(field)) {
				nutchField.add(value);
			}
			if (nutchField.getValues().size() > 0) {
				md.put(new Text(field), nutchField);
			}
		}

		return md;
	}


	/**
	 * Creates a new outlink with metadata.
	 * 
	 * @param url
	 *            Url for the link
	 * @param description
	 *            Descrition (anchor) for the link
	 * @param annotations
	 *            Metadata for the link
	 * @return The new outlink
	 * @throws MalformedURLException
	 *             If the given url was bad
	 */
	private Outlink createAnnotatedOutlink(String url, String description, MapWritable annotations) throws MalformedURLException {

		Outlink annotatedOutlink = new Outlink(url, description);
		annotatedOutlink.setMetadata(annotations);
		return annotatedOutlink;
	}


	/**
	 * Adds one outlink to an outlink array. The given array will not be modified.
	 * 
	 * @param outlinks
	 *            An array with current outlinks
	 * @param outlink
	 *            The outlink to add
	 * @return A new array containing a copy of the given outlinks and the additional outlink on the last position
	 */
	private Outlink[] addOutlink(Outlink[] outlinks, Outlink outlink) {

		Outlink[] extendedOutlinks = Arrays.copyOf(outlinks, outlinks.length + 1);
		extendedOutlinks[extendedOutlinks.length - 1] = outlink;
		return extendedOutlinks;
	}


	/**
	 * Check whether the document should be processed.
	 * 
	 * @param content
	 * @param parseResult
	 * @return True if the document should be processed.
	 */
	private boolean shouldProcess(Content content, Parse parse) {

		return this.isOutlinkSource(parse) || this.isOutlinkTarget(content);

	}


	/**
	 * Check if the current document is the target of an outlink that was created by outlinkmeta. This is the case if the content metadata contain a value for the key name in
	 * OutlinkMetaConfig.URL_FIELD.
	 * 
	 * The content metadata are modified by the ScoringFilter (passScoreBeforeParsing) of the outlinkmeta plugin.
	 * 
	 * @param content
	 * @return true if the document is a target
	 */
	private boolean isOutlinkTarget(Content content) {

		return StringUtils.isNotEmpty(content.getMetadata().get(this.getUrlField()));
	}


	/**
	 * Check if the current document is the source of an outlink that should be created by outlinkmeta. This is the case if the parse metadata contain a value for the key name in
	 * OutlinkMetaConfig.URL_FIELD.
	 * 
	 * The parse metadata are supposed to be added before the outlinkmeta plugin receives the document.
	 * 
	 * @param parse
	 * @return true if the document is a source
	 */
	private boolean isOutlinkSource(Parse parse) {

		return StringUtils.isNotEmpty(parse.getData().getParseMeta().get(this.getUrlField()));
	}

}
