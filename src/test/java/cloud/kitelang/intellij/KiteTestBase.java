package cloud.kitelang.intellij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

/**
 * Base test class for Kite language plugin tests.
 * Provides common test setup and utilities.
 */
public abstract class KiteTestBase extends BasePlatformTestCase implements HighlightingTestSupport {

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/testData";
    }

    /**
     * Returns the test fixture for use by HighlightingTestSupport.
     */
    @Override
    public CodeInsightTestFixture getFixture() {
        return myFixture;
    }

    /**
     * Configures the fixture with a Kite file containing the given content.
     */
    protected void configureByText(String text) {
        myFixture.configureByText("test.kite", text);
    }

    /**
     * Configures the fixture with a named Kite file.
     */
    protected void configureByText(String fileName, String text) {
        myFixture.configureByText(fileName, text);
    }

    /**
     * Adds a file to the project without opening it in the editor.
     */
    protected void addFile(String fileName, String text) {
        myFixture.addFileToProject(fileName, text);
    }
}
