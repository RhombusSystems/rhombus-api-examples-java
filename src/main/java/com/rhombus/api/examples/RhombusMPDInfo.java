package com.rhombus.api.examples;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Contains relevant MPD info to download video from Rhombus.
 */
public class RhombusMPDInfo {
    /**
     * The pattern containing "$Number$" which can be replaced with an index that is incremented for each segment.
     */
    public String segmentPattern;

    /**
     * The string which is appended to the end of the mpd URI to get the start mp4 file.
     */
    public String segmentInitString;

    /**
     * The starting index which will be incremented for each segment.
     */
    public int startIndex;

    /**
     * Creates a Rhombus MPD info object from a raw XML string of an MPD doc.
     *
     * @param rawMPDDoc The raw MPD doc string.
     */
    RhombusMPDInfo(String rawMPDDoc) throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        final DocumentBuilder builder;
        builder = factory.newDocumentBuilder();

        final ByteArrayInputStream input = new ByteArrayInputStream(rawMPDDoc.getBytes("UTF-8"));

        final Document doc = builder.parse(input);
        final Element MPD = doc.getDocumentElement();
        final Element period = (Element) MPD.getElementsByTagName("Period").item(0);
        final Element adaptationSet = (Element) period.getElementsByTagName("AdaptationSet").item(0);
        final Element segmentTemplate = (Element) adaptationSet.getElementsByTagName("SegmentTemplate").item(0);

        segmentPattern = segmentTemplate.getAttribute("media");
        segmentInitString = segmentTemplate.getAttribute("initialization");
        startIndex = Integer.parseInt(segmentTemplate.getAttribute("startNumber"));
    }
}
