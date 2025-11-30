package cloud.kitelang.intellij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Base test class for Kite language plugin tests.
 * Provides common test setup and utilities.
 */
public abstract class KiteTestBase extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/testData";
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
