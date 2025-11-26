package io.kite.intellij.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import io.kite.intellij.KiteIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Color settings page for Kite language.
 * Allows users to customize syntax highlighting colors.
 */
public class KiteColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Keyword", KiteSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("String", KiteSyntaxHighlighter.STRING),
            new AttributesDescriptor("Number", KiteSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Line Comment", KiteSyntaxHighlighter.LINE_COMMENT),
            new AttributesDescriptor("Block Comment", KiteSyntaxHighlighter.BLOCK_COMMENT),
            new AttributesDescriptor("Identifier", KiteSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Type Name", KiteAnnotator.TYPE_NAME),
            new AttributesDescriptor("Function Name", KiteSyntaxHighlighter.FUNCTION_NAME),
            new AttributesDescriptor("Decorator", KiteSyntaxHighlighter.DECORATOR),
            new AttributesDescriptor("Operator", KiteSyntaxHighlighter.OPERATOR),
    };

    @Nullable
    @Override
    public Icon getIcon() {
        return KiteIcons.FILE;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new KiteSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return """
                // Kite Infrastructure as Code
                import * from "common.kite"

                type Environment = "dev" | "staging" | "prod"

                schema DatabaseConfig {
                  string host
                  number port = 5432
                  boolean ssl = true
                }

                @provisionOn(["aws"])
                @tags({Environment: "production"})
                resource S3.Bucket photos {
                  name = "my-photos-bucket"
                  region = "us-east-1"
                }

                component WebServer api {
                  input number port = 8080

                  resource VM.Instance server {
                    size = "t2.micro"
                  }

                  output string endpoint = server.publicIp
                }

                fun calculateCost(number instances) number {
                  var baseCost = 0.10
                  return instances * baseCost
                }

                /* Multi-line comment
                   for documentation */
                for env in ["dev", "prod"] {
                  resource Bucket data {
                    name = "data-${env}"
                  }
                }
                """;
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Kite";
    }
}
