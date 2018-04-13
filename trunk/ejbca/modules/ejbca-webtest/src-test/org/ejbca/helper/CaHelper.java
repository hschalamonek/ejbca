package org.ejbca.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.ejbca.utils.WebTestUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/**
 * 
 * Certificate Authorities helper class for EJBCA Web Tests.
 * 
 * @version $Id$
 *
 */
public class CaHelper {

    private CaHelper() {}
    
    /**
     * Opens the 'Certificate Authorities' page.
     * 
     * @param webDriver the WebDriver to use
     * @param adminWebUrl the URL of the AdminWeb
     */
    public static void goTo(WebDriver webDriver, String adminWebUrl) {
        webDriver.get(adminWebUrl);
        webDriver.findElement(By.xpath("//li/a[contains(@href,'editcas.jsp')]")).click();
        assertEquals("Clicking 'Certificate Authorities' link did not redirect to expected page",
                WebTestUtils.getUrlIgnoreDomain(webDriver.getCurrentUrl()),
                "/ejbca/adminweb/ca/editcas/editcas.jsp");
    }
    
    /**
     * Adds a new CA. Browser will end up in the edit page for this CA once method is done.
     * 
     * @param webDriver the WebDriver to use
     * @param caName the name of the CA
     */
    public static void add(WebDriver webDriver, String caName) {
        WebElement nameInput = webDriver.findElement(By.xpath("//input[@name='textfieldcaname']"));
        nameInput.sendKeys(caName);
        webDriver.findElement(By.xpath("//input[@name='buttoncreateca']")).click();
    }
    
    /**
     * Selects CA from the list of CAs and clicks on 'Edit CA'
     * 
     * @param webDriver the WebDriver to use
     * @param caName the name of the CA to edit
     */
    public static void edit(WebDriver webDriver, String caName) {
        try {
            Select caList = new Select(webDriver.findElement(By.xpath("//select[@name='selectcas']")));
            caList.selectByVisibleText(caName + ", (Active)");
        } catch (NoSuchElementException e) {
            fail("Could not edit ca: " + caName + ". Was not found in list of CAs");
        }
        webDriver.findElement(By.xpath("//input[@name='buttoneditca']")).click();
    }
    
    /**
     * Saves & Creates the CA
     * 
     * @param webDriver
     */
    public static void save(WebDriver webDriver) {
        webDriver.findElement(By.xpath("//input[@name='buttoncreate']")).click();
    }
    
    /**
     * Sets the CA validity
     * 
     * @param webDriver the WebDriver to use
     * @param validityString (*y *mo *d *h *m *s) or end date of the certificate. E.g. '1y'
     */
    public static void setValidity(WebDriver webDriver, String validityString) {
        WebElement validityInput = webDriver.findElement(By.id("textfieldvalidity"));
        validityInput.sendKeys(validityString);
    }
    
    
    /**
     * Checks that a given CA exists in 'List of Certificate Authorities'.
     * 
     * @param webDriver the WebDriver to use
     * @param caName the name of the Certificate Profile
     */
    public static void assertExists(WebDriver webDriver, String caName) {
        try {
            Select caList = new Select(webDriver.findElement(By.xpath("//select[@name='selectcas']")));
            caList.selectByVisibleText(caName + ", (Active)");
        } catch (NoSuchElementException e) {
            fail(caName + " was not found in the List of Certificate Authorities");
        }
    }
    
}
