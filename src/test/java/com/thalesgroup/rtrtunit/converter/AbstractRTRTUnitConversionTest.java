package com.thalesgroup.rtrtunit.converter;

import java.io.File;
import java.net.URISyntaxException;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.Before;

import org.jenkinsci.lib.dtkit.model.InputMetric;
import org.jenkinsci.lib.dtkit.model.InputMetricFactory;
import org.jenkinsci.lib.dtkit.util.validator.ValidationException;
import com.thalesgroup.rtrtunit.tdcreader.TdcException;

public class AbstractRTRTUnitConversionTest {

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
        XMLUnit.setIgnoreComments(true);
    }

    public void conversionAndValidation(Class<? extends InputMetric> classType,
            String inputRioPath, String expectedResultPath) throws Exception {

        InputMetric inputMetric = InputMetricFactory.getInstance(classType);
        File outputXMLFile = File.createTempFile("result", "xml");

        File inputRioFile = new File(this.getClass().getResource(inputRioPath)
                .toURI());

        // The input file must be valid
        Assert.assertTrue(inputMetric.validateInputFile(inputRioFile));

        inputMetric.convert(inputRioFile, outputXMLFile);
        Diff myDiff = new Diff(XMLUtil.readXmlAsString(new File(this.getClass()
                .getResource(expectedResultPath).toURI())), XMLUtil
                .readXmlAsString(outputXMLFile));
        Assert.assertTrue(
        		"Conversion failed, see: " + outputXMLFile.getAbsolutePath(),
        		myDiff.similar());

        // The generated output file must be valid
        Assert.assertTrue(inputMetric.validateOutputFile(outputXMLFile));

        outputXMLFile.deleteOnExit();
    }
    
    public void conversion(Class<? extends InputMetric> classType,
            String inputRioPath) throws Exception {

        InputMetric inputMetric = InputMetricFactory.getInstance(classType);
        File outputXMLFile = File.createTempFile("result", "xml");

        File inputRioFile = new File(this.getClass().getResource(inputRioPath)
                .toURI());

        // The input file must be valid
        Assert.assertTrue(inputMetric.validateInputFile(inputRioFile));

        inputMetric.convert(inputRioFile, outputXMLFile);

        // The generated output file must be valid
        Assert.assertTrue(inputMetric.validateOutputFile(outputXMLFile));

        outputXMLFile.deleteOnExit();
    }

    public void failedInputValidation(Class<? extends InputMetric> classType,
            String inputRioPath) throws URISyntaxException {

        InputMetric inputMetric = InputMetricFactory.getInstance(classType);
        File inputRioFile = new File(this.getClass().getResource(inputRioPath)
                .toURI());

        boolean valid = true;

        try {
            valid = inputMetric.validateInputFile(inputRioFile);
        } catch (ValidationException e) {
            // No display of the exceptions generated by the checker.
        }
        Assert.assertFalse(valid);
    }

    public void failedConversion(Class<? extends InputMetric> classType,
            String inputRioPath) throws Exception {

        File outputXMLFile = File.createTempFile("result", "xml");

        File inputRioFile = new File(this.getClass().getResource(inputRioPath)
                .toURI());

        RTRTtoXMLConverter converter = null;
        TdcException exception = null;

        try {
            converter = new RTRTtoXMLConverter(inputRioFile, outputXMLFile);
        } catch (TdcException e) {
            exception = e;
        }

        converter.buildHeader();
        try {
            converter.buildTests();
        } catch (TdcException e) {
            exception = e;
        }

        Assert.assertNotNull(exception);

        outputXMLFile.deleteOnExit();
    }

}
