package com.thalesgroup.rtrtunit.converter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.jenkinsci.lib.dtkit.util.converter.ConversionException;
import com.thalesgroup.rtrtunit.errreader.ErrorReader;
import com.thalesgroup.rtrtunit.junit.Failure;
import com.thalesgroup.rtrtunit.junit.ObjectFactory;
import com.thalesgroup.rtrtunit.junit.Testcase;
import com.thalesgroup.rtrtunit.junit.Testsuite;
import com.thalesgroup.rtrtunit.junit.Testsuites;
import com.thalesgroup.rtrtunit.rioreader.RioFailedVariable;
import com.thalesgroup.rtrtunit.rioreader.RioStructure;
import com.thalesgroup.rtrtunit.rioreader.RioTest;
import com.thalesgroup.rtrtunit.rioreader.SyntaxRioReader;
import com.thalesgroup.rtrtunit.tdcreader.TdcException;
import com.thalesgroup.rtrtunit.tdcreader.TdcReader;

/**
 * Parse the .rio file and the .tdc file to generate a XML file. Use the Java
 * classes generated by jaxb from the junit-1.0.xsd
 * @author Sebastien Barbier
 * @version 1.1
 */
public class RTRTtoXMLConverter {

    /**
     * output file : the junit xml report.
     */
    private File outputXMLFile;

    /**
     * Name of the .rio file to extract .tdc file and given information to the
     * xml report.
     */
    private String nameTest;

    /**
     * Indicates if the current set of tests failed during compilation.
     */
    private boolean testError;

    // JAXB object
    /**
     * Jaxb marshaller.
     */
    private Marshaller marshaller;
    /**
     * Jaxb factory.
     */
    private ObjectFactory objFactory;

    // Junit JAXB object
    /**
     * header testsuites for junit xml report.
     */
    private Testsuites testsuites;
    /**
     * header testsuite for junit xml report.
     */
    private Testsuite testsuite;

    // Error reade
    /**
     * Reader err file.
     */
    private ErrorReader errReader;

    // JavaCC reader
    /**
     * Reader rio file.
     */
    private SyntaxRioReader rioReader;
    /**
     * Rio structure.
     */
    private RioStructure rioData;

    // Tdc Reader
    /**
     * Reader tdc file.
     */
    private TdcReader tdcReader;

    /**
     * Initialization of the files. WARNING: .rio and .tdc must be in the same
     * directory and with the same name.
     * @param inputFile the .rio file
     * @param outputFile the junit-report.xml file
     * @throws TdcException if bad input
     * @throws IOException if bad input
     */
    public RTRTtoXMLConverter(final File inputFile, final File outputFile)
            throws TdcException, IOException {

        nameTest = inputFile.getName().substring(0,
                inputFile.getName().lastIndexOf('.'));

        // Find an error file if exists based on .rio
        String sERR = inputFile.getAbsolutePath().substring(0,
                inputFile.getAbsolutePath().lastIndexOf('.'))
                + ".err";

        File errFile = new File(sERR);

        if (errFile.exists()) {
            testError = true;
            try {
                errReader = new ErrorReader(new FileInputStream(errFile));
                rioData = errReader.read();
            } catch (FileNotFoundException e) {
                System.out.println("The file " + errFile.getAbsolutePath()
                        + " cannot be read");
                e.printStackTrace();
            }
            // XML file
            initJAXB();
            outputXMLFile = outputFile;
            return;
        }

        testError = false;

        // RIO file
        try {
            rioReader = new SyntaxRioReader(new FileInputStream(inputFile));
            rioData = rioReader.read();
        } catch (FileNotFoundException e) {
            System.out.println("The file " + inputFile.getAbsolutePath()
                    + " cannot be read");
            e.printStackTrace();
        }

        // TDC file
        String sTDC = inputFile.getAbsolutePath().substring(0,
                inputFile.getAbsolutePath().lastIndexOf('.'))
                + ".tdc";
        tdcReader = new TdcReader(new File(sTDC));
        tdcReader.generateTable();

        // XML file
        initJAXB();
        outputXMLFile = outputFile;
    }

    /**
     * Initialization of the JAXB context Mainly, initialize the ObjectFactory.
     * @see ObjectFactory
     */
    private void initJAXB() {
        try {
            // WARNING: 2nd argument is necessary to instaure context. Without
            // the relative path is unknown and throwed Exception.
            JAXBContext jaxbContext = JAXBContext.newInstance(
                    "com.thalesgroup.rtrtunit.junit", this.getClass()
                            .getClassLoader());

            marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            objFactory = new ObjectFactory();
        } catch (JAXBException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            /* Showing the error into the Hudson console */
            throw new ConversionException(e.getMessage());
        }

    }

    /**
     * Build the Header of the Junit Report XML file.
     */
    public final void buildHeader() {

        // XML & JAXB
        testsuites = (Testsuites) objFactory.createTestsuites();

        testsuite = objFactory.createTestsuite();
        if (testError) {
            testsuite.setErrors("1");
            testsuite.setFailures("0");
        } else {
            testsuite.setErrors("0");
            testsuite.setFailures(Integer.toString(rioData.getNbErrors()));
        }
        testsuite.setTests(Integer.toString(rioData.getNbTests()));
        testsuite.setName(nameTest);

    }

    /**
     * Build the body of the Junit Report XML file with all the tests performed
     * by RTRT.
     * @throws TdcException if bad match between rio and tdc files
     */
    public final void buildTests() throws TdcException {

        // For errors
        ErrorReport errorReport = null;

        Testcase testcase = objFactory.createTestcase();

        // Read the structure extracted from the RIO File
        // The pointer into .tdc file is moving accordingly to the token

        RioTest currentTest = null;
        if (testError) {
            testcase.setName("Error during compilation or execution");
            currentTest = rioData.getTests().get(0);
            com.thalesgroup.rtrtunit.junit.Error error = objFactory
                    .createError();
            error.setMessage(currentTest.getName());
            error.setType("Error");

            testcase.getError().add(error);
        } else {

            for (int test = 0; test < rioData.getTests().size(); ++test) {
                // If a previous test exists, writing into xml file.
                // WARNING: the last test must be written at the end of the
                // parsing
                if (test != 0) {
                    if (errorReport != null) {
                        // XML & JAXB

                        Failure failure = objFactory.createFailure();
                        failure.setMessage(Integer.toString(currentTest
                                .getNbFailedVariables())
                                + " variables failed");
                        failure.setType("Error");
                        testcase.getFailure().add(failure);

                        testcase.getSystemErr().add(
                                errorReport.getErrorReport());
                    }

                    testsuite.getTestcase().add(testcase);
                    errorReport = null;
                }

                // INIT only know to guarantee the exact value of
                // NbFailedVariables
                // in the if above
                currentTest = rioData.getTests().get(test);

                // {
                // NEW TEST

                // Get the number of the test and find its name into .tdc
                // a. The number of the test is in the currentTest variable
                // b. The name of the test is stored into the tdcReader
                testcase = objFactory.createTestcase();
                testcase.setClassname(tdcReader.getTestedServiceName(currentTest.getName()));
                testcase.setName(tdcReader.getTestName(currentTest.getName()));

                // }
                // {
                // TIME
                testcase.setTime(currentTest.getTime());
                // }

                // {
                // VARIABLES
                if (currentTest.getNbFailedVariables() > 0) {
                    // An error occurred with some variables
                    errorReport = new ErrorReport();

                    for (int failedVar = 0; failedVar < currentTest
                            .getFailedVariables().size(); ++failedVar) {
                        RioFailedVariable var = currentTest
                                .getFailedVariables().get(failedVar);

                        boolean isPointerVariable;
                        String nameCurrentVar;
                        isPointerVariable = tdcReader.isPointerVariable(
                                currentTest.getName(), var.getName());
                        nameCurrentVar = tdcReader.getVariableName(currentTest
                                .getName(), var.getName());

                        if (isPointerVariable) {
                            errorReport.addPointerError(nameCurrentVar);
                        } else {
                            errorReport.addVariableError(nameCurrentVar, var
                                    .getGivenValue(), var.getExpectedValue());
                        }
                    }
                }
                // else no error for this variable
                // }
            }

            // Must write the last test!

            if (errorReport != null) {

                Failure failure = objFactory.createFailure();
                failure.setMessage(Integer.toString(currentTest
                        .getNbFailedVariables())
                        + " variables failed");
                failure.setType("Error");

                testcase.getFailure().add(failure);
                testcase.getSystemErr().add(errorReport.getErrorReport());
            }

        }

        testsuite.getTestcase().add(testcase);

    }

    /**
     * Close and write the XML file.
     */
    public final void writeXML() {
        try {
            // XML and JAXB
            testsuites.getTestsuite().add(testsuite);
            marshaller.marshal(testsuites, new FileOutputStream(outputXMLFile));

        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            /* Showing the error into the Hudson console */
            throw new ConversionException("The file "
                    + outputXMLFile.getAbsolutePath() + " does not exist");
        } catch (JAXBException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            /* Showing the error into the Hudson console */
            throw new ConversionException(e.getMessage());
        }
    }

}
